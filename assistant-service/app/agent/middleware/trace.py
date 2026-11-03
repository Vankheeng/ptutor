"""`TraceMiddleware` — hook ①③⑤⑥ (thiết kế 02 mục 2.3).

Mỗi lời gọi model / tool là một span (thời gian, token, trạng thái). Cuối lượt điền span `SKIPPED` cho bước không
chạy và tổng hợp số liệu cho `turn_trace`. Đăng ký **đầu tiên** để bọc ngoài cùng: thời gian đo được gồm cả các
lần thử lại do middleware bên trong thực hiện.
"""

from __future__ import annotations

import time

from app.agent.middleware.base import Middleware, ModelCall, ToolCall
from app.agent.state import ToolCallRecord, TurnResult, TurnState
from app.agent.trace import add_span, fill_skipped
from app.providers.llm import LlmRequest, LlmResponse, LlmToolCall


class TraceMiddleware(Middleware):
    name = "trace"

    def wrap_model_call(self, state: TurnState, request: LlmRequest, call_next: ModelCall) -> LlmResponse:
        actor_calls = sum(1 for span in state.spans if span.name.startswith("actor#"))
        name = "retry_actor" if state.retry_feedback and state.verify_retries else f"actor#{actor_calls + 1}"
        started = time.perf_counter()
        try:
            response = call_next(request)
        except Exception:
            add_span(state, name, "model", _ms(started), status="ERROR")
            raise
        add_span(state, name, "model", _ms(started), tokens_in=response.tokens_in, tokens_out=response.tokens_out,
                 tools=len(request.tools), layers=dict(state.layer_tokens))
        return response

    def wrap_tool_call(self, state: TurnState, call: LlmToolCall, call_next: ToolCall) -> ToolCallRecord:
        index = sum(1 for span in state.spans if span.name.startswith("tool:")) + 1
        started = time.perf_counter()
        record = call_next(call)
        status = "BLOCKED" if record.blocked_reason else ("ERROR" if (record.http_status or 200) >= 400 else "OK")
        add_span(state, f"tool:{call.name}#{index}", "tool", _ms(started), status=status,
                 blocked=record.blocked_reason, http=record.http_status)
        return record

    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult:
        fill_skipped(state)
        return result


def trace_status(result: TurnResult) -> str:
    """`turn_trace.status`: OK | ABSTAINED | BUDGET_EXCEEDED | ERROR (06 mục 6.4)."""
    if result.fallback_reason == "BUDGET":
        return "BUDGET_EXCEEDED"
    if result.fallback_reason in {"TIMEOUT", "MODEL_ERROR", "ERROR"}:
        return "ERROR"
    if result.grounded == "NONE" and result.intent not in {"CHITCHAT"}:
        return "ABSTAINED"
    return "OK"


def _ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
