"""API hội thoại nội bộ (thiết kế 07 mục 7.5): xem lại, kết thúc phiên, xoá.

Mọi endpoint kiểm quyền sở hữu: hội thoại không thuộc người gọi (hoặc đã xoá) → 404 như không tồn tại
(09 mục 9.3 "Cách ly": người dùng A không mở / tiếp tục / xoá được hội thoại của B).

- `POST /{id}/end`: đăng xuất hoặc "Cuộc trò chuyện mới". Gọi lại trên phiên đã kết thúc vẫn 200 (frontend gọi kiểu
  "bắn rồi quên" khi đăng xuất).
- `DELETE /{id}`: xoá mềm — đặt `deleted_at` ngay (từ đó không bao giờ được nạp / trả về); job hằng đêm xoá thật
  sau 7 ngày bằng `purge_conversation` có kiểm chứng (09 mục 9.3).
"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, Request

from app.api.deps import Caller, assistant_caller, require_internal_token
from app.db.conversations import ConversationInfo
from app.privacy.redact import redact
from app.services import Services

router = APIRouter(prefix="/v1/conversations", dependencies=[Depends(require_internal_token)])


def owned_conversation(services: Services, conversation_id: uuid.UUID, caller: Caller) -> ConversationInfo:
    info = services.conversations.get(conversation_id)
    if info is None or info.user_id != caller.user_id or info.deleted_at is not None:
        raise HTTPException(status_code=404, detail="CONVERSATION_NOT_FOUND")
    return info


@router.get("")
def list_conversations(request: Request, page: int = Query(0, ge=0), size: int = Query(20, ge=1, le=100),
                       caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    items, total = services.conversations.list_for_user(caller.user_id, page, size)
    return {
        "content": [{"id": str(info.id), "title": info.title, "createdAt": info.created_at,
                     "lastMessageAt": info.last_message_at, "ended": info.ended_at is not None} for info in items],
        "page": page, "size": size, "totalElements": total,
    }


@router.get("/{conversation_id}/messages")
def conversation_messages(conversation_id: uuid.UUID, request: Request,
                          caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    info = owned_conversation(services, conversation_id, caller)
    messages = [message | {"id": str(message["id"]) if message["id"] else None}
                for message in services.conversations.messages(conversation_id)]
    return {"conversationId": str(conversation_id), "ended": info.ended_at is not None, "messages": messages}


@router.post("/{conversation_id}/end")
def end_conversation(conversation_id: uuid.UUID, request: Request,
                     caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    owned_conversation(services, conversation_id, caller)
    services.conversations.end(conversation_id, services.clock.now())
    return {"conversationId": str(conversation_id), "ended": True}


@router.delete("/{conversation_id}")
def delete_conversation(conversation_id: uuid.UUID, request: Request,
                        caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    owned_conversation(services, conversation_id, caller)
    services.conversations.mark_deleted(conversation_id, services.clock.now())
    return {"conversationId": str(conversation_id), "deleted": True}


EXCERPT_MAX_TURNS = 6


@router.get("/{conversation_id}/excerpt")
def conversation_excerpt(conversation_id: uuid.UUID, request: Request, up_to: uuid.UUID = Query(alias="upTo"),
                         caller: Caller = Depends(assistant_caller)) -> dict:
    """Đoạn hội thoại người dùng đồng ý đính kèm vào yêu cầu hỗ trợ (thiết kế 14 mục 14.2, 14.5).

    Tối đa 6 lượt hỏi – đáp, kết thúc ở câu trả lời `upTo`. Chỉ người sở hữu lấy được (backend gọi bằng chính danh
    tính người dùng). Câu hỏi đã được che khi lưu; che lại lần nữa ở đây vì đoạn trích sẽ rời assistant-service và
    sống lâu hơn cuộc trò chuyện (365 ngày sau khi đóng yêu cầu).
    """
    services: Services = request.app.state.services
    owned_conversation(services, conversation_id, caller)
    messages = services.conversations.messages(conversation_id)
    end = next((index for index, message in enumerate(messages) if message["id"] == up_to), None)
    if end is None:
        raise HTTPException(status_code=404, detail="MESSAGE_NOT_FOUND")
    window = [message for message in messages[: end + 1] if message["content"]][-2 * EXCERPT_MAX_TURNS:]
    turns = [{"role": "user" if message["sender"] == "USER" else "assistant",
              "content": redact(message["content"]), "createdAt": message["createdAt"]} for message in window]
    return {"conversationId": str(conversation_id), "upTo": str(up_to), "turns": turns}
