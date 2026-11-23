"""`RouteGuardMiddleware` — hook ④' (thiết kế 02 mục 2.4 "Không đưa link lạ", 04 mục 4.7).

Mỗi `actions[].route` model đề xuất phải:
1. Khớp một mẫu trong danh sách route cho phép **của vai trò** người hỏi (học viên không nhận nút tới trang gia sư);
2. Nếu mẫu có tham số (`/student/contracts/:contractId`): giá trị tham số phải là ID đã xuất hiện trong kết quả tool
   (`seen_ids` = sổ tay phiên + kết quả tool của lượt này) — model không tự đặt ra được đường dẫn tới đối tượng lạ.
Vi phạm → bỏ action đó (không làm hỏng cả câu trả lời).

Cùng nguyên tắc cho **văn bản** câu trả lời: mã UUID không nằm trong `seen_ids` (ví dụ mã người dùng tự gõ để thử
"đoán ID") được thay bằng "[mã không xác định]" — trợ lý không nhắc lại một mã mà hệ thống chưa từng trả cho người này
(09 mục 9.3). Thêm ở chặng 8: model thật đáp "chưa có thông tin về hợp đồng mã 9999…", nhắc lại mã của câu tấn công.
"""

from __future__ import annotations

import re

from app.agent.middleware.base import Middleware
from app.agent.state import Decision, TurnState
from app.kb.routes import RouteCatalog
from app.providers.llm import LlmResponse

UUID = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
UNKNOWN_ID = "[mã không xác định]"


def mask_unseen_ids(text: str, known_ids: set[str]) -> str:
    known = {value.lower() for value in known_ids}
    return UUID.sub(lambda match: match.group(0) if match.group(0).lower() in known else UNKNOWN_ID, text)


def seen_ids(state: TurnState) -> set[str]:
    ids = state.session_state.seen_ids()
    for record in state.tool_calls:
        if record.blocked_reason is None:
            ids |= {item.id for item in record.focus_items}
    return ids


def route_allowed(route: str, role: str, routes: RouteCatalog, known_ids: set[str]) -> bool:
    pattern = routes.find_pattern(role, route)
    if pattern is None:
        return False
    pattern_parts = pattern.strip("/").split("/")
    route_parts = route.split("?")[0].strip("/").split("/")
    parameters = [value for name, value in zip(pattern_parts, route_parts, strict=True) if name.startswith(":")]
    return all(value in known_ids for value in parameters)


class RouteGuardMiddleware(Middleware):
    name = "route_guard"

    def __init__(self, routes: RouteCatalog):
        self._routes = routes

    def after_model(self, state: TurnState, response: LlmResponse) -> Decision:
        if response.tool_calls or state.output is None:
            return Decision.accept()
        known = seen_ids(state)
        kept = [action for action in state.output.actions
                if route_allowed(action.route, state.caller.role, self._routes, known)]
        dropped = len(state.output.actions) - len(kept)
        state.output.actions = kept
        masked = mask_unseen_ids(state.output.answer, known)
        if masked != state.output.answer:
            state.output.answer = masked
            state.verification["unseen_ids_masked"] = True
        if dropped:
            state.verification["actions_dropped"] = dropped
        return Decision.accept()
