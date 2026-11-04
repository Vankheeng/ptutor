"""Hộp dịch vụ: mọi thành phần dùng chung của service, dựng một lần khi khởi động (thiết kế 02 mục 2.1).

Router lấy thành phần từ `request.app.state.services`. Test dựng `Services(...)` với bản giả (ScriptedLlm,
FakeEmbedding, MemoryKnowledgeStore, MemoryConversationStore...) — không cần mạng, không cần DB.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

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
from app.tools.registry import ToolRegistry


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
    tool_registry: ToolRegistry | None = None
    tool_executor: Any = None  # app.tools.executor.ToolExecutor; None = không có tool (ASSISTANT_TOOLS_MODE=off)
    engine: Engine | None = None
    session_factory: sessionmaker[Session] | None = None
    kb_service: Any = None  # app.kb.service.KnowledgeBaseService — cần PostgreSQL (trang quản trị)
    kb_queries: Any = None  # app.kb.admin_queries.KbAdminQueries

    # ------------------------------------------------------------------ cài đặt admin sửa được (kb_setting)

    def read_setting(self, key: str) -> Any:
        from app.kb.service import read_setting

        if self.session_factory is None:
            return None
        with self.session_factory() as session:
            return read_setting(session, key)

    def write_setting(self, key: str, value: Any, editor: Any) -> None:
        """Ghi `kb_setting` rồi áp dụng ngay cho service đang chạy (không cần khởi động lại)."""
        from app.db.models import KbSetting
        from app.kb.routes import AllowedRoute

        assert self.session_factory is not None
        if key == "routes":
            if not isinstance(value, list) or not all(isinstance(item, dict) and str(item.get("path", ""))
                                                      .startswith("/") for item in value):
                raise ValueError("routes phải là danh sách {path, audience}")
            self.routes = RouteCatalog([AllowedRoute(item["path"], tuple(item.get("audience") or ()))
                                        for item in value])
        elif key == "suggestions":
            self.suggestions = dict(value or {})
        elif key == "scope_rules":
            self.scope_rules = ScopeRules.from_setting(value)  # biên dịch luật trước khi lưu: luật hỏng → lỗi ngay
        with self.session_factory.begin() as session:
            setting = session.get(KbSetting, key) or KbSetting(key=key, value=value)
            setting.value, setting.updated_by = value, editor
            session.merge(setting)

    def add_scope_exemplar(self, text: str, label: str, editor: Any) -> None:
        from app.db.models import ScopeExemplar
        from app.scope.exemplars import PgExemplarStore

        assert self.session_factory is not None
        result = self.embedder.embed([text])
        with self.session_factory.begin() as session:
            session.add(ScopeExemplar(text=text, label=label, source="ADMIN", embedding=result.vectors[0],
                                      embedding_model=result.model, created_by=editor))
        if isinstance(self.exemplars, PgExemplarStore):
            self.exemplars.refresh()

    def load_settings_from_db(self) -> None:
        """Lúc khởi động: route / gợi ý / luật chặn đã được admin sửa thì dùng bản trong DB thay cho mặc định."""
        from app.kb.routes import AllowedRoute

        routes = self.read_setting("routes")
        if routes:
            self.routes = RouteCatalog([AllowedRoute(item["path"], tuple(item.get("audience") or ()))
                                        for item in routes])
        suggestions = self.read_setting("suggestions")
        if suggestions:
            self.suggestions = suggestions
        scope_rules = self.read_setting("scope_rules")
        if scope_rules:
            self.scope_rules = ScopeRules.from_setting(scope_rules)


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
    registry, executor = _build_tools(settings)
    services = Services(
        settings=settings, llm=llm, embedder=embedder,
        retriever=build_retriever(settings, store) if store is not None else None,
        routes=RouteCatalog(load_routes_file(Path(settings.routes_file))),
        conversations=conversations, counter=counter, engine=engine, session_factory=session_factory,
        exemplars=exemplars, tool_registry=registry, tool_executor=executor,
    )
    if session_factory is not None:
        from app.kb.admin_queries import KbAdminQueries
        from app.kb.service import KnowledgeBaseService

        services.load_settings_from_db()
        # Danh sách route cho phép đọc lúc kiểm tra (lambda) → admin sửa route là có hiệu lực ngay khi công bố.
        services.kb_service = KnowledgeBaseService(session_factory, counter, lambda: services.routes.all_paths(),
                                                   embedder)
        services.kb_queries = KbAdminQueries(session_factory)
    return services


def _build_tools(settings: Settings) -> tuple[ToolRegistry | None, Any]:
    """`live`: gọi backend thật; `mock`: fixture theo kịch bản; `off`: không có tool."""
    from app.tools.backend_client import LiveBackendClient, MockBackendClient
    from app.tools.executor import ToolExecutor

    if settings.tools_mode == "off":
        return None, None
    registry = ToolRegistry.from_file(settings.tool_catalog_path)
    if settings.tools_mode == "mock":
        client: Any = MockBackendClient(registry, Path(settings.tool_fixtures_dir), settings.tool_scenario)
    else:
        client = LiveBackendClient(registry, settings.backend_base_url, timeout_s=settings.tool_timeout_s)
    return registry, ToolExecutor(registry, client, settings.backend_jvm_time_zone)


def _build_exemplars(session_factory, embedder: EmbeddingProvider) -> ExemplarStore:
    """Có DB → đọc bảng scope_exemplar (nạp bằng `python -m app.scope.exemplars --load`); không có DB → embed câu
    mẫu nguồn DATASET ngay lúc khởi động (chạy thử / eval không cần DB)."""
    from app.scope.exemplars import PgExemplarStore, dataset_texts, embed_exemplars

    if session_factory is not None:
        return PgExemplarStore(session_factory, embedder.embed(["kiểm tra model"]).model)
    return MemoryExemplarStore(embed_exemplars(dataset_texts(), embedder))
