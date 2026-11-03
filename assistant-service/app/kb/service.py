"""`KnowledgeBaseService`: vòng đời tài liệu kho tri thức — lưu nháp, kiểm tra, công bố, lưu trữ, khôi phục.

Thiết kế 03 mục 3.2 (vòng đời) và 06 mục 6.3 (bảng). Các quy tắc chính:
- Mỗi tài liệu có tối đa một bản `DRAFT` và một bản `PUBLISHED`; bản cũ chuyển `SUPERSEDED` nhưng **giữ lại** để
  trích dẫn trong hội thoại cũ vẫn tra được.
- Công bố là thao tác **nguyên tử**: chia đoạn + embedding xong mới đổi bản active, tất cả trong một transaction.
  Embedding lỗi → công bố thất bại, bản cũ vẫn chạy.
- Chỉ đoạn của bản đang công bố có `is_active = true` → chỉ chúng được truy xuất.
"""

from __future__ import annotations

import hashlib
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, sessionmaker

from app.db.models import KbChunk, KbDocument, KbDocumentVersion, KbSetting
from app.kb.chunker import ChunkDraft
from app.kb.parser import KbDocument as ParsedDocument
from app.kb.tokens import TokenCounter
from app.kb.validate import ValidationResult, validate_document


class KbError(Exception):
    """Lỗi nghiệp vụ của kho tri thức; `code` trả về cho trang quản trị."""

    def __init__(self, code: str, message: str, details: list[str] | None = None):
        super().__init__(message)
        self.code = code
        self.details = details or []


@dataclass
class SaveResult:
    doc_key: str
    action: str  # created | updated | skipped
    document_id: uuid.UUID


class KnowledgeBaseService:
    def __init__(self, session_factory: sessionmaker[Session], counter: TokenCounter,
                 allowed_routes: Callable[[], set[str]]):
        self._sessions = session_factory
        self._counter = counter
        self._allowed_routes = allowed_routes

    # ------------------------------------------------------------------ nháp

    def save_draft(self, parsed: ParsedDocument, *, origin: str, editor: uuid.UUID | None = None,
                   overwrite_draft: bool = True) -> SaveResult:
        """Tạo tài liệu mới hoặc lưu bản nháp cho tài liệu đã có.

        Tài liệu đang công bố mà chưa có nháp → tạo nháp mới (bản công bố vẫn được dùng tới khi công bố nháp).
        `overwrite_draft=False` (import mặc định): đã có nháp thì bỏ qua, không đè công sức admin đang soạn.
        """
        with self._sessions.begin() as session:
            document = session.scalar(select(KbDocument).where(KbDocument.doc_key == parsed.doc_key))
            if document is None:
                document = KbDocument(doc_key=parsed.doc_key, status="DRAFT", created_by=editor)
                session.add(document)
                session.flush()
                session.add(self._new_version(document.id, 1, "DRAFT", parsed, origin, editor))
                return SaveResult(parsed.doc_key, "created", document.id)

            draft = self._version_in_state(session, document.id, "DRAFT")
            if draft is not None:
                if not overwrite_draft:
                    return SaveResult(parsed.doc_key, "skipped", document.id)
                self._fill_version(draft, parsed, editor)
                return SaveResult(parsed.doc_key, "updated", document.id)

            active = session.get(KbDocumentVersion, document.active_version_id) if document.active_version_id else None
            if active is not None and active.content_hash == content_hash(parsed):
                return SaveResult(parsed.doc_key, "skipped", document.id)  # không có gì mới
            session.add(self._new_version(document.id, self._next_version(session, document.id), "DRAFT",
                                          parsed, origin, editor))
            return SaveResult(parsed.doc_key, "updated", document.id)

    def discard_draft(self, document_id: uuid.UUID) -> None:
        with self._sessions.begin() as session:
            draft = self._version_in_state(session, document_id, "DRAFT")
            if draft is None:
                raise KbError("DRAFT_NOT_FOUND", "Tài liệu không có bản nháp")
            session.delete(draft)

    def validate_draft(self, document_id: uuid.UUID, *, allow_open_todo: bool = False) -> ValidationResult:
        with self._sessions() as session:
            draft = self._version_in_state(session, document_id, "DRAFT")
            if draft is None:
                raise KbError("DRAFT_NOT_FOUND", "Tài liệu không có bản nháp")
            return self._validate(session, draft, allow_open_todo=allow_open_todo)

    # ------------------------------------------------------------------ công bố

    def publish(self, document_id: uuid.UUID, *, publisher: uuid.UUID | None = None,
                allow_open_todo: bool = False) -> KbDocumentVersion:
        """Công bố bản nháp. `allow_open_todo=True` chỉ dùng cho import eval/demo (`--allow-todo`)."""
        with self._sessions.begin() as session:
            draft = self._version_in_state(session, document_id, "DRAFT", lock=True)
            if draft is None:
                raise KbError("DRAFT_NOT_FOUND", "Tài liệu không có bản nháp để công bố")
            self._activate(session, draft, publisher, allow_open_todo=allow_open_todo)
            return draft

    def restore(self, document_id: uuid.UUID, version: int, *, actor: uuid.UUID | None = None) -> KbDocumentVersion:
        """Khôi phục: công bố lại nội dung một phiên bản cũ thành **phiên bản mới** (không sửa lịch sử)."""
        with self._sessions.begin() as session:
            source = session.scalar(select(KbDocumentVersion).where(
                KbDocumentVersion.document_id == document_id, KbDocumentVersion.version == version))
            if source is None:
                raise KbError("VERSION_NOT_FOUND", f"Không có phiên bản {version}")
            restored = KbDocumentVersion(
                id=uuid.uuid4(), document_id=document_id, version=self._next_version(session, document_id),
                # Trạng thái tạm trong transaction: chưa thể là DRAFT vì tài liệu có thể đang có một bản nháp khác.
                state="RESTORING", origin="ADMIN", edited_by=actor, restored_from_version=version,
                **{name: getattr(source, name) for name in _CONTENT_COLUMNS},
            )
            session.add(restored)
            session.flush()
            self._activate(session, restored, actor, allow_open_todo=source.published_with_open_todo)
            return restored

    def archive(self, document_id: uuid.UUID) -> None:
        """Ngừng sử dụng: bản đang công bố → ARCHIVED, đoạn của nó không còn được truy xuất."""
        with self._sessions.begin() as session:
            document = session.get(KbDocument, document_id, with_for_update=True)
            if document is None:
                raise KbError("DOCUMENT_NOT_FOUND", "Không tìm thấy tài liệu")
            if document.active_version_id is not None:
                session.execute(update(KbChunk).where(KbChunk.version_id == document.active_version_id)
                                .values(is_active=False))
                session.execute(update(KbDocumentVersion).where(KbDocumentVersion.id == document.active_version_id)
                                .values(state="ARCHIVED"))
            document.active_version_id = None
            document.status = "ARCHIVED"

    # ------------------------------------------------------------------ thống kê cho /health

    def counts(self) -> dict[str, int]:
        with self._sessions() as session:
            published = session.scalar(select(func.count()).select_from(KbDocument)
                                       .where(KbDocument.active_version_id.is_not(None))) or 0
            chunks = session.scalar(select(func.count()).select_from(KbChunk).where(KbChunk.is_active)) or 0
            return {"published": published, "chunks": chunks}

    # ------------------------------------------------------------------ phần dùng chung

    def _activate(self, session: Session, version: KbDocumentVersion, publisher: uuid.UUID | None,
                  *, allow_open_todo: bool) -> None:
        """Kiểm tra → chia đoạn → (embed) → thay bản active. Mọi lỗi làm transaction quay lui."""
        result = self._validate(session, version, allow_open_todo=allow_open_todo)
        if not result.ok:
            raise KbError("DOCUMENT_INVALID", "Tài liệu chưa hợp lệ", result.errors)
        document = session.get(KbDocument, version.document_id, with_for_update=True)
        assert document is not None
        previous_id = document.active_version_id
        if previous_id is not None:
            session.execute(update(KbChunk).where(KbChunk.version_id == previous_id).values(is_active=False))
            session.execute(update(KbDocumentVersion).where(KbDocumentVersion.id == previous_id)
                            .values(state="SUPERSEDED"))
            session.flush()  # trước khi đặt bản mới PUBLISHED, để không vi phạm chỉ mục "một bản PUBLISHED"
        self._insert_chunks(session, version, result.chunks)
        version.state = "PUBLISHED"
        version.published_by = publisher
        version.published_at = datetime.now(UTC)
        version.published_with_open_todo = bool(version.todo) and allow_open_todo
        document.active_version_id = version.id
        document.status = "PUBLISHED"

    def _insert_chunks(self, session: Session, version: KbDocumentVersion, drafts: list[ChunkDraft]) -> None:
        keywords = " ".join(version.keywords)
        for draft in drafts:
            session.add(KbChunk(
                version_id=version.id, ordinal=draft.ordinal, heading_path=draft.heading_path, anchor=draft.anchor,
                content=draft.content, search_text=draft.search_text, token_count=draft.token_count,
                # Full-text trên văn bản đã bỏ dấu: câu hỏi "huy hop dong" vẫn khớp "Hủy hợp đồng" (04 mục 4.3).
                tsv=func.to_tsvector("simple", func.assistant.f_unaccent(draft.search_text + " " + keywords)),
                audience=version.audience, features=version.features, routes=version.routes, type=version.type,
                is_active=True,
            ))

    def _validate(self, session: Session, version: KbDocumentVersion, *, allow_open_todo: bool) -> ValidationResult:
        parsed = to_parsed(version, doc_key="(db)")
        return validate_document(parsed, self._counter, self._allowed_routes(), allow_open_todo=allow_open_todo)

    @staticmethod
    def _version_in_state(session: Session, document_id: uuid.UUID, state: str,
                          lock: bool = False) -> KbDocumentVersion | None:
        query = select(KbDocumentVersion).where(KbDocumentVersion.document_id == document_id,
                                                KbDocumentVersion.state == state)
        return session.scalar(query.with_for_update() if lock else query)

    @staticmethod
    def _next_version(session: Session, document_id: uuid.UUID) -> int:
        current = session.scalar(select(func.max(KbDocumentVersion.version))
                                 .where(KbDocumentVersion.document_id == document_id))
        return (current or 0) + 1

    def _new_version(self, document_id: uuid.UUID, number: int, state: str, parsed: ParsedDocument, origin: str,
                     editor: uuid.UUID | None) -> KbDocumentVersion:
        version = KbDocumentVersion(id=uuid.uuid4(), document_id=document_id, version=number, state=state,
                                    origin=origin)
        self._fill_version(version, parsed, editor)
        return version

    @staticmethod
    def _fill_version(version: KbDocumentVersion, parsed: ParsedDocument, editor: uuid.UUID | None) -> None:
        version.title = parsed.title
        version.type = parsed.type
        version.audience = parsed.audience
        version.features = parsed.features
        version.routes = parsed.routes
        version.keywords = parsed.keywords
        version.error_codes = parsed.error_codes
        version.source_refs = parsed.source_refs
        version.todo = parsed.todo
        version.content_md = parsed.body
        version.content_hash = content_hash(parsed)
        version.edited_by = editor
        version.edited_at = datetime.now(UTC)


_CONTENT_COLUMNS = ("title", "type", "audience", "features", "routes", "keywords", "error_codes", "source_refs",
                    "todo", "content_md", "content_hash")


def content_hash(parsed: ParsedDocument) -> str:
    """Băm nội dung + metadata, để import lại cùng file không tạo phiên bản thừa."""
    payload = "\n".join([parsed.title, parsed.type, ",".join(parsed.audience), ",".join(parsed.features),
                         ",".join(parsed.routes), ",".join(parsed.keywords), parsed.body])
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def to_parsed(version: KbDocumentVersion, doc_key: str) -> ParsedDocument:
    return ParsedDocument(doc_key=doc_key, title=version.title, type=version.type, audience=list(version.audience),
                          body=version.content_md, features=list(version.features), routes=list(version.routes),
                          keywords=list(version.keywords), source_refs=list(version.source_refs or []),
                          error_codes=list(version.error_codes or []), todo=list(version.todo or []))


def read_setting(session: Session, key: str, default: object = None) -> object:
    setting = session.get(KbSetting, key)
    return setting.value if setting is not None else default

