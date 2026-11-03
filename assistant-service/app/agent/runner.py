"""`AgentRunner`: vòng lặp agent đơn, gọi middleware tại 6 điểm móc (thiết kế 02 mục 2.2–2.3).

    ① before_agent (mọi middleware, theo thứ tự đăng ký) — middleware có thể đặt `state.final` để dừng sớm
    lặp:
        ② before_model  → dựng LlmRequest
        ③ wrap_model_call (lớp đăng ký trước bọc ngoài) → model
        ④ after_model   → Decision: ACCEPT / RETRY(góp ý) / ABSTAIN(lý do)
           model đề nghị tool → ⑤ wrap_tool_call cho từng lời gọi → quay lại ②
           câu trả lời cuối được chấp nhận → ghép kết quả, thoát
    ⑥ after_agent (theo thứ tự đăng ký)

Vòng lặp **không tự quyết** điều gì về an toàn: mọi giới hạn (số lần gọi, ngân sách, tool được phép...) nằm trong
middleware. Runner chỉ đảm bảo các móc được gọi đúng thứ tự và vòng lặp luôn kết thúc.
"""

from __future__ import annotations

from collections.abc import Callable

from app.agent.compose import Composer
from app.agent.middleware.base import Middleware
from app.agent.state import DecisionKind, ToolCallRecord, TurnResult, TurnState
from app.providers.llm import ChatMessage, LlmError, LlmProvider, LlmRequest, LlmResponse, LlmToolCall

ToolExecutor = Callable[[TurnState, LlmToolCall], ToolCallRecord]
# Van an toàn cuối cùng: kể cả khi cấu hình ngân sách sai, vòng lặp không chạy quá số vòng này.
HARD_ROUND_LIMIT = 8


def no_tools(state: TurnState, call: LlmToolCall) -> ToolCallRecord:
    return ToolCallRecord(call=call, blocked_reason="NOT_ALLOWED", result_text="Công cụ này không có.")


class AgentRunner:
    def __init__(self, llm: LlmProvider, middlewares: list[Middleware], composer: Composer,
                 base_request: Callable[[TurnState], LlmRequest], tool_executor: ToolExecutor = no_tools):
        self._llm = llm
        self._middlewares = middlewares
        self._composer = composer
        self._base_request = base_request
        self._tool_executor = tool_executor

    def run(self, state: TurnState) -> TurnResult:
        for middleware in self._middlewares:
            middleware.before_agent(state)
            if state.final is not None:
                break
        if state.final is None:
            try:
                self._loop(state)
            except LlmError as error:
                state.final = self._composer.busy(state, "TIMEOUT" if error.timeout else "MODEL_ERROR")
        result = state.final or self._composer.abstain(state, "ERROR")
        for middleware in self._middlewares:
            result = middleware.after_agent(state, result)
        return result

    def _loop(self, state: TurnState) -> None:
        for _ in range(HARD_ROUND_LIMIT):
            request = self._base_request(state)
            for middleware in self._middlewares:
                request = middleware.before_model(state, request)
                if state.final is not None:  # ví dụ: hết ngân sách, không có căn cứ và không có tool
                    return
            response = self._call_model(state, request)
            decision = self._after_model(state, response)
            if decision.kind is DecisionKind.ABSTAIN:
                state.final = self._composer.abstain(state, decision.reason)
                return
            if decision.kind is DecisionKind.RETRY:
                state.retry_feedback = decision.feedback
                continue
            if response.tool_calls:
                self._run_tools(state, response)
                continue
            state.final = self._composer.finish(state)
            return
        state.final = self._composer.abstain(state, "BUDGET")

    def _call_model(self, state: TurnState, request: LlmRequest) -> LlmResponse:
        """Xếp các `wrap_model_call` lồng nhau: middleware đăng ký trước nằm ngoài cùng."""
        call: Callable[[LlmRequest], LlmResponse] = self._llm.complete
        for middleware in reversed(self._middlewares):
            call = _bind_model(middleware, state, call)
        return call(request)

    def _after_model(self, state: TurnState, response: LlmResponse):
        decision = None
        for middleware in self._middlewares:
            decision = middleware.after_model(state, response)
            if decision.kind is not DecisionKind.ACCEPT:
                return decision
        return decision

    def _run_tools(self, state: TurnState, response: LlmResponse) -> None:
        state.messages.append(ChatMessage("assistant", response.content, tool_calls=list(response.tool_calls)))
        for tool_call in response.tool_calls:
            call: Callable[[LlmToolCall], ToolCallRecord] = lambda c: self._tool_executor(state, c)  # noqa: E731
            for middleware in reversed(self._middlewares):
                call = _bind_tool(middleware, state, call)
            record = call(tool_call)
            state.tool_calls.append(record)
            state.messages.append(ChatMessage("tool", record.result_text, tool_call_id=tool_call.id))


def _bind_model(middleware: Middleware, state: TurnState, inner: Callable[[LlmRequest], LlmResponse]):
    return lambda request: middleware.wrap_model_call(state, request, inner)


def _bind_tool(middleware: Middleware, state: TurnState, inner: Callable[[LlmToolCall], ToolCallRecord]):
    return lambda call: middleware.wrap_tool_call(state, call, inner)
