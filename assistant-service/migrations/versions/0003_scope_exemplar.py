"""M12: bảng câu mẫu cho lớp chặn ngoài phạm vi (thiết kế 06 mục 6.3 `scope_exemplar`, 04 mục 4.11).

Ít dòng (vài trăm) → không cần chỉ mục vector; service đọc hết vào bộ nhớ và quét tuần tự.

Revision ID: 0003
"""

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects.postgresql import UUID

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "scope_exemplar",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("text", sa.String(500), nullable=False),
        sa.Column("label", sa.String(10), nullable=False),
        sa.Column("source", sa.String(20), nullable=False),
        sa.Column("embedding", Vector(1024), nullable=False),
        sa.Column("embedding_model", sa.String(80), nullable=False),
        sa.Column("created_by", UUID(as_uuid=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        schema="assistant",
    )


def downgrade() -> None:
    op.drop_table("scope_exemplar", schema="assistant")
