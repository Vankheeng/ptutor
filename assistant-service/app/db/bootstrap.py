"""Tạo role DB `assistant_app` và schema `assistant` (chạy một lần, bằng tài khoản quản trị Postgres).

Thiết kế 06 mục 6.1 và 09 (mối đe doạ T10): role của trợ lý chỉ có quyền trên schema `assistant`, **không** đọc
được bảng nghiệp vụ ở schema `public`. Role và mật khẩu tạo ngoài migration để không ghi mật khẩu vào file.

    $env:ASSISTANT_DB_ADMIN_URL = "postgresql+psycopg://postgres:<mật khẩu>@127.0.0.1:5432/ptutor"
    $env:ASSISTANT_DB_APP_PASSWORD = "<mật khẩu cho assistant_app>"
    .venv/Scripts/python -m app.db.bootstrap

Chạy lại nhiều lần không sao (chỉ tạo khi chưa có, mật khẩu được đặt lại theo biến môi trường).
"""

from __future__ import annotations

import os

from sqlalchemy import create_engine, text

ROLE = "assistant_app"


def bootstrap(admin_url: str, app_password: str) -> None:
    engine = create_engine(admin_url, isolation_level="AUTOCOMMIT")
    with engine.connect() as connection:
        exists = connection.scalar(text("SELECT 1 FROM pg_roles WHERE rolname = :role"), {"role": ROLE})
        # Mật khẩu không ghép thẳng vào chuỗi SQL mà để Postgres tự trích dẫn bằng format('%L').
        statement = "CREATE ROLE" if not exists else "ALTER ROLE"
        quoted = connection.scalar(text("SELECT format('%L', CAST(:password AS text))"), {"password": app_password})
        connection.execute(text(f"{statement} {ROLE} LOGIN PASSWORD {quoted}"))
        # Extension phải do quản trị tạo (assistant_app không có quyền tạo extension).
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS unaccent"))
        connection.execute(text(f"CREATE SCHEMA IF NOT EXISTS assistant AUTHORIZATION {ROLE}"))
        connection.execute(text(f"ALTER SCHEMA assistant OWNER TO {ROLE}"))
        # USAGE trên public chỉ để dùng kiểu `vector` và hàm `unaccent` (nằm ở public); không cấp quyền đọc bảng nào.
        connection.execute(text(f"GRANT USAGE ON SCHEMA public TO {ROLE}"))
        connection.execute(text(f"REVOKE CREATE ON SCHEMA public FROM {ROLE}"))
    engine.dispose()


if __name__ == "__main__":
    from app.cli import use_utf8_output

    use_utf8_output()
    bootstrap(os.environ["ASSISTANT_DB_ADMIN_URL"], os.environ["ASSISTANT_DB_APP_PASSWORD"])
    print(f"Đã chuẩn bị role {ROLE} và schema assistant.")
