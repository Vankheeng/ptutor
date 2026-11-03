"""`PersistenceMiddleware` — hook ⑥ (thiết kế 02 mục 2.3, 06 mục 6.4, 09 mục 9.3).

Lưu một lượt: câu hỏi (đã che), câu trả lời (đã che bởi RedactMiddleware nếu có), các đoạn đã dùng kèm điểm, log
tool (**chỉ** tên, tham số ID / enum, mã HTTP, độ trễ, số mục — không lưu dữ liệu trả về), trace, `session_state`.
Không bao giờ lưu access token. Đăng ký **cuối cùng** để lưu kết quả sau mọi kiểm tra.
"""

from __future__ import annotations

import re
import time
from collections.abc import Callable

from app.agent.middleware.base import Middleware
from app.agent.middleware.trace import trace_status
from app.agent.prompts import PROMPT_VERSIONS
from app.agent.state import TurnResult, TurnState
from app.agent.trace import add_span
from app.db.conversations import ConversationStore, TurnRecord

_SAFE_ARG = re.compile(r"^[A-Za-z0-9_\-:.]{1,64}$")


class PersistenceMiddleware(Middleware):
    name = "persistence"

    def __init__(self, conversations: ConversationStore, llm_model: str, config_hash: Callable[[], str]):
        self._conversations = conversations
        self._llm_model = llm_model
        self._config_hash = config_hash

    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult:
        started = time.perf_counter()
        record = self._record(state, result)
        add_span(state, "persist", "step", int((time.perf_counter() - started) * 1000))
        record.trace["spans"] = [span.__dict__ for span in state.spans]
        saved = self._conversations.save_turn(record)
        result.conversation_id = saved.conversation_id
        result.message_id = saved.assistant_message_id
        return result

    def _record(self, state: TurnState, result: TurnResult) -> TurnRecord:
        cited_doc_keys = {citation.docKey for citation in result.citations}
        candidates = []
        if state.retrieval is not None:
            for candidate in state.retrieval.candidates:
                candidates.append({
                    "chunk_id": candidate.chunk.id, "rank": candidate.rank,
                    "vector_score": candidate.vector_score, "text_score": candidate.text_score,
                    "rrf_score": candidate.rrf_score, "rerank_score": candidate.rerank_score,
                    "in_context": candidate.in_context,
                    "cited": candidate.in_context and candidate.chunk.doc_key in cited_doc_keys,
                })
        tool_logs = [{
            "tool_name": record.call.name, "args": _safe_args(record.call.arguments),
            "blocked_reason": record.blocked_reason, "http_status": record.http_status,
            "latency_ms": record.latency_ms, "result_items": record.result_items,
        } for record in state.tool_calls]
        tokens_in = sum(span.tokens_in for span in state.spans)
        tokens_out = sum(span.tokens_out for span in state.spans)
        return TurnRecord(
            user_id=state.caller.user_id, role=state.caller.role, conversation_id=state.conversation_id,
            page_path=state.page_path, question=state.safe_question,
            rewritten_query=state.rewritten_query if state.rewritten_query != state.safe_question else "",
            answer=state.verification.get("redacted_answer") or result.answer,
            intent=result.intent, grounded=result.grounded, fallback_reason=result.fallback_reason,
            verification={key: value for key, value in state.verification.items() if key != "redacted_answer"},
            actions=result.actions, citations=[citation.__dict__ for citation in result.citations],
            llm_model=self._llm_model, prompt_version=PROMPT_VERSIONS["actor"],
            candidates=candidates, tool_logs=tool_logs,
            trace={
                "request_id": state.request_id[:64] or None, "config_hash": self._config_hash(),
                "status": trace_status(result), "spans": [], "total_ms": state.elapsed_ms(),
                "tokens_in": tokens_in, "tokens_out": tokens_out, "model_calls": state.budget.model_calls,
                "tool_calls": len(state.tool_calls), "verify_retries": state.verify_retries,
            },
            session_state=state.session_state.to_json(), now=state.now,
        )


def _safe_args(arguments: dict) -> dict:
    """Chỉ giữ tham số dạng ID / enum / số trang; bỏ mọi chuỗi tự do (có thể chứa thông tin cá nhân)."""
    return {key: value for key, value in arguments.items()
            if isinstance(value, int) or (isinstance(value, str) and _SAFE_ARG.match(value))}
