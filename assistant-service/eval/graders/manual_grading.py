"""Chấm tay theo rubric (thiết kế 13 mục 13.5 "Chấm tay theo rubric").

Hai bước:
1. `export_sheet`: từ một hay nhiều lần chạy, lấy các case `POLICY`, `HOWTO` của tập test (lần chạy đầu tiên của mỗi
   case), trộn thứ tự, **ẩn cấu hình** (người chấm chỉ thấy mã mù `blind_id`) → CSV có cột trống để điền:
   `kp1..kpN` (2 / 1 / 0 cho từng ý chính), `contradiction` (1 nếu trái `reference`), `unsupported_claims` (số khẳng
   định không có trong đoạn được trích — chỉ số "khẳng định không căn cứ lọt qua", 10 mục 10.2).
   Khoá giải mã (blind_id → lần chạy, case) ghi riêng, người chấm không mở.
2. `score_sheet`: đọc CSV đã điền + khoá → tỉ lệ đạt theo cấu hình. **Đạt** khi mọi ý chính ≥ 1, trung bình ≥ 1,5 và
   không có `contradiction`.
"""

from __future__ import annotations

import csv
import json
import random
from collections import defaultdict
from pathlib import Path

from eval.cases import load_cases

CATEGORIES = {"POLICY", "HOWTO"}
MAX_KEY_POINTS = 6


def _first_runs(run_dir: Path) -> dict[str, dict]:
    traces: dict[str, dict] = {}
    for line in (run_dir / "traces.jsonl").read_text(encoding="utf-8").splitlines():
        trace = json.loads(line)
        if trace.get("run_idx") == 0 and trace.get("turn") == 0:
            traces[trace["case_id"]] = trace
    return traces


def export_sheet(run_dirs: list[Path], out_csv: Path, key_file: Path, seed: int = 13) -> int:
    cases = {case.id: case for case in load_cases(["golden"], "test") if case.category in CATEGORIES}
    rows, keys = [], {}
    for run_dir in run_dirs:
        meta = json.loads((run_dir / "run.json").read_text(encoding="utf-8")).get("meta", {})
        for case_id, trace in _first_runs(run_dir).items():
            case = cases.get(case_id)
            if case is None:
                continue
            expect = case.turns[0].expect
            rows.append({"question": case.turns[0].question, "reference": expect.get("reference", ""),
                         "key_points": expect.get("key_points", [])[:MAX_KEY_POINTS], "answer": trace.get("answer", ""),
                         "docs": ", ".join(dict.fromkeys(trace.get("docs") or [])),
                         "_key": {"run": run_dir.name, "config": meta.get("config", run_dir.name), "case": case_id}})
    random.Random(seed).shuffle(rows)
    header = ["blind_id", "question", "reference", "answer", "docs"]
    header += [f"key_point_{i}" for i in range(1, MAX_KEY_POINTS + 1)]
    header += [f"kp{i}" for i in range(1, MAX_KEY_POINTS + 1)] + ["contradiction", "unsupported_claims", "note"]
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        for index, row in enumerate(rows, start=1):
            blind = f"M{index:03d}"
            keys[blind] = row["_key"] | {"n_key_points": len(row["key_points"])}
            points = row["key_points"] + [""] * (MAX_KEY_POINTS - len(row["key_points"]))
            writer.writerow([blind, row["question"], row["reference"], row["answer"], row["docs"], *points,
                             *[""] * MAX_KEY_POINTS, "", "", ""])
    key_file.write_text(json.dumps(keys, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(rows)


def _passes(scores: list[int], contradiction: bool) -> bool:
    return bool(scores) and min(scores) >= 1 and sum(scores) / len(scores) >= 1.5 and not contradiction


def score_sheet(filled_csv: Path, key_file: Path) -> dict[str, dict]:
    keys = json.loads(key_file.read_text(encoding="utf-8"))
    by_config: dict[str, dict] = defaultdict(lambda: {"graded": 0, "passed": 0, "contradictions": 0,
                                                      "unsupported_claims": 0})
    with filled_csv.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            key = keys.get(row["blind_id"])
            if key is None:
                continue
            raw = [row.get(f"kp{i}", "").strip() for i in range(1, key["n_key_points"] + 1)]
            if not all(raw):
                continue  # chưa chấm xong dòng này
            scores = [int(value) for value in raw]
            contradiction = row.get("contradiction", "").strip() in {"1", "x", "X", "có"}
            stats = by_config[key["config"]]
            stats["graded"] += 1
            stats["passed"] += _passes(scores, contradiction)
            stats["contradictions"] += contradiction
            stats["unsupported_claims"] += int(row.get("unsupported_claims") or 0)
    return {config: stats | {"pass_rate": stats["passed"] / stats["graded"] if stats["graded"] else None}
            for config, stats in by_config.items()}
