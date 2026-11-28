"""Lệnh chạy eval (thiết kế 10 mục 10.4, 13 mục 13.6):

    .venv/Scripts/python -m eval.run --config eval/configs/baseline.yaml --mode quick --split dev
    .venv/Scripts/python -m eval.run --mode gate --split test --suites golden,multiturn,safety
    .venv/Scripts/python -m eval.run --config eval/configs/real_llm.yaml --mode gate --split test --yes \\
        --max-cost-usd 3 --budget-ledger eval/reports/t3/ledger.json --budget-usd 10      # tốn tiền

- `--mode quick | gate` = mỗi case chạy 1 / 3 lần.
- Với model thật (`llm: openai`) phải thêm `--yes` sau khi xem **ước tính số lời gọi và số tiền**; giới hạn cứng:
  `--max-llm-calls`, `--max-tokens`, `--max-cost-usd` (của lần chạy này) và `--budget-ledger` + `--budget-usd` (ngân
  sách tổng của cả chuỗi lần chạy). Chạm trần → dừng, ghi phần đã chạy.
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
    BudgetLedger,
    EvalConfig,
    ProviderUnavailable,
    RunBudgetExceeded,
    build_environment,
    dataset_version,
    harness_hash,
    load_price,
    load_users,
    run_case,
)

CALLS_PER_TURN_ESTIMATE = 3  # Actor + verifier (+ viết lại câu hỏi ở lượt tiếp nối) — ước tính thô
# Token mỗi lượt để ước tính tiền trước khi chạy: đo từ trace của Actor trích xuất (~1.900 token / lượt), cộng dư cho
# câu trả lời dài hơn và lần thử lại của model thật. Chi phí thật lấy từ `usage` của nhà cung cấp, ghi vào run.json.
TOKENS_IN_PER_TURN_ESTIMATE = 3000
TOKENS_OUT_PER_TURN_ESTIMATE = 400


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
    parser.add_argument("--max-cost-usd", type=float, default=None, help="Trần tiền của lần chạy này")
    parser.add_argument("--budget-ledger", type=Path, default=None, help="Sổ chi tiêu dùng chung nhiều lần chạy")
    parser.add_argument("--budget-usd", type=float, default=10.0, help="Ngân sách tổng khi tạo sổ mới")
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
    ledger = BudgetLedger(arguments.budget_ledger, arguments.budget_usd) if arguments.budget_ledger else None
    if config.llm == "openai":
        model = config.settings.get("llm_model", "")
        price = load_price(model)
        if price is not None:
            usd = turns * k * (TOKENS_IN_PER_TURN_ESTIMATE * price[0] + TOKENS_OUT_PER_TURN_ESTIMATE * price[1]) / 1e6
            print(f"Ước tính chi phí ({model}): ~{usd:.2f} $ "
                  f"(trần lần chạy: {arguments.max_cost_usd or 'không'}; "
                  + (f"sổ chi tiêu: đã tiêu {ledger.prior_usd:.4f} / {ledger.limit_usd} $)" if ledger
                     else "không có sổ)"))
        if not arguments.yes:
            raise SystemExit("Model thật tốn tiền: xem ước tính trên rồi chạy lại với --yes.")

    env = build_environment(config, max_llm_calls=arguments.max_llm_calls, max_tokens=arguments.max_tokens,
                            max_cost_usd=arguments.max_cost_usd, ledger=ledger)
    users = load_users()
    run_id = f"{datetime.now():%Y%m%d-%H%M%S}-{config.name}-{arguments.split}-{arguments.mode}"
    runs: dict = {}
    note = ""
    provider_failed = False
    started = time.perf_counter()
    try:
        for index, case in enumerate(cases, start=1):
            runs[case.id] = [run_case(env, case, run_index, users) for run_index in range(k)]
            if index % 25 == 0:
                print(f"  {index}/{len(cases)} case… ({env.llm.spent_usd:.4f} $)")
    except RunBudgetExceeded as error:
        provider_failed = isinstance(error, ProviderUnavailable)
        note = (f"Dừng vì nhà cung cấp từ chối (kết quả KHÔNG dùng được): {error}" if provider_failed
                else f"Dừng sớm vì chạm giới hạn chi phí: {error}")
        print(note)
    finally:
        env.embedder.save()
        if ledger is not None:
            ledger.record(run_id, env.llm.spent_usd, env.llm.calls, env.llm.tokens_in, env.llm.tokens_out)
    meta = {
        "run_id": run_id, "config": config.name, "llm": config.llm, "llm_model": env.settings.llm_model,
        "embedding": env.embedder.model_name, "store": config.store, "config_hash": harness_hash(env),
        "dataset_version": dataset_version(), "mode": arguments.mode, "k": k, "split": arguments.split,
        "suites": ",".join(suites), "started_at": datetime.now().isoformat(timespec="seconds"),
        "duration_s": round(time.perf_counter() - started, 1), "llm_calls": env.llm.calls,
        "llm_tokens": env.llm.tokens, "llm_tokens_in": env.llm.tokens_in, "llm_tokens_out": env.llm.tokens_out,
        "cost_usd": round(env.llm.spent_usd, 4), "note": note or "",
    }
    ran = [case for case in cases if case.id in runs]
    summary = write_report(arguments.out / run_id, meta, ran, runs)
    total = summary["groups"]["tổng"]
    print(f"Xong: đạt {100 * total['pass_rate']:.1f}% ({total['n']} case), {env.llm.calls} lời gọi, "
          f"{env.llm.tokens_in} token vào / {env.llm.tokens_out} token ra, {env.llm.spent_usd:.4f} $. "
          f"Báo cáo: {arguments.out / run_id}")
    if provider_failed:
        raise SystemExit(2)  # để chuỗi thí nghiệm (set -e) dừng thay vì chạy tiếp ra kết quả rác


if __name__ == "__main__":
    main()
