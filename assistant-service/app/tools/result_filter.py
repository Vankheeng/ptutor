"""Lọc và rút gọn kết quả tool trước khi đưa model (thiết kế 05 mục 5.4; quy tắc trong `tools.yaml`).

1. **Chỉ giữ trường `keep: true`** — email, họ, số tài khoản, mã nội bộ bị bỏ (09 mục 9.2, T6).
2. **Thời gian**: trường thời điểm đổi sang giờ Việt Nam theo nhãn `time_zone` của từng trường (`UTC` → đổi;
   `BACKEND_JVM` → đổi từ múi giờ JVM backend, mặc định cũng là giờ Việt Nam; `NOT_WRITTEN` → giữ nguyên), định dạng
   `dd/MM/yyyy HH:mm`. Ngày lịch (`LocalDate`) chỉ đổi định dạng, giờ học (`LocalTime`) giữ nguyên.
3. **Tiền** `BigDecimal` → `1.200.000 đ`. **Trạng thái** giữ mã enum + thêm nhãn tiếng Việt (`status_label`).
4. **Danh sách** → tối đa `max_items` mục theo chiến lược (`newest`, `installments_due_first`, `lessons_pending_first`),
   luôn kèm `total` và `counts` theo trạng thái: `{"total": 12, "counts": {...}, "items": [...]}`.
5. **Văn bản người dùng tự viết** (`user_text`) cắt ngắn — dữ liệu, không phải lệnh.
6. Rút **đối tượng có ID** (`seen_id`) → `FocusItem` cho sổ tay phiên và `seen_ids`.
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any
from zoneinfo import ZoneInfo

from app.agent.state import FocusItem
from app.tools.registry import ToolDefinition

VIETNAM = ZoneInfo("Asia/Ho_Chi_Minh")
USER_TEXT_LIMIT = 300
LABEL_FIELDS = ["subjectName", "customSubjectName", "title", "studyingRequestTitle", "code", "gradeName"]
_CAMEL = re.compile(r"(?<!^)(?=[A-Z])")


@dataclass
class FilteredResult:
    data: Any
    items_count: int | None
    focus_items: list[FocusItem] = field(default_factory=list)


@dataclass
class FilterContext:
    user_id: str
    backend_zone: ZoneInfo = VIETNAM


def _is_field_spec(spec: Any) -> bool:
    return isinstance(spec, dict) and "type" in spec


def format_money(value: Any) -> str:
    try:
        amount = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return str(value)
    return f"{int(amount):,}".replace(",", ".") + " đ"


def format_instant(value: Any, zone_rule: str, backend_zone: ZoneInfo) -> str:
    """Chuỗi LocalDateTime của backend ("2026-11-03T02:15:00") → "03/11/2026 09:15" giờ Việt Nam."""
    if not isinstance(value, str):
        return str(value)
    try:
        moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return value
    if zone_rule == "NOT_WRITTEN":
        return f"{moment:%d/%m/%Y %H:%M}"
    if moment.tzinfo is None:
        source = ZoneInfo("UTC") if zone_rule == "UTC" else backend_zone
        moment = moment.replace(tzinfo=source)
    return f"{moment.astimezone(VIETNAM):%d/%m/%Y %H:%M}"


def format_date(value: Any) -> str:
    try:
        return f"{date.fromisoformat(str(value)):%d/%m/%Y}"
    except ValueError:
        return str(value)


def _filter_value(value: Any, spec: dict, context: FilterContext) -> Any:
    if value is None:
        return None
    kind = str(spec.get("type", ""))
    if "items" in spec and isinstance(value, list):
        return [filter_object(item, spec["items"], context) for item in value if isinstance(item, dict)]
    if kind == "LocalDateTime":
        return format_instant(value, str(spec.get("time_zone", "BACKEND_JVM")), context.backend_zone)
    if kind == "LocalDate":
        return format_date(value)
    if kind == "BigDecimal":
        return format_money(value)
    if spec.get("user_text") and isinstance(value, str) and len(value) > USER_TEXT_LIMIT:
        return value[:USER_TEXT_LIMIT] + "…"
    return value


def filter_object(raw: dict, schema: dict, context: FilterContext) -> dict:
    result: dict[str, Any] = {}
    for name, spec in schema.items():
        if not _is_field_spec(spec):
            continue
        if spec.get("derived"):
            value = _derive(raw, str(spec.get("derived_from", "")), context)
        elif name in raw:
            value = raw[name]
        else:
            continue
        if not spec.get("keep", False):
            continue
        result[name] = _filter_value(value, spec, context)
        labels = spec.get("enum_labels") or {}
        if isinstance(value, str) and value in labels:
            result[f"{name}_label"] = labels[value].get("label", value)
    return result


def _derive(raw: dict, rule: str, context: FilterContext) -> Any:
    """Hiện chỉ có dạng `<trường> == X-User-Id` (ví dụ `createdByMe`): so với mã người hỏi do backend gửi sang."""
    match = re.match(r"^\s*(\w+)\s*==\s*X-User-Id\s*$", rule)
    if match:
        return str(raw.get(match.group(1))) == context.user_id
    return None


# ------------------------------------------------------------------ chọn mục của danh sách


def _sort_key_due(item: dict) -> tuple:
    due = item.get("dueDate")
    return (due is None, due or "")


def select_items(items: list[dict], strategy: str | None, limit: int | None) -> list[dict]:
    if limit is None:
        return items
    if strategy == "installments_due_first":
        pending = sorted([i for i in items if i.get("status") == "PENDING"], key=_sort_key_due)
        paid = sorted([i for i in items if i.get("status") == "PAID"], key=lambda i: i.get("dueDate") or "",
                      reverse=True)
        rest = [i for i in items if i.get("status") not in {"PENDING", "PAID"}]
        return (pending + paid + rest)[:limit]
    if strategy == "lessons_pending_first":
        waiting = [i for i in items if i.get("status") == "PENDING_CONFIRMATION"]  # giữ đủ nhóm này
        upcoming = sorted([i for i in items if i.get("status") == "SCHEDULED"],
                          key=lambda i: (i.get("date") or "", i.get("startTime") or ""))
        past = sorted([i for i in items if i.get("status") not in {"PENDING_CONFIRMATION", "SCHEDULED"}],
                      key=lambda i: (i.get("date") or "", i.get("startTime") or ""), reverse=True)
        return waiting + (upcoming + past)[:max(limit - len(waiting), 0)]
    return items[:limit]  # newest, api_order: API đã sắp sẵn


def shape_result(tool: ToolDefinition, data: Any, context: FilterContext) -> FilteredResult:
    """Dữ liệu `data` của ApiResponse → kết quả đã lọc cho model + đối tượng có ID."""
    if tool.wrapper == "object":
        item = filter_object(data, tool.schema, context) if isinstance(data, dict) else {}
        return FilteredResult(item, None, focus_from([data] if isinstance(data, dict) else [], tool))
    raw_items = (data.get("content") or data.get("items") or []) if isinstance(data, dict) else (data or [])
    total = data.get("totalElements", len(raw_items)) if isinstance(data, dict) else len(raw_items)
    chosen_raw = select_items([item for item in raw_items if isinstance(item, dict)], tool.strategy, tool.max_items)
    shaped: dict[str, Any] = {"total": total}
    if tool.count_by:
        shaped["counts"] = dict(Counter(str(item.get(tool.count_by)) for item in raw_items if isinstance(item, dict)))
        if isinstance(data, dict) and total > len(raw_items):
            shaped["counts_note"] = f"Số lượng theo trạng thái chỉ tính trên {len(raw_items)} mục mới nhất"
    shaped["items"] = [filter_object(item, tool.schema, context) for item in chosen_raw]
    if not raw_items and tool.empty_message:
        shaped["message"] = tool.empty_message
    return FilteredResult(shaped, len(raw_items), focus_from(chosen_raw, tool))


# ------------------------------------------------------------------ đối tượng có ID → sổ tay phiên


def _entity_from_class(item_class: str) -> str:
    base = item_class.split(".")[-1].removesuffix("Response") or "item"
    return _CAMEL.sub("_", base).lower()


def focus_from(raw_items: list[dict], tool: ToolDefinition) -> list[FocusItem]:
    found: list[FocusItem] = []
    for raw in raw_items:
        label = " · ".join(str(raw[name]) for name in LABEL_FIELDS if raw.get(name))
        for name, spec in tool.schema.items():
            if not (_is_field_spec(spec) and spec.get("seen_id") and raw.get(name)):
                continue
            entity = _entity_from_class(tool.item_class) if name == "id" else _CAMEL.sub("_", name[:-2]).lower()
            found.append(FocusItem(entity, str(raw[name]), label if name == "id" else "", f"tool:{tool.name}", 0, 0))
            if "items" in spec:
                continue
        for name, spec in tool.schema.items():  # ID nằm trong danh sách con (ví dụ disputedLessons[].lessonId)
            if isinstance(spec, dict) and "items" in spec and isinstance(raw.get(name), list):
                for child in raw[name]:
                    for child_name, child_spec in spec["items"].items():
                        if _is_field_spec(child_spec) and child_spec.get("seen_id") and isinstance(child, dict) \
                                and child.get(child_name):
                            found.append(FocusItem(_CAMEL.sub("_", child_name[:-2]).lower(), str(child[child_name]),
                                                   str(child.get("title") or ""), f"tool:{tool.name}", 0, 0))
    unique: dict[str, FocusItem] = {}
    for item in found:
        if item.id not in unique or (item.label and not unique[item.id].label):
            unique[item.id] = item
    return list(unique.values())
