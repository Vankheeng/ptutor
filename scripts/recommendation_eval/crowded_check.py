"""Checks of the simulator on the rebuilt crowded database, before reading any result about the algorithm (13.3).

    python scripts/recommendation_eval/crowded_check.py --scenario crowded

Reads out/<scenario>/runs/V2-a.json (run.py search V2-a) and the hidden layer of world.json; no backend, no LLM.
Reports: median number of candidates after the hard filters on both sides; mean P(accept) of the top 50 candidates;
NDCG@10, EA@10 and MS@10 of Random, Popularity and V2-a on the test half; the correlation between the logged Q of a
candidate and its hidden rel; rejections per student in the one-shot market of evaluate.py (every student applies
in turn to the top 3 of the list). The correlation also gets a 95 % bootstrap CI that resamples whole queries (the 50
candidates of one query are not independent). Writes ket-qua/<scenario>/kiem-tra-mo-phong.csv.
"""

import csv
import random
import statistics
import sys

import numpy as np

import evaluate as ev
from common import OUT, RESULTS, SEED, read_json

TARGETS = {
    "median_candidates_s2t": "≥ 50", "median_candidates_t2s": "≥ 50", "mean_p_accept_top50": "0,4–0,6",
    "random_ea10": "0,4–0,6", "corr_q_rel": "0,2–0,4", "popularity_vs_random_ndcg": "> 0",
    "popularity_rejections_highest": "có",
}


def corr_ci(groups, rng: np.random.Generator, rounds: int = 2_000):
    """95 % CI of the Pearson correlation of (q, rel) pairs, resampling the groups (queries) with replacement."""
    values = []
    for _ in range(rounds):
        picked = rng.integers(0, len(groups), len(groups))
        qs = np.concatenate([groups[i][0] for i in picked])
        rels = np.concatenate([groups[i][1] for i in picked])
        values.append(np.corrcoef(qs, rels)[0, 1])
    return float(np.percentile(values, 2.5)), float(np.percentile(values, 97.5))


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    run = read_json(OUT / "runs" / "V2-a.json")
    judge = ev.Judge(world)
    queries = {query["id"]: query for query in world["queries"]}

    totals = {"S2T": [], "T2S": []}
    for query_id, result in run["results"].items():
        totals[queries[query_id]["side"]].append(result["total"])
    accepts, qs, rels, groups = [], [], [], []
    for query_id, result in run["results"].items():
        query = queries[query_id]
        if query["side"] != "S2T":
            continue
        start = len(qs)
        for candidate in result["candidates"][:50]:
            if judge.known(query, candidate["candidateId"]):
                accepts.append(judge.accept(query, candidate["candidateId"]))
                qs.append(candidate.get("q") or 0)
                rels.append(judge.rel(query, candidate["candidateId"]))
        if len(qs) > start:
            groups.append((np.array(qs[start:], dtype=float), np.array(rels[start:], dtype=float)))

    test = [query for query in world["queries"] if query["split"] == "test" and query["side"] == "S2T"]
    market_queries = [query for query in world["queries"] if query["side"] == "S2T" and query["kind"] == "full"]
    rows = {}
    for name in ("Random", "Popularity", "V2-a"):
        values = []
        for query in test:
            ranking = ev.rankings(run, query, name, judge, None)
            metrics = ranking and ev.query_metrics(judge, query, ranking)
            if metrics:
                values.append(metrics)
        ranked = {query["id"]: (query, ev.rankings(run, query, name, judge, None)) for query in market_queries}
        market = ev.market(judge, ranked, random.Random(SEED))
        rows[name] = {metric: float(np.mean([value[metric] for value in values])) for metric in ("ndcg10", "ea10",
                                                                                                    "ms10")}
        rows[name].update(market)
        rows[name]["queries"] = len(values)

    checks = {
        "median_candidates_s2t": statistics.median(totals["S2T"]),
        "median_candidates_t2s": statistics.median(totals["T2S"]),
        "mean_p_accept_top50": float(np.mean(accepts)),
        "random_ea10": rows["Random"]["ea10"],
        "corr_q_rel": float(np.corrcoef(qs, rels)[0, 1]),
        "popularity_vs_random_ndcg": rows["Popularity"]["ndcg10"] - rows["Random"]["ndcg10"],
        "popularity_rejections_highest": "có" if rows["Popularity"]["rejections"] == max(
            row["rejections"] for row in rows.values()) else "không",
    }
    low, high = corr_ci(groups, np.random.default_rng(SEED))
    RESULTS.mkdir(parents=True, exist_ok=True)
    with (RESULTS / "kiem-tra-mo-phong.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["kiem_tra", "gia_tri", "muc_tieu"])
        for key, value in checks.items():
            writer.writerow([key, f"{value:.3f}" if isinstance(value, float) else value, TARGETS[key]])
        writer.writerow(["corr_q_rel_ktc95", f"[{low:.3f}; {high:.3f}]", "bootstrap theo truy vấn"])
        writer.writerow([])
        writer.writerow(["cau_hinh", "queries", "ndcg10", "ea10", "ms10", "match_rate", "rejections", "match_rel"])
        for name, row in rows.items():
            writer.writerow([name, row["queries"], *(f"{row[key]:.4f}" for key in ("ndcg10", "ea10", "ms10",
                                                                                     "match_rate", "rejections",
                                                                                     "match_rel"))])
    for key, value in checks.items():
        print(f"{key:32s} {value:.3f}" if isinstance(value, float) else f"{key:32s} {value}", f"(mục tiêu {TARGETS[key]})")
    print(f"{'corr_q_rel_ktc95':32s} [{low:.3f}; {high:.3f}] ({len(groups)} truy vấn)")
    for name, row in rows.items():
        print(f"{name:11s} NDCG {row['ndcg10']:.3f} EA {row['ea10']:.3f} MS {row['ms10']:.3f} "
              f"ghép {row['match_rate']:.3f} từ chối/HS {row['rejections']:.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
