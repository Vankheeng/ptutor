"""26c (2): sequential learning experiment of L1 (design 09, 9.9.2-9.9.4; 12).

    python scripts/recommendation_eval/learning.py           # full run: 3000 searches x 10 seeds
    python scripts/recommendation_eval/learning.py --quick   # 1000 searches x 3 seeds
    python scripts/recommendation_eval/learning.py --scenario crowded --run V2-RC-s0.5-g0.65 --diverse   # step 41

The features are exported once from the backend (out/runs/V2-full.json: up to 200 logged candidates per
search, all queries of both halves) and do not depend on the weights, so each simulated search is scored
again here with the weights the learner serves. The simulated searcher knows a hidden w* (price matters more,
semantics less than the prior); the system only sees the feedback. Every 100 searches the "nightly job" folds
the new samples into linear_bayes.LinearBayesModel (checked against the Java model by LinearBayesParityTest)
and projects the mean as in production. b is learned the same way from accept / reject decisions drawn with
a hidden beta.

Step 41 (design 13, 13.7): the defaults are κ = 50, an exploration scale of 0.5 and γ = 0.99 (once per update); R is the weighted geometric mean
of the ranking (α = 0.65); --run picks the logged search to read the features from (on crowded, the run with the
defaults of 26/10/2026, so that b_cap follows the new congestion formula); --diverse draws the searches without
repeating one before all have been used (crowded has ~1 000, against the 106 that base-dev repeated); the sensitivity
grid is κ x max-step; Fixed-bad / Fixed-reversed keep the wrong prior, the yardstick of whether L1 corrects it.
"""

import csv
import math
import sys

import numpy as np

from common import OUT, RESULTS, SEED, read_json
from linear_bayes import LinearBayesModel, prior_scale, project
from scoring import A_S2T, B_S2T, Params

ALPHA = Params().reciprocal_alpha  # reciprocal.alpha of the backend
# learning.prior-strength, learning.exploration-scale and learning.forgetting since 27/10/2026 (13.7)
KAPPA, RHO, GAMMA = 50.0, 0.5, 0.99
RUN = sys.argv[sys.argv.index("--run") + 1] if "--run" in sys.argv else "V2-full"
DIVERSE = "--diverse" in sys.argv

PRIOR_A = np.array([0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05])
W_STAR = np.array([0.15, 0.20, 0.35, 0.10, 0.10, 0.05, 0.05])  # hidden: price up, sem down
BAD_PRIOR = np.full(7, 1 / 7)
# The prior turned upside down: the most important feature gets the smallest weight (2.5).
REVERSED_PRIOR = np.array([0.05, 0.05, 0.10, 0.10, 0.20, 0.20, 0.30])
# Drift (2.5): from the search DRIFT_AT on, the searchers care about the need more and the price less.
W_DRIFT = np.array([0.15, 0.35, 0.15, 0.10, 0.10, 0.05, 0.10])
CURRENT = [None]  # the hidden weights in force at the current step
PRIOR_B = np.array([0.5, 1 / 6, 1 / 6, 1 / 6])
BETA_STAR = np.array([-2.0, 2.5, 1.0, 1.5, 1.5])  # intercept, hist, fitSegment, fitSched, fitPrice
KAPPA_U, CENTER_U, NOISE_U = 12.0, 0.55, 0.05
REWARD = {"view": 0.3, "favorite": 0.6, "request": 1.0}
EXAMINE = 0.85
SHOWN = 10
BATCH = 100
MIN_RATIO, MAX_RATIO, MAX_STEP, NOISE = 0.5, 2.0, 0.05, 0.3
INTERCEPT_KAPPA = 1.0  # learning.intercept-prior-strength
# The weights a strategy starts from; a Fixed* strategy keeps them.
PRIORS = {"LinTS-bad": BAD_PRIOR, "LinTS-reversed": REVERSED_PRIOR,
          "Fixed-bad": BAD_PRIOR, "Fixed-reversed": REVERSED_PRIOR}


def load_searches() -> list:
    """Per S2T search: feature matrix X (a), parts Z (b), quality factor and gate of each candidate."""
    world = read_json(OUT / "world.json")
    run = read_json(OUT / "runs" / f"{RUN}.json")
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
    r = np.where((a > 0) & (b > 0), np.maximum(a, 1e-12) ** ALPHA * np.maximum(b, 1e-12) ** (1 - ALPHA), 0)
    return r * search["quality"] * search["g"]


def search_order(rng: np.random.Generator, count: int, steps: int) -> np.ndarray:
    """Index of the search at every step. --diverse: a fresh permutation each time all have been used, so a search
    comes back only after every other one; otherwise drawn with replacement (the runs of 21/10/2026)."""
    if not DIVERSE:
        return rng.integers(count, size=steps)
    rounds = -(-steps // count)
    return np.concatenate([rng.permutation(count) for _ in range(rounds)])[:steps]


def utility(search: dict) -> np.ndarray:
    return search["x"] @ (W_STAR if CURRENT[0] is None else CURRENT[0])


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


def simulate(strategy: str, seed: int, steps: int, kappa: float = KAPPA, rho: float = RHO,
             ipw: bool = False, intercept_kappa: float = INTERCEPT_KAPPA, scaled_prior: bool = True,
             drift_at: int | None = None, forgetting: float = GAMMA, max_step: float = MAX_STEP) -> dict:
    """intercept_kappa = kappa and scaled_prior = False reproduce the model before 21/10/2026 (design 12, 12.3).
    drift_at: the hidden weights become W_DRIFT from that search on; forgetting: γ before every update."""
    rng = np.random.default_rng(seed)
    searches = load_searches.cache
    prior = PRIORS.get(strategy, PRIOR_A)
    fixed = strategy.startswith("Fixed")

    def new_model(weights, batch):
        # Like WeightLearningService: beta is measured on the first batch.
        x, r, s = batch
        scale = prior_scale(weights, x, r, s) if scaled_prior else 1.0
        return LinearBayesModel(weights, kappa, intercept_kappa, scale)

    model = LinearBayesModel(prior, kappa, intercept_kappa)  # until the first batch: the prior itself
    model_b = None
    first_a = True
    served = prior.copy()
    served_b = PRIOR_B.copy()
    w_star = W_STAR / W_STAR.sum()
    pending_a, pending_b = [], []
    samples = {}
    history = {"regret": [], "weight_error": [], "reward": [], "b_logloss": [], "b_auc": [], "clamped": 0,
               "max_step_seen": 0.0, "weights": []}
    cumulative = 0.0
    day_weights = {}
    order_of_searches = search_order(rng, len(searches), steps)
    for step in range(steps):
        CURRENT[0] = W_DRIFT if drift_at is not None and step >= drift_at else W_STAR
        w_star = CURRENT[0] / CURRENT[0].sum()
        search = searches[order_of_searches[step]]
        if strategy == "Oracle":
            order = np.argsort(-utility(search))
        elif fixed:
            order = np.argsort(-scores(search, prior, PRIOR_B))
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
            if not fixed and strategy != "Oracle":
                if pending_a:
                    x, r, s = map(np.array, zip(*pending_a))
                    if first_a:
                        model, first_a = new_model(prior, (x, r, s)), False
                    elif forgetting < 1:
                        model.forget(forgetting)
                    model.update_batch(x, r, s)
                new = project(model.mean(), prior, served, MIN_RATIO, MAX_RATIO, max_step)
                history["max_step_seen"] = max(history["max_step_seen"], float(np.max(np.abs(new - served))))
                low = np.maximum(0.01, MIN_RATIO * prior)
                high = np.maximum(low, MAX_RATIO * prior)
                positive = np.maximum(0.0, model.mean()[1:])
                scaled = positive / positive.sum() if positive.sum() > 0 else prior
                history["clamped"] += int(np.any((scaled < low) | (scaled > high)))
                served = new
                if pending_b:
                    z, y = map(np.array, zip(*pending_b))
                    if model_b is None:
                        model_b = new_model(PRIOR_B, (z, y, np.ones(len(y))))
                    model_b.update_batch(z, y, np.ones(len(y)))
                    served_b = project(model_b.mean(), PRIOR_B, served_b, MIN_RATIO, MAX_RATIO, max_step)
            history["b_logloss"].append(b_quality(model_b, pending_b)[0])
            history["b_auc"].append(b_quality(model_b, pending_b)[1])
            pending_a, pending_b = [], []
            day_weights = {}
            current = prior if fixed else (CURRENT[0] if strategy == "Oracle" else served)
            history["weight_error"].append(float(np.abs(current / current.sum() - w_star).sum()))
            history["weights"].append((current / current.sum()).tolist())
            history["regret"].append(cumulative)
            history["reward"].append(gained)
        samples["final_weights"] = (prior if fixed else CURRENT[0] if strategy == "Oracle" else served).tolist()
    history.update(samples)
    CURRENT[0] = None
    return history


def b_quality(model: LinearBayesModel, pending: list):
    """Log-loss and AUC of the learned b (mean, clipped to (0, 1)) on the decisions of the last batch."""
    if len(pending) < 5:
        return math.nan, math.nan
    z, y = map(np.array, zip(*pending))
    theta = model.mean() if model is not None else np.concatenate([[0.0], PRIOR_B])
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
    strategies = ["Fixed", "LinTS-prior", "LinTS-bad", "Fixed-bad", "LinTS-reversed", "Fixed-reversed", "Greedy",
                  "Oracle"]
    runs = {name: [simulate(name, SEED + seed, steps) for seed in range(seeds)] for name in strategies}
    # The defaults until 27/10/2026, for comparison (κ = 200, exploration 1.0, no forgetting).
    runs["LinTS-prior (κ=200, ρ=1, γ=1)"] = [simulate("LinTS-prior", SEED + seed, steps, kappa=200.0, rho=1.0,
                                                      forgetting=1.0) for seed in range(seeds)]
    runs["LinTS-prior (γ=1)"] = [simulate("LinTS-prior", SEED + seed, steps, forgetting=1.0) for seed in range(seeds)]
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
    plot_trajectories(runs["LinTS-prior"], runs["LinTS-reversed"], steps)
    for row in rows:
        print({key: (round(value, 4) if isinstance(value, float) else value) for key, value in row.items()})

    if not quick:
        # Step 41: κ x max-step at the exploration scale of the defaults, as many seeds as the main runs.
        fixed = np.array([h["regret"][-1] for h in runs["Fixed"]])
        sensitivity = []
        for kappa in (20.0, 50.0, 100.0):
            for max_step in (0.05, 0.1):
                histories = [simulate("LinTS-prior", SEED + seed, steps, kappa, max_step=max_step)
                             for seed in range(seeds)]
                regret = np.array([h["regret"][-1] for h in histories])
                sensitivity.append({"kappa": kappa, "max_step": max_step, "rho": RHO,
                                    "regret_final": float(regret.mean()), "regret_sd": float(regret.std()),
                                    "regret_vs_fixed": float((regret - fixed).mean()),
                                    "seeds_better_than_fixed": f"{int(np.sum(regret < fixed))}/{len(regret)}",
                                    "weight_error_final": float(np.mean([h["weight_error"][-1] for h in histories])),
                                    "max_step_seen": max(h["max_step_seen"] for h in histories)})
        write_rows(RESULTS / "9.9.4-do-nhay-L1.csv", sensitivity)
        print("L1 sensitivity:", *sensitivity, sep="\n  ")
        plot_kappa(sensitivity)
        drift(steps, seeds)
    print("prior weights", PRIOR_A, "hidden w*", W_STAR, "names", A_S2T)
    return 0


def drift(steps: int, seeds: int) -> None:
    """2.5: the searchers change their mind half way; does forgetting (γ < 1) catch up faster?"""
    at = steps // 2
    runs = {
        "Fixed": [simulate("Fixed", SEED + s, steps, drift_at=at) for s in range(seeds)],
        "LinTS γ=1": [simulate("LinTS-prior", SEED + s, steps, drift_at=at, forgetting=1.0) for s in range(seeds)],
        "LinTS γ=0,99": [simulate("LinTS-prior", SEED + s, steps, drift_at=at, forgetting=0.99) for s in range(seeds)],
        "LinTS γ=0,9": [simulate("LinTS-prior", SEED + s, steps, drift_at=at, forgetting=0.9) for s in range(seeds)],
        "Greedy γ=0,99": [simulate("Greedy", SEED + s, steps, drift_at=at) for s in range(seeds)],
        "Oracle": [simulate("Oracle", SEED + s, steps, drift_at=at) for s in range(seeds)],
    }
    rows = []
    for name, histories in runs.items():
        regret = np.array([h["regret"] for h in histories])
        error = np.array([h["weight_error"] for h in histories])
        half = at // BATCH - 1
        rows.append({"strategy": name, "regret_final": float(regret[:, -1].mean()),
                     "regret_after_drift": float((regret[:, -1] - regret[:, half]).mean()),
                     "weight_error_before_drift": float(error[:, half].mean()),
                     "weight_error_final": float(error[:, -1].mean())})
    write_rows(RESULTS / "9.9.5-drift.csv", rows)
    plot(runs, steps, RESULTS / "9.9.5-drift.png", f"đổi w* ở lần tìm {at}")
    for row in rows:
        print("drift", {k: (round(v, 4) if isinstance(v, float) else v) for k, v in row.items()})


def plot_trajectories(prior_runs: list, reversed_runs: list, steps: int) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    x = np.arange(BATCH, steps + 1, BATCH)
    figure, axes = plt.subplots(1, 2, figsize=(12, 4), sharey=True)
    for axis, histories, title in ((axes[0], prior_runs, "LinTS-prior"), (axes[1], reversed_runs, "LinTS-reversed")):
        weights = np.mean([h["weights"] for h in histories], axis=0)
        for k, name in enumerate(A_S2T):
            line = axis.plot(x, weights[:, k], label=name)[0]
            axis.axhline(W_STAR[k], color=line.get_color(), linestyle="--", linewidth=0.8)
        axis.set_title(f"Quỹ đạo trọng số — {title} (nét đứt: w*)")
        axis.set_xlabel("Số lần tìm")
    axes[0].legend(fontsize=8)
    figure.tight_layout()
    figure.savefig(RESULTS / "9.9.4-quy-dao-trong-so.png", dpi=150)
    plt.close(figure)


def plot_kappa(sensitivity: list) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    figure, axis = plt.subplots(figsize=(6, 4))
    for max_step in sorted({r["max_step"] for r in sensitivity}):
        rows = [r for r in sensitivity if r["max_step"] == max_step]
        axis.plot([r["kappa"] for r in rows], [r["weight_error_final"] for r in rows], marker="o",
                  label=f"max-step {max_step:g}")
    axis.set_xscale("log")
    axis.set_xlabel("κ (độ mạnh prior)")
    axis.set_ylabel("‖w − w*‖₁ sau 3 000 lần tìm")
    axis.legend(fontsize=8)
    figure.tight_layout()
    figure.savefig(RESULTS / "9.9.4-sai-so-theo-kappa.png", dpi=150)
    plt.close(figure)


def write_rows(path, rows: list) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({key: (f"{value:.4f}" if isinstance(value, float) else value) for key, value in row.items()})


def plot(runs: dict, steps: int, path=None, note: str = "") -> None:
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
    axes[0].set_title("Regret tích luỹ" + (f" ({note})" if note else ""))
    axes[1].set_title("Sai số trọng số ‖w − w*‖₁")
    axes[1].axhline(0.05, color="grey", linestyle="--", linewidth=0.8)
    for axis in axes:
        axis.set_xlabel("Số lần tìm")
        axis.legend(fontsize=8)
    figure.tight_layout()
    figure.savefig(path or RESULTS / "9.9.4-regret-va-sai-so.png", dpi=150)
    plt.close(figure)


if __name__ == "__main__":
    raise SystemExit(main())
