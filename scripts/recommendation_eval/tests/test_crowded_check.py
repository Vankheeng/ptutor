import numpy as np

import crowded_check


def test_the_interval_holds_the_correlation_of_queries_that_agree():
    groups = [(np.array([0.1, 0.5, 0.9]), np.array([1.0, 2.0, 3.0])) for _ in range(20)]

    low, high = crowded_check.corr_ci(groups, np.random.default_rng(1), rounds=200)

    assert abs(low - 1) < 1e-9 and abs(high - 1) < 1e-9


def test_the_interval_widens_when_queries_disagree():
    rng = np.random.default_rng(3)
    groups = [(q := rng.random(10), q * (1 if i % 2 else -1) + rng.normal(0, 0.1, 10)) for i in range(40)]

    low, high = crowded_check.corr_ci(groups, np.random.default_rng(1), rounds=500)

    point = np.corrcoef(np.concatenate([g[0] for g in groups]), np.concatenate([g[1] for g in groups]))[0, 1]
    assert low < point < high
    assert high - low > 0.1
