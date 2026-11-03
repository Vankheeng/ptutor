"""Chọn bản cài đặt nhà cung cấp theo cấu hình (thiết kế 02 mục 2.1: "đổi được bằng cấu hình")."""

from __future__ import annotations

from app.config import Settings
from app.providers.embedding import AiServiceEmbedding, EmbeddingProvider, FakeEmbedding
from app.retrieval.rerank import AiServiceReranker, DisabledReranker, Reranker
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.store import KnowledgeStore


def build_embedder(settings: Settings) -> EmbeddingProvider:
    if settings.embedding_provider == "ai-service":
        return AiServiceEmbedding(settings.ai_service_url, settings.ai_internal_token)
    if settings.embedding_provider == "fake":
        return FakeEmbedding()
    raise ValueError(f"ASSISTANT_EMBEDDING_PROVIDER không hợp lệ: {settings.embedding_provider}")


def build_reranker(settings: Settings) -> Reranker:
    if settings.rerank_enabled:
        return AiServiceReranker(settings.ai_service_url, settings.ai_internal_token)
    return DisabledReranker()


def retrieval_config(settings: Settings) -> RetrievalConfig:
    return RetrievalConfig(
        mode=settings.retrieval_mode, k_each=settings.retrieval_k_each, rerank_k=settings.rerank_k,
        context_k=settings.context_k, rrf_k=settings.rrf_k, min_rerank_score=settings.min_rerank_score,
        min_cosine=settings.min_cosine, min_text_coverage=settings.min_text_coverage,
    )


def build_retriever(settings: Settings, store: KnowledgeStore) -> Retriever:
    return Retriever(store, build_reranker(settings), retrieval_config(settings))
