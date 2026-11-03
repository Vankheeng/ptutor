"""`ToolGateMiddleware` — hook ⑤: cổng gác mọi lời gọi tool (thiết kế 05 mục 5.1, 09 mục 9.1 lớp 3).

Model chỉ **đề nghị**; cổng này quyết định. Mọi kiểm tra là code, model không vượt qua được:
1. Tool có trong danh mục, đúng vai trò người hỏi → nếu không: `NOT_ALLOWED`.
2. Tham số đúng kiểu, đúng enum; tham số lạ bị bỏ; tham số cố định (`source=PAYMENT`…) không cho model đặt.
3. **Kiểm nguồn gốc ID**: mọi tham số ID phải là UUID **đã xuất hiện** trong kết quả tool trước đó của hội thoại
   (`seen_ids` = sổ tay phiên + kết quả của lượt này) → nếu không: `UNSEEN_ID`. Chặn việc đoán / bịa mã của người khác.
4. Chỉ khi đạt hết mới gọi `call_next` (gọi backend bằng token của người hỏi, lọc kết quả).

Bị chặn → trả cho model một câu báo ngắn để nó đổi cách (ví dụ "gọi công cụ liệt kê trước"), ghi `blocked_reason`.
"""

from __future__ import annotations

import uuid

from app.agent.middleware.base import Middleware, ToolCall
from app.agent.middleware.route_guard import seen_ids
from app.agent.state import ToolCallRecord, TurnState
from app.providers.llm import LlmToolCall
from app.tools.registry import ParamSpec, ToolRegistry


def blocked(call: LlmToolCall, reason: str, message: str) -> ToolCallRecord:
    return ToolCallRecord(call=call, blocked_reason=reason,
                          result_text=f'<tool_result tool="{call.name}" blocked="{reason}">{message}</tool_result>')


class ToolGateMiddleware(Middleware):
    name = "tool_gate"

    def __init__(self, registry: ToolRegistry):
        self._registry = registry

    def wrap_tool_call(self, state: TurnState, call: LlmToolCall, call_next: ToolCall) -> ToolCallRecord:
        tool = self._registry.get(call.name)
        if tool is None or state.caller.role not in tool.roles:
            return blocked(call, "NOT_ALLOWED", "Công cụ này không có cho vai trò của bạn.")
        cleaned: dict = {}
        known_ids = seen_ids(state)
        for param in tool.params:
            if param.name in tool.fixed_params:
                continue  # model không được đặt
            value = call.arguments.get(param.name)
            if value in (None, ""):
                if param.required:
                    return blocked(call, "INVALID_ARGS", f"Thiếu tham số {param.name}.")
                continue
            problem = _check_value(param, value, known_ids)
            if problem == "UNSEEN_ID":
                return blocked(call, "UNSEEN_ID", f"Mã {param.name} này chưa xuất hiện trong kết quả tra cứu của bạn. "
                                                  f"Hãy gọi {', '.join(param.from_tools[:2]) or 'công cụ liệt kê'} "
                                                  "trước để lấy đúng mã.")
            if problem:
                return blocked(call, "INVALID_ARGS", problem)
            cleaned[param.name] = value
        return call_next(LlmToolCall(id=call.id, name=call.name, arguments=cleaned))


def _check_value(param: ParamSpec, value, known_ids: set[str]) -> str | None:
    if param.seen_id or param.type == "UUID":
        try:
            canonical = str(uuid.UUID(str(value)))
        except ValueError:
            return f"{param.name} phải là mã UUID."
        if param.seen_id and canonical not in known_ids and str(value) not in known_ids:
            return "UNSEEN_ID"
        return None
    if param.enum and str(value) not in param.enum:
        return f"{param.name} phải là một trong {', '.join(param.enum)}."
    if param.type.lower() in {"int", "integer"} and not _int_in_range(value, param):
        return f"{param.name} không hợp lệ."
    return None


def _int_in_range(value, param: ParamSpec) -> bool:
    if not isinstance(value, int) or isinstance(value, bool):
        return False
    too_small = param.minimum is not None and value < param.minimum
    too_big = param.maximum is not None and value > param.maximum
    return not (too_small or too_big)
