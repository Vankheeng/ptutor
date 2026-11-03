"""Test M1: /health và cấu hình."""

from fastapi.testclient import TestClient

from app.api.health import overall_status
from app.config import Settings
from app.main import create_app


def test_health_reports_llm_disabled_without_key(settings):
    client = TestClient(create_app(settings))
    body = client.get("/health").json()
    assert body["llm"] == "disabled"
    assert body["status"] == "degraded"


def test_llm_enabled_needs_both_key_and_model():
    assert not Settings(_env_file=None, openai_api_key="k", llm_model="").llm_enabled
    assert not Settings(_env_file=None, openai_api_key="", llm_model="m").llm_enabled
    assert Settings(_env_file=None, openai_api_key="k", llm_model="m").llm_enabled


def test_rewrite_and_verifier_default_to_actor_model():
    settings = Settings(_env_file=None, llm_model="actor-model")
    assert settings.effective_rewrite_model == "actor-model"
    assert settings.effective_verifier_model == "actor-model"


def test_overall_status_rules():
    assert overall_status({"db": "error", "llm": "ready"}) == "error"
    assert overall_status({"db": "ready", "llm": "ready", "embedding": "ready", "rerank": "ready"}) == "ready"
    assert overall_status({"db": "ready", "llm": "disabled"}) == "degraded"
