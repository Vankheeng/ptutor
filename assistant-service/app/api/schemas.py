"""Dạng dữ liệu vào / ra của API nội bộ `/v1/**` (thiết kế 07 mục 7.2 và 7.5).

Tên trường dùng camelCase giống API backend, vì backend chuyển tiếp nguyên khối `data` cho frontend.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    conversationId: UUID | None = None
    # Không đặt max_length ở đây: quá 1.000 ký tự phải trả lỗi nghiệp vụ INVALID_MESSAGE (InputGuard), không phải 422.
    message: str = ""
    pagePath: str = Field(default="", max_length=200)


class Citation(BaseModel):
    index: int
    documentTitle: str
    section: str
    docKey: str
    anchor: str


class Action(BaseModel):
    label: str
    route: str


class ChatResponse(BaseModel):
    conversationId: UUID | None = None
    messageId: UUID | None = None
    answer: str
    citations: list[Citation] = []
    actions: list[Action] = []
    suggestions: list[str] = []
    intent: str = "POLICY"
    grounded: str = "NONE"
    createdAt: datetime
    # Hội thoại vượt 20 lượt: widget chỉ gợi ý "Bắt đầu cuộc trò chuyện mới", không chặn (04 mục 4.10).
    suggestNewConversation: bool = False
