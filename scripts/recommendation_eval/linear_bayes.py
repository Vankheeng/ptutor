"""Python port of backend LinearBayesModel and its project() (design 12, 12.3), for the learning experiment.

    python scripts/recommendation_eval/linear_bayes.py --fixture
        # writes backend/src/test/resources/recommendation/linear-bayes-parity.json, which
        # LinearBayesParityTest replays in Java: same inputs -> same mu and same projected weights.
"""

import json
import random
import sys

import numpy as np

from common import REPO

FIXTURE = REPO / "backend" / "src" / "test" / "resources" / "recommendation" / "linear-bayes-parity.json"


class LinearBayesModel:
    """r ~ theta0 + theta . x; prior theta ~ N((0, w_prior), I / kappa); kept as A and c."""

    def __init__(self, prior_weights, kappa: float):
        self.kappa = kappa
        self.prior_theta = np.concatenate([[0.0], np.asarray(prior_weights, dtype=float)])
        size = len(self.prior_theta)
        self.a = np.eye(size) * kappa
        self.c = kappa * self.prior_theta.copy()

    def forget(self, gamma: float) -> None:
        a0 = np.eye(len(self.c)) * self.kappa
        c0 = self.kappa * self.prior_theta
        self.a = a0 + gamma * (self.a - a0)
        self.c = c0 + gamma * (self.c - c0)

    def update(self, features, reward: float, weight: float = 1.0) -> None:
        x = np.concatenate([[1.0], np.asarray(features, dtype=float)])
        self.a += weight * np.outer(x, x)
        self.c += weight * reward * x

    def update_batch(self, features: np.ndarray, rewards: np.ndarray, weights: np.ndarray) -> None:
        x = np.hstack([np.ones((len(features), 1)), features])
        self.a += (x * weights[:, None]).T @ x
        self.c += x.T @ (weights * rewards)

    def mean(self) -> np.ndarray:
        return np.linalg.solve(self.a, self.c)

    def sample(self, rng: np.random.Generator, scale: float, noise: float) -> np.ndarray:
        lower = np.linalg.cholesky(self.a)
        deviation = np.linalg.solve(lower.T, rng.standard_normal(len(self.c)))
        return self.mean() + scale * noise * deviation


def project(theta, prior, previous, min_ratio: float, max_ratio: float, max_step: float) -> np.ndarray:
    """Same steps as LinearBayesModel.project in Java: theta on the scale of the weights (no intercept, negatives
    as 0, sum 1), then the closest weights summing to 1 within the band around the prior and the step limit."""
    prior = np.asarray(prior, dtype=float)
    previous = np.asarray(previous, dtype=float)
    target = np.maximum(0.0, np.asarray(theta, dtype=float)[1:])
    target = target / target.sum() if target.sum() > 0 else previous.copy()
    low = np.maximum(0.01, min_ratio * prior)
    high = np.maximum(low, max_ratio * prior)
    lower = np.maximum(low, previous - max_step)
    upper = np.minimum(high, previous + max_step)
    outside = lower > upper
    lower[outside] = upper[outside] = np.minimum(high, np.maximum(low, previous))[outside]
    if lower.sum() > 1 or upper.sum() < 1:
        lower, upper = low, high
    if lower.sum() > 1 or upper.sum() < 1:
        clipped = np.minimum(high, np.maximum(low, target))
        return clipped / clipped.sum()
    start, stop = float(np.min(lower - target)), float(np.max(upper - target))
    for _ in range(100):
        middle = (start + stop) / 2
        if np.minimum(upper, np.maximum(lower, target + middle)).sum() < 1:
            start = middle
        else:
            stop = middle
    return np.minimum(upper, np.maximum(lower, target + (start + stop) / 2))


def make_fixture() -> dict:
    rng = random.Random(7)
    cases = []
    for number in range(6):
        prior = [rng.uniform(0.02, 0.4) for _ in range(rng.choice([4, 6, 7]))]
        total = sum(prior)
        prior = [value / total for value in prior]
        kappa = rng.choice([50.0, 200.0, 800.0])
        observations = [{"x": [round(rng.random(), 4) for _ in prior], "r": round(rng.uniform(-0.2, 1.0), 3),
                         "s": rng.choice([0.5, 1.0, 1.5, 2.0])} for _ in range(rng.randint(5, 400))]
        gamma = rng.choice([1.0, 0.9, 0.5])
        model = LinearBayesModel(prior, kappa)
        for observation in observations:
            model.update(observation["x"], observation["r"], observation["s"])
        model.forget(gamma)
        mu = model.mean()
        previous = [value * rng.uniform(0.8, 1.2) for value in prior]
        min_ratio, max_ratio, max_step = 0.5, 2.0, rng.choice([0.05, 0.02, 1.0])
        cases.append({
            "name": f"case-{number}", "prior": prior, "kappa": kappa, "observations": observations,
            "forgetting": gamma, "expectedMean": mu.tolist(), "previous": previous,
            "minRatio": min_ratio, "maxRatio": max_ratio, "maxStep": max_step,
            "expectedProjected": project(mu, prior, previous, min_ratio, max_ratio, max_step).tolist(),
        })
    return {"description": "Written by scripts/recommendation_eval/linear_bayes.py --fixture; replayed by "
                           "LinearBayesParityTest. Python and Java must give the same mean and weights.",
            "cases": cases}


def main() -> int:
    if "--fixture" not in sys.argv:
        print(__doc__)
        return 1
    FIXTURE.write_text(json.dumps(make_fixture(), indent=1), encoding="utf-8")
    print(f"wrote {FIXTURE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
