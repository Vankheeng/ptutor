"""M4: schema assistant và các bảng kho tri thức (thiết kế 06 mục 6.1, 6.3).

Viết tay (không tự sinh) để dễ đọc. Extension `vector`, `unaccent` và hàm `f_unaccent` thường do Flyway của backend
tạo (V25); ở đây tạo lại nếu thiếu, để assistant-service chạy được trên DB test / eval riêng.

Revision ID: 0001
"""

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects.postgresql import ARRAY, JSONB, TSVECTOR, UUID

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

S = "assistant"

F_UNACCENT = """
CREATE OR REPLACE FUNCTION assistant.f_unaccent(text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$
"""


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.execute("CREATE EXTENSION IF NOT EXISTS unaccent")
    # unaccent() không phải IMMUTABLE nên không dùng trong chỉ mục được; bọc lại với từ điển chỉ định rõ.
    op.execute(F_UNACCENT)

    op.create_table(
        "kb_document",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("doc_key", sa.String(120), nullable=False, unique=True),
        sa.Column("active_version_id", UUID(as_uuid=True)),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("created_by", UUID(as_uuid=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        schema=S,
    )
    op.create_table(
        "kb_document_version",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("document_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.kb_document.id"), nullable=False),
        sa.Column("version", sa.Integer, nullable=False),
        sa.Column("state", sa.String(20), nullable=False),
        sa.Column("title", sa.String(255), nullable=False),
        sa.Column("type", sa.String(20), nullable=False),
        sa.Column("audience", ARRAY(sa.String(20)), nullable=False),
        sa.Column("features", ARRAY(sa.String(40)), nullable=False),
        sa.Column("routes", ARRAY(sa.String(200)), nullable=False),
        sa.Column("keywords", ARRAY(sa.String(80)), nullable=False),
        sa.Column("error_codes", ARRAY(sa.String(80)), nullable=False),
        sa.Column("source_refs", JSONB, nullable=False),
        sa.Column("todo", JSONB, nullable=False),
        sa.Column("content_md", sa.Text, nullable=False),
        sa.Column("content_hash", sa.CHAR(64), nullable=False),
        sa.Column("origin", sa.String(20), nullable=False),
        sa.Column("published_with_open_todo", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("edited_by", UUID(as_uuid=True)),
        sa.Column("edited_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("published_by", UUID(as_uuid=True)),
        sa.Column("published_at", sa.DateTime(timezone=True)),
        sa.Column("restored_from_version", sa.Integer),
        sa.UniqueConstraint("document_id", "version"),
        schema=S,
    )
    # Mỗi tài liệu có tối đa một bản nháp và một bản đang công bố.
    op.create_index("uq_kb_version_one_draft", "kb_document_version", ["document_id"], unique=True,
                    schema=S, postgresql_where=sa.text("state = 'DRAFT'"))
    op.create_index("uq_kb_version_one_published", "kb_document_version", ["document_id"], unique=True,
                    schema=S, postgresql_where=sa.text("state = 'PUBLISHED'"))
    op.create_foreign_key("fk_kb_document_active_version", "kb_document", "kb_document_version",
                          ["active_version_id"], ["id"], source_schema=S, referent_schema=S)

    op.create_table(
        "kb_chunk",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("version_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.kb_document_version.id"), nullable=False),
        sa.Column("ordinal", sa.Integer, nullable=False),
        sa.Column("heading_path", sa.String(500), nullable=False),
        sa.Column("anchor", sa.String(200), nullable=False),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("search_text", sa.Text, nullable=False),
        sa.Column("tsv", TSVECTOR),
        sa.Column("embedding", Vector(1024)),
        sa.Column("embedding_model", sa.String(80)),
        sa.Column("token_count", sa.Integer, nullable=False),
        sa.Column("audience", ARRAY(sa.String(20)), nullable=False),
        sa.Column("features", ARRAY(sa.String(40)), nullable=False),
        sa.Column("routes", ARRAY(sa.String(200)), nullable=False),
        sa.Column("type", sa.String(20), nullable=False),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.false()),
        schema=S,
    )
    op.create_index("ix_kb_chunk_embedding_hnsw", "kb_chunk", ["embedding"], schema=S, postgresql_using="hnsw",
                    postgresql_ops={"embedding": "vector_cosine_ops"}, postgresql_where=sa.text("is_active"))
    op.create_index("ix_kb_chunk_tsv", "kb_chunk", ["tsv"], schema=S, postgresql_using="gin")
    op.create_index("ix_kb_chunk_audience", "kb_chunk", ["audience"], schema=S, postgresql_using="gin")
    op.create_index("ix_kb_chunk_section", "kb_chunk", ["version_id", "heading_path", "ordinal"], schema=S)

    op.create_table(
        "kb_setting",
        sa.Column("key", sa.String(60), primary_key=True),
        sa.Column("value", JSONB, nullable=False),
        sa.Column("updated_by", UUID(as_uuid=True)),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        schema=S,
    )


def downgrade() -> None:
    op.drop_table("kb_setting", schema=S)
    op.drop_table("kb_chunk", schema=S)
    op.drop_constraint("fk_kb_document_active_version", "kb_document", schema=S)
    op.drop_table("kb_document_version", schema=S)
    op.drop_table("kb_document", schema=S)
    op.execute("DROP FUNCTION IF EXISTS assistant.f_unaccent(text)")
