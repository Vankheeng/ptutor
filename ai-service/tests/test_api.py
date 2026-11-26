from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.embed import pool_and_normalize
from app.main import create_app

TOKEN = "test-token"


class FakeEmbedder:
    model_version = "fake-model"
    dimensions = 4

    def __init__(self):
        self.calls = []

    def embed(self, texts):
        self.calls.append(texts)
        return np.array([[float(len(text)), 0.0, 0.0, 1.0] for text in texts], dtype=np.float32)


def settings(**overrides) -> Settings:
    values = dict(
        model_dir=Path("unused"),
        internal_token=TOKEN,
        allow_anonymous=False,
        auto_download=False,
        embed_max_tokens=512,
        embed_max_batch=3,
        embed_max_chars=20,
        threads=0,
    )
    values.update(overrides)
    return Settings(**values)


@pytest.fixture
def embedder():
    return FakeEmbedder()


@pytest.fixture
def client(embedder):
    with TestClient(create_app(settings(), embedder=embedder)) as test_client:
        yield test_client


def post(client, texts, token=TOKEN):
    headers = {} if token is None else {"X-Internal-Token": token}
    return client.post("/embed", json={"texts": texts}, headers=headers)


def test_health_is_ready_when_model_is_loaded_and_needs_no_token(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ready"


def test_embed_returns_one_vector_per_text_in_order(client, embedder):
    response = post(client, ["a", "abc"])

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "fake-model"
    assert body["dimensions"] == 4
    assert [vector[0] for vector in body["embeddings"]] == [1.0, 3.0]
    assert embedder.calls == [["a", "abc"]]


@pytest.mark.parametrize("token", [None, "wrong", ""])
def test_embed_rejects_missing_or_wrong_token(client, embedder, token):
    assert post(client, ["a"], token=token).status_code == 401
    assert embedder.calls == []


@pytest.mark.parametrize(
    "texts",
    [[], ["   "], ["a", "b", "c", "d"], ["x" * 21]],
    ids=["empty list", "blank text", "too many texts", "text too long"],
)
def test_embed_rejects_invalid_input(client, embedder, texts):
    assert post(client, texts).status_code == 422
    assert embedder.calls == []


def test_embed_answers_503_and_health_reports_error_when_model_files_are_missing(tmp_path):
    app = create_app(settings(model_dir=tmp_path))
    with TestClient(app) as test_client:
        for _ in range(200):
            if test_client.get("/health").json()["status"] != "loading":
                break

        health = test_client.get("/health").json()
        assert health["status"] == "error"
        assert "Missing model files" in health["detail"]
        assert post(test_client, ["a"]).status_code == 503


def test_service_refuses_to_start_without_token():
    with pytest.raises(RuntimeError, match="AI_INTERNAL_TOKEN"):
        create_app(settings(internal_token=""))


def test_anonymous_mode_must_be_enabled_explicitly(embedder):
    app = create_app(settings(internal_token="", allow_anonymous=True), embedder=embedder)
    with TestClient(app) as test_client:
        assert post(test_client, ["a"], token=None).status_code == 200


def test_pooling_takes_first_token_and_normalizes_to_unit_length():
    hidden = np.array(
        [
            [[3.0, 4.0], [100.0, 100.0]],
            [[0.0, 2.0], [-5.0, 9.0]],
        ]
    )

    vectors = pool_and_normalize(hidden)

    np.testing.assert_allclose(vectors, [[0.6, 0.8], [0.0, 1.0]], rtol=1e-6)
    np.testing.assert_allclose(np.linalg.norm(vectors, axis=1), [1.0, 1.0], rtol=1e-6)


def test_pooling_does_not_divide_by_zero():
    assert np.isfinite(pool_and_normalize(np.zeros((1, 2, 3)))).all()
