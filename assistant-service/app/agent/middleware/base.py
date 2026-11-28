"""Giao diện `Middleware`: 6 điểm móc của vòng lặp agent (thiết kế 02 mục 2.3).

    ① before_agent      — một lần, đầu lượt (kiểm tra đầu vào, nạp bộ nhớ, truy xuất...)
    ② before_model      — trước mỗi lời gọi model (dựng ngữ cảnh, chọn tool)
    ③ wrap_model_call   — bọc quanh lời gọi model (đo thời gian, timeout, thử lại)
    ④ after_model       — sau mỗi câu trả lời của model (kiểm schema, chặn lặp, kiểm chứng...) → Decision
    ⑤ wrap_tool_call    — bọc quanh mỗi lời gọi tool (cổng kiểm soát)
    ⑥ after_agent       — một lần, cuối lượt (lưu, chốt ngân sách, đóng trace)

Mỗi middleware chỉ viết những móc nó cần; móc không viết thì mặc định "không làm gì, cho đi tiếp".
`wrap_*` nhận `call_next`: gọi nó để chuyển tiếp xuống lớp trong (hoặc tới model / tool thật), hoặc **không gọi** để
chặn — đây là cách `ToolGate` chặn một lời gọi tool mà model không thể vượt qua.
"""

from __future__ import annotations

from collections.abc import Callable

from app.agent.state import Decision, ToolCallRecord, TurnResult, TurnState
from app.providers.llm import LlmRequest, LlmResponse, LlmToolCall

ModelCall = Callable[[LlmRequest], LlmResponse]
ToolCall = Callable[[LlmToolCall], ToolCallRecord]


class Middleware:
    name = "middleware"

    def before_agent(self, state: TurnState) -> None:
        return None

    def before_model(self, state: TurnState, request: LlmRequest) -> LlmRequest:
        return request

    def wrap_model_call(self, state: TurnState, request: LlmRequest, call_next: ModelCall) -> LlmResponse:
        return call_next(request)

    def after_model(self, state: TurnState, response: LlmResponse) -> Decision:
        return Decision.accept()

    def wrap_tool_call(self, state: TurnState, call: LlmToolCall, call_next: ToolCall) -> ToolCallRecord:
        return call_next(call)

    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult:
        return result
