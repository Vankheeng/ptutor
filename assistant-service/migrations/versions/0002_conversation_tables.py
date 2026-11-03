"""M8: bảng hội thoại, tin nhắn, trích dẫn, log tool, trace, đánh giá, nhật ký xoá (thiết kế 06 mục 6.4).

Khoá ngoại từ các bảng con sang `message` / `conversation` đặt `ON DELETE CASCADE`, để `purge_conversation` xoá
một dòng `conversation` là kéo theo mọi mảnh dữ liệu của hội thoại (06 mục "Xoá hội thoại").

Revision ID: 0002
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB, UUID

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

S = "assistant"


def _now() -> sa.Column:
    return sa.func.now()


def upgrade() -> None:
    op.create_table(
        "conversation",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", UUID(as_uuid=True), nullable=False),
        sa.Column("user_role", sa.String(20), nullable=False),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("last_message_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("session_state", JSONB),
        sa.Column("ended_at", sa.DateTime(timezone=True)),
        sa.Column("deleted_at", sa.DateTime(timezone=True)),
        schema=S,
    )
    op.create_index("ix_conversation_user_last", "conversation", ["user_id", sa.text("last_message_at DESC")],
                    schema=S)

    op.create_table(
        "message",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("conversation_id", UUID(as_uuid=True),
                  sa.ForeignKey(f"{S}.conversation.id", ondelete="CASCADE"), nullable=False),
        sa.Column("sender", sa.String(10), nullable=False),
        sa.Column("reply_to_message_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.message.id", ondelete="CASCADE")),
        sa.Column("content", sa.Text),
        sa.Column("rewritten_query", sa.Text),
        sa.Column("intent", sa.String(20)),
        sa.Column("grounded", sa.String(10)),
        sa.Column("fallback_reason", sa.String(40)),
        sa.Column("verification", JSONB),
        sa.Column("actions", JSONB),
        sa.Column("citations", JSONB),
        sa.Column("page_path", sa.String(200)),
        sa.Column("llm_model", sa.String(80)),
        sa.Column("prompt_version", sa.String(40)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        schema=S,
    )
    op.create_index("ix_message_conversation_created", "message", ["conversation_id", "created_at"], schema=S)

    op.create_table(
        "message_citation",
        sa.Column("message_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.message.id", ondelete="CASCADE"),
                  primary_key=True),
        sa.Column("chunk_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.kb_chunk.id"), primary_key=True),
        sa.Column("rank", sa.Integer, nullable=False),
        sa.Column("vector_score", sa.REAL),
        sa.Column("text_score", sa.REAL),
        sa.Column("rrf_score", sa.REAL),
        sa.Column("rerank_score", sa.REAL),
        sa.Column("in_context", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("cited", sa.Boolean, nullable=False, server_default=sa.false()),
        schema=S,
    )

    op.create_table(
        "tool_call_log",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("message_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.message.id", ondelete="CASCADE"),
                  nullable=False),
        sa.Column("tool_name", sa.String(80), nullable=False),
        sa.Column("args", JSONB),
        sa.Column("blocked_reason", sa.String(40)),
        sa.Column("http_status", sa.Integer),
        sa.Column("latency_ms", sa.Integer),
        sa.Column("result_items", sa.Integer),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        schema=S,
    )

    op.create_table(
        "turn_trace",
        sa.Column("message_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.message.id", ondelete="CASCADE"),
                  primary_key=True),
        sa.Column("request_id", sa.String(64)),
        sa.Column("config_hash", sa.CHAR(12), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("spans", JSONB, nullable=False),
        sa.Column("total_ms", sa.Integer, nullable=False),
        sa.Column("tokens_in", sa.Integer, nullable=False, server_default="0"),
        sa.Column("tokens_out", sa.Integer, nullable=False, server_default="0"),
        sa.Column("model_calls", sa.SmallInteger, nullable=False, server_default="0"),
        sa.Column("tool_calls", sa.SmallInteger, nullable=False, server_default="0"),
        sa.Column("verify_retries", sa.SmallInteger, nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        schema=S,
    )

    op.create_table(
        "message_feedback",
        sa.Column("message_id", UUID(as_uuid=True), sa.ForeignKey(f"{S}.message.id", ondelete="CASCADE"),
                  primary_key=True),
        sa.Column("user_id", UUID(as_uuid=True), nullable=False),
        sa.Column("rating", sa.SmallInteger, nullable=False),
        sa.Column("reason", sa.String(30)),
        sa.Column("comment", sa.String(500)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        schema=S,
    )

    op.create_table(
        "deletion_log",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("conversation_id", UUID(as_uuid=True), nullable=False),
        sa.Column("requested_at", sa.DateTime(timezone=True)),
        sa.Column("purged_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("rows_deleted", JSONB, nullable=False),
        sa.Column("verified", sa.Boolean, nullable=False),
        schema=S,
    )


def downgrade() -> None:
    for table in ("deletion_log", "message_feedback", "turn_trace", "tool_call_log", "message_citation", "message",
                  "conversation"):
        op.drop_table(table, schema=S)
