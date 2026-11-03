"""Kết nối PostgreSQL: tạo engine và phiên làm việc (SQLAlchemy, đồng bộ).

Thiết kế 06 mục 6.1: dùng role `assistant_app`, chỉ có quyền trên schema `assistant`.
`ASSISTANT_DATABASE_URL` dạng `postgresql+psycopg://assistant_app:<mật khẩu>@127.0.0.1:5432/ptutor`.
"""

from __future__ import annotations

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session, sessionmaker


def make_engine(database_url: str) -> Engine:
    # pool_pre_ping: kết nối chết (Postgres khởi động lại) được phát hiện và mở lại thay vì báo lỗi cho người dùng.
    return create_engine(database_url, pool_pre_ping=True, pool_size=5, max_overflow=5)


def make_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False)


def database_status(engine: Engine | None) -> str:
    """Cho /health: `ready`, `error` (không kết nối được) hoặc `disabled` (chưa cấu hình)."""
    if engine is None:
        return "disabled"
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        return "ready"
    except Exception:  # noqa: BLE001 — /health chỉ cần biết đạt hay không
        return "error"
