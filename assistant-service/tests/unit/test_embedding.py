"""Test M5: EmbeddingProvider (bản giả và bản gọi ai-service qua transport giả)."""

import json

import httpx
import pytest

from app.providers.embedding import AiServiceEmbedding, EmbeddingError, FakeEmbedding, cosine


def test_fake_embedding_is_deterministic_normalized_1024():
    first = FakeEmbedding().embed(["Nạp ví tối thiểu bao nhiêu?"]).vectors[0]
    second = FakeEmbedding().embed(["Nạp ví tối thiểu bao nhiêu?"]).vectors[0]
    assert first == second and len(first) == 1024
    assert abs(sum(value * value for value in first) - 1.0) < 1e-9


def test_fake_embedding_ignores_accents_and_rewards_shared_words():
    embedder = FakeEmbedding()
    question = embedder.vector("nap vi toi thieu bao nhieu")
    related = embedder.vector("Nạp ví: mức nạp tối thiểu 10.000 đ")
    unrelated = embedder.vector("Chứng chỉ gia sư bị từ chối")
    assert cosine(question, related) > cosine(question, unrelated) + 0.2


def _transport(handler):
    return httpx.Client(base_url="http://ai", transport=httpx.MockTransport(handler))


def test_ai_service_embedding_sends_token_and_batches_by_32():
    batches = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        batches.append(len(body["texts"]))
        assert request.headers["X-Internal-Token"] == "secret"
        return httpx.Response(200, json={"model": "bge-m3-int8", "dimensions": 1024,
                                         "embeddings": [[0.0] * 1024 for _ in body["texts"]]})

    embedder = AiServiceEmbedding("http://ai", "secret", client=_transport(handler))
    result = embedder.embed([f"t{i}" for i in range(70)])
    assert batches == [32, 32, 6]
    assert len(result.vectors) == 70 and result.model == "bge-m3-int8"


def test_ai_service_errors_become_embedding_error():
    embedder = AiServiceEmbedding("http://ai", "t", client=_transport(lambda request: httpx.Response(503)))
    with pytest.raises(EmbeddingError):
        embedder.embed(["x"])


def test_ai_service_wrong_dimensions_rejected():
    def handler(request):
        return httpx.Response(200, json={"model": "m", "dimensions": 768, "embeddings": [[0.0] * 768]})

    with pytest.raises(EmbeddingError, match="768"):
        AiServiceEmbedding("http://ai", "t", client=_transport(handler)).embed(["x"])
