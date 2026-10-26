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
