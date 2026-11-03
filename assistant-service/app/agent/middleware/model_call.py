"""`ModelCallMiddleware` — hook ③ (thiết kế 02 mục 2.3).

Bọc lời gọi model: giới hạn token đầu ra (chống trả lời lan man — 02 mục 2.4), thử lại **1 lần** khi lỗi mạng / quá
giờ / 5xx / 429. Lỗi không đáng thử lại (sai khoá, yêu cầu sai) → đẩy lên ngay; runner trả câu "đang bận".
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware, ModelCall
from app.agent.state import TurnState
from app.providers.llm import LlmError, LlmRequest, LlmResponse


class ModelCallMiddleware(Middleware):
    name = "model_call"

    def __init__(self, timeout_s: float, max_output_tokens: int, retries: int = 1):
        self._timeout_s = timeout_s
        self._max_output_tokens = max_output_tokens
        self._retries = retries

    def wrap_model_call(self, state: TurnState, request: LlmRequest, call_next: ModelCall) -> LlmResponse:
        request.timeout_s = min(request.timeout_s, self._timeout_s)
        request.max_output_tokens = min(request.max_output_tokens, self._max_output_tokens)
        attempt = 0
        while True:
            try:
                return call_next(request)
            except LlmError as error:
                if not error.retryable or attempt >= self._retries:
                    raise
                attempt += 1
