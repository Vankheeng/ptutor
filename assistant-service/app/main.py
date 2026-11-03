"""Điểm vào của assistant-service: `create_app()` dựng ứng dụng FastAPI.

Thiết kế 02 mục 2.5 (`main.py`: create_app, /health). Mọi thành phần (LLM, embedding, kho tri thức...) nằm trong
`Services`, dựng một lần và gắn vào `app.state`; test truyền `Services` dựng từ bản giả để chạy không cần mạng.

Chạy (PowerShell, trong thư mục assistant-service):
    .venv/Scripts/uvicorn app.main:create_app --factory --port 8100
"""

from __future__ import annotations

from fastapi import FastAPI

from app.api import chat, health
from app.config import Settings, get_settings
from app.db.session import database_status
from app.services import Services, build_services


def create_app(settings: Settings | None = None, *, services: Services | None = None) -> FastAPI:
    """`services=None` → dựng bản thật theo cấu hình; test truyền bản giả."""
    settings = settings or (services.settings if services else get_settings())
    services = services or build_services(settings)
    app = FastAPI(title="PTutor assistant-service", version="0.1.0")
    app.state.settings = settings
    app.state.services = services
    app.state.health_checks = _health_checks(services)
    app.include_router(health.router)
    app.include_router(chat.router)
    return app


def _health_checks(services: Services) -> dict:
    """Mỗi thành phần một hàm kiểm tra; /health gọi lần lượt (thiết kế 07 mục 7.5)."""
    checks = {
        "db": lambda: database_status(services.engine),
        "llm": lambda: "ready" if services.llm is not None else "disabled",
        "embedding": lambda: services.embedder.status(),
        "rerank": lambda: services.retriever.reranker.status() if services.retriever else "disabled",
    }
    if services.settings.knowledge_store == "memory":
        checks["db"] = lambda: "ready"  # chạy thử không cần DB
    return checks
