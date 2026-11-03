"""Test M2: LlmProvider (ScriptedLlm, OpenAiLlm với client giả) và /v1/chat."""

from types import SimpleNamespace

import httpx
import openai
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.providers.llm import ChatMessage, LlmError, LlmRequest, LlmResponse, OpenAiLlm, ScriptedLlm, ToolSpec
from tests.conftest import caller_headers


def test_scripted_llm_returns_steps_in_order_and_records_requests():
    llm = ScriptedLlm(["một", LlmResponse(content="hai")])
    first = llm.complete(LlmRequest(messages=[ChatMessage("user", "a")]))
    second = llm.complete(LlmRequest(messages=[ChatMessage("user", "b")], purpose="verify"))
    assert (first.content, second.content) == ("một", "hai")
    assert len(llm.requests) == 2 and len(llm.calls_for("verify")) == 1


def test_scripted_llm_fails_loudly_when_script_runs_out():
    with pytest.raises(AssertionError, match="hết kịch bản"):
        ScriptedLlm([]).complete(LlmRequest(messages=[]))


def test_scripted_llm_policy_answers_from_request():
    llm = ScriptedLlm(policy=lambda request: LlmResponse(content=request.messages[-1].content.upper()))
    assert llm.complete(LlmRequest(messages=[ChatMessage("user", "xin chào")])).content == "XIN CHÀO"


class FakeCompletions:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.arguments = result, error, None

    def create(self, **arguments):
        self.arguments = arguments
        if self.error:
            raise self.error
        return self.result


def fake_client(completions):
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


def test_openai_llm_sends_schema_tools_and_parses_tool_calls():
    call = SimpleNamespace(id="c1", function=SimpleNamespace(name="get_my_wallet", arguments='{"x": 1}'))
    completion = SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=None, tool_calls=[call]))],
        usage=SimpleNamespace(prompt_tokens=10, completion_tokens=3), model="m")
    completions = FakeCompletions(result=completion)
    llm = OpenAiLlm(api_key="k", client=fake_client(completions))
    response = llm.complete(LlmRequest(messages=[ChatMessage("user", "ví")], model="m",
                                       tools=[ToolSpec("get_my_wallet", "Xem ví", {"type": "object"})],
                                       response_schema={"type": "object"}))
    assert response.tool_calls[0].name == "get_my_wallet" and response.tool_calls[0].arguments == {"x": 1}
    assert (response.tokens_in, response.tokens_out) == (10, 3)
    assert completions.arguments["store"] is False  # không cho nhà cung cấp lưu dữ liệu
    assert completions.arguments["response_format"]["json_schema"]["strict"] is True


def test_openai_llm_maps_timeout_to_retryable_error():
    error = openai.APITimeoutError(request=httpx.Request("POST", "https://example.invalid"))
    llm = OpenAiLlm(api_key="k", client=fake_client(FakeCompletions(error=error)))
    with pytest.raises(LlmError) as raised:
        llm.complete(LlmRequest(messages=[]))
    assert raised.value.retryable and raised.value.timeout


def test_chat_returns_503_when_llm_disabled(settings):
    client = TestClient(create_app(settings))
    response = client.post("/v1/chat", json={"message": "xin chào"}, headers=caller_headers())
    assert response.status_code == 503


def test_chat_requires_internal_token(settings):
    client = TestClient(create_app(settings, llm=ScriptedLlm(["x"])))
    headers = caller_headers() | {"X-Internal-Token": "sai"}
    assert client.post("/v1/chat", json={"message": "hi"}, headers=headers).status_code == 401


def test_chat_rejects_staff_roles(settings):
    client = TestClient(create_app(settings, llm=ScriptedLlm(["x"])))
    response = client.post("/v1/chat", json={"message": "hi"}, headers=caller_headers(role="EMPLOYEE"))
    assert response.status_code == 403


def test_chat_answers_with_scripted_llm(settings):
    llm = ScriptedLlm(["Chào bạn!"])
    client = TestClient(create_app(settings, llm=llm))
    response = client.post("/v1/chat", json={"message": "xin chào"}, headers=caller_headers())
    assert response.status_code == 200 and response.json()["answer"] == "Chào bạn!"
    assert llm.requests[0].messages[-1].content == "xin chào"
