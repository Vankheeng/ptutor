"""Các bảng của schema `assistant` (thiết kế 06), viết bằng SQLAlchemy 2.0 (khai báo kiểu).

Chỉ assistant-service đọc / ghi các bảng này. Service **không** đọc bảng nghiệp vụ ở schema `public` (QĐ6):
dữ liệu người dùng chỉ lấy qua API của backend.

Cột `user_id` là UUID lấy từ token, cố ý **không** đặt khoá ngoại sang `public.users` (06 mục 6.1).
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    ARRAY,
    CHAR,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    MetaData,
    String,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, TSVECTOR, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

SCHEMA = "assistant"
EMBEDDING_DIMENSIONS = 1024  # bge-m3


class Base(DeclarativeBase):
    metadata = MetaData(schema=SCHEMA)


def _uuid_pk() -> Mapped[uuid.UUID]:
    return mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)


def _now() -> Mapped[datetime]:
    return mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


# ---------------------------------------------------------------- Kho tri thức (06 mục 6.3)


class KbDocument(Base):
    """Định danh ổn định của một tài liệu; nội dung nằm ở các phiên bản."""

    __tablename__ = "kb_document"

    id: Mapped[uuid.UUID] = _uuid_pk()
    doc_key: Mapped[str] = mapped_column(String(120), unique=True, nullable=False)
    # use_alter: hai bảng trỏ vòng sang nhau (tài liệu → bản đang công bố → tài liệu).
    active_version_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("kb_document_version.id", use_alter=True, name="fk_kb_document_active_version"))
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="DRAFT")  # DRAFT | PUBLISHED | ARCHIVED
    created_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = _now()


class KbDocumentVersion(Base):
    __tablename__ = "kb_document_version"
    __table_args__ = (
        UniqueConstraint("document_id", "version"),
        # Mỗi tài liệu có tối đa một bản nháp và một bản đang công bố (chỉ mục duy nhất một phần).
        Index("uq_kb_version_one_draft", "document_id", unique=True, postgresql_where=text("state = 'DRAFT'")),
        Index("uq_kb_version_one_published", "document_id", unique=True,
              postgresql_where=text("state = 'PUBLISHED'")),
    )

    id: Mapped[uuid.UUID] = _uuid_pk()
    document_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("kb_document.id"), nullable=False)
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    state: Mapped[str] = mapped_column(String(20), nullable=False)  # DRAFT | PUBLISHED | SUPERSEDED | ARCHIVED
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    audience: Mapped[list[str]] = mapped_column(ARRAY(String(20)), nullable=False)
    features: Mapped[list[str]] = mapped_column(ARRAY(String(40)), nullable=False, default=list)
    routes: Mapped[list[str]] = mapped_column(ARRAY(String(200)), nullable=False, default=list)
    keywords: Mapped[list[str]] = mapped_column(ARRAY(String(80)), nullable=False, default=list)
    error_codes: Mapped[list[str]] = mapped_column(ARRAY(String(80)), nullable=False, default=list)
    source_refs: Mapped[Any] = mapped_column(JSONB, nullable=False, default=list)
    todo: Mapped[Any] = mapped_column(JSONB, nullable=False, default=list)
    content_md: Mapped[str] = mapped_column(Text, nullable=False)
    content_hash: Mapped[str] = mapped_column(CHAR(64), nullable=False)
    origin: Mapped[str] = mapped_column(String(20), nullable=False)  # IMPORT | ADMIN
    # Công bố bằng `--allow-todo` (chỉ eval/demo) khi còn câu hỏi mở; trang quản trị hiện cảnh báo.
    published_with_open_todo: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    edited_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    edited_at: Mapped[datetime] = _now()
    published_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    restored_from_version: Mapped[int | None] = mapped_column(Integer)


class KbChunk(Base):
    """Đoạn để tìm kiếm; chỉ sinh khi công bố. Sao chép audience/features/routes/type để lọc không cần join."""

    __tablename__ = "kb_chunk"
    __table_args__ = (
        Index("ix_kb_chunk_embedding_hnsw", "embedding", postgresql_using="hnsw",
              postgresql_ops={"embedding": "vector_cosine_ops"}, postgresql_where=text("is_active")),
        Index("ix_kb_chunk_tsv", "tsv", postgresql_using="gin"),
        Index("ix_kb_chunk_audience", "audience", postgresql_using="gin"),
        Index("ix_kb_chunk_section", "version_id", "heading_path", "ordinal"),
    )

    id: Mapped[uuid.UUID] = _uuid_pk()
    version_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("kb_document_version.id"),
                                                  nullable=False)
    ordinal: Mapped[int] = mapped_column(Integer, nullable=False)
    heading_path: Mapped[str] = mapped_column(String(500), nullable=False)
    anchor: Mapped[str] = mapped_column(String(200), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    search_text: Mapped[str] = mapped_column(Text, nullable=False)
    tsv: Mapped[Any] = mapped_column(TSVECTOR)
    embedding: Mapped[list[float] | None] = mapped_column(Vector(EMBEDDING_DIMENSIONS))
    embedding_model: Mapped[str | None] = mapped_column(String(80))
    token_count: Mapped[int] = mapped_column(Integer, nullable=False)
    audience: Mapped[list[str]] = mapped_column(ARRAY(String(20)), nullable=False)
    features: Mapped[list[str]] = mapped_column(ARRAY(String(40)), nullable=False)
    routes: Mapped[list[str]] = mapped_column(ARRAY(String(200)), nullable=False)
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


class KbSetting(Base):
    """Cấu hình admin sửa được: `routes`, `suggestions`, `scope_rules`."""

    __tablename__ = "kb_setting"

    key: Mapped[str] = mapped_column(String(60), primary_key=True)
    value: Mapped[Any] = mapped_column(JSONB, nullable=False)
    updated_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    updated_at: Mapped[datetime] = _now()


