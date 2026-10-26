"""Step 39 (design 13, 13.4-13.5): one table for the λ / α sweep on crowded.

    python scripts/recommendation_eval/sweep_report.py --scenario crowded [--as t39]

Joins the search metrics of evaluate.py (9.7-bang-ket-qua.json, S2T test half) with the 8-round market of
market.py --as t39. The market draws the same coin for the same (student, posting, round) in every configuration,
so the rejections and the rounds a student waited are compared student by student (Wilcoxon, bootstrap CI of the
mean difference) against the controls. Writes ket-qua/<scenario>/<run>-quet.csv and <run>-kiem-dinh-thi-truong.csv.
"""

import csv
import sys

import numpy as np
from scipy import stats

from common import OUT, RESULTS, SEED, read_json

ROUNDS = 8
CONFIGS = ["V2-a", "V2-R-harm", "V2-RC-old", "V2-RC-s0.25", "V2-RC-s0.5", "V2-RC-s1", "V2-RC-g0.5", "V2-RC-g0.65",
           "V2-RC-g0.8", "V2-RC-s0.5-g0.65"]
PAIRS = [("V2-R-harm", "V2-RC-old"), ("V2-RC-old", "V2-RC-s0.25"), ("V2-RC-old", "V2-RC-s0.5"),
         ("V2-RC-old", "V2-RC-s1"), ("V2-a", "V2-RC-g0.5"), ("V2-a", "V2-RC-g0.65"), ("V2-a", "V2-RC-g0.8"),
         ("V2-RC-old", "V2-RC-g0.5"), ("V2-RC-old", "V2-RC-g0.65"), ("V2-RC-old", "V2-RC-g0.8"),
         ("V2-a", "V2-RC-s0.5-g0.65"), ("V2-RC-old", "V2-RC-s0.5-g0.65"), ("V2-RC-g0.65", "V2-RC-s0.5-g0.65")]


def paired(left: list, right: list, rng: np.random.Generator) -> dict:
    differences = np.asarray(right, dtype=float) - np.asarray(left, dtype=float)
    nonzero = differences[np.abs(differences) > 1e-12]
    p_value = float(stats.wilcoxon(nonzero).pvalue) if len(nonzero) >= 10 else float("nan")
    means = rng.choice(differences, (10_000, len(differences)), replace=True).mean(axis=1)
    return {"mean_diff": float(differences.mean()), "ci_low": float(np.percentile(means, 2.5)),
            "ci_high": float(np.percentile(means, 97.5)), "p": p_value}


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    run = sys.argv[sys.argv.index("--as") + 1] if "--as" in sys.argv else "t39"
    search = read_json(RESULTS / "9.7-bang-ket-qua.json")["summary"]
    market = {name: read_json(OUT / f"market-{run}" / f"{name}.json") for name in CONFIGS
              if (OUT / f"market-{run}" / f"{name}.json").exists()}
    rows = []
    for name in CONFIGS:
        if name not in market or name not in search:
            continue
        summary, found = market[name]["summary"], search[name]
        rows.append({"config": name, "ndcg10": found["ndcg10"], "ms10": found["ms10"], "ea10": found["ea10"],
                     "t2s_ndcg10": found["t2s_ndcg10"], "rejections": summary["rejections_per_student"],
                     "rounds": summary["mean_rounds_censored"], "match_rate": summary["match_rate"],
                     "gini": summary["gini_exposure"], "new_exposure": summary["new_tutor_exposure_share"],
                     "matched_rel": summary["matched_rel"]})
    tests = []
    rng = np.random.default_rng(SEED)
    for left, right in PAIRS:
        if left not in market or right not in market:
            continue
        students = sorted(market[left]["rejections"])
        rounds = lambda name: [market[name]["matched_round"].get(key, ROUNDS + 1) for key in students]
        for metric, values in (("rejections", lambda name: [market[name]["rejections"][key] for key in students]),
                               ("rounds", rounds)):
            tests.append({"from": left, "to": right, "metric": metric, "n": len(students),
                          **paired(values(left), values(right), rng)})
    RESULTS.mkdir(parents=True, exist_ok=True)
    with (RESULTS / f"{run}-quet.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({k: (f"{v:.4f}" if isinstance(v, float) else v) for k, v in row.items()})
    with (RESULTS / f"{run}-kiem-dinh-thi-truong.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(tests[0]))
        writer.writeheader()
        for row in tests:
            writer.writerow({k: (f"{v:.4f}" if isinstance(v, float) else v) for k, v in row.items()})
    print(f"{'config':12s} NDCG   MS10   EA10   T2S    rej/HS rounds match  Gini   new    relGhép")
    for row in rows:
        print(f"{row['config']:12s} {row['ndcg10']:.3f}  {row['ms10']:.3f}  {row['ea10']:.3f}  "
              f"{row['t2s_ndcg10']:.3f}  {row['rejections']:.2f}   {row['rounds']:.2f}   {row['match_rate']:.3f}  "
              f"{row['gini']:.3f}  {row['new_exposure']:.3f}  {row['matched_rel']:.2f}")
    for row in tests:
        print(f"{row['from']:>11s} -> {row['to']:<12s} {row['metric']:10s} diff {row['mean_diff']:+.3f} "
              f"[{row['ci_low']:+.3f}, {row['ci_high']:+.3f}] p={row['p']:.3g}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
