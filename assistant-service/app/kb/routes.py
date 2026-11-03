"""Danh sách route frontend được phép dẫn tới (thiết kế 03 mục 3.8).

Nguồn ban đầu là `knowledge-base/_routes.yaml` (lấy từ `frontend/src/App.tsx`); khi import, danh sách được lưu vào
`kb_setting` (khoá `routes`) để admin xem/sửa. Route có tham số (`:contractId`) chỉ được điền bằng ID lấy từ
kết quả tool — việc này do `RouteGuardMiddleware` kiểm.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml


@dataclass(frozen=True)
class AllowedRoute:
    path: str
    audience: tuple[str, ...]


def load_routes_file(path: Path) -> list[AllowedRoute]:
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return [AllowedRoute(path=item["path"], audience=tuple(item.get("audience") or ())) for item in data["routes"]]


def route_pattern_matches(pattern: str, path: str) -> bool:
    """`/student/contracts/:contractId` khớp `/student/contracts/3b2e...` (mỗi tham số là một đoạn đường dẫn)."""
    pattern_parts = pattern.strip("/").split("/")
    path_parts = path.split("?")[0].strip("/").split("/")
    if len(pattern_parts) != len(path_parts):
        return False
    pairs = zip(pattern_parts, path_parts, strict=True)
    return all(expected.startswith(":") or expected == actual for expected, actual in pairs)
