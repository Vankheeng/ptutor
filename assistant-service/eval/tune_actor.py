"""Chọn ngưỡng phủ `COVERAGE_MIN` của Actor trích xuất trên tập **dev** (không bao giờ nhìn test — 13 mục 13.9).

    .venv/Scripts/python -m eval.tune_actor --values 0,0.2,0.3,0.4,0.5

Ngưỡng cao → từ chối nhiều hơn: nhóm NO_ANSWER (phải từ chối) tăng, các nhóm có đáp án (POLICY, FAQ, HOWTO, ERROR)
giảm. Chọn giá trị cho **tỉ lệ đạt tổng** cao nhất trên dev; báo cáo đánh giá ghi lại bảng quét này.
"""

from __future__ import annotations

import argparse
from collections import defaultdict

import eval.scripted_policy as scripted_policy
from app.cli import use_utf8_output
from eval.cases import load_cases
from eval.runner import EVAL_DIR, EvalConfig, build_environment, load_users, run_case


def main() -> None:
    use_utf8_output()
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--values", default="0,0.2,0.3,0.4,0.5")
    arguments = parser.parse_args()
    cases = load_cases(["golden"], "dev")
    env = build_environment(EvalConfig.load(EVAL_DIR / "configs" / "baseline.yaml"), max_llm_calls=100_000,
                            max_tokens=100_000_000)
    users = load_users()
    categories = sorted({case.category for case in cases})
    print("| COVERAGE_MIN | tổng | " + " | ".join(categories) + " |")
    print("|---|---|" + "---|" * len(categories))
    try:
        for value in [float(item) for item in arguments.values.split(",")]:
            scripted_policy.COVERAGE_MIN = value
            passed: dict[str, list[bool]] = defaultdict(list)
            for case in cases:
                ok = run_case(env, case, 0, users).passed
                passed[case.category].append(ok)
                passed["tổng"].append(ok)
            cells = [f"{100 * sum(passed[c]) / len(passed[c]):.1f}%" for c in ["tổng", *categories]]
            print(f"| {value} | " + " | ".join(cells) + " |")
    finally:
        env.embedder.save()


if __name__ == "__main__":
    main()
