import math

import scoring


def test_reciprocal_follows_the_backend():
    geometric = scoring.Params()
    harmonic = scoring.Params(reciprocal_mode="HARMONIC")

    assert math.isclose(scoring.combine(0.9, 0.2, geometric), 0.9 ** 0.65 * 0.2 ** 0.35)
    assert math.isclose(scoring.combine(0.9, 0.2, scoring.Params(reciprocal_alpha=0.5)), math.sqrt(0.18))
    assert math.isclose(scoring.combine(0.9, 0.2, scoring.Params(reciprocal_alpha=0.8)), 0.9 ** 0.8 * 0.2 ** 0.2)
    assert scoring.combine(0.9, 0.0, geometric) == 0, "nobody who will surely say no"
    assert math.isclose(scoring.combine(0.37, 0.81, scoring.Params(reciprocal_alpha=1.0)), 0.37)
    assert math.isclose(scoring.combine(0.9, 0.2, harmonic), 2 * 0.18 / 1.1)


def test_the_logged_gate_is_redone_with_the_new_gate_values():
    params = scoring.Params(gate_schedule=0.6, gate_reject=0.25)

    assert scoring.gate(1.0, params) == 1.0
    assert scoring.gate(0.4, params) == 0.6
    assert scoring.gate(0.5, params) == 0.25
    assert math.isclose(scoring.gate(0.2, params), 0.15)
    assert scoring.gate(None, params) == 1.0
    assert scoring.gate(0.4, scoring.Params()) == 0.4


def test_the_complaint_penalty_moves_q_by_its_change_in_points():
    candidate = {"candidateUserId": "u", "q": 0.3406}
    penalties = {"u": 5.71}

    assert scoring.quality(candidate, scoring.Params(penalties=penalties)) == 0.3406
    assert math.isclose(scoring.quality(candidate, scoring.Params(penalties=penalties, complaint_factor=1.5)),
                        0.3406 - 0.02855)
    assert math.isclose(scoring.quality(candidate, scoring.Params(penalties=penalties, complaint_factor=0.5)),
                        0.3406 + 0.02855)
    assert scoring.quality({"candidateUserId": "other", "q": 0.5},
                           scoring.Params(penalties=penalties, complaint_factor=1.5)) == 0.5
    assert scoring.quality({"candidateUserId": "u", "q": 0.01},
                           scoring.Params(penalties=penalties, complaint_factor=1.5)) == 0.0
