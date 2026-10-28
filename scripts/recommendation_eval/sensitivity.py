"""26c (1): sensitivity of the ranking to each weight and constant (design 09, 9.9.1).

    python scripts/recommendation_eval/sensitivity.py [--run V2-final]   # reads out/runs/<run>.json, writes ket-qua/9.9.1-*

Each parameter is scaled by 0.5, 0.75, 1.25 and 1.5 (a weight's group is normalised back to 1), the logged
candidates of the test half are scored again offline (scoring.py) and compared with the unchanged ranking:
NDCG@10, EA@10 and Kendall tau of the top 10. Since step 43b also α, the two gates and the complaint penalty
(read from score_breakdown of <db>_seed, the state the searches ran on). The offline order leaves out the
exploration slots. Parameters that live outside the log (the kernels of the features) need the backend; the
congestion λ only acts in the market (market.py), a static search has almost no pending application.
"""

import csv
import sys

import numpy as np
from scipy import stats

from dataclasses import replace

from common import DATABASE, OUT, RESULTS, query_json, read_json
from evaluate import Judge, query_metrics
from scoring import Params, rerank

FACTORS = [0.5, 0.75, 1.25, 1.5]


def parameters(base: Params) -> list:
    """(label, function factor -> Params)."""
    found = []
    # Student side only, like the main table: the tutor side is too thin to measure (evaluate.py).
    for group, label in (("a_s2t", "a"), ("b_s2t", "b")):
        for name in getattr(base, group):
            found.append((f"{label}.{name}", lambda factor, g=group, n=name: base.with_weight(g, n, factor)))
    weight = base.quality_weight
    found.append(("hệ số Q", lambda factor: replace(base, quality_base=1 - weight * factor,
                                                     quality_weight=weight * factor)))
    found.append(("prior đặc trưng thiếu", lambda factor: replace(base, confidence_prior=min(1.0, 0.5 * factor))))
    found.append(("α (reciprocal)", lambda factor: replace(base, reciprocal_alpha=min(1.0, base.reciprocal_alpha
                                                                                      * factor))))
    found.append(("cổng chặn lịch", lambda factor: replace(base, gate_schedule=min(1.0, base.gate_schedule * factor))))
    found.append(("cổng chặn từ chối gần đây", lambda factor: replace(base, gate_reject=min(1.0, base.gate_reject
                                                                                             * factor))))
    found.append(("phạt khiếu nại", lambda factor: replace(base, complaint_factor=factor)))
    return found


def complaint_penalties(database: str) -> dict:
    """Tutor user id -> complaint penalty (points) in the score the searches read."""
    rows = query_json("SELECT user_id, (score_breakdown->>'complaintPenalty')::float AS penalty FROM tutors "
                      "WHERE (score_breakdown->>'faultComplaints')::int > 0", database)
    return {row["user_id"]: row["penalty"] for row in rows}


def kendall_top10(base: list, other: list) -> float:
    items = base[:10]
    position = {candidate_id: index for index, candidate_id in enumerate(other)}
    ranks = [position.get(candidate_id, len(other)) for candidate_id in items]
    tau = stats.kendalltau(range(len(items)), ranks).statistic
    return 1.0 if np.isnan(tau) else float(tau)


def evaluate(judge: Judge, cases: list, params: Params, base_rankings: dict | None = None):
    ndcg, ea, taus, rankings = [], [], [], {}
    for query, candidates in cases:
        ranking = rerank(candidates, query["side"], params)
        rankings[query["id"]] = ranking
        values = query_metrics(judge, query, ranking[:20])
        if values:
            ndcg.append(values["ndcg10"])
            ea.append(values["ea10"])
        if base_rankings is not None:
            taus.append(kendall_top10(base_rankings[query["id"]], ranking))
    return float(np.mean(ndcg)), float(np.mean(ea)), (float(np.mean(taus)) if taus else 1.0), rankings


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    name = sys.argv[sys.argv.index("--run") + 1] if "--run" in sys.argv else "V2-final"
    run = read_json(OUT / "runs" / f"{name}.json")
    judge = Judge(world)
    cases = [(query, run["results"][query["id"]]["candidates"] or []) for query in world["queries"]
             if query["split"] == "test" and query["side"] == "S2T"]
    base = Params(penalties=complaint_penalties(f"{DATABASE}_seed"))
    base_ndcg, base_ea, _, base_rankings = evaluate(judge, cases, base)
    rows = []
    for label, make in parameters(base):
        for factor in FACTORS:
            ndcg, ea, tau, _ = evaluate(judge, cases, make(factor), base_rankings)
            rows.append({"parameter": label, "factor": factor, "ndcg10": ndcg, "d_ndcg10": ndcg - base_ndcg,
                         "ea10": ea, "d_ea10": ea - base_ea, "kendall_tau10": tau})
    RESULTS.mkdir(parents=True, exist_ok=True)
    with (RESULTS / "9.9.1-do-nhay.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows({key: (f"{value:.4f}" if isinstance(value, float) else value) for key, value in row.items()}
                         for row in rows)
    tornado(rows, base_ndcg)
    print(f"base NDCG@10 {base_ndcg:.4f}  EA@10 {base_ea:.4f}")
    for label in dict.fromkeys(row["parameter"] for row in rows):
        mine = [row for row in rows if row["parameter"] == label]
        print(f"{label:24s} ΔNDCG {min(r['d_ndcg10'] for r in mine):+.4f}..{max(r['d_ndcg10'] for r in mine):+.4f}"
              f"  τ≥{min(r['kendall_tau10'] for r in mine):.3f}")
    return 0


def tornado(rows: list, base_ndcg: float) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    labels = list(dict.fromkeys(row["parameter"] for row in rows))
    low = {label: min(r["d_ndcg10"] for r in rows if r["parameter"] == label) for label in labels}
    high = {label: max(r["d_ndcg10"] for r in rows if r["parameter"] == label) for label in labels}
    labels.sort(key=lambda label: high[label] - low[label])
    figure, axis = plt.subplots(figsize=(8, 0.32 * len(labels) + 1.2))
    for index, label in enumerate(labels):
        axis.barh(index, high[label] - low[label], left=low[label], color="#5b6bbf")
    axis.set_yticks(range(len(labels)), labels, fontsize=8)
    axis.axvline(0, color="black", linewidth=0.8)
    axis.set_xlabel(f"Thay đổi NDCG@10 khi nhân tham số ×0,5…×1,5 (gốc {base_ndcg:.3f})")
    figure.tight_layout()
    figure.savefig(RESULTS / "9.9.1-tornado.png", dpi=150)
    plt.close(figure)


if __name__ == "__main__":
    raise SystemExit(main())
