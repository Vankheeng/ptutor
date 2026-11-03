"""`ContextInjectionMiddleware` — hook ② (thiết kế 04 mục 4.4 và 4.10 "Ngân sách token theo lớp ngữ cảnh").

Dựng toàn bộ ngữ cảnh gửi Actor theo thứ tự lớp cố định:

    1 system prompt theo vai trò   — không bao giờ cắt
    2 mô tả tool (≤ 6)              — không cắt nội dung (ToolSelection chọn ít tool hơn nếu cần)
    3 ngữ cảnh người dùng           — không cắt
    4 trạng thái phiên (focus)      — ≤ 300 token, cắt sau cùng
    5 các mục tài liệu [D1]…        — ≤ 3.000 token, bỏ mục hạng thấp (giữ tối thiểu 2)
    6 kết quả tool của lượt này     — ≤ 2.000 token
    7 lịch sử                       — ≤ 1.500 token, cắt TRƯỚC TIÊN (bỏ lượt cũ nhất)

Khi tổng vượt `context_token_cap`: cắt theo thứ tự 7 → 6 → 5 → 4. Số token từng lớp ghi vào `state.layer_tokens`
để phân tích (13 mục 13.11).

Thêm một quy tắc của 04 mục 4.4: không có tài liệu đủ liên quan (`NO_CONTEXT`) **và** không có tool nào được chọn →
không gọi Actor, từ chối ngay (không cho model cơ hội bịa).
"""

from __future__ import annotations

from app.agent.compose import Composer
from app.agent.context import format_sections, system_prompt, user_context
from app.agent.middleware.base import Middleware
from app.agent.state import HistoryTurn, TurnState
from app.kb.routes import RouteCatalog
from app.kb.tokens import TokenCounter
from app.providers.llm import ChatMessage, LlmRequest
from app.retrieval.expand import MIN_SECTIONS, Section

HISTORY_BUDGET = 1500
TOOL_RESULT_BUDGET = 2000
FOCUS_BUDGET = 300
DOCS_BUDGET = 3000


class ContextInjectionMiddleware(Middleware):
    name = "context_injection"

    def __init__(self, routes: RouteCatalog, counter: TokenCounter, composer: Composer, *,
                 context_token_cap: int = 10_000, history_budget: int = HISTORY_BUDGET):
        self._routes = routes
        self._counter = counter
        self._composer = composer
        self._cap = context_token_cap
        self._history_budget = history_budget

    def before_model(self, state: TurnState, request: LlmRequest) -> LlmRequest:
        retrieval = state.retrieval
        if retrieval is not None and retrieval.no_context and not request.tools and not state.tool_calls:
            state.final = self._composer.abstain(state, "NO_CONTEXT")
            return request

        sections = list(retrieval.sections) if retrieval else []
        history = self._fit_history(state.history, self._history_budget)
        tool_messages = self._fit_tool_results(state.messages, TOOL_RESULT_BUDGET)
        focus = self._focus_block(state)
        system = system_prompt(state.caller.role)
        context = user_context(state.caller.role, state.page_path, state.now,
                               self._routes.paths_for_role(state.caller.role))
        tools_tokens = sum(self._counter.count(tool.description) + 40 for tool in request.tools)

        def total() -> int:
            return (self._counter.count(system) + tools_tokens + self._counter.count(context)
                    + self._counter.count(focus) + self._counter.count(format_sections(sections))
                    + sum(self._counter.count(message.content) for message in tool_messages)
                    + sum(self._counter.count(turn.question + turn.answer) for turn in history))

        # Cắt theo thứ tự lịch sử → kết quả tool → tài liệu → trạng thái phiên.
        while total() > self._cap and history:
            history = history[1:]
        if total() > self._cap and any(message.role == "tool" for message in tool_messages):
            tool_messages = self._fit_tool_results(tool_messages, TOOL_RESULT_BUDGET // 2)
        while total() > self._cap and len(sections) > MIN_SECTIONS:
            sections = sections[:-1]
        if total() > self._cap:
            focus = ""

        state.layer_tokens = {
            "system": self._counter.count(system), "tools": tools_tokens, "user_context": self._counter.count(context),
            "session_state": self._counter.count(focus), "documents": self._counter.count(format_sections(sections)),
            "tool_results": sum(self._counter.count(message.content) for message in tool_messages),
            "history": sum(self._counter.count(turn.question + turn.answer) for turn in history),
            "history_turns_dropped": len(state.history) - len(history),
        }

        messages = [ChatMessage("system", system)]
        for turn in history:
            messages += [ChatMessage("user", turn.question), ChatMessage("assistant", turn.answer)]
        question = state.rewritten_query or state.safe_question
        if state.rewritten_query and state.rewritten_query != state.safe_question:
            question = f"{state.safe_question}\n(Câu hỏi đầy đủ: {state.rewritten_query})"
        blocks = [context, focus, self._docs_block(sections), f"<question>{question}</question>"]
        messages.append(ChatMessage("user", "\n\n".join(block for block in blocks if block)))
        messages += tool_messages
        if state.retry_feedback:
            messages.append(ChatMessage("user", "<feedback>" + state.retry_feedback + "</feedback>\n"
                                        "Hãy viết lại câu trả lời theo góp ý trên, vẫn đúng schema JSON."))
            state.retry_feedback = ""
        request.messages = messages
        return request

    def _docs_block(self, sections: list[Section]) -> str:
        return format_sections(sections)

    def _focus_block(self, state: TurnState) -> str:
        """Lớp 4: các đối tượng đang bàn (ID + nhãn), mới nhắc nhất trước, ≤ 300 token."""
        if not state.session_state.focus:
            return ""
        lines: list[str] = []
        for item in sorted(state.session_state.focus, key=lambda focus: focus.last_turn, reverse=True):
            line = f"- {item.type} {item.id}: {item.label}"
            if self._counter.count("\n".join(lines + [line])) > FOCUS_BUDGET:
                break
            lines.append(line)
        return "<session_focus>\n" + "\n".join(lines) + "\n</session_focus>"

    def _fit_history(self, history: list[HistoryTurn], budget: int) -> list[HistoryTurn]:
        kept: list[HistoryTurn] = []
        used = 0
        for turn in reversed(history):  # giữ lượt mới nhất trước
            cost = self._counter.count(turn.question + " " + turn.answer)
            if used + cost > budget:
                break
            kept.insert(0, turn)
            used += cost
        return kept

    def _fit_tool_results(self, messages: list[ChatMessage], budget: int) -> list[ChatMessage]:
        """Kết quả tool quá dài → cắt bớt phần cuối của từng kết quả (đã có `total` ở đầu nên vẫn trả lời đếm được)."""
        tool_count = sum(1 for message in messages if message.role == "tool") or 1
        per_result = budget // tool_count
        fitted = []
        for message in messages:
            if message.role == "tool" and self._counter.count(message.content) > per_result:
                words = message.content.split()
                keep = max(int(per_result / 1.3), 20)
                message = ChatMessage("tool", " ".join(words[:keep]) + " …(đã cắt bớt)",
                                      tool_call_id=message.tool_call_id)
            fitted.append(message)
        return fitted
