"""Dựng `Services` từ toàn bản giả cho test: ScriptedLlm, FakeEmbedding, kho tri thức trong bộ nhớ, đồng hồ cố định.

Không cần mạng, không cần DB, không cần khoá API (thiết kế 13 mục 13.1).
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from app.clock import FixedClock
from app.config import Settings
from app.kb.parser import parse_document
from app.kb.routes import RouteCatalog, load_routes_file
from app.kb.tokens import ApproxTokenCounter
from app.providers.embedding import FakeEmbedding
from app.providers.llm import LlmProvider
from app.retrieval.rerank import DisabledReranker
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.store import MemoryKnowledgeStore
from app.services import Services
from app.tools.backend_client import MockBackendClient
from app.tools.executor import ToolExecutor
from app.tools.registry import ToolRegistry

ROUTES_FILE = Path(__file__).resolve().parents[2] / "knowledge-base" / "_routes.yaml"
CATALOG_FILE = Path(__file__).resolve().parents[2] / "tool-catalog" / "tools.yaml"
FIXTURES_DIR = Path(__file__).resolve().parents[1] / "eval" / "fixtures" / "scenarios"
FIXED_NOW = datetime(2026, 10, 20, 9, 0)  # mặc định của eval (13 mục 13.3), giờ Việt Nam

WALLET_DOC = """---
id: vi-nap-tien
title: Nạp ví
type: policy
audience: [STUDENT, TUTOR]
features: [wallet]
routes: [/student/wallet, /tutor/wallet]
keywords: [nạp ví, nap vi]
---

# Nạp ví

## Hạn mức nạp ví

Mỗi lần nạp ví tối thiểu 10.000 đ và tối đa 100.000.000 đ. Số tiền phải là số chẵn đồng.

## Cách nạp

Vào trang Ví, nhập số tiền, bấm Nạp qua VNPay và thanh toán trong 15 phút.
"""

CERTIFICATE_DOC = """---
id: chung-chi-nop
title: Nộp chứng chỉ
type: guide
audience: [TUTOR]
features: [certificate]
routes: [/tutor/certificates]
---

# Nộp chứng chỉ

## Cách nộp chứng chỉ

Gia sư vào trang Chứng chỉ, tải tệp tối đa 10 MB rồi bấm Gửi duyệt.
"""


def make_store(*documents: str) -> MemoryKnowledgeStore:
    store = MemoryKnowledgeStore(FakeEmbedding(), ApproxTokenCounter())
    for text in documents or (WALLET_DOC, CERTIFICATE_DOC):
        store.add_document(parse_document(text))
    return store


def make_services(settings: Settings, llm: LlmProvider | None = None, *documents: str, tools_scenario: str = "",
                  **extra) -> Services:
    """`tools_scenario` khác rỗng → bật tool ở chế độ mock với kịch bản đó (eval/fixtures/scenarios/<tên>)."""
    store = make_store(*documents)
    services = Services(
        settings=settings, llm=llm, embedder=FakeEmbedding(),
        retriever=Retriever(store, DisabledReranker(), RetrievalConfig()),
        routes=RouteCatalog(load_routes_file(ROUTES_FILE)), clock=FixedClock(FIXED_NOW), **extra,
    )
    if tools_scenario:
        registry = ToolRegistry.from_file(CATALOG_FILE)
        services.tool_registry = registry
        services.tool_executor = ToolExecutor(registry, MockBackendClient(registry, FIXTURES_DIR, tools_scenario))
    return services
