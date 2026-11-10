"""Recomputes the score of logged candidates with other weights (design 06, 6.5-6.8; 09, 9.9.3).

The features in recommendation_logs do not depend on the weights, so a ranking can be redone offline:
    a_base = sum_k w_k x_k  (a missing feature counts as the neutral prior 0.5)
    a      = (1 - wHist) a_base + wHist hist
    b      = (w_hist history + sum_k w_k fit_k) x cap      (1 when the other side reached out first)
    R      = a^alpha b^(1 - alpha)  (GEOMETRIC, since 26/10/2026)  or  2ab / (a + b)  (HARMONIC)
    score  = R x (base + weight q) x G                     (score = a x ... when b is off)
G and q can be redone too (9.9.1): the logged G is 1, the schedule gate, the recent-rejection gate or both, told
apart by their default values; q moves by the change of a tutor's complaint penalty (points out of 100).
"""

from dataclasses import dataclass, field, replace

A_S2T = ["sem", "need", "price", "style", "sched", "area", "segment"]
A_T2S = ["sem", "price", "need", "segment", "sched", "area"]
B_S2T = ["hist", "fitSegment", "fitSched", "fitPrice"]
B_T2S = ["hist", "fitQuality", "fitStyle", "fitPrice"]
# Names of the fit parts inside the logged "willingness" object (the model's feature names without "hist").
LOGGED_FIT = {"fitSegment": "fitSegment", "fitSched": "fitSched", "fitPrice": "fitPrice",
              "fitQuality": "fitQuality", "fitStyle": "fitStyle"}


@dataclass(frozen=True)
class Params:
    """The prior weights and constants of RecommendationSettings."""
    a_s2t: dict = field(default_factory=lambda: dict(zip(A_S2T, [0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05])))
    a_t2s: dict = field(default_factory=lambda: dict(zip(A_T2S, [0.30, 0.25, 0.20, 0.10, 0.10, 0.05])))
    b_s2t: dict = field(default_factory=lambda: dict(zip(B_S2T, [0.5, 1 / 6, 1 / 6, 1 / 6])))
    b_t2s: dict = field(default_factory=lambda: dict(zip(B_T2S, [0.5, 0.2, 0.15, 0.15])))
    confidence_prior: float = 0.5
    quality_base: float = 0.7
    quality_weight: float = 0.3
    reciprocal: bool = True
    reciprocal_mode: str = "GEOMETRIC"  # reciprocal.mode (design 13, 13.5)
    reciprocal_alpha: float = 0.65
    gate_schedule: float = 0.4  # gate.no-schedule-overlap
    gate_reject: float = 0.5  # gate.recent-reject
    complaint_factor: float = 1.0  # complaint.penalty-scale x this
    penalties: dict = field(default_factory=dict)  # tutor user id -> complaint penalty in the logged q (points)

    def with_weight(self, group: str, name: str, factor: float) -> "Params":
        """One weight times factor, then the group normalised back to a sum of 1."""
        weights = dict(getattr(self, group))
        weights[name] *= factor
        total = sum(weights.values())
        return replace(self, **{group: {key: value / total for key, value in weights.items()}})


def a_value(candidate: dict, weights: dict, params: Params) -> float:
    features = candidate.get("features") or {}
    base = sum(weight * (params.confidence_prior if features.get(name) is None else features[name])
               for name, weight in weights.items())
    hist, share = features.get("hist"), candidate.get("wHist") or 0
    return base if hist is None else (1 - share) * base + share * hist


def b_value(candidate: dict, weights: dict):
    parts = candidate.get("willingness")
    if candidate.get("b") is None or not parts:
        return None
    if parts.get("invited"):
        return 1.0
    value = weights[next(iter(weights))] * parts["hist"]
    for name, weight in list(weights.items())[1:]:
        value += weight * parts.get(LOGGED_FIT[name], 0)
    return value * parts["cap"]


def combine(a: float, b: float, params: Params) -> float:
    """R as in Ranker.reciprocal."""
    if params.reciprocal_mode == "HARMONIC":
        return 0 if a + b == 0 else 2 * a * b / (a + b)
    return 0 if a <= 0 or b <= 0 else a ** params.reciprocal_alpha * b ** (1 - params.reciprocal_alpha)


DEFAULT_GATES = Params()


def gate(logged, params: Params) -> float:
    """The logged G with the gates of params: each logged gate is recognised by its default value."""
    if logged is None:
        return 1.0
    schedule, reject = DEFAULT_GATES.gate_schedule, DEFAULT_GATES.gate_reject
    for value, redone in ((1.0, 1.0), (schedule, params.gate_schedule), (reject, params.gate_reject),
                          (schedule * reject, params.gate_schedule * params.gate_reject)):
        if abs(logged - value) < 1e-6:
            return redone
    return logged


def quality(candidate: dict, params: Params):
    """The logged q, with the complaint penalty of the tutor scaled by complaint_factor (Q stays in [0, 1])."""
    q = candidate.get("q")
    penalty = params.penalties.get(candidate.get("candidateUserId"))
    if q is None or not penalty or params.complaint_factor == 1.0:
        return q
    return min(1.0, max(0.0, q + penalty * (1 - params.complaint_factor) / 100))


def score(candidate: dict, side: str, params: Params) -> float:
    a = a_value(candidate, params.a_s2t if side == "S2T" else params.a_t2s, params)
    b = b_value(candidate, params.b_s2t if side == "S2T" else params.b_t2s) if params.reciprocal else None
    r = a if b is None else combine(a, b, params)
    q = quality(candidate, params)
    factor = params.quality_base + params.quality_weight * (0.5 if q is None else q) if side == "S2T" else 1.0
    return r * factor * gate(candidate.get("g"), params)


def rerank(candidates: list, side: str, params: Params) -> list:
    """Candidate ids ordered by the recomputed score (ties keep the logged order)."""
    scored = [(score(candidate, side, params), -index, candidate["candidateId"])
              for index, candidate in enumerate(candidates)]
    return [candidate_id for _, _, candidate_id in sorted(scored, reverse=True)]


def v1_rerank(candidates: list, side: str, params: Params) -> list:
    """Baseline V1 (design 09, 9.4): one linear sum of the 7 suitability features and Q, no gate, no b,
    a missing feature counts 0. Rebuilt from the description; the old documents are not in the repository."""
    weights = params.a_s2t if side == "S2T" else params.a_t2s
    scored = []
    for index, candidate in enumerate(candidates):
        features = candidate.get("features") or {}
        linear = sum(weight * (features.get(name) or 0) for name, weight in weights.items())
        q = candidate.get("q")
        scored.append((0.85 * linear + 0.15 * (0.5 if q is None else q), -index, candidate["candidateId"]))
    return [candidate_id for _, _, candidate_id in sorted(scored, reverse=True)]
