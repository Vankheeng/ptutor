"""`LoopGuardMiddleware` — hook ②⑤ (thiết kế 02 mục 2.3, 05 mục 5.1 ý 6).

- ⑤ Cùng tool + cùng tham số lần thứ hai trong một lượt → **không gọi lại**, trả "đã có kết quả ở trên" (`DUPLICATE`).
  Chặn kiểu "kiểm tra lại ví của tôi 10 lần" (13 mục 13.7).
- ② Đã dùng hết lượt gọi tool (`max_tool_calls`) → không gửi tool nữa: model buộc phải trả lời bằng dữ liệu đã có.
"""

from __future__ import annotations

import json

from app.agent.middleware.base import Middleware, ToolCall
from app.agent.middleware.tool_gate import blocked
from app.agent.state import ToolCallRecord, TurnState
from app.providers.llm import LlmRequest, LlmToolCall


def call_key(call: LlmToolCall) -> str:
    return call.name + json.dumps(call.arguments, sort_keys=True, ensure_ascii=False, default=str)


class LoopGuardMiddleware(Middleware):
    name = "loop_guard"

    def before_model(self, state: TurnState, request: LlmRequest) -> LlmRequest:
        if state.budget.tool_calls >= state.budget.max_tool_calls:
            request.tools = []
        return request

    def wrap_tool_call(self, state: TurnState, call: LlmToolCall, call_next: ToolCall) -> ToolCallRecord:
        done = {call_key(record.call) for record in state.tool_calls if record.blocked_reason is None}
        if call_key(call) in done:
            return blocked(call, "DUPLICATE", "Đã gọi công cụ này với cùng tham số ở trên; dùng kết quả đó.")
        return call_next(call)
