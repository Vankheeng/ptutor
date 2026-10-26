"""Searches whose query text could not be embedded in time (design 13, 13.2).

The backend then ranks with the searcher's profile vector instead, so such a search measures the profile, not the
configuration under test: on 22/10/2026 that, not reranking, made the text-only "rerank gain". Since 26/10/2026 the
log context says so (queryVector = PROFILE_FALLBACK); older runs are read from the backend log, where every search
writes one "phases" line in query order and a failed embedding writes a warning before it.
"""

import re
from pathlib import Path

PHASES = re.compile(r"(Student|Tutor) search \S+ phases")
EMBED_FAILED = "AI service /embed failed"
QUEUE_EMPTY = "Rerank worker: queue empty"
QUEUE_BUSY = "Rerank worker: scoring"


def from_contexts(results: dict) -> set | None:
    """Query ids logged as PROFILE_FALLBACK; None when the run predates the field."""
    contexts = {qid: (result.get("queryContext") or {}) for qid, result in results.items()}
    if not any("queryVector" in context for context in contexts.values()):
        return None
    return {qid for qid, context in contexts.items() if context.get("queryVector") == "PROFILE_FALLBACK"}


def from_log(results: dict, log_text: str) -> set | None:
    """Query ids whose search came after a failed embedding; None when the log does not line up with the run."""
    marks, failed = [], False
    for line in log_text.splitlines():
        if EMBED_FAILED in line:
            failed = True
        elif PHASES.search(line):
            marks.append(failed)
            failed = False
    ids = list(results)
    if len(ids) != len(marks):
        return None
    return {qid for qid, mark in zip(ids, marks) if mark}


def flagged(results: dict, log: Path | None) -> set:
    found = from_contexts(results)
    if found is None and log is not None and log.exists():
        found = from_log(results, log.read_text(encoding="utf-8", errors="replace"))
        if found is None:
            print(f"warning: {log.name} does not line up with its run; fallbacks unknown")
    return found or set()


def queue_drained(log_text: str) -> bool:
    """The background rerank worker has nothing left: it never had to score, or it said its queue is empty and has
    not started anything since. Call it once the worker had time to start (its quiet period has passed)."""
    empty, busy = log_text.rfind(QUEUE_EMPTY), log_text.rfind(QUEUE_BUSY)
    return busy < 0 or empty > busy
