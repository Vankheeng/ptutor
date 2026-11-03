"""Lớp trung gian gọi model ngôn ngữ: `LlmProvider` và hai bản cài đặt.

Thiết kế 02 mục 2.1 ("Nhà cung cấp: LlmProvider — đổi được bằng cấu hình") và 13 mục 13.1 (`ScriptedLlm`).

- `LlmProvider`: giao diện chung. Phần còn lại của service chỉ biết giao diện này, nên đổi nhà cung cấp hay đổi
  sang bản giả không phải sửa vòng lặp agent.
- `ScriptedLlm`: bản giả trả lời theo kịch bản định sẵn → test chạy không cần mạng, không tốn tiền, kết quả tất định.
- `OpenAiLlm`: gọi API tương thích OpenAI (Chat Completions), có gọi tool và trả JSON theo schema.
"""

from __future__ import annotations

import json
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field
from typing import Any


@dataclass
class LlmToolCall:
    """Model *đề nghị* gọi một tool. Harness quyết định có chạy hay không (thiết kế 05 mục 5.1)."""

    id: str
    name: str
    arguments: dict[str, Any]


@dataclass
class ChatMessage:
    role: str  # system | user | assistant | tool
    content: str = ""
    tool_calls: list[LlmToolCall] = field(default_factory=list)  # chỉ với role=assistant
    tool_call_id: str = ""  # chỉ với role=tool: trả lời cho lời gọi nào


@dataclass
class ToolSpec:
    """Mô tả một tool gửi cho model: tên, mô tả, JSON schema của tham số."""

    name: str
    description: str
    parameters: dict[str, Any]


@dataclass
class LlmRequest:
    messages: list[ChatMessage]
    model: str = ""
    tools: list[ToolSpec] = field(default_factory=list)
    # JSON schema bắt buộc cho câu trả lời; None = văn bản tự do (ví dụ viết lại câu hỏi).
    response_schema: dict[str, Any] | None = None
    response_schema_name: str = "answer"
    temperature: float | None = 0.0
    max_output_tokens: int = 1200
    timeout_s: float = 15.0
    purpose: str = "actor"  # actor | rewrite | verify — để trace và ScriptedLlm phân biệt


@dataclass
class LlmResponse:
    content: str = ""
    tool_calls: list[LlmToolCall] = field(default_factory=list)
    tokens_in: int = 0
    tokens_out: int = 0
    model: str = ""


class LlmError(Exception):
    """Lỗi khi gọi model. `retryable=True` khi đáng thử lại (mạng, quá giờ, 5xx, 429) — thiết kế 02, ModelCall."""

    def __init__(self, message: str, *, retryable: bool, timeout: bool = False):
        super().__init__(message)
        self.retryable = retryable
        self.timeout = timeout


class LlmProvider:
    """Giao diện chung. Mỗi bản cài đặt chỉ cần viết `complete`."""

    def complete(self, request: LlmRequest) -> LlmResponse:
        raise NotImplementedError


# Một bước kịch bản: chuỗi (câu trả lời văn bản), LlmResponse dựng sẵn, hoặc hàm nhận request để trả lời theo nó.
ScriptStep = str | LlmResponse | Callable[[LlmRequest], LlmResponse]


class ScriptedLlm(LlmProvider):
    """Bản giả: trả lần lượt các bước kịch bản, và ghi lại mọi request để test kiểm tra.

    Hai cách dùng:
    - `ScriptedLlm(["câu 1", "câu 2"])`: trả theo thứ tự; hết kịch bản thì báo lỗi rõ ràng (test viết thiếu bước).
    - `ScriptedLlm(policy=ham)`: mọi lời gọi đều hỏi `ham(request)` — dùng cho chạy eval không cần mạng.
    """

    def __init__(self, steps: Iterable[ScriptStep] = (), *, policy: Callable[[LlmRequest], LlmResponse] | None = None):
        self._steps = list(steps)
        self._policy = policy
        self.requests: list[LlmRequest] = []

    def complete(self, request: LlmRequest) -> LlmResponse:
        self.requests.append(request)
        if self._policy is not None:
            return self._policy(request)
        if not self._steps:
            raise AssertionError(f"ScriptedLlm hết kịch bản ở lời gọi thứ {len(self.requests)} ({request.purpose})")
        step = self._steps.pop(0)
        if callable(step):
            return step(request)
        if isinstance(step, LlmResponse):
            return step
        return LlmResponse(content=step, tokens_in=_rough_tokens(request), tokens_out=len(step) // 4, model="scripted")

    def calls_for(self, purpose: str) -> list[LlmRequest]:
        return [request for request in self.requests if request.purpose == purpose]


def _rough_tokens(request: LlmRequest) -> int:
    """Ước lượng token vào cho bản giả (khoảng 4 ký tự / token) để trace và ngân sách vẫn có số liệu."""
    return sum(len(message.content) for message in request.messages) // 4


class OpenAiLlm(LlmProvider):
    """Gọi API Chat Completions tương thích OpenAI.

    - `store=False`: yêu cầu nhà cung cấp không lưu dữ liệu (thiết kế 09, T6), giống `ai-service`.
    - JSON theo schema dùng `response_format=json_schema, strict=True`.
    - Lỗi được đổi thành `LlmError` để `ModelCallMiddleware` quyết định thử lại hay không.
    """

    def __init__(self, api_key: str, base_url: str = "", client: Any = None):
        if client is None:
            import openai  # nhập muộn: test dùng ScriptedLlm không cần thư viện mạng

            client = openai.OpenAI(api_key=api_key, base_url=base_url or None, max_retries=0)
        self._client = client

    def complete(self, request: LlmRequest) -> LlmResponse:
        import openai

        arguments: dict[str, Any] = {
            "model": request.model,
            "messages": [_to_openai_message(message) for message in request.messages],
            "max_completion_tokens": request.max_output_tokens,
            "store": False,
            "timeout": request.timeout_s,
        }
        if request.temperature is not None:
            arguments["temperature"] = request.temperature
        if request.tools:
            arguments["tools"] = [_to_openai_tool(tool) for tool in request.tools]
        if request.response_schema is not None:
            arguments["response_format"] = {
                "type": "json_schema",
                "json_schema": {
                    "name": request.response_schema_name, "schema": request.response_schema, "strict": True,
                },
            }
        try:
            completion = self._client.chat.completions.create(**arguments)
        except openai.APITimeoutError as error:
            raise LlmError(str(error), retryable=True, timeout=True) from error
        except (openai.APIConnectionError, openai.InternalServerError, openai.RateLimitError) as error:
            raise LlmError(str(error), retryable=True) from error
        except openai.OpenAIError as error:
            raise LlmError(str(error), retryable=False) from error
        return _from_openai_completion(completion)


def _to_openai_message(message: ChatMessage) -> dict[str, Any]:
    payload: dict[str, Any] = {"role": message.role, "content": message.content}
    if message.tool_calls:
        payload["tool_calls"] = [
            {"id": call.id, "type": "function",
             "function": {"name": call.name, "arguments": json.dumps(call.arguments, ensure_ascii=False)}}
            for call in message.tool_calls
        ]
    if message.role == "tool":
        payload["tool_call_id"] = message.tool_call_id
    return payload


def _to_openai_tool(tool: ToolSpec) -> dict[str, Any]:
    return {"type": "function", "function": {"name": tool.name, "description": tool.description,
                                             "parameters": tool.parameters}}


def _from_openai_completion(completion: Any) -> LlmResponse:
    choice = completion.choices[0].message
    tool_calls = []
    for call in choice.tool_calls or []:
        try:
            parsed = json.loads(call.function.arguments or "{}")
        except json.JSONDecodeError:
            parsed = {"_invalid_json": call.function.arguments}  # ToolGate sẽ chặn tham số sai
        tool_calls.append(LlmToolCall(id=call.id, name=call.function.name, arguments=parsed))
    usage = completion.usage
    return LlmResponse(
        content=choice.content or "",
        tool_calls=tool_calls,
        tokens_in=getattr(usage, "prompt_tokens", 0) or 0,
        tokens_out=getattr(usage, "completion_tokens", 0) or 0,
        model=completion.model,
    )
