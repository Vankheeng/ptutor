"""`InputGuardMiddleware` — hook ① (thiết kế 04 mục 4.2, 09 mục 9.1 lớp 1).

Ba việc, trước khi model thấy bất cứ thứ gì:
1. Độ dài 1..1.000 ký tự, nếu không → `400 INVALID_MESSAGE`.
2. Hội thoại phải thuộc đúng người hỏi (`X-User-Id`) và chưa bị xoá → nếu không `404 CONVERSATION_NOT_FOUND`
   (không nói "của người khác" để không lộ mã đó có tồn tại). Đã kết thúc → `409 CONVERSATION_ENDED`.
3. Che số điện thoại, email, dãy số dài → `state.safe_question`. Từ đây trở đi chỉ dùng bản đã che.
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.state import TurnError, TurnState
from app.agent.trace import timed
from app.db.conversations import ConversationStore
from app.privacy.redact import redact


class InputGuardMiddleware(Middleware):
    name = "input_guard"

    def __init__(self, conversations: ConversationStore, max_chars: int = 1000):
        self._conversations = conversations
        self._max_chars = max_chars

    def before_agent(self, state: TurnState) -> None:
        with timed(state, "input_guard"):
            question = state.question.strip()
            if not question or len(question) > self._max_chars:
                raise TurnError(400, "INVALID_MESSAGE")
            if state.conversation_id is not None:
                info = self._conversations.get(state.conversation_id)
                if info is None or info.user_id != state.caller.user_id or info.deleted_at is not None:
                    raise TurnError(404, "CONVERSATION_NOT_FOUND")
                if info.ended_at is not None:
                    raise TurnError(409, "CONVERSATION_ENDED")
                state.turn_number = info.turn_count + 1
            state.safe_question = redact(question)
