import numpy as np

import learning
import scoring


def test_the_simulated_ranking_uses_the_reciprocal_of_the_backend():
    search = {"x": np.array([[0.9, 0, 0, 0, 0, 0, 0], [0.4, 0, 0, 0, 0, 0, 0]]),
              "z": np.array([[0.2, 0, 0, 0], [0.0, 0, 0, 0]]), "cap": np.array([1.0, 1.0]),
              "quality": np.array([1.0, 1.0]), "g": np.array([1.0, 1.0])}
    weights_a = np.array([1, 0, 0, 0, 0, 0, 0.0])
    weights_b = np.array([1, 0, 0, 0.0])

    scores = learning.scores(search, weights_a, weights_b)

    assert np.isclose(scores[0], scoring.combine(0.9, 0.2, scoring.Params()))
    assert scores[1] == 0, "b = 0: nobody who will surely say no"


def test_diverse_searches_come_back_only_after_every_other_one(monkeypatch):
    monkeypatch.setattr(learning, "DIVERSE", True)
    order = learning.search_order(np.random.default_rng(1), 5, 12)

    assert sorted(order[:5]) == [0, 1, 2, 3, 4]
    assert sorted(order[5:10]) == [0, 1, 2, 3, 4]
    assert len(order) == 12


def test_the_defaults_follow_the_backend():
    assert learning.KAPPA == 50.0 and learning.RHO == 0.5 and learning.GAMMA == 0.99
