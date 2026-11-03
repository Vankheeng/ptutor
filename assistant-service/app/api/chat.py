"""Endpoint `POST /v1/chat`: một lượt hỏi đáp (thiết kế 07 mục 7.5).

M2: gọi thẳng model qua `LlmProvider`, chưa có kho tri thức. Từ M8 phần thân được thay bằng `AgentRunner`.
"""

from __future__ import annotations

from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, Request

from app.api.deps import Caller, assistant_caller, require_internal_token
from app.api.schemas import ChatRequest, ChatResponse
from app.providers.llm import ChatMessage, LlmError, LlmRequest

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_token)])

SYSTEM_PROMPT = "Bạn là trợ lý hỗ trợ người dùng của PTutor. Trả lời ngắn gọn bằng tiếng Việt."


@router.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest, request: Request, caller: Caller = Depends(assistant_caller)) -> ChatResponse:
    llm = request.app.state.llm
    settings = request.app.state.settings
    if llm is None:
        # Chưa có khoá hoặc chưa chọn model: báo "tạm thời không khả dụng" thay vì lỗi 500 (thiết kế 11.2).
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE")
    llm_request = LlmRequest(
        messages=[ChatMessage("system", SYSTEM_PROMPT), ChatMessage("user", body.message)],
        model=settings.llm_model,
        temperature=settings.llm_temperature,
        max_output_tokens=settings.llm_max_output_tokens,
        timeout_s=settings.llm_timeout_s,
    )
    try:
        response = llm.complete(llm_request)
    except LlmError as error:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE") from error
    return ChatResponse(answer=response.content, createdAt=datetime.now(UTC))
