"""Fixture dùng chung cho mọi test. Mọi test chạy không cần mạng và không cần khoá API (thiết kế 13 mục 13.1)."""

from __future__ import annotations

import pytest

from app.config import Settings

INTERNAL_TOKEN = "test-internal-token"


@pytest.fixture
def settings() -> Settings:
    # _env_file=None: không đọc file .env trên máy, để test không phụ thuộc cấu hình của người chạy.
    return Settings(_env_file=None, internal_token=INTERNAL_TOKEN, openai_api_key="", llm_model="")
