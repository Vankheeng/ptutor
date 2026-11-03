"""`BackendClient`: lời gọi HTTP duy nhất từ trợ lý sang API nghiệp vụ của backend (thiết kế 05 mục 5.1, 5.6).

Hai bảo vệ nằm ngay ở tầng HTTP, độc lập với model và với ToolGate:
1. **Chỉ GET** — mọi phương thức khác bị từ chối trước khi tạo request.
2. **Chỉ đường dẫn trong allowlist** (= danh mục công cụ `ready`), không có đường dẫn tự do.

Token gắn vào là `Authorization` của chính người hỏi (backend chuyển tiếp) → backend chỉ trả dữ liệu của người đó.
Token không ghi log, không lưu.

- `LiveBackendClient`: httpx, timeout 5 giây.
- `MockBackendClient` (`ASSISTANT_TOOLS_MODE=mock`): đọc `eval/fixtures/scenarios/<kịch bản>/<tool>.json`
  (định dạng 13 mục 13.4: `default` + `byArgs`). Mock chỉ thay tầng HTTP — lời gọi vẫn đi qua ToolGate đầy đủ.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from app.tools.registry import ToolRegistry

DEFAULT_FIXTURES = Path(__file__).resolve().parents[2] / "eval" / "fixtures" / "scenarios"


class ForbiddenRequest(Exception):
    """Lời gọi không phải GET hoặc ngoài allowlist — lỗi lập trình / tấn công, không bao giờ được gửi đi."""


@dataclass
class BackendResponse:
    status: int  # 0 = không kết nối được / quá giờ
    data: Any = None
    code: str = ""


class BackendClient:
    def __init__(self, registry: ToolRegistry):
        self._registry = registry

    def get(self, tool_name: str, path: str, query: dict[str, Any], authorization: str,
            path_params: dict[str, str] | None = None) -> BackendResponse:
        if not self._registry.allows("GET", path):
            raise ForbiddenRequest(f"Đường dẫn ngoài allowlist: {path}")
        return self._send(tool_name, path, query, authorization, path_params or {})

    def request(self, method: str, tool_name: str, path: str, query: dict[str, Any],
                authorization: str) -> BackendResponse:
        """Cửa duy nhất cho phương thức khác GET — luôn từ chối (để test chứng minh không có đường ghi)."""
        if method.upper() != "GET":
            raise ForbiddenRequest(f"Phương thức {method} bị cấm: trợ lý chỉ đọc (QĐ7)")
        return self.get(tool_name, path, query, authorization)

    def _send(self, tool_name: str, path: str, query: dict[str, Any], authorization: str,
              path_params: dict[str, str]) -> BackendResponse:
        raise NotImplementedError


class LiveBackendClient(BackendClient):
    def __init__(self, registry: ToolRegistry, base_url: str, *, timeout_s: float = 5.0,
                 client: httpx.Client | None = None):
        super().__init__(registry)
        self._client = client or httpx.Client(base_url=base_url, timeout=timeout_s)

    def _send(self, tool_name: str, path: str, query: dict[str, Any], authorization: str,
              path_params: dict[str, str]) -> BackendResponse:
        try:
            response = self._client.get(path, params={k: v for k, v in query.items() if v is not None},
                                        headers={"Authorization": authorization, "Accept": "application/json"})
        except httpx.HTTPError:
            return BackendResponse(status=0, code="UNREACHABLE")
        try:
            body = response.json()
        except ValueError:
            body = {}
        if response.status_code >= 400 or not body.get("success", False):
            return BackendResponse(status=response.status_code, code=str(body.get("code", "")))
        return BackendResponse(status=response.status_code, data=body.get("data"))


class MockBackendClient(BackendClient):
    """Trả dữ liệu giả theo kịch bản. `scenario` đổi được cho từng case eval."""

    def __init__(self, registry: ToolRegistry, fixtures_dir: Path = DEFAULT_FIXTURES, scenario: str = "default"):
        super().__init__(registry)
        self._fixtures_dir = Path(fixtures_dir)
        self.scenario = scenario
        self.calls: list[tuple[str, str, dict]] = []  # để test / grader kiểm tra đã gọi gì

    def _send(self, tool_name: str, path: str, query: dict[str, Any], authorization: str,
              path_params: dict[str, str]) -> BackendResponse:
        self.calls.append((tool_name, path, {**query, **path_params}))
        folder = self._fixtures_dir / self.scenario
        file = folder / f"{tool_name}.json"
        if not file.is_file():
            all_down = folder / "_all.json"  # kịch bản lỗi: một file cho mọi tool (ví dụ 503)
            if not all_down.is_file():
                return BackendResponse(status=404, code="RESOURCE_NOT_FOUND")
            file = all_down
        fixture = json.loads(file.read_text(encoding="utf-8"))
        arguments = {**query, **path_params}
        chosen = fixture.get("default", {})
        for variant in fixture.get("byArgs", []):
            if all(str(arguments.get(key)) == str(value) for key, value in variant.get("match", {}).items()):
                chosen = variant
                break
        status = int(chosen.get("status", 200))
        if status >= 400 or status == 0:
            return BackendResponse(status=status, code=str(chosen.get("code", "")))
        return BackendResponse(status=status, data=chosen.get("body"))

