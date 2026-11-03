"""Đánh giá câu trả lời và câu hỏi gợi ý (thiết kế 07 mục 7.2, 7.5).

- `POST /v1/messages/{id}/feedback`: 👍 (`1`) / 👎 (`-1`) kèm lý do; gửi lại → ghi đè. Chỉ chủ hội thoại được đánh giá.
- `GET /v1/suggestions?role=&pagePath=`: câu hỏi gợi ý theo vai trò và trang (lấy từ `kb_setting.suggestions`).
"""

from __future__ import annotations

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import BaseModel, Field

from app.api.deps import ASSISTANT_ROLES, Caller, assistant_caller, require_internal_token
from app.kb.suggestions import suggestions_for
from app.privacy.redact import redact
from app.services import Services

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_token)])


class FeedbackRequest(BaseModel):
    rating: Literal[1, -1]
    reason: Literal["WRONG", "NOT_HELPFUL", "OUTDATED", "OTHER"] | None = None
    comment: str | None = Field(default=None, max_length=500)


@router.post("/messages/{message_id}/feedback")
def save_feedback(message_id: uuid.UUID, body: FeedbackRequest, request: Request,
                  caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    if services.conversations.assistant_message_owner(message_id) != caller.user_id:
        raise HTTPException(status_code=404, detail="MESSAGE_NOT_FOUND")
    # Ghi chú người dùng tự gõ có thể chứa SĐT / email → che trước khi lưu (09 mục 9.3).
    comment = redact(body.comment) if body.comment else None
    services.conversations.save_feedback(message_id, caller.user_id, body.rating, body.reason, comment)
    return {"messageId": str(message_id), "rating": body.rating}


@router.get("/suggestions")
def suggestions(request: Request, role: str = Query(...), pagePath: str = Query(""),  # noqa: N803 — tên theo API
                caller: Caller = Depends(assistant_caller)) -> dict:
    services: Services = request.app.state.services
    role = role.upper()
    if role not in ASSISTANT_ROLES:
        raise HTTPException(status_code=400, detail="INVALID_ROLE")
    return {"suggestions": suggestions_for(role, pagePath, services.suggestions)}
