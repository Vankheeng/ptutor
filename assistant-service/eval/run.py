"""Lệnh chạy eval (thiết kế 10 mục 10.4, 13 mục 13.6):

    .venv/Scripts/python -m eval.run --config eval/configs/baseline.yaml --mode quick --split dev
    .venv/Scripts/python -m eval.run --mode gate --split test --suites golden,multiturn,safety
    .venv/Scripts/python -m eval.run --config eval/configs/real_llm.yaml --mode gate --split test --yes   # tốn tiền

- `--mode quick | gate` = mỗi case chạy 1 / 3 lần.
- Với model thật (`llm: openai`) phải thêm `--yes` sau khi xem **ước tính số lời gọi**; giới hạn cứng cả lần chạy:
  `--max-llm-calls`, `--max-tokens` (chạm trần → dừng, ghi phần đã chạy).
"""

from __future__ import annotations

import argparse
import time
from datetime import datetime
from pathlib import Path

from app.cli import use_utf8_output
from eval.cases import load_cases
from eval.report import write_report
from eval.runner import (
    EVAL_DIR,
    EvalConfig,
    RunBudgetExceeded,
    build_environment,
    dataset_version,
    harness_hash,
    load_users,
    run_case,
)

CALLS_PER_TURN_ESTIMATE = 3  # Actor + verifier (+ viết lại câu hỏi ở lượt tiếp nối) — ước tính thô


def main() -> None:
    use_utf8_output()
    parser = argparse.ArgumentParser(description="Chạy bộ eval của trợ lý")
    parser.add_argument("--config", type=Path, default=EVAL_DIR / "configs" / "baseline.yaml")
    parser.add_argument("--mode", choices=["quick", "gate"], default="quick")
    parser.add_argument("--split", choices=["dev", "test", "all"], default="dev")
    parser.add_argument("--suites", default="golden,multiturn,safety")
    parser.add_argument("--out", type=Path, default=EVAL_DIR / "reports")
    parser.add_argument("--limit", type=int, default=0, help="Chỉ chạy N case đầu (thử nhanh)")
    parser.add_argument("--max-llm-calls", type=int, default=3000)
    parser.add_argument("--max-tokens", type=int, default=6_000_000)
    parser.add_argument("--yes", action="store_true", help="Đồng ý chạy với model thật sau khi xem ước tính")
    arguments = parser.parse_args()

    config = EvalConfig.load(arguments.config)
    suites = [suite.strip() for suite in arguments.suites.split(",") if suite.strip()]
    split = None if arguments.split == "all" else arguments.split
    cases = load_cases(suites, split)
    if arguments.limit:
        cases = cases[:arguments.limit]
    k = 3 if arguments.mode == "gate" else 1
    turns = sum(len(case.turns) for case in cases)
    estimate = turns * k * CALLS_PER_TURN_ESTIMATE
    print(f"{len(cases)} case, {turns} lượt × k={k} → ước tính ~{estimate} lời gọi model "
          f"(giới hạn {arguments.max_llm_calls} lời gọi, {arguments.max_tokens} token).")
    if config.llm == "openai" and not arguments.yes:
        raise SystemExit("Model thật tốn tiền: xem ước tính trên rồi chạy lại với --yes.")

    env = build_environment(config, max_llm_calls=arguments.max_llm_calls, max_tokens=arguments.max_tokens)
    users = load_users()
    run_id = f"{datetime.now():%Y%m%d-%H%M%S}-{config.name}-{arguments.split}-{arguments.mode}"
    runs: dict = {}
    note = ""
    started = time.perf_counter()
    try:
        for index, case in enumerate(cases, start=1):
            runs[case.id] = [run_case(env, case, run_index, users) for run_index in range(k)]
            if index % 25 == 0:
                print(f"  {index}/{len(cases)} case…")
    except RunBudgetExceeded as error:
        note = f"Dừng sớm vì chạm giới hạn chi phí: {error}"
        print(note)
    finally:
        env.embedder.save()
    meta = {
        "run_id": run_id, "config": config.name, "llm": config.llm, "llm_model": env.settings.llm_model,
        "embedding": env.embedder.model_name, "store": config.store, "config_hash": harness_hash(env),
        "dataset_version": dataset_version(), "mode": arguments.mode, "k": k, "split": arguments.split,
        "suites": ",".join(suites), "started_at": datetime.now().isoformat(timespec="seconds"),
        "duration_s": round(time.perf_counter() - started, 1), "llm_calls": env.llm.calls,
        "llm_tokens": env.llm.tokens, "note": note or "",
    }
    ran = [case for case in cases if case.id in runs]
    summary = write_report(arguments.out / run_id, meta, ran, runs)
    total = summary["groups"]["tổng"]
    print(f"Xong: đạt {100 * total['pass_rate']:.1f}% ({total['n']} case). Báo cáo: {arguments.out / run_id}")


if __name__ == "__main__":
    main()
