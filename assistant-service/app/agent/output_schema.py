"""Định dạng đầu ra bắt buộc của Actor (thiết kế 04 mục 4.5) và hàm đọc kết quả.

Model không trả văn tự do mà trả JSON: loại câu hỏi, **từng khẳng định kèm nguồn**, câu trả lời, nút bấm, gợi ý.
Nhờ tách sẵn `claims`, code kiểm tra được từng khẳng định (nguồn có tồn tại không, verifier chấm từng câu).

`ACTOR_OUTPUT_SCHEMA` gửi cho model ở chế độ `strict`: mọi trường bắt buộc, không thêm trường lạ.
"""

from __future__ import annotations

import json
import re
from typing import Literal

from pydantic import BaseModel, ValidationError

INTENTS = ["POLICY", "HOWTO", "LOOKUP", "CHITCHAT", "OUT_OF_SCOPE"]

ACTOR_OUTPUT_SCHEMA: dict = {
    "type": "object",
    "additionalProperties": False,
    "required": ["intent", "claims", "answer", "actions", "suggestions"],
    "properties": {
        "intent": {"type": "string", "enum": INTENTS},
        "claims": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["text", "source"],
                "properties": {
                    "text": {"type": "string", "description": "Một khẳng định ngắn"},
                    "source": {"type": "string", "description": "D1..D6 hoặc tool:<tên công cụ>; rỗng nếu không có"},
                },
            },
        },
        "answer": {"type": "string", "description": "Markdown ngắn, đánh dấu [D1]... tại câu dựa trên tài liệu"},
        "actions": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["label", "route"],
                "properties": {"label": {"type": "string"}, "route": {"type": "string"}},
            },
        },
        "suggestions": {"type": "array", "items": {"type": "string"}},
    },
}


class Claim(BaseModel):
    text: str
    source: str = ""


class ActorAction(BaseModel):
    label: str
    route: str


class ActorOutput(BaseModel):
    intent: Literal["POLICY", "HOWTO", "LOOKUP", "CHITCHAT", "OUT_OF_SCOPE"]
    claims: list[Claim]
    answer: str
    actions: list[ActorAction] = []
    suggestions: list[str] = []


class OutputSchemaError(ValueError):
    """Đầu ra không đúng schema; `OutputSchemaMiddleware` cho sinh lại một lần rồi abstain."""


_CODE_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$", re.MULTILINE)


def parse_actor_output(content: str) -> ActorOutput:
    """Đọc JSON của Actor. Chấp nhận trường hợp model bọc JSON trong ```json ... ``` (một số model hay làm)."""
    text = _CODE_FENCE.sub("", content.strip())
    try:
        return ActorOutput.model_validate(json.loads(text))
    except (json.JSONDecodeError, ValidationError) as error:
        raise OutputSchemaError(str(error)[:300]) from error
