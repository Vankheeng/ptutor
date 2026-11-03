"""Chọn bản cài đặt nhà cung cấp theo cấu hình (thiết kế 02 mục 2.1: "đổi được bằng cấu hình")."""

from __future__ import annotations

from app.config import Settings
from app.providers.embedding import AiServiceEmbedding, EmbeddingProvider, FakeEmbedding


def build_embedder(settings: Settings) -> EmbeddingProvider:
    if settings.embedding_provider == "ai-service":
        return AiServiceEmbedding(settings.ai_service_url, settings.ai_internal_token)
    if settings.embedding_provider == "fake":
        return FakeEmbedding()
    raise ValueError(f"ASSISTANT_EMBEDDING_PROVIDER không hợp lệ: {settings.embedding_provider}")
