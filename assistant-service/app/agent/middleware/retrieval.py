"""`RetrievalMiddleware` — hook ① (thiết kế 02 mục 2.3, 04 mục 4.3).

Chạy truy xuất lai **một lần** mỗi lượt và đặt kết quả vào `state.retrieval`. Embedding câu hỏi do `QueryMiddleware`
làm (M9, dùng chung vector cho ScopeGuard); nếu chưa có vector thì tự embed.
Ghi span `embed_query`, `retrieve_sql`, `rerank` (SKIPPED khi rerank tắt) cho benchmark B2.
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.state import TurnState
from app.agent.trace import add_span, timed
from app.providers.embedding import EmbeddingProvider
from app.retrieval.retriever import Retriever


class RetrievalMiddleware(Middleware):
    name = "retrieval"

    def __init__(self, retriever: Retriever, embedder: EmbeddingProvider):
        self._retriever = retriever
        self._embedder = embedder

    def before_agent(self, state: TurnState) -> None:
        if state.query_vector is None:
            with timed(state, "embed_query", "retrieval"):
                state.query_vector = self._embedder.embed([state.query]).vectors[0]
        result = self._retriever.retrieve(state.query, state.query_vector, state.caller.role, state.page_path)
        add_span(state, "retrieve_sql", "retrieval", result.timings_ms.get("retrieve_sql", 0),
                 candidates=len(result.candidates), no_context=result.no_context)
        add_span(state, "rerank", "retrieval", result.timings_ms.get("rerank", 0),
                 status="OK" if result.rerank_used else "SKIPPED")
        state.retrieval = result
