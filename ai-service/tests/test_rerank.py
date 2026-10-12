from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.rerank import BOS_ID, EOS_ID, build_pair, sigmoid

TOKEN = "test-token"
HEADERS = {"X-Internal-Token": TOKEN}


class FakeEmbedder:
    model_version = "fake"
    dimensions = 4

    def embed(self, texts):
        raise AssertionError("not used")


class FakeReranker:
    model_version = "fake-reranker"

    def __init__(self):
        self.calls = []

    def score(self, query, documents):
        self.calls.append((query, documents))
        return [1.0 if "Toán" in document else 0.25 for document in documents]


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
        rerank_max_documents=3,
        rerank_max_chars=40,
    )
    values.update(overrides)
    return Settings(**values)


def test_scores_come_back_in_document_order_with_the_model_version():
    reranker = FakeReranker()
    with TestClient(create_app(settings(), embedder=FakeEmbedder(), reranker=reranker)) as client:
        response = client.post(
            "/rerank", json={"query": "Cần gia sư Toán", "documents": ["Dạy Văn", "Dạy Toán 9"]}, headers=HEADERS
        )

    assert response.status_code == 200
    assert response.json() == {"model": "fake-reranker", "scores": [0.25, 1.0]}
    assert reranker.calls == [("Cần gia sư Toán", ["Dạy Văn", "Dạy Toán 9"])]


@pytest.mark.parametrize("token", [None, "wrong"])
def test_rerank_requires_the_internal_token(token):
    reranker = FakeReranker()
    with TestClient(create_app(settings(), embedder=FakeEmbedder(), reranker=reranker)) as client:
        headers = {} if token is None else {"X-Internal-Token": token}
        response = client.post("/rerank", json={"query": "a", "documents": ["b"]}, headers=headers)

    assert response.status_code == 401
    assert reranker.calls == []


@pytest.mark.parametrize(
    "body",
    [
        {"query": "a", "documents": []},
        {"query": "a", "documents": ["b", "c", "d", "e"]},
        {"query": " ", "documents": ["b"]},
        {"query": "a", "documents": ["b", "  "]},
        {"query": "a", "documents": ["x" * 41]},
    ],
    ids=["no documents", "too many documents", "blank query", "blank document", "document too long"],
)
def test_rerank_rejects_invalid_requests(body):
    reranker = FakeReranker()
    with TestClient(create_app(settings(), embedder=FakeEmbedder(), reranker=reranker)) as client:
        assert client.post("/rerank", json=body, headers=HEADERS).status_code == 422
    assert reranker.calls == []


def test_embedding_keeps_working_while_the_reranker_is_missing_or_switched_off():
    with TestClient(create_app(settings(), embedder=FakeEmbedder())) as client:
        health = client.get("/health").json()
        assert health["status"] == "ready"
        assert health["rerank"] == "loading"
        assert client.post("/rerank", json={"query": "a", "documents": ["b"]}, headers=HEADERS).status_code == 503

    with TestClient(create_app(settings(rerank_enabled=False), embedder=FakeEmbedder())) as client:
        health = client.get("/health").json()
        assert health["rerank"] == "disabled"
        assert health["rerank_model"] is None


def test_health_reports_the_reranker_when_loaded():
    with TestClient(create_app(settings(), embedder=FakeEmbedder(), reranker=FakeReranker())) as client:
        health = client.get("/health").json()

    assert health["rerank"] == "ready"
    assert health["rerank_model"] == "bge-reranker-v2-m3-int8"


def test_pair_is_query_then_document_each_cut_to_the_token_limit():
    assert build_pair([10, 11, 12], [20, 21, 22, 23], 2) == [BOS_ID, 10, 11, EOS_ID, EOS_ID, 20, 21, EOS_ID]
    assert build_pair([10], [20], 256) == [BOS_ID, 10, EOS_ID, EOS_ID, 20, EOS_ID]


def test_sigmoid_maps_logits_into_zero_one():
    values = sigmoid(np.array([-20.0, 0.0, 20.0], dtype=np.float32))

    assert values[0] < 1e-6 and values[1] == pytest.approx(0.5) and values[2] > 1 - 1e-6
