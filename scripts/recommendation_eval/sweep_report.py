"""Steps 39-40 (design 13, 13.4-13.5, 13.8): one table for a set of configurations.

    python scripts/recommendation_eval/sweep_report.py --scenario crowded --preset t39
    python scripts/recommendation_eval/sweep_report.py --scenario crowded --preset t40-crowded
    EVAL_RESULTS=base-t40 python scripts/recommendation_eval/sweep_report.py --scenario base --preset t40-base

Joins the search metrics of evaluate.py (9.7-bang-ket-qua.json, S2T test half) with the 8-round market of
market.py --as t39. The market draws the same coin for the same (student, posting, round) in every configuration,
so the rejections and the rounds a student waited are compared student by student (Wilcoxon, bootstrap CI of the
mean difference) against the controls. Writes ket-qua/<results>/<preset>-quet.csv and
<preset>-kiem-dinh-thi-truong.csv. t40-crowded also compares Q on and off with the popular tutors left out (they
always have a high hidden quality, 13.3).
"""

import csv
import sys
from types import SimpleNamespace

import numpy as np
from scipy import stats

from common import OUT, RESULTS, SEED, read_json

ROUNDS = 8
CROWDED_DEFAULTS = "V2-RC-s0.5-g0.65"  # the defaults of 26/10/2026 on crowded (step 39)
PRESETS = {
    "t39": SimpleNamespace(
        market="t39",
        configs=["V2-a", "V2-R-harm", "V2-RC-old", "V2-RC-s0.25", "V2-RC-s0.5", "V2-RC-s1", "V2-RC-g0.5",
                 "V2-RC-g0.65", "V2-RC-g0.8", CROWDED_DEFAULTS],
        pairs=[("V2-R-harm", "V2-RC-old"), ("V2-RC-old", "V2-RC-s0.25"), ("V2-RC-old", "V2-RC-s0.5"),
               ("V2-RC-old", "V2-RC-s1"), ("V2-a", "V2-RC-g0.5"), ("V2-a", "V2-RC-g0.65"), ("V2-a", "V2-RC-g0.8"),
               ("V2-RC-old", "V2-RC-g0.5"), ("V2-RC-old", "V2-RC-g0.65"), ("V2-RC-old", "V2-RC-g0.8"),
               ("V2-a", CROWDED_DEFAULTS), ("V2-RC-old", CROWDED_DEFAULTS), ("V2-RC-g0.65", CROWDED_DEFAULTS)],
        popular_check=None),
    "t40-crowded": SimpleNamespace(
        market="t39",  # same coins as the sweep, so the defaults run of step 39 is the reference
        configs=["V2-a", CROWDED_DEFAULTS, "V2-RC-noQ", "V2-RC-noSchedGate", "V2-RC-noRejectGate"],
        pairs=[(CROWDED_DEFAULTS, "V2-RC-noQ"), (CROWDED_DEFAULTS, "V2-RC-noSchedGate"),
               (CROWDED_DEFAULTS, "V2-RC-noRejectGate")],
        popular_check=(CROWDED_DEFAULTS, "V2-RC-noQ")),
    "t40-base": SimpleNamespace(
        market="t40",
        configs=["V2-a", "V2-R", "V2-RC-oldCongestion", "V2-RC", "V2-RC-noQ", "V2-RC-noSchedGate",
                 "V2-RC-noRejectGate"],
        pairs=[("V2-R", "V2-RC-oldCongestion"), ("V2-R", "V2-RC"), ("V2-RC-oldCongestion", "V2-RC"),
               ("V2-RC", "V2-RC-noQ"), ("V2-RC", "V2-RC-noSchedGate"), ("V2-RC", "V2-RC-noRejectGate")],
        popular_check=None),
}


def popular_free_test(pair: tuple, rng: np.random.Generator) -> list:
    """NDCG@10 and MS@10 of the pair with the popular tutors left out of both rankings and of the ideal list: does Q
    help to order the other tutors? (Popular tutors have a high hidden quality, so they are one level more relevant
    by construction, 13.3; only 8 of 597 test searches have none of them in the top 10, too few to look at apart.)"""
    import evaluate as ev

    world = read_json(OUT / "world.json")
    judge = ev.Judge(world)
    runs = {name: read_json(OUT / "runs" / f"{name}.json") for name in pair}
    ordinary = {cid for cid, (_, tutor) in judge.teaching.items() if not tutor.get("famous")}
    kept = []
    for query in (q for q in world["queries"] if q["split"] == "test" and q["side"] == "S2T"):
        ideal = sorted((judge.rel(query, cid) for cid in ordinary), reverse=True)
        if not ideal or ideal[0] == 0:
            continue
        values = {}
        for name in pair:
            ranking = [cid for cid in ev.rankings(runs[name], query, name, judge, None) if cid in ordinary]
            gains = [judge.rel(query, cid) for cid in ranking[:10]]
            accepts = [judge.accept(query, cid) for cid in ranking[:10]]
            values[name] = {"ndcg10": ev.dcg(gains) / ev.dcg(ideal[:10]),
                            "ms10": sum((gain >= 2) * accept for gain, accept in zip(gains, accepts)) / 10}
        kept.append(values)
    tests = []
    for metric in ("ndcg10", "ms10"):
        result = paired([m[pair[0]][metric] for m in kept], [m[pair[1]][metric] for m in kept], rng)
        tests.append({"from": pair[0], "to": pair[1], "metric": f"{metric} (popular tutors left out)",
                      "n": len(kept), **result})
    return tests


def paired(left: list, right: list, rng: np.random.Generator) -> dict:
    differences = np.asarray(right, dtype=float) - np.asarray(left, dtype=float)
    nonzero = differences[np.abs(differences) > 1e-12]
    p_value = float(stats.wilcoxon(nonzero).pvalue) if len(nonzero) >= 10 else float("nan")
    means = rng.choice(differences, (10_000, len(differences)), replace=True).mean(axis=1)
    return {"mean_diff": float(differences.mean()), "ci_low": float(np.percentile(means, 2.5)),
            "ci_high": float(np.percentile(means, 97.5)), "p": p_value}


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    preset_name = sys.argv[sys.argv.index("--preset") + 1] if "--preset" in sys.argv else "t39"
    preset = PRESETS[preset_name]
    run = preset.market
    search = read_json(RESULTS / "9.7-bang-ket-qua.json")["summary"]
    market = {name: read_json(OUT / f"market-{run}" / f"{name}.json") for name in preset.configs
              if (OUT / f"market-{run}" / f"{name}.json").exists()}
    rows = []
    for name in preset.configs:
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
    for left, right in preset.pairs:
        if left not in market or right not in market:
            continue
        students = sorted(market[left]["rejections"])
        rounds = lambda name: [market[name]["matched_round"].get(key, ROUNDS + 1) for key in students]
        for metric, values in (("rejections", lambda name: [market[name]["rejections"][key] for key in students]),
                               ("rounds", rounds)):
            tests.append({"from": left, "to": right, "metric": metric, "n": len(students),
                          **paired(values(left), values(right), rng)})
    if preset.popular_check:
        tests += popular_free_test(preset.popular_check, rng)
    RESULTS.mkdir(parents=True, exist_ok=True)
    with (RESULTS / f"{preset_name}-quet.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({k: (f"{v:.4f}" if isinstance(v, float) else v) for k, v in row.items()})
    with (RESULTS / f"{preset_name}-kiem-dinh-thi-truong.csv").open("w", newline="", encoding="utf-8") as handle:
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
