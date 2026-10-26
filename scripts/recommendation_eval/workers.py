"""Extra A1 workers while a setup waits for the enrichment queue (design 09, 9.10).

The backend calls the LLM one job at a time, so thousands of postings take hours. This script starts a second
backend on another port against the same evaluation database while the queue is long; jobs are claimed with
FOR UPDATE SKIP LOCKED, so the two never take the same job. The helper is stopped when the queue is short or
memory is low, and jobs it left half done go back to the queue. Results do not change: each job is still one call.

Usage: python workers.py [--until-file out/phase2.out] [--port 8091]
(stops when that file says the runs are done; one watcher per helper, each on its own port)
"""
import subprocess
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

import psutil

from common import DATABASE, REPO, psql
from run import BASE, JAR

START_ABOVE, STOP_BELOW = 200, 40   # pending ENRICH jobs
START_FREE_GB, STOP_FREE_GB = 1.8, 0.9
ORPHAN_AFTER = timedelta(minutes=4)  # a claim older than this, with the helper gone, was the helper's
HERE = Path(__file__).resolve().parent
PORT = sys.argv[sys.argv.index("--port") + 1] if "--port" in sys.argv else "8091"
LOG = HERE / "out" / f"backend-worker-{PORT}.log"


def log(message: str) -> None:
    print(f"[{time.strftime('%H:%M')}] {message}", flush=True)


def free_gb() -> float:
    return psutil.virtual_memory().available / 1024 ** 3


def pending() -> int | None:
    try:
        return int(psql("SELECT count(*) FROM ai_enrichment_jobs WHERE job_type = 'ENRICH' AND status = 'PENDING';")
                   .strip() or 0)
    except RuntimeError:
        return None  # the database is being reset or copied


def start_helper() -> subprocess.Popen:
    properties = dict(BASE, **{"server.port": PORT})
    args = ["java", "-Xmx512m", "-jar", str(JAR)] + [f"--{key}={value}" for key, value in properties.items()]
    return subprocess.Popen(args, cwd=REPO / "backend", stdout=open(LOG, "a", encoding="utf-8"),
                            stderr=subprocess.STDOUT)


def release_orphans() -> int:
    # updated_at is the backend's local time without a zone; the database session runs in UTC, so no now() here.
    cutoff = (datetime.now() - ORPHAN_AFTER).strftime("%Y-%m-%d %H:%M:%S")
    text = psql(f"WITH x AS (UPDATE ai_enrichment_jobs SET status = 'PENDING' WHERE job_type = 'ENRICH' "
                f"AND status = 'PROCESSING' AND updated_at < '{cutoff}' RETURNING 1) SELECT count(*) FROM x;")
    return int(text.strip() or 0)


def finished(until: Path | None, offset: int) -> bool:
    """Only lines written after the watcher started count."""
    if until is None or not until.exists():
        return False
    with open(until, "rb") as file:
        file.seek(offset)
        new = file.read().decode("utf-8", errors="replace")
    return "phase 2 runs done" in new or "setup failed" in new


def main() -> int:
    until = Path(sys.argv[sys.argv.index("--until-file") + 1]) if "--until-file" in sys.argv else None
    offset = until.stat().st_size if until is not None and until.exists() else 0
    helper, stopped_at = None, None
    log(f"watching {DATABASE}, helper on port {PORT}")
    try:
        while not finished(until, offset):
            waiting, free = pending(), free_gb()
            if helper is not None and helper.poll() is not None:
                log(f"helper exited with {helper.returncode}")
                helper, stopped_at = None, time.time()
            if helper is None and waiting is not None and waiting > START_ABOVE and free > START_FREE_GB:
                helper = start_helper()
                log(f"helper started: {waiting} pending, {free:.2f} GB free")
            elif helper is not None and (waiting is None or waiting < STOP_BELOW or free < STOP_FREE_GB):
                helper.kill()
                helper.wait()
                helper, stopped_at = None, time.time()
                log(f"helper stopped: {waiting} pending, {free:.2f} GB free")
            if helper is None and stopped_at is not None and time.time() - stopped_at < 15 * 60:
                try:
                    released = release_orphans()
                    if released:
                        log(f"{released} jobs the helper left half done are back in the queue")
                except RuntimeError:
                    pass
            time.sleep(15)
    finally:
        if helper is not None:
            helper.kill()
            log("helper stopped at exit")
    log("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
