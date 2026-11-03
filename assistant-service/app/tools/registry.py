"""`ToolRegistry`: đọc danh mục công cụ tra cứu **thẳng** từ `tool-catalog/tools.yaml` (quyết định của người dùng,
thay cho khai báo tay ở thiết kế 05 mục 5.3).

Mỗi công cụ `status: ready` thành một `ToolDefinition`: tên (`func_code`), vai trò, nhóm chức năng, đường dẫn GET,
tham số (bắt buộc / tuỳ chọn / cố định), schema kết quả (trường nào giữ, múi giờ, nhãn enum...), cách chọn mục của
danh sách. Công cụ `planned` bị bỏ qua (backend chưa có API).

Allowlist của `BackendClient` chính là tập (phương thức, mẫu đường dẫn) của các định nghĩa này — thêm công cụ = thêm
khai báo trong `tools.yaml`, không sửa vòng lặp agent (05 mục 5.2).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

DEFAULT_CATALOG = Path(__file__).resolve().parents[3] / "tool-catalog" / "tools.yaml"
_PATH_PARAM = re.compile(r"\{(\w+)\}")


@dataclass
class ParamSpec:
    name: str
    location: str  # path | query
    type: str
    required: bool
    description: str = ""
    enum: list[str] = field(default_factory=list)
    seen_id: bool = False  # giá trị phải thuộc seen_ids (05 mục 5.1 ý 5)
    from_tools: list[str] = field(default_factory=list)
    default: Any = None
    minimum: int | None = None
    maximum: int | None = None


@dataclass
class ToolDefinition:
    name: str
    title: str
    description: str
    roles: set[str]
    features: set[str]
    method: str
    path: str
    params: list[ParamSpec]
    fixed_params: dict[str, Any]
    wrapper: str  # object | list | page
    item_class: str
    schema: dict[str, Any]
    max_items: int | None
    strategy: str | None
    count_by: str | None
    empty_message: str | None
    errors: dict[str, dict[str, Any]]
    example_questions: list[str]
    reversibility: str

    @property
    def id_params(self) -> list[ParamSpec]:
        return [param for param in self.params if param.seen_id]

    def parameters_schema(self) -> dict[str, Any]:
        """JSON schema tham số gửi model. Tham số cố định (`fixed_params`, ví dụ `source=PAYMENT`) KHÔNG có ở đây:
        model không thấy và không đổi được chúng."""
        properties: dict[str, Any] = {}
        required: list[str] = []
        for param in self.params:
            if param.name in self.fixed_params:
                continue
            spec: dict[str, Any] = {"type": _json_type(param.type), "description": param.description}
            if param.enum:
                spec["enum"] = param.enum
            properties[param.name] = spec
            if param.required:
                required.append(param.name)
        return {"type": "object", "properties": properties, "required": required, "additionalProperties": False}


def _json_type(java_type: str) -> str:
    return "integer" if java_type.lower() in {"int", "integer", "long"} else "string"


def _wrapper_kind(text: str) -> str:
    if "phân trang" in text or ".content" in text:
        return "page"
    if "danh sách" in text:
        return "list"
    return "object"


def _param(raw: dict, required: bool) -> ParamSpec:
    return ParamSpec(
        name=raw["name"], location=raw.get("in", "query"), type=str(raw.get("type", "String")), required=required,
        description=str(raw.get("description", "")).strip(), enum=[str(value) for value in raw.get("enum") or []],
        seen_id=raw.get("source") == "seen_ids", from_tools=list(raw.get("from_tools") or []),
        default=raw.get("default"), minimum=raw.get("min"), maximum=raw.get("max"),
    )


def parse_tool(raw: dict) -> ToolDefinition:
    request = raw["request"]
    params = [_param(item, True) for item in raw.get("required_params") or []]
    params += [_param(item, False) for item in raw.get("optional_params") or []]
    response = raw["response"]
    selection = raw.get("result_selection") or {}
    return ToolDefinition(
        name=raw["func_code"], title=raw.get("name", raw["func_code"]),
        description=" ".join(str(raw.get("description", "")).split()), roles=set(raw["roles"]),
        features=set(raw.get("features") or []), method=request["method"].upper(), path=request["path"],
        params=params, fixed_params=dict(raw.get("fixed_params") or {}), wrapper=_wrapper_kind(response["wrapper"]),
        item_class=str(response.get("item_class", "")), schema=dict(response.get("schema") or {}),
        max_items=raw.get("max_items"), strategy=selection.get("strategy"), count_by=selection.get("count_by"),
        empty_message=raw.get("empty_result_message"),
        errors={error["code"]: error for error in raw.get("errors") or []},
        example_questions=list(raw.get("example_questions") or []),
        reversibility=str(raw.get("reversibility", "")),
    )


class ToolRegistry:
    def __init__(self, tools: list[ToolDefinition], common_errors: dict[str, dict] | None = None,
                 time_zones: dict | None = None):
        # Hai lớp bảo vệ ngay khi nạp: chỉ GET, chỉ READ_ONLY. Danh mục sai thì service không khởi động.
        for tool in tools:
            if tool.method != "GET" or tool.reversibility != "READ_ONLY":
                raise ValueError(f"Công cụ {tool.name} không phải GET / READ_ONLY — không được nạp")
        self._tools = {tool.name: tool for tool in tools}
        self.common_errors = common_errors or {}
        self.time_zones = time_zones or {}

    @classmethod
    def from_file(cls, path: str | Path = DEFAULT_CATALOG) -> ToolRegistry:
        data = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
        tools = [parse_tool(raw) for raw in data["tools"] if raw.get("status") == "ready"]
        common = {error["code"]: error for error in data.get("common_errors") or []}
        return cls(tools, common, (data.get("wrapper") or {}).get("time_zones") or {})

    def get(self, name: str) -> ToolDefinition | None:
        return self._tools.get(name)

    def for_role(self, role: str) -> list[ToolDefinition]:
        return [tool for tool in self._tools.values() if role in tool.roles]

    def all(self) -> list[ToolDefinition]:
        return list(self._tools.values())

    def allows(self, method: str, path: str) -> bool:
        """Allowlist của BackendClient: (GET, mẫu đường dẫn) có trong danh mục không."""
        if method.upper() != "GET":
            return False
        return any(_path_matches(tool.path, path) for tool in self._tools.values())


def _path_matches(pattern: str, path: str) -> bool:
    regex = "^" + _PATH_PARAM.sub(r"[^/]+", re.escape(pattern).replace(r"\{", "{").replace(r"\}", "}")) + "$"
    return re.match(regex, path.split("?")[0]) is not None


def fill_path(pattern: str, values: dict[str, str]) -> str:
    return _PATH_PARAM.sub(lambda match: values[match.group(1)], pattern)
