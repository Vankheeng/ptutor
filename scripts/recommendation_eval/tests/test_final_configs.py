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
    for name in run.FINAL:
        assert name in evaluate.ORDER
        assert any(name in pair for pair in evaluate.PAIRS)
