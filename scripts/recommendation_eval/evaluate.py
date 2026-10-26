"""26b: metrics of every configuration on the test half, significance tests, tables and charts (design 09).

    python scripts/recommendation_eval/evaluate.py      # reads out/runs/*.json, writes docs/recommendation/ket-qua/

The answer key comes from the hidden layer of out/world.json (rel, P(accept)); the system never saw it.
Candidates that are not part of the simulated market (demo and real rows of the dev database) are removed
from each list before scoring, and the list closes up. Searches that fell back to the profile vector
(fallback.py) are counted per configuration and left out of the paired tests.
"""

import csv
import math
import random
import sys
from collections import Counter, defaultdict

import numpy as np
from scipy import stats

from common import OUT, RESULTS, SEED, read_json, uid, write_json
import baselines
import fallback
from world import p_accept, p_student_accepts, rel

# Reference rankings computed offline from the candidates of the V2-a run (baselines.py), then the backend runs.
OFFLINE = ["Random", "Popularity", "Semantic", "V1-old", "V2-linear"]
ORDER = OFFLINE + ["V2-a", "V2-R", "V2-RC", "V2-RCX", "V2-full", "V2-full-H1", "V2-full-A1"]
LABEL = {"V2-full-H1": "V2-full − H1", "V2-full-A1": "V2-full − A1", "V1-old": "V1 (thiết kế cũ)"}
PAIRS = [("Random", "V2-full"), ("Popularity", "V2-full"), ("Semantic", "V2-a"), ("V1-old", "V2-full"),
         ("V2-linear", "V2-a"), ("V2-a", "V2-R"), ("V2-R", "V2-RC"), ("V2-RC", "V2-RCX"), ("V2-RCX", "V2-full"),
         ("V2-full-H1", "V2-full"), ("V2-full-A1", "V2-full"),
         # text-only scenario (2.3)
         ("cosine-only", "rerank-w0.3"), ("cosine-only", "rerank-w0.5"), ("cosine-only", "rerank-w0.7"),
         ("cosine-only", "rerank-w0.9"), ("cosine-only", "cosine-only-sem0.45"),
         ("cosine-only-sem0.45", "rerank-w0.7-sem0.45"), ("cosine-only", "rerank-w0.7-t4000"),
         ("rerank-w0.7", "rerank-w0.7-t4000"),
         # 26/10/2026 (design 13, 13.2): the first pass of ASYNC ranks by the cosine part; the second reads the
         # scores the worker computed after it, like ONLINE does once its cache is full.
         ("cosine-only", "rerank-online"), ("cosine-only", "rerank-async"), ("cosine-only", "rerank-async-2"),
         ("rerank-online-2", "rerank-async-2")]
TEXT_ORDER = ["cosine-only", "rerank-w0.3", "rerank-w0.5", "rerank-w0.7", "rerank-w0.9", "cosine-only-sem0.45",
              "rerank-w0.7-sem0.45", "rerank-w0.7-t4000", "rerank-online", "rerank-async", "rerank-async-2",
              "rerank-online-2"]
METRICS = ["ndcg10", "p5", "mrr", "ea10", "ms10"]


class Judge:
    """Hidden answers for any (query, candidate) of the simulated market."""

    def __init__(self, world: dict):
        self.world = world
        self.students = {student["key"]: student for student in world["students"]}
        self.tutors = {tutor["key"]: tutor for tutor in world["tutors"]}
        self.teaching = {uid("eval", posting["key"]): (posting, tutor) for tutor in world["tutors"]
                         for posting in tutor["postings"]}
        self.studying = {uid("eval", student["posting"]["key"]): student for student in world["students"]
                         if student["has_posting"]}
        self.tutor_posting = {posting["key"]: (posting, tutor) for tutor in world["tutors"]
                              for posting in tutor["postings"]}

    def known(self, query: dict, candidate_id: str) -> bool:
        return candidate_id in (self.teaching if query["side"] == "S2T" else self.studying)

    def rel(self, query: dict, candidate_id: str) -> int:
        if query["side"] == "S2T":
            posting, tutor = self.teaching[candidate_id]
            return rel(self.students[query["owner"]], posting, tutor)
        posting, tutor = self.tutor_posting[query["posting"]]
        return rel(self.studying[candidate_id], posting, tutor)

    def accept(self, query: dict, candidate_id: str) -> float:
        """P(the other side says yes)."""
        if query["side"] == "S2T":
            posting, tutor = self.teaching[candidate_id]
            return p_accept(self.students[query["owner"]], posting, tutor)
        posting, tutor = self.tutor_posting[query["posting"]]
        return p_student_accepts(self.studying[candidate_id], posting, tutor)

    def ideal(self, query: dict) -> list:
        pool = self.teaching if query["side"] == "S2T" else self.studying
        return sorted((self.rel(query, candidate_id) for candidate_id in pool), reverse=True)


def dcg(gains) -> float:
    return sum((2 ** gain - 1) / math.log2(position + 2) for position, gain in enumerate(gains))


def query_metrics(judge: Judge, query: dict, ranking: list) -> dict | None:
    ranking = [candidate_id for candidate_id in ranking if judge.known(query, candidate_id)]
    ideal = judge.ideal(query)
    if not ideal or ideal[0] == 0:
        return None  # nothing relevant exists for this query
    gains = [judge.rel(query, candidate_id) for candidate_id in ranking]
    accepts = [judge.accept(query, candidate_id) for candidate_id in ranking[:10]]
    first = next((position for position, gain in enumerate(gains) if gain >= 2), None)
    top10 = gains[:10]
    return {
        "ndcg10": dcg(top10) / dcg(ideal[:10]),
        "p5": sum(gain >= 2 for gain in gains[:5]) / 5,
        "mrr": 0.0 if first is None else 1 / (first + 1),
        "ea10": float(np.mean(accepts)) if accepts else 0.0,
        "ms10": sum((gain >= 2) * accept for gain, accept in zip(top10, accepts)) / 10,
    }


def rankings(run: dict, query: dict, config: str, judge: "Judge", v1_old) -> list | None:
    """Top 20 as shown to the user; the offline references reorder the candidates logged by V2-a. None when a
    reference does not apply (the old design only had the student side)."""
    if config == "Random":
        return baselines.random_ranking(run, query, SEED)
    if config == "Popularity":
        return baselines.popularity_ranking(run, query)
    if config == "Semantic":
        return baselines.semantic_ranking(run, query)
    if config == "V2-linear":
        return baselines.linear_ranking(run, query)
    if config == "V1-old":
        return v1_old.ranking(run, query, judge.students[query["owner"]]) if query["side"] == "S2T" else None
    return [item["id"] for item in run["results"][query["id"]]["items"]]


def market(judge: Judge, ranked: dict, rng: random.Random, repeats: int = 200) -> dict:
    """Every student applies in turn to its top 3; tutors decide with P(accept) within their seats."""
    matched, rejections, quality = [], [], []
    seats0 = {candidate_id: posting["quantity"] for candidate_id, (posting, _) in judge.teaching.items()}
    for _ in range(repeats):
        seats = dict(seats0)
        order = list(ranked)
        rng.shuffle(order)
        got, refused, rels = 0, 0, []
        for query_id in order:
            query, ranking = ranked[query_id]
            for candidate_id in [c for c in ranking if judge.known(query, c)][:3]:
                if seats[candidate_id] > 0 and rng.random() < judge.accept(query, candidate_id):
                    seats[candidate_id] -= 1
                    got += 1
                    rels.append(judge.rel(query, candidate_id))
                    break
                refused += 1
        matched.append(got / len(order))
        rejections.append(refused / len(order))
        quality.append(np.mean(rels) if rels else 0)
    return {"match_rate": float(np.mean(matched)), "rejections": float(np.mean(rejections)),
            "match_rel": float(np.mean(quality))}


def gini(values) -> float:
    values = np.sort(np.asarray(values, dtype=float))
    if values.sum() == 0:
        return 0.0
    n = len(values)
    return float((2 * np.arange(1, n + 1) - n - 1).dot(values) / (n * values.sum()))


def exposure(judge: Judge, ranked: dict) -> dict:
    counts = Counter()
    for query, ranking in ranked.values():
        counts.update(c for c in ranking[:20] if judge.known(query, c))
    pool = list(judge.teaching)
    shown = [counts.get(candidate_id, 0) for candidate_id in pool]
    return {"gini": gini(shown), "coverage20": sum(value > 0 for value in shown) / len(pool)}


def bootstrap_ci(differences, rng: np.random.Generator, rounds: int = 10_000):
    differences = np.asarray(differences)
    means = rng.choice(differences, (rounds, len(differences)), replace=True).mean(axis=1)
    return float(np.percentile(means, 2.5)), float(np.percentile(means, 97.5))


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    judge = Judge(world)
    runs = {path.stem: read_json(path) for path in (OUT / "runs").glob("*.json")}
    v1_old = None
    if "V2-a" in runs:
        for name in OFFLINE:
            runs[name] = runs["V2-a"]
        v1_old = baselines.V1Old()
    configs = [name for name in ORDER + TEXT_ORDER if name in runs]
    # The offline references reorder the candidates of the V2-a run, so they share its fallbacks.
    fell_back = {name: fallback.flagged(runs[name]["results"], OUT / f"backend-{name}.log")
                 for name in configs if name not in OFFLINE}
    for name in OFFLINE:
        if name in runs:
            fell_back[name] = fell_back.get("V2-a", set())
    test = [query for query in world["queries"] if query["split"] == "test"]
    # The market is one shared pool: every student's "full" search takes part, from both halves.
    market_queries = [query for query in world["queries"] if query["side"] == "S2T" and query["kind"] == "full"]

    per_query = defaultdict(dict)  # config -> query id -> metrics
    summary = {}
    rng = random.Random(SEED)
    for config in configs:
        run = runs[config]
        ranked = {}
        for query in test:
            ranking = rankings(run, query, config, judge, v1_old)
            if ranking is None:
                continue
            ranked[query["id"]] = (query, ranking)
            values = query_metrics(judge, query, ranking)
            if values:
                per_query[config][query["id"]] = values
        s2t = {query["id"]: (query, rankings(run, query, config, judge, v1_old)) for query in market_queries}
        latencies = [] if config in OFFLINE else [run["results"][q["id"]]["latencyMs"] for q in test
                                                   if run["results"][q["id"]].get("latencyMs")]
        sides = {query["id"]: query["side"] for query in test}
        # Main table: a student looks for tutors. The tutor side is too thin (32 studying postings) and is
        # reported apart.
        main = [v for qid, v in per_query[config].items() if sides[qid] == "S2T"]
        other = [v for qid, v in per_query[config].items() if sides[qid] == "T2S"]
        row = {metric: float(np.mean([v[metric] for v in main])) for metric in METRICS}
        row["t2s_ndcg10"] = float(np.mean([v["ndcg10"] for v in other])) if other else None
        row["t2s_queries"] = len(other)
        row.update(market(judge, s2t, rng))
        row.update(exposure(judge, {qid: pair for qid, pair in ranked.items() if pair[0]["side"] == "S2T"}))
        row["p50_ms"] = float(np.percentile(latencies, 50)) if latencies else None
        row["p95_ms"] = float(np.percentile(latencies, 95)) if latencies else None
        row["queries"] = len(main)
        row["fallbacks"] = sum(1 for qid in per_query[config] if sides[qid] == "S2T" and qid in fell_back[config])
        summary[config] = row

    tests = []
    nrng = np.random.default_rng(SEED)
    for left, right in PAIRS:
        if left not in per_query or right not in per_query:
            continue
        both = sorted(qid for qid in set(per_query[left]) & set(per_query[right])
                      if next(q for q in test if q["id"] == qid)["side"] == "S2T")
        common_ids = [qid for qid in both if qid not in fell_back[left] and qid not in fell_back[right]]
        for metric in ("ndcg10", "ea10", "ms10"):
            differences = [per_query[right][qid][metric] - per_query[left][qid][metric] for qid in common_ids]
            nonzero = [d for d in differences if abs(d) > 1e-12]
            p_value = float(stats.wilcoxon(nonzero).pvalue) if len(nonzero) >= 10 else float("nan")
            low, high = bootstrap_ci(differences, nrng)
            tests.append({"from": left, "to": right, "metric": metric, "n": len(common_ids),
                          "excluded_fallback": len(both) - len(common_ids),
                          "mean_diff": float(np.mean(differences)), "ci_low": low, "ci_high": high,
                          "wilcoxon_p": p_value})

    RESULTS.mkdir(parents=True, exist_ok=True)
    write_csv(RESULTS / "9.7-bang-ket-qua.csv", summary)
    write_tests(RESULTS / "9.6-kiem-dinh.csv", tests)
    write_json(RESULTS / "9.7-bang-ket-qua.json", {"summary": summary, "tests": tests})
    chart(summary)
    print_table(summary, tests)
    return 0


def write_csv(path, summary: dict) -> None:
    columns = ["queries", "fallbacks", *METRICS, "match_rate", "rejections", "match_rel", "gini", "coverage20",
               "p50_ms", "p95_ms",
               "t2s_queries", "t2s_ndcg10"]
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["config", *columns])
        for config, row in summary.items():
            writer.writerow([LABEL.get(config, config), *[fmt(row[column]) for column in columns]])


def write_tests(path, tests: list) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["from", "to", "metric", "n", "excluded_fallback", "mean_diff", "ci95_low", "ci95_high",
                         "wilcoxon_p"])
        for row in tests:
            writer.writerow([LABEL.get(row["from"], row["from"]), LABEL.get(row["to"], row["to"]), row["metric"],
                             row["n"], row["excluded_fallback"], fmt(row["mean_diff"]), fmt(row["ci_low"]),
                             fmt(row["ci_high"]),
                             fmt(row["wilcoxon_p"])])


def fmt(value):
    if value is None:
        return ""
    if isinstance(value, float):
        return "" if math.isnan(value) else f"{value:.4f}"
    return value


def chart(summary: dict) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    names = list(summary)
    figure, axes = plt.subplots(1, 3, figsize=(13, 4))
    for axis, (metric, title) in zip(axes, [("ndcg10", "NDCG@10"), ("ea10", "EA@10"), ("match_rate", "Tỉ lệ ghép")]):
        axis.bar([LABEL.get(n, n) for n in names], [summary[n][metric] for n in names], color="#5b6bbf")
        axis.set_title(title)
        axis.tick_params(axis="x", rotation=45, labelsize=8)
        axis.set_ylim(0, 1)
    figure.tight_layout()
    figure.savefig(RESULTS / "9.7-so-sanh-cau-hinh.png", dpi=150)
    plt.close(figure)


def print_table(summary: dict, tests: list) -> None:
    print("config        n   fb  NDCG10  P@5   MRR   EA10  MS10  match  rej   Gini  Cov20  p95")
    for config, row in summary.items():
        p95 = "" if row["p95_ms"] is None else f"{row['p95_ms']:.0f}"
        print(f"{LABEL.get(config, config):13s} {row['queries']:3d} {row['fallbacks']:3d} {row['ndcg10']:.3f}  "
              f"{row['p5']:.3f} "
              f"{row['mrr']:.3f} {row['ea10']:.3f} {row['ms10']:.3f} {row['match_rate']:.3f} "
              f"{row['rejections']:.2f}  {row['gini']:.3f} {row['coverage20']:.3f} {p95}")
    for row in tests:
        print(f"{row['from']:>11s} -> {row['to']:<11s} {row['metric']:6s} n={row['n']} (-{row['excluded_fallback']}) "
              f"diff {row['mean_diff']:+.4f} "
              f"[{row['ci_low']:+.4f}, {row['ci_high']:+.4f}] p={row['wilcoxon_p']:.4g}")


if __name__ == "__main__":
    raise SystemExit(main())
