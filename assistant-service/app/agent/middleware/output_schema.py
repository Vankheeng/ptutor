"""`OutputSchemaMiddleware` — hook ④ (thiết kế 02 mục 2.3, 04 mục 4.5).

Câu trả lời cuối phải đúng JSON schema. Sai → cho sinh lại **1 lần** kèm góp ý; vẫn sai → từ chối trả lời
(`fallback_reason = SCHEMA_INVALID`). Lượt model chỉ đề nghị gọi tool thì không cần JSON, cho qua.
"""

from __future__ import annotations

from app.agent.middleware.base import Middleware
from app.agent.output_schema import OutputSchemaError, parse_actor_output
from app.agent.state import Decision, TurnState
from app.providers.llm import LlmResponse

MAX_SCHEMA_RETRIES = 1


class OutputSchemaMiddleware(Middleware):
    name = "output_schema"

    def after_model(self, state: TurnState, response: LlmResponse) -> Decision:
        if response.tool_calls:
            return Decision.accept()
        try:
            state.output = parse_actor_output(response.content)
        except OutputSchemaError as error:
            if state.schema_retries >= MAX_SCHEMA_RETRIES:
                return Decision.abstain("SCHEMA_INVALID")
            state.schema_retries += 1
            return Decision.retry(f"Câu trả lời trước không phải JSON đúng schema ({error}). "
                                  "Chỉ trả về một đối tượng JSON với các trường intent, claims, answer, actions, "
                                  "suggestions.")
        return Decision.accept()
