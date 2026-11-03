"""API hội thoại nội bộ (thiết kế 07 mục 7.5): kết thúc phiên.

`POST /v1/conversations/{id}/end` — gọi khi người dùng đăng xuất hoặc bấm "Cuộc trò chuyện mới" (04 mục 4.10).
Hội thoại không thuộc người gọi → 404 (như không tồn tại). Gọi lại trên phiên đã kết thúc → vẫn 200 (idempotent),
vì frontend gọi `/end` kiểu "bắn rồi quên" khi đăng xuất.
"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Request

from app.api.deps import Caller, assistant_caller, require_internal_token
from app.db.conversations import ConversationInfo
from app.services import Services

router = APIRouter(prefix="/v1/conversations", dependencies=[Depends(require_internal_token)])


def owned_conversation(services: Services, conversation_id: uuid.UUID, caller: Caller) -> ConversationInfo:
    """Kiểm quyền sở hữu: dùng cho mọi API đọc / ghi một hội thoại (09 mục 9.3 "Cách ly")."""
    info = services.conversations.get(conversation_id)
    if info is None or info.user_id != caller.user_id or info.deleted_at is not None:
        raise HTTPException(status_code=404, detail="CONVERSATION_NOT_FOUND")
    return info


@router.post("/{conversation_id}/end")
def end_conversation(conversation_id: uuid.UUID, request: Request,
                     caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    owned_conversation(services, conversation_id, caller)
    services.conversations.end(conversation_id, services.clock.now())
    return {"conversationId": str(conversation_id), "ended": True}
