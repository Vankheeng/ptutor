"""Fixture cho test tích hợp T1 (thiết kế 13 mục 13.1): chạy trên PostgreSQL + pgvector thật.

DB test riêng `ptutor_test` (không đụng DB phát triển). Địa chỉ quản trị lấy từ `ASSISTANT_TEST_ADMIN_DATABASE_URL`,
mặc định là container `ptutor-postgres` của docker-compose. Không kết nối được → **bỏ qua** cả nhóm test kèm lý do,
thay vì báo lỗi giả.
"""

from __future__ import annotations

import os

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url

from app.db.session import make_engine, make_session_factory

ADMIN_URL = os.environ.get("ASSISTANT_TEST_ADMIN_DATABASE_URL",
                           "postgresql+psycopg://postgres:postgres@127.0.0.1:5432/postgres")
TEST_DB = "ptutor_test"
TABLES_IN_DELETE_ORDER = [
    "message_feedback", "turn_trace", "tool_call_log", "message_citation", "message", "conversation",
    "deletion_log", "scope_exemplar", "kb_chunk", "kb_setting",
]


def _admin_engine():
    return create_engine(ADMIN_URL, isolation_level="AUTOCOMMIT", connect_args={"connect_timeout": 3})


@pytest.fixture(scope="session")
def database_url() -> str:
    try:
        with _admin_engine().connect() as connection:
            exists = connection.scalar(text("SELECT 1 FROM pg_database WHERE datname = :name"), {"name": TEST_DB})
            if not exists:
                connection.execute(text(f"CREATE DATABASE {TEST_DB}"))
    except Exception as error:  # noqa: BLE001
        pytest.skip(f"Không kết nối được PostgreSQL ({error.__class__.__name__}); bật Docker để chạy test T1")
    url = make_url(ADMIN_URL).set(database=TEST_DB).render_as_string(hide_password=False)
    config = Config("alembic.ini")
    config.attributes["database_url"] = url
    command.upgrade(config, "head")
    return url


@pytest.fixture
def session_factory(database_url):
    engine = make_engine(database_url)
    _clean(engine)
    yield make_session_factory(engine)
    engine.dispose()


def _clean(engine) -> None:
    """Xoá dữ liệu giữa các test. Bảng chưa tồn tại (migration của module sau) thì bỏ qua."""
    with engine.begin() as connection:
        existing = set(connection.scalars(text(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'assistant'")))
        for table in TABLES_IN_DELETE_ORDER:
            if table in existing:
                connection.execute(text(f"DELETE FROM assistant.{table}"))
        connection.execute(text("UPDATE assistant.kb_document SET active_version_id = NULL"))
        connection.execute(text("DELETE FROM assistant.kb_document_version"))
        connection.execute(text("DELETE FROM assistant.kb_document"))
