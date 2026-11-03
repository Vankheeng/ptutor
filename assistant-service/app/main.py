"""Điểm vào của assistant-service: `create_app()` dựng ứng dụng FastAPI.

Thiết kế 02 mục 2.5 (`main.py`: create_app, /health). Mọi thành phần phụ thuộc (LLM, embedding, DB...) được
tạo một lần ở đây và gắn vào `app.state`; test truyền bản giả vào `create_app(...)` để chạy không cần mạng.

Chạy thử (PowerShell):  .venv/Scripts/uvicorn app.main:app --port 8100
"""

from __future__ import annotations

from fastapi import FastAPI

from app.api import chat, health
from app.config import Settings, get_settings
from app.providers.llm import LlmProvider, OpenAiLlm


def create_app(settings: Settings | None = None, *, llm: LlmProvider | None = None) -> FastAPI:
    """Dựng ứng dụng. Tham số nào để None thì tạo bản thật theo cấu hình; test truyền bản giả."""
    settings = settings or get_settings()
    app = FastAPI(title="PTutor assistant-service", version="0.1.0")
    app.state.settings = settings
    app.state.llm = llm or _default_llm(settings)
    # Mỗi thành phần đăng ký một hàm kiểm tra trạng thái; /health gọi lần lượt.
    app.state.health_checks = {
        "llm": lambda: "ready" if app.state.llm is not None else "disabled",
    }
    app.include_router(health.router)
    app.include_router(chat.router)
    return app


def _default_llm(settings: Settings) -> LlmProvider | None:
    """Không có khoá hoặc chưa chọn model → None: /v1/chat trả 503 có kiểm soát."""
    if not settings.llm_enabled:
        return None
    return OpenAiLlm(api_key=settings.openai_api_key, base_url=settings.openai_base_url)


app = create_app()
