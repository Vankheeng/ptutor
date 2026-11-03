"""`QueryMiddleware` — hook ① (thiết kế 02 mục 2.3, 04 mục 4.3 "Viết lại câu hỏi").

1. **Viết lại câu hỏi** — chỉ khi hội thoại có lịch sử: một lời gọi model ngắn (≈ 3 giây, nhiệt độ 0) với các lượt
   gần nhất **và** sổ tay phiên (đối tượng đang bàn), biến "còn cái tiếng Anh thì sao?" thành câu độc lập.
   Lỗi / quá giờ / trả về rỗng → dùng nguyên câu gốc (không bao giờ làm hỏng lượt vì bước phụ này).
2. **Embed câu hỏi** một lần mỗi lượt; vector dùng chung cho ScopeGuard và Retrieval.

Tắt bằng `ASSISTANT_REWRITE_ENABLED=false` — để chạy thí nghiệm E4 (10 mục 10.5).
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.middleware.budget import charge_side_call
from app.agent.prompts import load_prompt
from app.agent.state import TurnState
from app.agent.trace import add_span, timed
from app.providers.embedding import EmbeddingProvider
from app.providers.llm import ChatMessage, LlmError, LlmProvider, LlmRequest

REWRITE_TIMEOUT_S = 3.0
REWRITE_HISTORY_TURNS = 3
MAX_REWRITE_CHARS = 500


class QueryMiddleware(Middleware):
    name = "query"

    def __init__(self, llm: LlmProvider, embedder: EmbeddingProvider, model: str, *, rewrite_enabled: bool = True):
        self._llm = llm
        self._embedder = embedder
        self._model = model
        self._rewrite_enabled = rewrite_enabled

    def before_agent(self, state: TurnState) -> None:
        if self._rewrite_enabled and state.history:
            state.rewritten_query = self._rewrite(state)
        else:
            add_span(state, "rewrite", "model", 0, status="SKIPPED")
        with timed(state, "embed_query", "retrieval"):
            state.query_vector = self._embedder.embed([state.query]).vectors[0]

    def _rewrite(self, state: TurnState) -> str:
        lines = []
        for turn in state.history[-REWRITE_HISTORY_TURNS:]:
            lines.append(f"Người dùng: {turn.question}\nTrợ lý: {turn.answer[:400]}")
        focus = "\n".join(f"- {item.type} {item.id}: {item.label}" for item in state.session_state.focus)
        user = ("<history>\n" + "\n\n".join(lines) + "\n</history>\n"
                + (f"<session_focus>\n{focus}\n</session_focus>\n" if focus else "")
                + f"<question>{state.safe_question}</question>")
        request = LlmRequest(messages=[ChatMessage("system", load_prompt("rewrite")), ChatMessage("user", user)],
                             model=self._model, temperature=0.0, max_output_tokens=120,
                             timeout_s=REWRITE_TIMEOUT_S, purpose="rewrite")
        with timed(state, "rewrite", "model") as span:
            try:
                response = self._llm.complete(request)
            except LlmError:
                span.status = "ERROR"
                return ""
            span.tokens_in, span.tokens_out = response.tokens_in, response.tokens_out
            charge_side_call(state, response)
        rewritten = response.content.strip().strip('"').strip()
        if not rewritten or len(rewritten) > MAX_REWRITE_CHARS:
            return ""
        return rewritten
