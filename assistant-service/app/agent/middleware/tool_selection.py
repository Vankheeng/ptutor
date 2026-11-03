"""`ToolSelectionMiddleware` — hook ② (thiết kế 05 mục 5.2 "Chọn tool động").

Mỗi lời gọi model chỉ thấy **tối đa 6 tool**:
- chỉ tool của vai trò người hỏi;
- câu hỏi quy định thuần (không có dấu hiệu "của tôi", "hiện tại"…) và chưa có đối tượng nào đang bàn → không gửi tool;
- còn lại xếp hạng: trùng nhóm chức năng (`features`) với các mục tài liệu truy xuất được, trùng từ với câu hỏi mẫu
  của tool; tool cần ID (ví dụ `list_my_payment_installments` cần `contractId`) kéo theo tool liệt kê sinh ra ID đó.

Ít tool hơn = ngữ cảnh ngắn hơn, model ít chọn nhầm hơn (12 — chọn tool động).
"""

from __future__ import annotations

import re
import unicodedata

from app.agent.middleware.base import Middleware
from app.agent.state import TurnState
from app.providers.llm import LlmRequest, ToolSpec
from app.retrieval.store import query_terms
from app.text import strip_accents
from app.tools.registry import ToolDefinition, ToolRegistry

MAX_TOOLS = 6
# Dấu hiệu câu hỏi về dữ liệu của chính người hỏi. Viết CÓ DẤU: so trên câu có dấu nếu người dùng gõ có dấu, chỉ
# bỏ dấu khi chính câu hỏi không dấu. (Bỏ dấu cả hai phía thì "ví tối thiểu" khớp nhầm "ví tôi".)
PERSONAL_MARKERS = [
    r"\bcủa (tôi|mình|em|tớ)\b", r"\b(tôi|mình|em|tớ) (có|đã|đang|chưa|được|bị|cần|muốn|gửi|nạp)\b", r"\bhiện tại\b",
    r"\bbây giờ\b", r"\bcòn (bao nhiêu|lại)\b", r"\bkhi nào\b",
    r"\bđến đâu\b", r"\b(đã|được) .* chưa\b", r"\bsố dư\b",
    r"\bví (của )?(tôi|mình)\b", r"\bhợp đồng (nào|đó|kia|này)\b", r"\bkỳ học phí (tiếp theo|tới)\b",
    r"\bthế nào rồi\b", r"\bsao rồi\b",
]
UNACCENTED_MARKERS = [strip_accents(pattern) for pattern in PERSONAL_MARKERS]


def is_personal_question(text: str) -> bool:
    lowered = " " + unicodedata.normalize("NFC", text).lower() + " "
    has_diacritics = strip_accents(lowered) != lowered
    patterns = PERSONAL_MARKERS if has_diacritics else UNACCENTED_MARKERS
    target = lowered if has_diacritics else strip_accents(lowered)
    return any(re.search(pattern, target) for pattern in patterns)


class ToolSelectionMiddleware(Middleware):
    name = "tool_selection"

    def __init__(self, registry: ToolRegistry, max_tools: int = MAX_TOOLS):
        self._registry = registry
        self._max = max_tools

    def before_model(self, state: TurnState, request: LlmRequest) -> LlmRequest:
        if state.budget.tool_calls >= state.budget.max_tool_calls:
            return request  # LoopGuard sẽ bỏ hết tool; không cần chọn
        question = state.query
        if not is_personal_question(question) and not state.session_state.focus and not state.tool_calls:
            request.tools = []
            return request
        chosen = self.select(state)
        request.tools = [ToolSpec(tool.name, tool.description, tool.parameters_schema()) for tool in chosen]
        return request

    def select(self, state: TurnState) -> list[ToolDefinition]:
        tools = self._registry.for_role(state.caller.role)
        features = {feature for section in (state.retrieval.sections if state.retrieval else [])
                    for feature in section.features}
        terms = set(query_terms(state.query))
        used = {record.call.name for record in state.tool_calls}

        def score(tool: ToolDefinition) -> float:
            sample = " ".join(tool.example_questions + [tool.title])
            overlap = len(terms & set(query_terms(sample)))
            return 2.0 * len(tool.features & features) + overlap + (1.5 if tool.name in used else 0.0)

        ranked = sorted(tools, key=score, reverse=True)
        chosen: list[ToolDefinition] = []
        for tool in ranked:
            if len(chosen) >= self._max:
                break
            if tool in chosen:
                continue
            chosen.append(tool)
            # Tool cần ID → thêm tool liệt kê tạo ra ID đó (nếu chưa có ID trong sổ tay phiên).
            for param in tool.id_params:
                for source_name in param.from_tools[:1]:
                    source = self._registry.get(source_name)
                    if source and source not in chosen and state.caller.role in source.roles \
                            and len(chosen) < self._max:
                        chosen.append(source)
        return chosen
