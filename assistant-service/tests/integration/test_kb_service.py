"""Test T1 M4: vòng đời tài liệu trên PostgreSQL thật (thiết kế 03 mục 3.2)."""

from pathlib import Path

import pytest
from sqlalchemy import func, select

from app.db.models import KbChunk, KbDocument, KbDocumentVersion
from app.kb.importer import import_directory
from app.kb.parser import find_document_files, parse_document
from app.kb.service import KbError, KnowledgeBaseService
from app.kb.tokens import ApproxTokenCounter
from app.providers.embedding import EmbeddingError, FakeEmbedding
from tests.unit.test_kb_chunking import SAMPLE

pytestmark = pytest.mark.db
KB_ROOT = Path(__file__).resolve().parents[3] / "knowledge-base"


@pytest.fixture
def service(session_factory):
    return KnowledgeBaseService(session_factory, ApproxTokenCounter(), lambda: {"/student/wallet"}, FakeEmbedding())


def active_chunks(session_factory) -> int:
    with session_factory() as session:
        return session.scalar(select(func.count()).select_from(KbChunk).where(KbChunk.is_active))


def test_publish_creates_active_chunks_with_unaccented_tsv(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    version = service.publish(saved.document_id)
    assert version.state == "PUBLISHED" and version.version == 1
    assert active_chunks(session_factory) > 0
    with session_factory() as session:
        tsv = session.scalar(select(func.cast(KbChunk.tsv, KbChunk.search_text.type)).limit(1))
    assert "nap" in tsv and "nạp" not in tsv


def test_republish_supersedes_old_version_but_keeps_it(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    service.save_draft(parse_document(SAMPLE.replace("10.000", "20.000")), origin="ADMIN")
    service.publish(saved.document_id)
    with session_factory() as session:
        states = dict(session.execute(select(KbDocumentVersion.version, KbDocumentVersion.state)).all())
        active_contents = session.scalars(select(KbChunk.content).where(KbChunk.is_active)).all()
    assert states == {1: "SUPERSEDED", 2: "PUBLISHED"}
    assert any("20.000" in content for content in active_contents)
    assert not any("10.000" in content for content in active_contents)


def test_publish_blocked_by_todo_and_old_version_keeps_running(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    before = active_chunks(session_factory)
    service.save_draft(parse_document(SAMPLE.replace("todo: []", "todo: ['Hỏi admin']")), origin="ADMIN")
    with pytest.raises(KbError) as raised:
        service.publish(saved.document_id)
    assert raised.value.code == "DOCUMENT_INVALID"
    assert active_chunks(session_factory) == before  # bản cũ vẫn chạy


def test_allow_todo_marks_version(service):
    saved = service.save_draft(parse_document(SAMPLE.replace("todo: []", "todo: ['Hỏi admin']")), origin="IMPORT")
    version = service.publish(saved.document_id, allow_open_todo=True)
    assert version.published_with_open_todo is True


def test_restore_publishes_old_content_as_new_version(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    service.save_draft(parse_document(SAMPLE.replace("10.000", "20.000")), origin="ADMIN")
    service.publish(saved.document_id)
    restored = service.restore(saved.document_id, 1)
    assert restored.version == 3 and restored.restored_from_version == 1
    with session_factory() as session:
        contents = session.scalars(select(KbChunk.content).where(KbChunk.is_active)).all()
    assert any("10.000" in content for content in contents)


def test_archive_removes_document_from_retrieval(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    service.archive(saved.document_id)
    assert active_chunks(session_factory) == 0
    with session_factory() as session:
        assert session.get(KbDocument, saved.document_id).status == "ARCHIVED"


def test_import_whole_knowledge_base_as_drafts_then_publish_with_allow_todo(session_factory):
    from app.kb.routes import load_routes_file

    allowed = {route.path for route in load_routes_file(KB_ROOT / "_routes.yaml")}
    service = KnowledgeBaseService(session_factory, ApproxTokenCounter(), lambda: allowed, FakeEmbedding())
    total = len(find_document_files(KB_ROOT))  # không đếm cứng: kho tăng theo module (M17 thêm 1 tài liệu)
    drafts = import_directory(service, KB_ROOT)
    assert drafts.actions["created"] == total and drafts.published == 0
    blocked = import_directory(service, KB_ROOT, publish=True)
    assert blocked.published < total  # còn todo → bị chặn như thiết kế 3.3
    report = import_directory(service, KB_ROOT, publish=True, allow_todo=True)
    with session_factory() as session:
        published = session.scalar(select(func.count()).select_from(KbDocument)
                                   .where(KbDocument.active_version_id.is_not(None)))
    assert published == total and report.published_with_open_todo > 0


class BrokenEmbedding(FakeEmbedding):
    def embed(self, texts):
        raise EmbeddingError("ai-service tắt")


def test_publish_stores_vectors(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    with session_factory() as session:
        chunk = session.scalars(select(KbChunk)).first()
    assert len(chunk.embedding) == 1024 and chunk.embedding_model == "fake-hash-v1"


def test_embedding_failure_keeps_old_version(service, session_factory):
    saved = service.save_draft(parse_document(SAMPLE), origin="IMPORT")
    service.publish(saved.document_id)
    service.save_draft(parse_document(SAMPLE.replace("10.000", "20.000")), origin="ADMIN")
    broken = KnowledgeBaseService(session_factory, ApproxTokenCounter(), lambda: {"/student/wallet"},
                                  BrokenEmbedding())
    with pytest.raises(KbError) as raised:
        broken.publish(saved.document_id)
    assert raised.value.code == "EMBEDDING_FAILED"
    with session_factory() as session:
        contents = session.scalars(select(KbChunk.content).where(KbChunk.is_active)).all()
    assert any("10.000" in content for content in contents)
