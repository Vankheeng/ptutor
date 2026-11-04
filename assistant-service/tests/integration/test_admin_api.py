"""Test T1 M16: API quản trị kho tri thức trên PostgreSQL thật (thiết kế 07 mục 7.3, 03 mục 3.2)."""

import pytest
from fastapi.testclient import TestClient

from app.db.conversations import PgConversationStore
from app.kb.admin_queries import KbAdminQueries
from app.kb.service import KnowledgeBaseService
from app.main import create_app
from app.providers.llm import LlmResponse, ScriptedLlm
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.store import PgKnowledgeStore
from tests.conftest import caller_headers
from tests.fakes import make_services
from tests.unit.test_answer_with_citations import actor_json
from tests.unit.test_retrieval import NoRerank

pytestmark = pytest.mark.db
ADMIN = caller_headers(role="ADMIN", user_id="44444444-4444-4444-4444-000000000004")
BODY = {
    "docKey": "vi-nap-tien", "title": "Nạp ví", "type": "policy", "audience": ["STUDENT", "TUTOR"],
    "features": ["wallet"], "routes": ["/student/wallet"], "keywords": ["nạp ví"],
    "contentMd": "# Nạp ví\n\n## Hạn mức\n\nMỗi lần nạp tối thiểu 10.000 đ, tối đa 100.000.000 đ, số chẵn đồng.\n",
    "todo": ["Nạp có hết hạn không?"],
}


@pytest.fixture
def client(settings, session_factory):
    services = make_services(settings, ScriptedLlm(policy=lambda request: LlmResponse(content=actor_json())))
    services.session_factory = session_factory
    services.conversations = PgConversationStore(session_factory)
    services.retriever = Retriever(PgKnowledgeStore(session_factory), NoRerank(), RetrievalConfig())
    services.kb_service = KnowledgeBaseService(session_factory, services.counter,
                                               lambda: services.routes.all_paths(), services.embedder)
    services.kb_queries = KbAdminQueries(session_factory)
    return TestClient(create_app(services=services))


def test_document_lifecycle_through_admin_api(client):
    created = client.post("/v1/admin/documents", json=BODY, headers=ADMIN)
    assert created.status_code == 201 and created.json()["draft"]["version"] == 1
    document_id = created.json()["id"]
    assert client.post("/v1/admin/documents", json=BODY, headers=ADMIN).json()["detail"] == "DOC_KEY_EXISTS"

    validation = client.post(f"/v1/admin/documents/{document_id}/draft/validate", headers=ADMIN).json()
    assert not validation["ok"] and "todo" in validation["errors"][0]
    assert validation["chunks"][0]["headingPath"] == "Nạp ví > Hạn mức"
    blocked = client.post(f"/v1/admin/documents/{document_id}/publish", headers=ADMIN)
    assert blocked.status_code == 409 and blocked.json()["detail"] == "DOCUMENT_INVALID"

    client.put(f"/v1/admin/documents/{document_id}/draft", json=BODY | {"todo": []}, headers=ADMIN)
    published = client.post(f"/v1/admin/documents/{document_id}/publish", headers=ADMIN).json()
    assert published["published"]["version"] == 1 and published["draft"] is None

    changed = BODY | {"todo": [], "contentMd": BODY["contentMd"].replace("10.000", "20.000")}
    client.put(f"/v1/admin/documents/{document_id}/draft", json=changed, headers=ADMIN)
    client.post(f"/v1/admin/documents/{document_id}/publish", headers=ADMIN)
    restored = client.post(f"/v1/admin/documents/{document_id}/versions/1/restore", headers=ADMIN).json()
    assert restored["published"]["version"] == 3 and restored["published"]["restoredFromVersion"] == 1
    assert "10.000" in restored["published"]["contentMd"]
    assert [v["state"] for v in restored["versions"]] == ["PUBLISHED", "SUPERSEDED", "SUPERSEDED"]

    listing = client.get("/v1/admin/documents", params={"feature": "wallet"}, headers=ADMIN).json()
    assert listing["totalElements"] == 1 and listing["content"][0]["publishedVersion"] == 3
    archived = client.post(f"/v1/admin/documents/{document_id}/archive", headers=ADMIN).json()
    assert archived["status"] == "ARCHIVED"


def test_non_admin_is_rejected(client):
    assert client.get("/v1/admin/documents", headers=caller_headers(role="STUDENT")).status_code == 403
    assert client.get("/v1/admin/documents", headers=caller_headers(role="EMPLOYEE")).status_code == 403


def test_unanswered_and_stats_hide_user_id(client):
    client.post("/v1/chat", json={"message": "Chính sách bảo mật dữ liệu của PTutor quy định gì?"},
                headers=caller_headers())
    items = client.get("/v1/admin/unanswered", headers=ADMIN).json()["items"]
    assert items and items[0]["fallbackReason"] == "NO_CONTEXT"
    assert "11111111-1111-1111-1111-000000000001" not in str(items)
    stats = client.get("/v1/admin/stats", headers=ADMIN).json()
    assert stats["turns"] == 1 and stats["grounded"] == {"NONE": 1}


def test_settings_update_applies_immediately(client):
    routes = [{"path": "/student/wallet", "audience": ["STUDENT"]}]
    assert client.put("/v1/admin/settings/routes", json={"value": routes}, headers=ADMIN).status_code == 200
    assert client.app.state.services.routes.all_paths() == {"/student/wallet"}
    assert client.get("/v1/admin/settings/routes", headers=ADMIN).json()["value"] == routes
    assert client.put("/v1/admin/settings/unknown", json={"value": 1}, headers=ADMIN).status_code == 404
