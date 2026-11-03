"""Test hợp đồng T2 (thiết kế 05 mục 5.6, 13 mục 13.1): gọi backend dev **thật** bằng tài khoản test, kiểm các trường
`keep: true` của mỗi công cụ liệt kê vẫn có trong response — phát hiện sớm khi DTO đổi tên trường.

Chạy tay (không chạy trong pytest thường):
    $env:ASSISTANT_CONTRACT_BACKEND_URL = "http://127.0.0.1:8080"
    $env:ASSISTANT_CONTRACT_STUDENT_TOKEN = "<access token học viên test>"
    $env:ASSISTANT_CONTRACT_TUTOR_TOKEN = "<access token gia sư test>"
    .venv/Scripts/python -m pytest -m contract tests/contract
"""

from __future__ import annotations

import os

import httpx
import pytest

from app.tools.registry import ToolRegistry
from tests.fakes import CATALOG_FILE

pytestmark = pytest.mark.contract
BASE_URL = os.environ.get("ASSISTANT_CONTRACT_BACKEND_URL", "")
TOKENS = {"STUDENT": os.environ.get("ASSISTANT_CONTRACT_STUDENT_TOKEN", ""),
          "TUTOR": os.environ.get("ASSISTANT_CONTRACT_TUTOR_TOKEN", "")}
REGISTRY = ToolRegistry.from_file(CATALOG_FILE)
NO_ID_TOOLS = [(tool, role) for tool in REGISTRY.all() if not tool.id_params for role in sorted(tool.roles)]


@pytest.mark.parametrize(("tool", "role"), NO_ID_TOOLS, ids=lambda value: getattr(value, "name", value))
def test_kept_fields_exist_in_real_response(tool, role):
    if not BASE_URL or not TOKENS[role]:
        pytest.skip("Chưa đặt ASSISTANT_CONTRACT_BACKEND_URL / token test")
    response = httpx.get(BASE_URL + tool.path, params=tool.fixed_params,
                         headers={"Authorization": f"Bearer {TOKENS[role]}"}, timeout=10)
    assert response.status_code == 200, response.text[:300]
    data = response.json()["data"]
    items = (data.get("content") if isinstance(data, dict) and "content" in data else data)
    sample = items[0] if isinstance(items, list) and items else (data if isinstance(data, dict) else None)
    if not sample:
        pytest.skip("Tài khoản test chưa có dữ liệu cho công cụ này")
    expected = {name for name, spec in tool.schema.items()
                if isinstance(spec, dict) and spec.get("keep") and not spec.get("derived")}
    missing = expected - set(sample)
    assert not missing, f"{tool.name}: backend không còn trường {sorted(missing)}"
