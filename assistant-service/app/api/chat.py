"""Endpoint `POST /v1/chat`: một lượt hỏi đáp (thiết kế 07 mục 7.5).

M7: truy xuất → (không đủ căn cứ thì từ chối ngay, **không gọi model**) → Actor trả JSON có khẳng định + nguồn →
đánh số trích dẫn. Từ M8 phần thân được thay bằng `AgentRunner` với chuỗi middleware.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request

from app.agent.answer import ABSTAIN_ANSWER, OUT_OF_SCOPE_ANSWER, number_citations, truncate_answer
from app.agent.context import format_sections, system_prompt, user_context
from app.agent.output_schema import ACTOR_OUTPUT_SCHEMA, OutputSchemaError, parse_actor_output
from app.api.deps import Caller, assistant_caller, require_internal_token
from app.api.schemas import Action, ChatRequest, ChatResponse, Citation
from app.kb.suggestions import suggestions_for
from app.providers.embedding import EmbeddingError
from app.providers.llm import ChatMessage, LlmError, LlmRequest
from app.services import Services

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_token)])
MAX_MESSAGE_CHARS = 1000


@router.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest, request: Request, caller: Caller = Depends(assistant_caller)) -> ChatResponse:
    services: Services = request.app.state.services
    question = body.message.strip()
    if not question or len(question) > MAX_MESSAGE_CHARS:
        raise HTTPException(status_code=400, detail="INVALID_MESSAGE")
    if services.llm is None or services.retriever is None:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE")
    try:
        vector = services.embedder.embed([question]).vectors[0]
    except EmbeddingError as error:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE") from error

    retrieved = services.retriever.retrieve(question, vector, caller.role, body.pagePath)
    if retrieved.no_context:
        # Không có tài liệu đủ liên quan → không cho model cơ hội bịa (thiết kế 04 mục 4.4).
        return _abstain(services, caller, body.pagePath)

    settings = services.settings
    llm_request = LlmRequest(
        messages=[
            ChatMessage("system", system_prompt(caller.role)),
            ChatMessage("user", "\n\n".join([
                user_context(caller.role, body.pagePath, services.clock.now(),
                             services.routes.paths_for_role(caller.role)),
                format_sections(retrieved.sections),
                f"<question>{question}</question>",
            ])),
        ],
        model=settings.llm_model, response_schema=ACTOR_OUTPUT_SCHEMA, temperature=settings.llm_temperature,
        max_output_tokens=settings.llm_max_output_tokens, timeout_s=settings.llm_timeout_s,
    )
    try:
        output = parse_actor_output(services.llm.complete(llm_request).content)
    except LlmError as error:
        raise HTTPException(status_code=503, detail="ASSISTANT_UNAVAILABLE") from error
    except OutputSchemaError:
        return _abstain(services, caller, body.pagePath)

    if output.intent == "OUT_OF_SCOPE":
        return ChatResponse(answer=OUT_OF_SCOPE_ANSWER, intent="OUT_OF_SCOPE", grounded="NONE",
                            suggestions=suggestions_for(caller.role, body.pagePath, services.suggestions),
                            createdAt=services.clock.now())
    labels = {section.label for section in retrieved.sections}
    supported = [claim for claim in output.claims if claim.source in labels]
    answer, citations = number_citations(output.answer, retrieved.sections, [claim.source for claim in supported])
    return ChatResponse(
        answer=truncate_answer(answer),
        citations=[Citation(**citation.__dict__) for citation in citations],
        actions=[Action(label=action.label, route=action.route) for action in output.actions
                 if services.routes.find_pattern(caller.role, action.route) == action.route],
        suggestions=output.suggestions[:3], intent=output.intent,
        grounded="FULL" if supported else "NONE", createdAt=services.clock.now(),
    )


def _abstain(services: Services, caller: Caller, page_path: str) -> ChatResponse:
    return ChatResponse(answer=ABSTAIN_ANSWER, intent="POLICY", grounded="NONE",
                        suggestions=suggestions_for(caller.role, page_path, services.suggestions),
                        createdAt=services.clock.now())
