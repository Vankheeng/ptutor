"""Điểm vào của assistant-service: `create_app()` dựng ứng dụng FastAPI.

Thiết kế 02 mục 2.5 (`main.py`: create_app, /health). Mọi thành phần phụ thuộc (LLM, embedding, DB...) được
tạo một lần ở đây và gắn vào `app.state`; test truyền bản giả vào `create_app(...)` để chạy không cần mạng.

Chạy thử (PowerShell):  .venv/Scripts/uvicorn app.main:app --port 8100
"""

from __future__ import annotations

from fastapi import FastAPI

from app.api import health
from app.config import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="PTutor assistant-service", version="0.1.0")
    app.state.settings = settings
    # Mỗi thành phần đăng ký một hàm kiểm tra trạng thái; /health gọi lần lượt.
    app.state.health_checks = {
        "llm": lambda: "ready" if settings.llm_enabled else "disabled",
    }
    app.include_router(health.router)
    return app


def _build_default_app() -> FastAPI:
    return create_app()


app = _build_default_app()
