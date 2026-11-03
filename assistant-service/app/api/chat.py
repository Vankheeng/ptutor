"""Endpoint `POST /v1/chat`: một lượt hỏi đáp (thiết kế 07 mục 7.5).

Từ M8, phần thân chỉ còn: dựng `TurnState` → `AgentRunner.run` → đổi `TurnResult` thành `ChatResponse`. Mọi quy tắc
(kiểm tra đầu vào, truy xuất, ngân sách, schema, lưu...) nằm trong middleware.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request

from app.agent.harness import build_runner
from app.agent.state import Caller as TurnCaller
from app.agent.state import TurnError, TurnState
from app.api.deps import Caller, assistant_caller, require_internal_token
from app.api.schemas import Action, ChatRequest, ChatResponse, Citation
from app.providers.embedding import EmbeddingError
from app.services import Services

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_token)])


@router.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest, request: Request, caller: Caller = Depends(assistant_caller)) -> ChatResponse:
    services: Services = request.app.state.services
    if services.llm is None or services.retriever is None:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE")
    state = TurnState(
        caller=TurnCaller(caller.user_id, caller.role, caller.authorization), question=body.message,
        page_path=body.pagePath, now=services.clock.now(), request_id=caller.request_id,
        conversation_id=body.conversationId,
    )
    try:
        result = build_runner(services).run(state)
    except TurnError as error:
        raise HTTPException(status_code=error.status, detail=error.code) from error
    except EmbeddingError as error:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE") from error
    return ChatResponse(
        conversationId=result.conversation_id, messageId=result.message_id, answer=result.answer,
        citations=[Citation(**citation.__dict__) for citation in result.citations],
        actions=[Action(**action) for action in result.actions], suggestions=result.suggestions,
        intent=result.intent, grounded=result.grounded, createdAt=services.clock.now(),
    )
