"""Kiểm tra danh mục công cụ và danh sách route có khớp với code hay không.

Thuộc thiết kế 05 mục 5.2 (allowlist = danh mục công cụ) và 03 mục 3.8 (route cho phép).
Chạy sau mỗi lần merge `dev`, vì danh mục được sinh từ code `dev`:

    python scripts/check_catalog.py --repo ..

Hai việc:
1. Mỗi công cụ `ready` trong `tool-catalog/tools.yaml` phải trỏ tới một endpoint GET có thật
   trong các controller của backend.
2. Mỗi route trong `knowledge-base/_routes.yaml` phải có trong `frontend/src/App.tsx`.

Thoát với mã 1 nếu có chỗ lệch, để dùng được trong script kiểm tra.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

# Tên biến trong đường dẫn ({contractId}, {id}...) không quan trọng khi so khớp,
# nên đổi hết về "{}" trước khi so.
_PATH_VARIABLE = re.compile(r"\{[^}]*\}")
_CLASS_MAPPING = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"')
_GET_MAPPING = re.compile(r'@GetMapping(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"[^)]*\)|\(\s*\)|(?=\s))')


def normalize_api_path(path: str) -> str:
    path = _PATH_VARIABLE.sub("{}", path.strip())
    return path.rstrip("/") or "/"


def find_backend_get_paths(controller_dir: Path) -> set[str]:
    """Đọc mọi controller, ghép @RequestMapping của lớp với @GetMapping của phương thức."""
    paths: set[str] = set()
    for java_file in controller_dir.rglob("*.java"):
        source = java_file.read_text(encoding="utf-8")
        class_match = _CLASS_MAPPING.search(source)
        prefix = class_match.group(1) if class_match else ""
        for get_match in _GET_MAPPING.finditer(source):
            suffix = get_match.group(1) or ""
            paths.add(normalize_api_path(prefix + suffix))
    return paths


def check_tools(repo: Path) -> list[str]:
    catalog = yaml.safe_load((repo / "tool-catalog" / "tools.yaml").read_text(encoding="utf-8"))
    backend_paths = find_backend_get_paths(repo / "backend" / "src" / "main" / "java")
    problems = []
    for tool in catalog["tools"]:
        if tool.get("status") != "ready":
            continue
        request = tool["request"]
        if request["method"] != "GET":
            problems.append(f"{tool['func_code']}: phương thức {request['method']} (chỉ cho phép GET)")
        elif normalize_api_path(request["path"]) not in backend_paths:
            problems.append(f"{tool['func_code']}: không thấy controller GET {request['path']}")
    return problems


def _read_route_tags(tsx: str):
    """Duyệt các thẻ <Route ...> theo thứ tự, trả (thuộc tính, là thẻ tự đóng?, là thẻ đóng?).

    Không dùng một regex duy nhất vì thuộc tính element={<Page />} có chứa "/>" bên trong;
    phải đếm ngoặc nhọn {} để biết dấu ">" nào thật sự kết thúc thẻ Route.
    """
    position = 0
    while True:
        open_at = tsx.find("<Route", position)
        close_at = tsx.find("</Route>", position)
        if open_at == -1 and close_at == -1:
            return
        if close_at != -1 and (open_at == -1 or close_at < open_at):
            yield "", False, True
            position = close_at + len("</Route>")
            continue
        depth = 0
        index = open_at + len("<Route")
        while True:
            char = tsx[index]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
            elif char == ">" and depth == 0:
                break
            index += 1
        attributes = tsx[open_at + len("<Route"):index]
        self_closing = attributes.rstrip().endswith("/")
        yield attributes, self_closing, False
        position = index + 1


def find_frontend_routes(app_tsx: Path) -> set[str]:
    routes: set[str] = set()
    parents: list[str] = []
    for attributes, self_closing, is_closing in _read_route_tags(app_tsx.read_text(encoding="utf-8")):
        if is_closing:
            parents.pop()
            continue
        path_match = re.search(r'path="([^"]*)"', attributes)
        own = path_match.group(1) if path_match else ""
        base = parents[-1] if parents else ""
        if own.startswith("/"):
            full = own
        elif own:
            full = base.rstrip("/") + "/" + own
        else:
            full = base  # route index hoặc route chỉ để bọc guard
        if full:
            routes.add(full)
        if not self_closing:
            parents.append(full)
    return routes


def check_routes(repo: Path) -> list[str]:
    declared = yaml.safe_load((repo / "knowledge-base" / "_routes.yaml").read_text(encoding="utf-8"))
    existing = find_frontend_routes(repo / "frontend" / "src" / "App.tsx")
    missing = [item["path"] for item in declared["routes"] if item["path"] not in existing]
    return [f"route {path} không có trong App.tsx" for path in missing]


def main() -> int:
    # Console Windows mặc định cp1252, không in được tiếng Việt.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    problems = check_tools(args.repo) + check_routes(args.repo)
    for problem in problems:
        print("LỆCH:", problem)
    print("OK: danh mục công cụ và route khớp code." if not problems else f"Có {len(problems)} chỗ lệch.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
