"""`BudgetMiddleware` — hook ①②④⑤ (thiết kế 02 mục 2.3, 09 mục 9.1 lớp 5 "cầu dao").

Hạn mức mỗi lượt: ≤ 4 lời gọi model (không tính verifier), ≤ 3 lời gọi tool, ≤ 40.000 token, ≤ 25 giây. Trừ dần;
chạm trần → dừng có kiểm soát: trả phần đã có (nếu đã có câu trả lời hợp lệ) hoặc từ chối với
`fallback_reason = BUDGET`. Đây là van chống vòng lặp và chống đốt tiền, không phụ thuộc model "ngoan".
"""

from __future__ import annotations

from app.agent.compose import Composer
from app.agent.middleware.base import Middleware, ToolCall
from app.agent.state import Budget, Decision, ToolCallRecord, TurnState
from app.config import Settings
from app.providers.llm import LlmRequest, LlmResponse, LlmToolCall


class BudgetMiddleware(Middleware):
    name = "budget"

    def __init__(self, settings: Settings, composer: Composer):
        self._settings = settings
        self._composer = composer

    def before_agent(self, state: TurnState) -> None:
        state.budget = Budget(max_model_calls=self._settings.max_model_calls,
                              max_tool_calls=self._settings.max_tool_calls,
                              max_tokens=self._settings.turn_token_budget, max_seconds=self._settings.turn_timeout_s)

    def before_model(self, state: TurnState, request: LlmRequest) -> LlmRequest:
        if state.budget.exhausted() is not None:
            state.final = (self._composer.finish(state) if state.output is not None
                           else self._composer.abstain(state, "BUDGET"))
        # Không để một lời gọi model chờ lâu hơn thời gian còn lại của lượt.
        remaining = state.budget.max_seconds - state.budget.elapsed()
        request.timeout_s = max(1.0, min(request.timeout_s, remaining))
        return request

    def after_model(self, state: TurnState, response: LlmResponse) -> Decision:
        state.budget.model_calls += 1
        state.budget.tokens += response.tokens_in + response.tokens_out
        return Decision.accept()

    def wrap_tool_call(self, state: TurnState, call: LlmToolCall, call_next: ToolCall) -> ToolCallRecord:
        if state.budget.tool_calls >= state.budget.max_tool_calls:
            return ToolCallRecord(call=call, blocked_reason="BUDGET",
                                  result_text="Đã hết lượt tra cứu cho câu hỏi này. Hãy trả lời bằng dữ liệu đã có.")
        state.budget.tool_calls += 1
        return call_next(call)


def charge_side_call(state: TurnState, response: LlmResponse) -> None:
    """Lời gọi phụ (viết lại câu hỏi, verifier): tính token vào ngân sách nhưng không tính vào số lời gọi Actor."""
    state.budget.tokens += response.tokens_in + response.tokens_out
