"""26c (2): sequential learning experiment of L1 (design 09, 9.9.2-9.9.4; 12).

    python scripts/recommendation_eval/learning.py           # full run: 3000 searches x 10 seeds
    python scripts/recommendation_eval/learning.py --quick   # 1000 searches x 3 seeds

The features are exported once from the backend (out/runs/V2-full.json: up to 200 logged candidates per
search, all queries of both halves) and do not depend on the weights, so each simulated search is scored
again here with the weights the learner serves. The simulated searcher knows a hidden w* (price matters more,
semantics less than the prior); the system only sees the feedback. Every 100 searches the "nightly job" folds
the new samples into linear_bayes.LinearBayesModel (checked against the Java model by LinearBayesParityTest)
and projects the mean as in production. b is learned the same way from accept / reject decisions drawn with
a hidden beta.
"""

import csv
import math
import sys

import numpy as np

from common import OUT, RESULTS, SEED, read_json
from linear_bayes import LinearBayesModel, project
from scoring import A_S2T, B_S2T, Params

PRIOR_A = np.array([0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05])
W_STAR = np.array([0.15, 0.20, 0.35, 0.10, 0.10, 0.05, 0.05])  # hidden: price up, sem down
BAD_PRIOR = np.full(7, 1 / 7)
PRIOR_B = np.array([0.5, 1 / 6, 1 / 6, 1 / 6])
BETA_STAR = np.array([-2.0, 2.5, 1.0, 1.5, 1.5])  # intercept, hist, fitSegment, fitSched, fitPrice
KAPPA_U, CENTER_U, NOISE_U = 12.0, 0.55, 0.05
REWARD = {"view": 0.3, "favorite": 0.6, "request": 1.0}
EXAMINE = 0.85
SHOWN = 10
BATCH = 100
MIN_RATIO, MAX_RATIO, MAX_STEP, NOISE = 0.5, 2.0, 0.05, 0.3


def load_searches() -> list:
    """Per S2T search: feature matrix X (a), parts Z (b), quality factor and gate of each candidate."""
    world = read_json(OUT / "world.json")
    run = read_json(OUT / "runs" / "V2-full.json")
    searches = []
    for query in world["queries"]:
        if query["side"] != "S2T":
            continue
        candidates = run["results"][query["id"]].get("candidates") or []
        if len(candidates) < SHOWN:
            continue
        x = np.array([[0.5 if (c.get("features") or {}).get(name) is None else c["features"][name]
                       for name in A_S2T] for c in candidates])
        z = np.array([[(c.get("willingness") or {}).get("hist", 0.5)]
                      + [(c.get("willingness") or {}).get(name, 0.5) for name in B_S2T[1:]] for c in candidates])
        cap = np.array([(c.get("willingness") or {}).get("cap", 1.0) for c in candidates])
        q = np.array([0.5 if c.get("q") is None else c["q"] for c in candidates])
        g = np.array([c.get("g") or 1.0 for c in candidates])
        searches.append({"owner": query["owner"], "x": x, "z": z, "cap": cap, "quality": 0.7 + 0.3 * q, "g": g})
    return searches


def scores(search: dict, weights_a: np.ndarray, weights_b: np.ndarray) -> np.ndarray:
    a = search["x"] @ weights_a
    b = (search["z"] @ weights_b) * search["cap"]
    r = np.where(a + b > 0, 2 * a * b / np.maximum(a + b, 1e-12), 0)
    return r * search["quality"] * search["g"]


def utility(search: dict) -> np.ndarray:
    return search["x"] @ W_STAR


def attraction(u: np.ndarray) -> np.ndarray:
    return 1 / (1 + np.exp(-KAPPA_U * (u - CENTER_U)))


def level(u: np.ndarray) -> np.ndarray:
    """Reward of an attracted searcher: view, favorite or request, by how much they like it."""
    return np.where(u > CENTER_U + 0.12, REWARD["request"], np.where(u > CENTER_U + 0.05, REWARD["favorite"],
                                                                      REWARD["view"]))


def expected_reward(search: dict, order: np.ndarray) -> float:
    u = utility(search)[order[:SHOWN]]
    examine = EXAMINE ** np.arange(SHOWN)
    return float(np.sum(examine * attraction(u) * level(u)))


def p_accept(search: dict, index: np.ndarray) -> np.ndarray:
    z = search["z"][index]
    return 1 / (1 + np.exp(-(BETA_STAR[0] + z @ BETA_STAR[1:])))


def simulate(strategy: str, seed: int, steps: int, kappa: float = 200.0, rho: float = 1.0,
             ipw: bool = False) -> dict:
    rng = np.random.default_rng(seed)
    searches = load_searches.cache
    prior = BAD_PRIOR if strategy == "LinTS-bad" else PRIOR_A
    model = LinearBayesModel(prior, kappa)
    model_b = LinearBayesModel(PRIOR_B, kappa)
    served = prior.copy()
    served_b = PRIOR_B.copy()
    w_star = W_STAR / W_STAR.sum()
    pending_a, pending_b = [], []
    samples = {}
    history = {"regret": [], "weight_error": [], "reward": [], "b_logloss": [], "b_auc": [], "clamped": 0,
               "max_step_seen": 0.0}
    cumulative = 0.0
    day_weights = {}
    for step in range(steps):
        search = searches[rng.integers(len(searches))]
        if strategy == "Oracle":
            order = np.argsort(-utility(search))
        elif strategy == "Fixed":
            order = np.argsort(-scores(search, PRIOR_A, PRIOR_B))
        else:
            key = search["owner"]
            if key not in day_weights:
                if strategy == "Greedy" or rho == 0:
                    theta = model.mean()
                else:
                    theta = model.sample(rng, rho, NOISE)
                day_weights[key] = project(theta, prior, served, MIN_RATIO, MAX_RATIO, 1.0)
            order = np.argsort(-scores(search, day_weights[key], served_b))
        oracle = expected_reward(search, np.argsort(-utility(search)))
        gained = expected_reward(search, order)
        cumulative += oracle - gained
        # Feedback: examine top-down, attracted items give a reward; requests are decided by the other side.
        shown = order[:SHOWN]
        u = utility(search)[shown] + rng.normal(0, NOISE_U, SHOWN)
        examined = rng.random(SHOWN) < EXAMINE ** np.arange(SHOWN)
        attracted = examined & (rng.random(SHOWN) < attraction(u))
        rewards = np.where(attracted, level(u), 0.0)
        if attracted.any():
            depth = min(SHOWN, int(np.max(np.nonzero(attracted)[0])) + 1 + 2)
            for position in range(depth):
                weight = 1 / max(EXAMINE ** position, 0.1) if ipw else 1.0
                pending_a.append((search["x"][shown[position]], rewards[position], min(weight, 10.0)))
        requested = shown[rewards >= REWARD["request"]]
        if len(requested):
            accepted = rng.random(len(requested)) < p_accept(search, requested)
            for index, label in zip(requested, accepted):
                pending_b.append((search["z"][index], float(label)))
        if (step + 1) % BATCH == 0:
            if strategy not in ("Fixed", "Oracle"):
                if pending_a:
                    x, r, s = map(np.array, zip(*pending_a))
                    model.update_batch(x, r, s)
                new = project(model.mean(), prior, served, MIN_RATIO, MAX_RATIO, MAX_STEP)
                history["max_step_seen"] = max(history["max_step_seen"], float(np.max(np.abs(new - served))))
                low = np.maximum(0.01, MIN_RATIO * prior)
                high = np.maximum(low, MAX_RATIO * prior)
                history["clamped"] += int(np.any((model.mean()[1:] < low) | (model.mean()[1:] > high)))
                served = new
                if pending_b:
                    z, y = map(np.array, zip(*pending_b))
                    model_b.update_batch(z, y, np.ones(len(y)))
                    served_b = project(model_b.mean(), PRIOR_B, served_b, MIN_RATIO, MAX_RATIO, MAX_STEP)
            history["b_logloss"].append(b_quality(model_b, pending_b)[0])
            history["b_auc"].append(b_quality(model_b, pending_b)[1])
            pending_a, pending_b = [], []
            day_weights = {}
            current = PRIOR_A if strategy == "Fixed" else (W_STAR if strategy == "Oracle" else served)
            history["weight_error"].append(float(np.abs(current / current.sum() - w_star).sum()))
            history["regret"].append(cumulative)
            history["reward"].append(gained)
        samples["final_weights"] = (served if strategy not in ("Fixed", "Oracle") else
                                    (PRIOR_A if strategy == "Fixed" else W_STAR)).tolist()
    history.update(samples)
    return history


def b_quality(model: LinearBayesModel, pending: list):
    """Log-loss and AUC of the learned b (mean, clipped to (0, 1)) on the decisions of the last batch."""
    if len(pending) < 5:
        return math.nan, math.nan
    z, y = map(np.array, zip(*pending))
    theta = model.mean()
    p = np.clip(theta[0] + z @ theta[1:], 0.01, 0.99)
    logloss = float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))
    positives, negatives = p[y == 1], p[y == 0]
    if not len(positives) or not len(negatives):
        return logloss, math.nan
    auc = float(np.mean([(pos > neg) + 0.5 * (pos == neg) for pos in positives for neg in negatives]))
    return logloss, auc


def steps_to_converge(errors: list, threshold: float = 0.05):
    for index, value in enumerate(errors):
        if value < threshold:
            return (index + 1) * BATCH
    return None


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    quick = "--quick" in sys.argv
    steps, seeds = (1000, 3) if quick else (3000, 10)
    load_searches.cache = load_searches()
    print(f"{len(load_searches.cache)} searches with at least {SHOWN} logged candidates", flush=True)
    strategies = ["Fixed", "LinTS-prior", "LinTS-bad", "Greedy", "Oracle"]
    runs = {name: [simulate(name, SEED + seed, steps) for seed in range(seeds)] for name in strategies}
    RESULTS.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, histories in runs.items():
        regret = np.array([h["regret"] for h in histories])
        error = np.array([h["weight_error"] for h in histories])
        converge = [steps_to_converge(h["weight_error"]) for h in histories]
        reached = [value for value in converge if value is not None]
        rows.append({
            "strategy": name, "regret_final": float(regret[:, -1].mean()), "regret_sd": float(regret[:, -1].std()),
            "weight_error_final": float(error[:, -1].mean()),
            "searches_to_error_0.05": float(np.median(reached)) if reached else None,
            "seeds_converged": f"{len(reached)}/{len(histories)}",
            "max_step_seen": max(h["max_step_seen"] for h in histories),
            "batches_with_clamped_mean": float(np.mean([h["clamped"] for h in histories])),
            "b_logloss_last": float(np.nanmean([np.nanmean(h["b_logloss"][-5:]) for h in histories])),
            "b_auc_last": float(np.nanmean([np.nanmean(h["b_auc"][-5:]) for h in histories])),
            "final_weights": " ".join(f"{v:.3f}" for v in np.mean([h["final_weights"] for h in histories], axis=0)),
        })
    write_rows(RESULTS / "9.9.4-hoc-trong-so.csv", rows)
    plot(runs, steps)
    for row in rows:
        print({key: (round(value, 4) if isinstance(value, float) else value) for key, value in row.items()})

    if not quick:
        sensitivity = []
        for kappa in (50.0, 200.0, 800.0):
            for rho in (0.0, 0.5, 1.0, 2.0):
                for ipw in (False, True):
                    histories = [simulate("LinTS-prior", SEED + seed, steps, kappa, rho, ipw) for seed in range(3)]
                    sensitivity.append({"kappa": kappa, "rho": rho, "ipw": ipw,
                                        "regret_final": float(np.mean([h["regret"][-1] for h in histories])),
                                        "weight_error_final": float(np.mean([h["weight_error"][-1] for h in histories]))})
        write_rows(RESULTS / "9.9.4-do-nhay-L1.csv", sensitivity)
        print("L1 sensitivity:", *sensitivity, sep="\n  ")
    print("prior weights", PRIOR_A, "hidden w*", W_STAR, "names", A_S2T)
    return 0


def write_rows(path, rows: list) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({key: (f"{value:.4f}" if isinstance(value, float) else value) for key, value in row.items()})


def plot(runs: dict, steps: int) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    x = np.arange(BATCH, steps + 1, BATCH)
    figure, axes = plt.subplots(1, 2, figsize=(12, 4))
    for name, histories in runs.items():
        regret = np.mean([h["regret"] for h in histories], axis=0)
        error = np.mean([h["weight_error"] for h in histories], axis=0)
        axes[0].plot(x, regret, label=name)
        axes[1].plot(x, error, label=name)
    axes[0].set_title("Regret tích luỹ")
    axes[1].set_title("Sai số trọng số ‖w − w*‖₁")
    axes[1].axhline(0.05, color="grey", linestyle="--", linewidth=0.8)
    for axis in axes:
        axis.set_xlabel("Số lần tìm")
        axis.legend(fontsize=8)
    figure.tight_layout()
    figure.savefig(RESULTS / "9.9.4-regret-va-sai-so.png", dpi=150)
    plt.close(figure)


if __name__ == "__main__":
    raise SystemExit(main())
