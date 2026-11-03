"""Fixture dùng chung cho mọi test. Mọi test chạy không cần mạng và không cần khoá API (thiết kế 13 mục 13.1)."""

from __future__ import annotations

import pytest

from app.config import Settings

INTERNAL_TOKEN = "test-internal-token"
USER_ID = "11111111-1111-1111-1111-000000000001"


@pytest.fixture
def settings() -> Settings:
    # _env_file=None: không đọc file .env trên máy, để test không phụ thuộc cấu hình của người chạy.
    return Settings(_env_file=None, internal_token=INTERNAL_TOKEN, openai_api_key="", llm_model="")


def caller_headers(role: str = "STUDENT", user_id: str = USER_ID) -> dict[str, str]:
    """Header backend gửi sang (thiết kế 07 mục 7.4)."""
    return {"X-Internal-Token": INTERNAL_TOKEN, "X-User-Id": user_id, "X-User-Role": role,
            "Authorization": "Bearer user-access-token", "X-Request-Id": "req-test"}
