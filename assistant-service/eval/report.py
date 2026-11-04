"""Tổng hợp kết quả một lần chạy eval và ghi báo cáo (thiết kế 13 mục 13.6, 13.11).

- Một case **đạt** khi đạt ≥ ⌈k/2⌉ lần trong k lần (đa số); đạt 1/3 hoặc 2/3 = **dao động** (flaky).
- Chỉ số tổng kèm **khoảng tin cậy 95% bằng bootstrap** trên tập case (1.000 lần lấy mẫu lại).
- Recall@6 / MRR lấy từ grader truy xuất của lượt đầu; độ trễ / token từ `turn_trace`.

Ghi vào `eval/reports/<run_id>/`: `run.json` (cho `eval.compare`), `cases.csv`, `traces.jsonl`, `summary.md`.
"""

from __future__ import annotations

import csv
import json
import math
import random
from collections import defaultdict
from collections.abc import Sequence
from pathlib import Path
from statistics import mean
from typing import Any

from eval.cases import Case
from eval.runner import CaseRun

BOOTSTRAP_SAMPLES = 1000


def majority(passes: list[bool]) -> bool:
    return sum(passes) >= math.ceil(len(passes) / 2)


def bootstrap_ci(values: list[float], seed: int = 7) -> tuple[float, float]:
    if not values:
        return (0.0, 0.0)
    rng = random.Random(seed)
    means = sorted(mean(rng.choice(values) for _ in values) for _ in range(BOOTSTRAP_SAMPLES))
    return means[int(0.025 * BOOTSTRAP_SAMPLES)], means[int(0.975 * BOOTSTRAP_SAMPLES) - 1]


def percentile(values: Sequence[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(q * (len(ordered) - 1))))]


def summarize(cases: list[Case], runs: dict[str, list[CaseRun]]) -> dict:
    by_id = {case.id: case for case in cases}
    per_case: dict[str, dict[str, Any]] = {}
    for case_id, case_runs in runs.items():
        passes = [run.passed for run in case_runs]
        first_turn = case_runs[0].turns[0] if case_runs and case_runs[0].turns else None
        grades = {grade.grader: grade for grade in (first_turn.grades if first_turn else [])}
        failed = sorted({grade.grader for run in case_runs for turn in run.turns for grade in turn.grades
                         if not grade.passed and grade.grader not in {"retrieval_recall", "retrieval_rank"}})
        per_case[case_id] = {
            "suite": by_id[case_id].suite, "split": by_id[case_id].split, "category": by_id[case_id].category,
            "role": by_id[case_id].role, "passes": passes, "passed": majority(passes),
            "flaky": 0 < sum(passes) < len(passes),
            "recall": grades["retrieval_recall"].passed if "retrieval_recall" in grades else None,
            "mrr": grades["retrieval_rank"].score if "retrieval_rank" in grades else None,
            "failed_graders": failed, "error": next((run.error for run in case_runs if run.error), ""),
        }

    def group_stats(ids: list[str]) -> dict:
        values = [1.0 if per_case[i]["passed"] else 0.0 for i in ids]
        recall = [1.0 if per_case[i]["recall"] else 0.0 for i in ids if per_case[i]["recall"] is not None]
        mrr = [float(per_case[i]["mrr"]) for i in ids if per_case[i]["mrr"] is not None]
        low, high = bootstrap_ci(values)
        return {"n": len(ids), "pass_rate": mean(values) if values else 0.0, "ci95": [low, high],
                "recall_at_6": mean(recall) if recall else None, "mrr": mean(mrr) if mrr else None,
                "flaky": sum(per_case[i]["flaky"] for i in ids)}

    groups: dict[str, list[str]] = defaultdict(list)
    for case_id, info in per_case.items():
        groups["tổng"].append(case_id)
        groups[f"suite={info['suite']}"].append(case_id)
        groups[f"loại={info['category']}"].append(case_id)
        groups[f"vai trò={info['role']}"].append(case_id)
    grader_totals: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    totals_ms, tokens = [], []
    for case_runs in runs.values():
        for run in case_runs:
            for turn in run.turns:
                for grade in turn.grades:
                    grader_totals[grade.grader][0] += grade.passed
                    grader_totals[grade.grader][1] += 1
                trace = turn.observation.trace
                totals_ms.append(trace.get("total_ms", 0))
                tokens.append(trace.get("tokens_in", 0) + trace.get("tokens_out", 0))
    return {
        "cases": per_case,
        "groups": {name: group_stats(ids) for name, ids in sorted(groups.items())},
        "graders": {name: {"passed": counts[0], "total": counts[1], "rate": counts[0] / counts[1]}
                    for name, counts in sorted(grader_totals.items())},
        "latency_ms": {"p50": percentile(totals_ms, 0.5), "p95": percentile(totals_ms, 0.95),
                       "mean": mean(totals_ms) if totals_ms else 0},
        "tokens_per_turn": {"mean": mean(tokens) if tokens else 0, "p95": percentile(tokens, 0.95)},
    }


def write_report(out_dir: Path, meta: dict, cases: list[Case], runs: dict[str, list[CaseRun]]) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    summary = summarize(cases, runs)
    (out_dir / "run.json").write_text(json.dumps({"meta": meta, **summary}, ensure_ascii=False, indent=2,
                                                 default=str), encoding="utf-8")
    with (out_dir / "cases.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(["case_id", "run_idx", "turn", "passed", "failed_graders", "intent", "grounded",
                         "fallback_reason", "docs_in_context", "tools_called", "verify_retries", "latency_ms",
                         "tokens_in", "tokens_out", "tokens_by_layer"])
        for case_id, case_runs in runs.items():
            for run in case_runs:
                for index, turn in enumerate(run.turns):
                    obs = turn.observation
                    writer.writerow([case_id, run.run_index, index, turn.passed,
                                     ";".join(g.grader for g in turn.grades if not g.passed), obs.intent,
                                     obs.grounded, obs.fallback_reason or "", ";".join(obs.context_docs),
                                     ";".join(c["name"] for c in obs.tool_calls), obs.trace.get("verify_retries"),
                                     obs.trace.get("total_ms"), obs.trace.get("tokens_in"),
                                     obs.trace.get("tokens_out"), json.dumps(obs.layer_tokens)])
    with (out_dir / "traces.jsonl").open("w", encoding="utf-8") as file:
        for case_id, case_runs in runs.items():
            for run in case_runs:
                for index, turn in enumerate(run.turns):
                    obs = turn.observation
                    file.write(json.dumps({
                        "case_id": case_id, "run_idx": run.run_index, "turn": index, "question": obs.question,
                        "answer": obs.answer, "intent": obs.intent, "grounded": obs.grounded,
                        "fallback_reason": obs.fallback_reason, "rewritten_query": obs.rewritten_query,
                        "docs": obs.context_docs, "tools": obs.tool_calls, "actions": obs.actions,
                        "grades": [g.__dict__ for g in turn.grades], "trace": obs.trace,
                    }, ensure_ascii=False, default=str) + "\n")
    (out_dir / "summary.md").write_text(to_markdown(meta, summary), encoding="utf-8")
    return summary


def _pct(value) -> str:
    return "—" if value is None else f"{100 * value:.1f}%"


def to_markdown(meta: dict, summary: dict) -> str:
    lines = [f"# Kết quả eval `{meta['run_id']}`", "",
             "| Mục | Giá trị |", "|---|---|"]
    for key in ("config", "llm", "llm_model", "embedding", "store", "config_hash", "dataset_version", "mode", "k",
                "split", "suites", "started_at", "llm_calls", "llm_tokens", "note"):
        if key in meta:
            lines.append(f"| {key} | {meta[key]} |")
    lines += ["", "## Chỉ số theo nhóm (đạt = đa số trong k lần; CI 95% bootstrap)", "",
              "| Nhóm | n | Đạt | CI 95% | Recall@6 | MRR | Dao động |", "|---|---|---|---|---|---|---|"]
    for name, row in summary["groups"].items():
        low, high = row["ci95"]
        mrr = "—" if row["mrr"] is None else f"{row['mrr']:.3f}"
        lines.append(f"| {name} | {row['n']} | {_pct(row['pass_rate'])} | {_pct(low)} – {_pct(high)} | "
                     f"{_pct(row['recall_at_6'])} | {mrr} | {row['flaky']} |")
    lines += ["", "## Từng grader (mọi lượt, mọi lần chạy)", "", "| Grader | Đạt / tổng | Tỉ lệ |", "|---|---|---|"]
    for name, row in summary["graders"].items():
        lines.append(f"| {name} | {row['passed']} / {row['total']} | {_pct(row['rate'])} |")
    latency = summary["latency_ms"]
    lines += ["", f"Độ trễ một lượt: p50 {latency['p50']:.0f} ms, p95 {latency['p95']:.0f} ms. "
              f"Token / lượt: trung bình {summary['tokens_per_turn']['mean']:.0f}.", "",
              "## Case trượt (tối đa 15)", "", "| Case | Loại | Grader trượt |", "|---|---|---|"]
    failed = [(case_id, info) for case_id, info in summary["cases"].items() if not info["passed"]][:15]
    for case_id, info in failed:
        lines.append(f"| {case_id} | {info['category']} | {', '.join(info['failed_graders']) or info['error']} |")
    return "\n".join(lines) + "\n"
