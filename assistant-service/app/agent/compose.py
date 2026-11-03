"""Ghép kết quả cuối của một lượt từ `TurnState` (thiết kế 04 mục 4.5–4.7, 08 mục 8.1 "Hành vi").

- `finish`: câu trả lời đã qua mọi kiểm tra ở `after_model` → đánh số trích dẫn, chọn `grounded`, gợi ý.
- `abstain`: từ chối trả lời có lý do (`fallback_reason`) + nút "Gửi câu hỏi cho nhân viên".
- `busy`: model / dịch vụ phụ lỗi quá giờ → báo thử lại sau (không giả vờ "chưa có thông tin").
"""

from __future__ import annotations

from app.agent.answer import (
    ABSTAIN_ANSWER,
    CHITCHAT_ANSWER,
    OUT_OF_SCOPE_ANSWER,
    number_citations,
    truncate_answer,
)
from app.agent.state import TurnResult, TurnState
from app.kb.suggestions import suggestions_for

BUSY_ANSWER = "Trợ lý đang bận hoặc tạm thời không tra cứu được. Bạn thử lại sau ít phút nhé."


class Composer:
    def __init__(self, suggestions_table: dict):
        self._suggestions = suggestions_table

    def suggestions(self, state: TurnState) -> list[str]:
        return suggestions_for(state.caller.role, state.page_path, self._suggestions)

    def abstain(self, state: TurnState, reason: str, intent: str = "POLICY") -> TurnResult:
        return TurnResult(answer=ABSTAIN_ANSWER, intent=intent, grounded="NONE", fallback_reason=reason,
                          suggestions=self.suggestions(state), show_support_button=True)

    def out_of_scope(self, state: TurnState, reason: str) -> TurnResult:
        # Không hiện nút gửi nhân viên cho câu ngoài phạm vi (14 mục 14.2).
        return TurnResult(answer=OUT_OF_SCOPE_ANSWER, intent="OUT_OF_SCOPE", grounded="NONE",
                          fallback_reason=reason, suggestions=self.suggestions(state))

    def chitchat(self, state: TurnState, answer: str = CHITCHAT_ANSWER) -> TurnResult:
        return TurnResult(answer=answer, intent="CHITCHAT", grounded="FULL", suggestions=self.suggestions(state))

    def busy(self, state: TurnState, reason: str) -> TurnResult:
        return TurnResult(answer=BUSY_ANSWER, intent="POLICY", grounded="NONE", fallback_reason=reason)

    def finish(self, state: TurnState) -> TurnResult:
        output = state.output
        if output is None:
            return self.abstain(state, "SCHEMA_INVALID")
        if output.intent == "OUT_OF_SCOPE":
            return self.out_of_scope(state, "OUT_OF_SCOPE_MODEL")
        if output.intent == "CHITCHAT":
            return self.chitchat(state, truncate_answer(output.answer) or CHITCHAT_ANSWER)

        sections = state.retrieval.sections if state.retrieval else []
        doc_labels = {section.label for section in sections}
        tool_sources = {f"tool:{record.call.name}" for record in state.tool_calls if record.blocked_reason is None}
        valid = [claim for claim in output.claims if claim.source in doc_labels | tool_sources]
        answer, citations = number_citations(output.answer, sections, [claim.source for claim in valid])
        grounded = self._grounded(state, has_valid_claims=bool(valid))
        return TurnResult(
            answer=truncate_answer(answer), intent=output.intent, grounded=grounded,
            fallback_reason=None if grounded == "FULL" else (state.verification.get("reason") or "NO_CLAIMS"),
            citations=citations,
            # Chỉ đường dẫn nội bộ; kiểm tra đầy đủ (danh sách route, tham số thuộc seen_ids) ở RouteGuard.
            actions=[{"label": action.label, "route": action.route} for action in output.actions
                     if action.route.startswith("/")],
            suggestions=output.suggestions[:3] or self.suggestions(state),
            show_support_button=grounded != "FULL",
        )

    @staticmethod
    def _grounded(state: TurnState, *, has_valid_claims: bool) -> str:
        """Có kết quả kiểm chứng (M13) thì theo đó; chưa có thì: còn khẳng định có nguồn hợp lệ → FULL."""
        verdict = state.verification.get("grounded")
        if verdict:
            return verdict
        return "FULL" if has_valid_claims else "NONE"
