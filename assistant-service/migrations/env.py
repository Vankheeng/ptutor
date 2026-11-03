"""Môi trường chạy Alembic cho schema `assistant`.

Bảng ghi phiên bản migration (`alembic_version`) cũng nằm trong schema `assistant`, để không đụng schema `public`
của backend. Chạy:  .venv/Scripts/alembic upgrade head
"""

from __future__ import annotations

from alembic import context
from sqlalchemy import create_engine, text

from app.config import Settings
from app.db.models import SCHEMA, Base

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """`alembic upgrade head --sql`: in ra SQL, không cần kết nối DB (dùng để xem trước)."""
    context.configure(url="postgresql+psycopg://", target_metadata=target_metadata, literal_binds=True,
                      version_table_schema=SCHEMA, include_schemas=True)
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    url = context.config.attributes.get("database_url") or Settings().database_url
    if not url:
        raise SystemExit("Chưa đặt ASSISTANT_DATABASE_URL")
    engine = create_engine(url)
    with engine.connect() as connection:
        connection.execute(text(f"CREATE SCHEMA IF NOT EXISTS {SCHEMA}"))
        connection.commit()
        context.configure(connection=connection, target_metadata=target_metadata,
                          version_table_schema=SCHEMA, include_schemas=True)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
