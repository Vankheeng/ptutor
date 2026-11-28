"""`RedactMiddleware` — hook ⑥ (thiết kế 02 mục 2.3, 09 mục 9.3).

Che số điện thoại, email, dãy số dài trong **câu trả lời** trước khi lưu log (câu hỏi đã được InputGuard che từ đầu).
Câu trả lời trả cho người dùng không đổi — chỉ bản lưu trong DB bị che (đặt vào `state.verification`, được
PersistenceMiddleware dùng thay cho bản gốc). Đăng ký trước Persistence.
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.state import TurnResult, TurnState
from app.privacy.redact import redact


class RedactMiddleware(Middleware):
    name = "redact"

    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult:
        state.verification["redacted_answer"] = redact(result.answer)
        for verdict in state.verification.get("verdicts", []):
            verdict["evidence"] = redact(verdict.get("evidence", ""))
        return result
