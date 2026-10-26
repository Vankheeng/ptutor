import time
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

    def score(self, query, documents, wait_turn=None):
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


class BlockingReranker:
    """Holds every call until released, and remembers how many ran at the same time."""

    model_version = "fake-reranker"

    def __init__(self):
        import threading

        self.release = threading.Event()
        self.started = threading.Event()
        self._lock = threading.Lock()
        self.running = 0
        self.most_at_once = 0

    def score(self, query, documents, wait_turn=None):
        with self._lock:
            self.running += 1
            self.most_at_once = max(self.most_at_once, self.running)
        self.started.set()
        self.release.wait(5)
        with self._lock:
            self.running -= 1
        return [0.5 for _ in documents]


class QuickEmbedder:
    model_version = "fake"
    dimensions = 2

    def embed(self, texts):
        return np.ones((len(texts), 2), dtype=np.float32)


def test_reranks_one_request_at_a_time_without_holding_up_embedding():
    from concurrent.futures import ThreadPoolExecutor

    reranker = BlockingReranker()
    with TestClient(create_app(settings(), embedder=QuickEmbedder(), reranker=reranker)) as client:
        with ThreadPoolExecutor(3) as pool:
            pending = [pool.submit(client.post, "/rerank", json={"query": "a", "documents": ["b"]}, headers=HEADERS)
                       for _ in range(3)]
            assert reranker.started.wait(5)

            embedded = client.post("/embed", json={"texts": ["Cần gia sư Toán"]}, headers=HEADERS)

            assert embedded.status_code == 200, "embedding answers while a rerank is running"
            reranker.release.set()
            assert [future.result().status_code for future in pending] == [200, 200, 200]
    assert reranker.most_at_once == 1


def test_a_rerank_batch_waits_while_an_embedding_runs():
    import threading
    import time

    from app.main import EmbedFirst

    gate = EmbedFirst(max_wait_seconds=5)
    order = []
    with gate.embedding():
        waiting = threading.Thread(target=lambda: (gate.wait_turn(), order.append("rerank")))
        waiting.start()
        time.sleep(0.2)
        order.append("embedding done")
    waiting.join(5)

    assert order == ["embedding done", "rerank"]


def test_the_wait_for_embeddings_is_bounded():
    import time

    from app.main import EmbedFirst

    gate = EmbedFirst(max_wait_seconds=0.2)
    with gate.embedding():
        started = time.perf_counter()
        gate.wait_turn()
        assert time.perf_counter() - started < 2


def test_the_endpoint_hands_the_reranker_its_turn_keeper():
    seen = []

    class Recording(FakeReranker):
        def score(self, query, documents, wait_turn=None):
            seen.append(wait_turn)
            wait_turn()
            return super().score(query, documents)

    with TestClient(create_app(settings(), embedder=FakeEmbedder(), reranker=Recording())) as client:
        assert client.post("/rerank", json={"query": "a", "documents": ["b"]}, headers=HEADERS).status_code == 200
    assert callable(seen[0])


def test_work_still_waiting_when_its_caller_leaves_is_dropped():
    import asyncio
    import threading
    from concurrent.futures import ThreadPoolExecutor

    from app.main import Abandoned, run_unless_abandoned

    release = threading.Event()
    ran = []
    executor = ThreadPoolExecutor(max_workers=1)
    executor.submit(release.wait, 5)  # the one thread is busy

    async def caller_gone():
        return True

    async def scenario():
        with pytest.raises(Abandoned):
            await run_unless_abandoned(executor, lambda: ran.append("left behind"), caller_gone, poll_seconds=0.01)

    asyncio.run(scenario())
    release.set()
    executor.shutdown(wait=True)
    assert ran == []


def test_work_that_already_runs_finishes_even_if_its_caller_leaves():
    import asyncio
    import threading
    from concurrent.futures import ThreadPoolExecutor

    from app.main import run_unless_abandoned

    started = threading.Event()
    executor = ThreadPoolExecutor(max_workers=1)

    def work():
        started.set()
        time.sleep(0.1)
        return "scored"

    async def caller_gone():
        return started.is_set()

    async def scenario():
        return await run_unless_abandoned(executor, work, caller_gone, poll_seconds=0.01)

    assert asyncio.run(scenario()) == "scored"
    executor.shutdown(wait=True)
