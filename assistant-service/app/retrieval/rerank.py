"""Xếp hạng lại (rerank) bằng `ai-service /rerank` (bge-reranker-v2-m3) — thiết kế 04 mục 4.3, QĐ8.

API của `ai-service` (nhánh recommendation): `POST /rerank {"query", "documents"}` → `{"model", "scores"}`, điểm
trong [0, 1] theo đúng thứ tự `documents`, tối đa 40 văn bản mỗi lần. Rerank tắt hoặc lỗi → trả `None`, người gọi
giữ thứ tự RRF (thiết kế: "ai-service báo rerank disabled / lỗi → giữ thứ tự RRF").
"""

from __future__ import annotations

import httpx

MAX_DOCUMENTS = 40


class Reranker:
    def scores(self, query: str, documents: list[str]) -> list[float] | None:
        raise NotImplementedError

    def status(self) -> str:
        return "disabled"


class DisabledReranker(Reranker):
    def scores(self, query: str, documents: list[str]) -> list[float] | None:
        return None


class AiServiceReranker(Reranker):
    def __init__(self, base_url: str, internal_token: str, *, timeout_s: float = 8.0,
                 client: httpx.Client | None = None):
        self._client = client or httpx.Client(base_url=base_url, timeout=timeout_s)
        self._headers = {"X-Internal-Token": internal_token}

    def scores(self, query: str, documents: list[str]) -> list[float] | None:
        if not documents:
            return []
        try:
            response = self._client.post("/rerank", headers=self._headers,
                                         json={"query": query, "documents": documents[:MAX_DOCUMENTS]})
            response.raise_for_status()
            scores = response.json()["scores"]
        except (httpx.HTTPError, KeyError, ValueError):
            return None
        return [float(score) for score in scores]

    def status(self) -> str:
        try:
            return str(self._client.get("/health", timeout=3.0).json().get("rerank", "error"))
        except (httpx.HTTPError, ValueError):
            return "error"
