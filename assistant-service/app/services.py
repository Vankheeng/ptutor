"""Hộp dịch vụ: mọi thành phần dùng chung của service, dựng một lần khi khởi động (thiết kế 02 mục 2.1).

Router lấy thành phần từ `request.app.state.services`. Test dựng `Services(...)` với bản giả (ScriptedLlm,
FakeEmbedding, MemoryKnowledgeStore, MemoryConversationStore...) — không cần mạng, không cần DB.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.clock import Clock
from app.config import Settings
from app.db.conversations import ConversationStore, MemoryConversationStore
from app.kb.routes import RouteCatalog, load_routes_file
from app.kb.suggestions import DEFAULT_SUGGESTIONS
from app.kb.tokens import ApproxTokenCounter, TokenCounter
from app.providers.embedding import EmbeddingProvider
from app.providers.llm import LlmProvider, OpenAiLlm
from app.retrieval.retriever import Retriever
from app.scope.exemplars import ExemplarStore, MemoryExemplarStore
from app.scope.rules import ScopeRules


@dataclass
class Services:
    settings: Settings
    llm: LlmProvider | None
    embedder: EmbeddingProvider
    retriever: Retriever | None
    routes: RouteCatalog
    conversations: ConversationStore = field(default_factory=MemoryConversationStore)
    counter: TokenCounter = field(default_factory=ApproxTokenCounter)
    clock: Clock = field(default_factory=Clock)
    suggestions: dict = field(default_factory=lambda: DEFAULT_SUGGESTIONS)
    exemplars: ExemplarStore = field(default_factory=lambda: MemoryExemplarStore([]))
    scope_rules: ScopeRules = field(default_factory=lambda: ScopeRules.from_setting(None))
    engine: Engine | None = None
    session_factory: sessionmaker[Session] | None = None


def build_services(settings: Settings) -> Services:
    """Dựng bản thật theo cấu hình. DB chưa cấu hình và kho không phải `memory` → `retriever = None` (503)."""
    from app.db.conversations import PgConversationStore
    from app.db.session import make_engine, make_session_factory
    from app.kb.tokens import load_token_counter
    from app.providers.factory import build_embedder, build_retriever
    from app.retrieval.store import KnowledgeStore, MemoryKnowledgeStore, PgKnowledgeStore

    embedder = build_embedder(settings)
    counter = load_token_counter(settings.tokenizer_path)
    engine = make_engine(settings.database_url) if settings.database_url else None
    session_factory = make_session_factory(engine) if engine is not None else None
    store: KnowledgeStore | None = None
    if settings.knowledge_store == "memory":
        store = MemoryKnowledgeStore.from_directory(Path(settings.memory_kb_path), embedder, counter)
    elif session_factory is not None:
        store = PgKnowledgeStore(session_factory)
    conversations: ConversationStore = (PgConversationStore(session_factory) if session_factory is not None
                                        else MemoryConversationStore())
    llm = OpenAiLlm(settings.openai_api_key, settings.openai_base_url) if settings.llm_enabled else None
    exemplars = _build_exemplars(session_factory, embedder)
    return Services(
        settings=settings, llm=llm, embedder=embedder,
        retriever=build_retriever(settings, store) if store is not None else None,
        routes=RouteCatalog(load_routes_file(Path(settings.routes_file))),
        conversations=conversations, counter=counter, engine=engine, session_factory=session_factory,
        exemplars=exemplars,
    )


def _build_exemplars(session_factory, embedder: EmbeddingProvider) -> ExemplarStore:
    """Có DB → đọc bảng scope_exemplar (nạp bằng `python -m app.scope.exemplars --load`); không có DB → embed câu
    mẫu nguồn DATASET ngay lúc khởi động (chạy thử / eval không cần DB)."""
    from app.scope.exemplars import PgExemplarStore, dataset_texts, embed_exemplars

    if session_factory is not None:
        return PgExemplarStore(session_factory, embedder.embed(["kiểm tra model"]).model)
    return MemoryExemplarStore(embed_exemplars(dataset_texts(), embedder))
