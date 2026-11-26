"""`MemoryMiddleware` — hook ①⑥ (thiết kế 04 mục 4.10 "Bộ nhớ hội thoại").

Trợ lý giữ hai thứ trong **một** hội thoại (không có bộ nhớ xuyên phiên — QĐ13):

1. **Cửa sổ lịch sử**: 6 lượt gần nhất, mỗi lượt chỉ còn câu hỏi (đã che) + câu trả lời cuối — kết quả tool thô bị
   loại ("loại bỏ có cấu trúc"). Cắt thêm theo token ở ContextInjection (lớp 7, ≤ 1.500 token).
2. **Sổ tay phiên `session_state.focus`**: đối tượng đang bàn (ID + nhãn ngắn). **Chỉ** ghi từ kết quả tool đã lọc
   của lượt này — không bao giờ từ lời người dùng — nên người dùng không "cài" được ID lạ vào bộ nhớ, và `seen_ids`
   (dùng để chặn đoán ID) luôn là ID mà backend đã trả cho chính người này. Tối đa 20 mục; đầy thì bỏ mục lâu
   không nhắc nhất. Lời người dùng chỉ được dùng để **xếp thứ tự**: đối tượng có nhãn được gọi tên trong câu hỏi
   ("hợp đồng Toán") thành đối tượng đang bàn nhất, để đại từ "nó / cái đó" ở các lượt sau chỉ đúng nó — kể cả khi
   lượt gọi tên đã trôi khỏi cửa sổ lịch sử (thí nghiệm E4).

Quyền sở hữu hội thoại đã được InputGuard kiểm trước khi Memory nạp.
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.state import FocusItem, SessionState, TurnResult, TurnState
from app.db.conversations import ConversationStore
from app.text import collapse_spaces, strip_accents

MAX_FOCUS_ITEMS = 20


class MemoryMiddleware(Middleware):
    name = "memory"

    def __init__(self, conversations: ConversationStore, history_turns: int = 6):
        self._conversations = conversations
        self._history_turns = history_turns

    def before_agent(self, state: TurnState) -> None:
        if state.conversation_id is None:
            return
        state.history = self._conversations.history(state.conversation_id, self._history_turns)
        info = self._conversations.get(state.conversation_id)
        state.session_state = SessionState.from_json(info.session_state if info else None)

    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult:
        update_focus(state.session_state, state)
        return result


def update_focus(session_state: SessionState, state: TurnState) -> None:
    """Thêm đối tượng mới từ kết quả tool; đối tượng được nhắc lại (xuất hiện lại / dùng làm tham số) → cập nhật
    `last_turn`. Chỉ đọc `ToolCallRecord.focus_items` — do lớp lọc kết quả tool tạo ra."""
    by_id = {item.id: item for item in session_state.focus}
    turn = state.turn_number
    for record in state.tool_calls:
        if record.blocked_reason is not None:
            continue
        for found in record.focus_items:
            if found.id in by_id:
                by_id[found.id].last_turn = turn
                by_id[found.id].label = found.label or by_id[found.id].label
            else:
                by_id[found.id] = FocusItem(type=found.type, id=found.id, label=found.label,
                                            source=f"tool:{record.call.name}", turn=turn, last_turn=turn)
        for value in record.call.arguments.values():
            if isinstance(value, str) and value in by_id:
                by_id[value].last_turn = turn
    question = _normalized(state.question)
    for item in by_id.values():
        name = _normalized(item.label.split("·")[0]).strip()
        if len(name) >= 3 and f" {name} " in question:
            item.last_turn = item.mentioned_turn = turn
    items = sorted(by_id.values(), key=FocusItem.rank, reverse=True)[:MAX_FOCUS_ITEMS]
    # Thứ tự trong sổ tay: đang bàn nhất ở CUỐI (lời nhắc viết lại câu hỏi đọc từ trên xuống, mục cuối là gần nhất).
    session_state.focus = sorted(items, key=lambda item: (*item.rank(), item.turn, item.id))


def _normalized(text: str) -> str:
    return " " + collapse_spaces(strip_accents(text).lower().replace("·", " ")) + " "
