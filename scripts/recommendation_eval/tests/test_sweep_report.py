import numpy as np

import evaluate
import sweep_report


class FakeJudge:
    """Two ordinary postings and one of a popular tutor; rel and P(accept) fixed per posting."""

    def __init__(self, world):
        self.teaching = {"popular": ({}, {"famous": True}), "good": ({}, {}), "weak": ({}, {})}
        self.rels = {"popular": 3, "good": 2, "weak": 1}

    def rel(self, query, cid):
        return self.rels[cid]

    def accept(self, query, cid):
        return 1.0


def test_popular_tutors_are_left_out_of_the_rankings_and_of_the_ideal(monkeypatch):
    world = {"queries": [{"id": "q", "split": "test", "side": "S2T"}]}
    orders = {"with-Q": ["popular", "good", "weak"], "without-Q": ["popular", "weak", "good"]}
    monkeypatch.setattr(sweep_report, "read_json", lambda path: world if path.name == "world.json" else {})
    monkeypatch.setattr(evaluate, "Judge", FakeJudge)
    monkeypatch.setattr(evaluate, "rankings", lambda run, query, name, judge, v1: orders[name])

    tests = sweep_report.popular_free_test(("with-Q", "without-Q"), np.random.default_rng(1))

    ndcg = next(t for t in tests if t["metric"].startswith("ndcg10"))
    assert ndcg["n"] == 1
    # with Q: good, weak = the ideal order of the ordinary tutors -> 1; without: weak first -> lower
    expected = evaluate.dcg([1, 2]) / evaluate.dcg([2, 1]) - 1
    assert np.isclose(ndcg["mean_diff"], expected)
    ms = next(t for t in tests if t["metric"].startswith("ms10"))
    assert np.isclose(ms["mean_diff"], 0.0), "the same tutors with rel >= 2 are in both top 10s"


def test_the_final_preset_reads_the_market_written_without_a_name():
    assert sweep_report.market_dir(None).name == "market"
    assert sweep_report.market_dir("t39").name == "market-t39"
    final = sweep_report.PRESETS["final"]
    assert all(left in final.configs and right in final.configs for left, right in final.pairs)
