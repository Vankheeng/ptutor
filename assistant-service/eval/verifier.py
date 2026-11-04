"""Đo riêng verifier trên `datasets/verifier/claims.jsonl` (thiết kế 13 mục 13.8, chỉ số ở 10 mục 10.2).

    .venv/Scripts/python -m eval.verifier --k 3                       # verifier giả lập (so chuỗi con)
    .venv/Scripts/python -m eval.verifier --config eval/configs/real_llm.yaml --k 3 --yes   # model thật, tốn tiền

Mỗi khẳng định gửi verifier cùng **đúng** đoạn nguồn của nó, bằng prompt và bước chuẩn hoá bằng chứng của
`CitationVerifierMiddleware` (`normalize_verdict`) — không qua agent. Kết quả của k lần lấy đa số.

Chỉ số:
- **Recall bắt lỗi**: trong các khẳng định nhãn UNSUPPORTED / PARTIAL, tỉ lệ verifier KHÔNG cho SUPPORTED.
- **Tỉ lệ bác nhầm**: trong các khẳng định nhãn SUPPORTED, tỉ lệ verifier không cho SUPPORTED.
- Độ khớp nhãn đầy đủ (3 lớp) và ma trận nhầm lẫn theo `kind`.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from app.agent.prompts import load_prompt
from app.agent.verification import VERIFIER_SCHEMA, Verdict, normalize_verdict
from app.cli import use_utf8_output
from app.providers.llm import ChatMessage, LlmRequest
from eval.build_verifier_claims import OUT as CLAIMS_FILE
from eval.build_verifier_claims import load_chunks
from eval.runner import EVAL_DIR, BudgetLedger, EvalConfig, ProviderUnavailable, RunBudgetExceeded, build_environment

LABELS = ("SUPPORTED", "PARTIAL", "UNSUPPORTED")


def verify_once(env, claim: dict, source: str) -> str:
    request = LlmRequest(
        messages=[ChatMessage("system", load_prompt("verifier")),
                  ChatMessage("user", f'<source id="D1">\n{source}\n</source>\n\n'
                                      f'<claim id="0" source="D1">{claim["claim"]}</claim>')],
        model=env.settings.verifier_model or env.settings.llm_model, response_schema=VERIFIER_SCHEMA,
        response_schema_name="verdicts", temperature=0.0, max_output_tokens=400, timeout_s=15.0, purpose="verify")
    try:
        raw = json.loads(env.llm.complete(request).content)["verdicts"][0]
        verdict = Verdict(0, str(raw.get("verdict", "UNSUPPORTED")), str(raw.get("evidence", "")),
                          str(raw.get("reason", "")))
    except RunBudgetExceeded:
        raise
    except Exception:  # noqa: BLE001 — lỗi gọi / định dạng = không kiểm chứng được (giống middleware)
        return "UNSUPPORTED"
    return normalize_verdict(verdict, source).verdict


def summarize(rows: list[dict]) -> dict:
    bad = [row for row in rows if row["label"] != "SUPPORTED"]
    good = [row for row in rows if row["label"] == "SUPPORTED"]
    by_kind: dict[str, Counter] = defaultdict(Counter)
    for row in rows:
        by_kind[row["kind"]][row["predicted"]] += 1
    return {
        "n": len(rows),
        "catch_recall": sum(row["predicted"] != "SUPPORTED" for row in bad) / len(bad) if bad else None,
        "false_reject_rate": sum(row["predicted"] != "SUPPORTED" for row in good) / len(good) if good else None,
        "exact_agreement": sum(row["predicted"] == row["label"] for row in rows) / len(rows) if rows else None,
        "by_kind": {kind: dict(counter) for kind, counter in sorted(by_kind.items())},
    }


def write_markdown(path: Path, meta: dict, summary: dict) -> None:
    lines = [f"# Đo verifier — {meta['config']} (k = {meta['k']})", "",
             f"- Model: `{meta['model']}`; số khẳng định: {summary['n']}; lời gọi: {meta['calls']}; "
             f"chi phí: {meta.get('cost_usd', 0)} $", "",
             "| Chỉ số | Giá trị |", "|---|---|",
             f"| Recall bắt lỗi (UNSUPPORTED/PARTIAL không được cho SUPPORTED) | {summary['catch_recall']:.1%} |",
             f"| Tỉ lệ bác nhầm (SUPPORTED bị bác) | {summary['false_reject_rate']:.1%} |",
             f"| Khớp nhãn 3 lớp | {summary['exact_agreement']:.1%} |", "",
             "| kind | " + " | ".join(LABELS) + " |", "|---|" + "---|" * len(LABELS)]
    for kind, counts in summary["by_kind"].items():
        lines.append(f"| {kind} | " + " | ".join(str(counts.get(label, 0)) for label in LABELS) + " |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    use_utf8_output()
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--config", type=Path, default=EVAL_DIR / "configs" / "baseline.yaml")
    parser.add_argument("--k", type=int, default=3)
    parser.add_argument("--out", type=Path, default=EVAL_DIR / "reports" / "verifier")
    parser.add_argument("--max-llm-calls", type=int, default=600)
    parser.add_argument("--max-tokens", type=int, default=600_000)
    parser.add_argument("--max-cost-usd", type=float, default=None)
    parser.add_argument("--budget-ledger", type=Path, default=None)
    parser.add_argument("--budget-usd", type=float, default=10.0)
    parser.add_argument("--yes", action="store_true")
    arguments = parser.parse_args()

    config = EvalConfig.load(arguments.config)
    claims = [json.loads(line) for line in CLAIMS_FILE.read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"{len(claims)} khẳng định × k={arguments.k} → {len(claims) * arguments.k} lời gọi verifier "
          f"(giới hạn {arguments.max_llm_calls}).")
    if config.llm == "openai" and not arguments.yes:
        raise SystemExit("Model thật tốn tiền: xem ước tính trên rồi chạy lại với --yes.")
    ledger = BudgetLedger(arguments.budget_ledger, arguments.budget_usd) if arguments.budget_ledger else None
    env = build_environment(config, max_llm_calls=arguments.max_llm_calls, max_tokens=arguments.max_tokens,
                            max_cost_usd=arguments.max_cost_usd, ledger=ledger)
    chunks = load_chunks()
    rows = []
    provider_failed = False
    try:
        for claim in claims:
            votes = Counter(verify_once(env, claim, chunks[claim["chunk_key"]]) for _ in range(arguments.k))
            rows.append({**claim, "predicted": votes.most_common(1)[0][0], "votes": dict(votes)})
    except RunBudgetExceeded as error:
        provider_failed = isinstance(error, ProviderUnavailable)
        print(f"Dừng sớm ({'nhà cung cấp từ chối' if provider_failed else 'chạm giới hạn chi phí'}): {error}")
    finally:
        if ledger is not None:
            ledger.record(f"verifier-{config.name}", env.llm.spent_usd, env.llm.calls, env.llm.tokens_in,
                          env.llm.tokens_out)
    summary = summarize(rows)
    arguments.out.mkdir(parents=True, exist_ok=True)
    meta = {"config": config.name, "k": arguments.k, "model": env.settings.verifier_model or env.settings.llm_model
            if config.llm == "openai" else "scripted-substring", "calls": env.llm.calls,
            "cost_usd": round(env.llm.spent_usd, 4)}
    (arguments.out / f"{config.name}.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    write_markdown(arguments.out / f"{config.name}.md", meta, summary)
    print(json.dumps({key: value for key, value in summary.items() if key != "by_kind"}, ensure_ascii=False))
    print(f"Báo cáo: {arguments.out / (config.name + '.md')}")
    if provider_failed:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
