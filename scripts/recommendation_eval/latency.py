"""2.7: search latency with a cold and a warm rerank cache (plan of 21/10/2026).

    python scripts/recommendation_eval/latency.py --scenario base

Empties recommendation_rerank_cache in the evaluation database (never the dev database), runs every query of the
scenario twice with V2-full (first cold, then warm) and reads the per-phase timings the backend logs at DEBUG
(PhaseTimer). Writes ket-qua/<scenario>/latency.csv.
"""

import csv
import re
import sys
from collections import defaultdict

import numpy as np

from common import DATABASE, OUT, RESULTS, psql, read_json
from run import run_config

PHASES = ("context", "vector", "rank", "rerank", "explore+reasons", "relax", "log")


def phases(log_name: str) -> dict:
    text = (OUT / f"backend-{log_name}.log").read_text(encoding="utf-8", errors="replace")
    found = defaultdict(list)
    for match in re.finditer(r"(Student|Tutor) search \S+ phases \(ms\): (.*)", text):
        values = {key: int(value) for key, value in re.findall(r"(\S+)=(\d+)", match.group(2))}
        found[(match.group(1), "total")].append(sum(values.values()))
        for phase in PHASES:
            found[(match.group(1), phase)].append(values.get(phase, 0))
    timeouts = len(re.findall(r"(?i)rerank.*(timeout|timed out)", text))
    return {"phases": found, "timeouts": timeouts}


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    if DATABASE in ("ptutor", "ptutor_test"):
        raise SystemExit(f"refusing to empty the rerank cache of {DATABASE}")
    world = read_json(OUT / "world.json")
    psql("DELETE FROM recommendation_rerank_cache;")
    rows = []
    for state in ("cold", "warm"):
        run_config("V2-full", world)
        (OUT / "backend-V2-full.log").replace(OUT / f"backend-latency-{state}.log")
        (OUT / "runs" / "V2-full.json").replace(OUT / "runs" / f"latency-{state}.json")
        measured = phases(f"latency-{state}")
        for (side, phase), values in sorted(measured["phases"].items()):
            values = np.array(values)
            rows.append({"cache": state, "side": side, "phase": phase, "n": len(values),
                         "p50_ms": float(np.percentile(values, 50)), "p95_ms": float(np.percentile(values, 95)),
                         "max_ms": float(values.max())})
        rows.append({"cache": state, "side": "both", "phase": "rerank timeouts", "n": measured["timeouts"],
                     "p50_ms": None, "p95_ms": None, "max_ms": None})
    RESULTS.mkdir(parents=True, exist_ok=True)
    with (RESULTS / "latency.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({k: (f"{v:.0f}" if isinstance(v, float) else v) for k, v in row.items()})
    for row in rows:
        if row["phase"] in ("total", "rerank", "vector", "rerank timeouts"):
            print(row)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
