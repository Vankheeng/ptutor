"""Benchmark B2: phân rã độ trễ, token và chi phí từ trace của các lần chạy eval (thiết kế 10 mục 10.5, 13 mục 13.11).

    .venv/Scripts/python -m eval.latency_cost eval/reports/<run_id> [eval/reports/<run_id_2> …]

Không chạy thêm gì: đọc `traces.jsonl` (span của từng bước, ghi cả bước SKIPPED) → `spans.csv` + `latency_cost.md`:
- B2.1 độ trễ theo bước (trung bình, p50, p95, % tổng), B2.2 theo intent, B2.3 token theo lời gọi và theo lớp ngữ cảnh,
  B2.4 chi phí = token × đơn giá trong `eval/configs/pricing.yaml` (model không có giá → báo thiếu, không đoán),
  B2.5 so các lần chạy (cấu hình E1 / E2 / E4) cạnh nhau.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import mean

import yaml

from eval.report import percentile

PRICING_FILE = Path(__file__).resolve().parent / "configs" / "pricing.yaml"


def load_traces(run_dir: Path) -> list[dict]:
    return [json.loads(line) for line in (run_dir / "traces.jsonl").read_text(encoding="utf-8").splitlines() if line]


def span_group(name: str) -> str:
    """`actor#2` → `actor`, `tool:get_my_wallet#1` → `tool`: gộp để thống kê theo loại bước."""
    if name.startswith("actor#"):
        return "actor"
    if name.startswith("tool:"):
        return "tool"
    return name


def price_of(model: str, tokens_in: int, tokens_out: int, pricing: dict) -> float | None:
    entry = (pricing.get("models") or {}).get(model)
    if not entry:
        return None
    return tokens_in / 1e6 * entry["input_per_1m"] + tokens_out / 1e6 * entry["output_per_1m"]


def analyze(run_dir: Path, pricing: dict) -> dict:
    meta = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))["meta"]
    traces = load_traces(run_dir)
    durations: dict[str, list[int]] = defaultdict(list)
    by_intent: dict[str, list[int]] = defaultdict(list)
    tokens_by_purpose: dict[str, list[int]] = defaultdict(list)
    rows = []
    totals, costs = [], []
    for trace in traces:
        spans = trace["trace"].get("spans", [])
        total = trace["trace"].get("total_ms", 0)
        totals.append(total)
        by_intent[trace["intent"]].append(total)
        tokens_in = trace["trace"].get("tokens_in", 0)
        tokens_out = trace["trace"].get("tokens_out", 0)
        cost = price_of(meta.get("llm_model", ""), tokens_in, tokens_out, pricing)
        if cost is not None:
            costs.append(cost)
        for span in spans:
            group = span_group(span["name"])
            if span.get("status") != "SKIPPED":
                durations[group].append(span["duration_ms"])
            if span.get("kind") in {"model", "verify"} and span.get("status") != "SKIPPED":
                tokens_by_purpose[group].append(span.get("tokens_in", 0) + span.get("tokens_out", 0))
            rows.append([trace["case_id"], trace["run_idx"], meta["config_hash"], trace["intent"], span["name"],
                         span.get("kind"), span["duration_ms"], span.get("tokens_in", 0), span.get("tokens_out", 0),
                         span.get("status")])
    with (run_dir / "spans.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(["case_id", "run_idx", "config_hash", "intent", "span_name", "kind", "duration_ms",
                         "tokens_in", "tokens_out", "status"])
        writer.writerows(rows)
    grand = sum(sum(values) for values in durations.values()) or 1
    return {
        "run_id": meta["run_id"], "config": meta["config"], "model": meta.get("llm_model"),
        "steps": {name: {"n": len(values), "mean": mean(values), "p50": percentile(values, 0.5),
                         "p95": percentile(values, 0.95), "share": sum(values) / grand}
                  for name, values in sorted(durations.items()) if values},
        "intents": {name: {"n": len(values), "p50": percentile(values, 0.5), "p95": percentile(values, 0.95)}
                    for name, values in sorted(by_intent.items())},
        "turn": {"p50": percentile(totals, 0.5), "p95": percentile(totals, 0.95),
                 "mean": mean(totals) if totals else 0},
        "tokens": {name: mean(values) for name, values in tokens_by_purpose.items() if values},
        "cost_per_turn": mean(costs) if costs else None,
        "cost_per_1000_turns": mean(costs) * 1000 if costs else None,
    }


def to_markdown(results: list[dict], pricing: dict) -> str:
    lines = ["# B2 — Độ trễ, token, chi phí", "",
             f"Đơn giá: `eval/configs/pricing.yaml` (lấy ngày {pricing.get('as_of', '—')}). "
             "Độ trễ đo khi chạy tuần tự, không tải đồng thời.", ""]
    for result in results:
        lines += [f"## {result['config']} (`{result['run_id']}`, model `{result['model']}`)", "",
                  "| Bước | n | TB (ms) | p50 | p95 | % tổng |", "|---|---|---|---|---|---|"]
        for name, row in result["steps"].items():
            lines.append(f"| {name} | {row['n']} | {row['mean']:.0f} | {row['p50']:.0f} | {row['p95']:.0f} | "
                         f"{100 * row['share']:.1f}% |")
        lines += ["", "| Intent | n | p50 (ms) | p95 (ms) |", "|---|---|---|---|"]
        for name, row in result["intents"].items():
            lines.append(f"| {name} | {row['n']} | {row['p50']:.0f} | {row['p95']:.0f} |")
        tokens = ", ".join(f"{name}: {value:.0f}" for name, value in result["tokens"].items())
        cost = ("thiếu đơn giá cho model này" if result["cost_per_turn"] is None
                else f"{result['cost_per_turn']:.5f} USD / lượt, {result['cost_per_1000_turns']:.2f} USD / 1.000 lượt")
        lines += ["", f"Token trung bình mỗi lời gọi: {tokens or '—'}. Chi phí: {cost}.", ""]
    if len(results) > 1:
        lines += ["## B2.5 So sánh cấu hình", "", "| Cấu hình | p50 lượt (ms) | p95 lượt (ms) | Chi phí / lượt |",
                  "|---|---|---|---|"]
        for result in results:
            cost = "—" if result["cost_per_turn"] is None else f"{result['cost_per_turn']:.5f}"
            lines.append(f"| {result['config']} | {result['turn']['p50']:.0f} | {result['turn']['p95']:.0f} | {cost} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    parser = argparse.ArgumentParser(description="Benchmark B2 từ trace của các lần chạy eval")
    parser.add_argument("runs", nargs="+", type=Path)
    arguments = parser.parse_args()
    pricing = yaml.safe_load(PRICING_FILE.read_text(encoding="utf-8")) if PRICING_FILE.is_file() else {}
    results = [analyze(run, pricing) for run in arguments.runs]
    report = to_markdown(results, pricing)
    (arguments.runs[-1] / "latency_cost.md").write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
