"""Ghi các bước (span) của một lượt vào `TurnState.spans` — nguồn số liệu cho `turn_trace` và benchmark B2.

Tên span cố định theo thiết kế 13 mục 13.11: `input_guard`, `rewrite`, `embed_query`, `retrieve_sql`, `rerank`,
`actor#n`, `tool:<tên>#n`, `verify`, `retry_actor`, `retry_verify`, `persist`. Bước không chạy vẫn được ghi với
`status = SKIPPED`, `duration_ms = 0`, để các cấu hình so sánh được trên cùng một danh sách bước.
"""

from __future__ import annotations

import time
from collections.abc import Iterator
from contextlib import contextmanager

from app.agent.state import Span, TurnState

STANDARD_SPANS = [
    ("input_guard", "step"), ("rewrite", "model"), ("embed_query", "retrieval"), ("retrieve_sql", "retrieval"),
    ("rerank", "retrieval"), ("verify", "verify"), ("retry_actor", "model"), ("retry_verify", "verify"),
    ("persist", "step"),
]


@contextmanager
def timed(state: TurnState, name: str, kind: str = "step") -> Iterator[Span]:
    """Đo thời gian một khối lệnh: `with timed(state, "embed_query", "retrieval"): ...`."""
    span = Span(name=name, kind=kind, start_ms=state.elapsed_ms(), duration_ms=0)
    started = time.perf_counter()
    try:
        yield span
    except Exception:
        span.status = "ERROR"
        raise
    finally:
        span.duration_ms = int((time.perf_counter() - started) * 1000)
        state.spans.append(span)


def add_span(state: TurnState, name: str, kind: str, duration_ms: int, *, status: str = "OK", tokens_in: int = 0,
             tokens_out: int = 0, **detail) -> None:
    state.spans.append(Span(name=name, kind=kind, start_ms=max(state.elapsed_ms() - duration_ms, 0),
                            duration_ms=duration_ms, tokens_in=tokens_in, tokens_out=tokens_out, status=status,
                            detail=detail))


def fill_skipped(state: TurnState) -> None:
    present = {span.name for span in state.spans}
    for name, kind in STANDARD_SPANS:
        if name not in present:
            state.spans.append(Span(name=name, kind=kind, start_ms=0, duration_ms=0, status="SKIPPED"))
