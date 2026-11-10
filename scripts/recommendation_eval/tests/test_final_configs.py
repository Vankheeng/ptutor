import evaluate
import run


def test_the_final_configuration_runs_the_backend_defaults():
    properties = run.config_properties("V2-final")

    assert properties == run.BASE


def test_each_final_ablation_switches_off_one_part():
    assert run.CONFIGS["V2-final-A1"] == {"enrichment.use-llm-tags": "false"}
    assert run.CONFIGS["V2-final-H1"] == {"history.enabled": "false"}
    assert run.CONFIGS["V2-final-noQ"] == {"quality.base": "1", "quality.weight": "0"}


def test_the_final_configurations_are_scored_and_compared():
    # The λ levels only run in the market.
    for name in (name for name in run.FINAL if not name.startswith("V2-final-l")):
        assert name in evaluate.ORDER
        assert any(name in pair for pair in evaluate.PAIRS)


def test_the_lambda_sensitivity_changes_only_lambda():
    names = [name for name in run.FINAL if name.startswith("V2-final-l")]

    assert [run.CONFIGS[name] for name in names] == [{"willingness.congestion-lambda": lam}
                                                     for lam in ("0.25", "0.375", "0.625", "0.75")]


def test_the_exploration_slots_are_compared_alone():
    assert ("V2-RC", "V2-final-H1") in evaluate.PAIRS
    assert ("V1-old", "V2-a") in evaluate.PAIRS
