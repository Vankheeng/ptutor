"""Test M8: AgentRunner và từng middleware (thiết kế 02 mục 2.3, 13 mục 13.1 T0)."""

import json
import uuid

import pytest

from app.agent.compose import Composer
from app.agent.harness import build_runner
from app.agent.middleware.base import Middleware
from app.agent.middleware.budget import BudgetMiddleware
from app.agent.middleware.context_injection import ContextInjectionMiddleware
from app.agent.middleware.input_guard import InputGuardMiddleware
from app.agent.middleware.model_call import ModelCallMiddleware
from app.agent.middleware.persistence import _safe_args
from app.agent.runner import AgentRunner
from app.agent.state import Caller, Decision, HistoryTurn, ToolCallRecord, TurnError, TurnState
from app.db.conversations import MemoryConversationStore, TurnRecord
from app.kb.routes import RouteCatalog
from app.kb.tokens import ApproxTokenCounter
from app.providers.llm import LlmError, LlmRequest, LlmResponse, LlmToolCall, ScriptedLlm
from app.retrieval.retriever import RetrievalResult
from tests.fakes import FIXED_NOW, make_services
from tests.unit.test_answer_with_citations import actor_json

USER = uuid.UUID("11111111-1111-1111-1111-000000000001")
OTHER = uuid.UUID("22222222-2222-2222-2222-000000000002")


def new_state(question="Nạp ví tối thiểu bao nhiêu?", conversation_id=None, user=USER) -> TurnState:
    return TurnState(caller=Caller(user, "STUDENT", "Bearer secret-token"), question=question,
                     page_path="/student/wallet", now=FIXED_NOW, request_id="req-1",
                     conversation_id=conversation_id)


# ------------------------------------------------------------------ runner


class Recorder(Middleware):
    def __init__(self, name, log):
        self.name, self.log = name, log

    def before_agent(self, state):
        self.log.append(f"{self.name}.before_agent")

    def before_model(self, state, request):
        self.log.append(f"{self.name}.before_model")
        return request

    def wrap_model_call(self, state, request, call_next):
        self.log.append(f"{self.name}.wrap>")
        response = call_next(request)
        self.log.append(f"{self.name}.wrap<")
        return response

    def after_model(self, state, response):
        self.log.append(f"{self.name}.after_model")
        return Decision.accept()

    def after_agent(self, state, result):
        self.log.append(f"{self.name}.after_agent")
        return result


def test_runner_calls_hooks_in_registration_order_and_wraps_outer_first():
    log: list[str] = []
    composer = Composer({})
    runner = AgentRunner(ScriptedLlm([actor_json()]), [Recorder("A", log), Recorder("B", log)], composer,
                         lambda state: LlmRequest(messages=[]))
    state = new_state()

    class FinishWithOutput(Middleware):
        def after_model(self, state, response):
            from app.agent.output_schema import parse_actor_output
            state.output = parse_actor_output(response.content)
            return Decision.accept()

    runner._middlewares.append(FinishWithOutput())
    runner.run(state)
    assert log == ["A.before_agent", "B.before_agent", "A.before_model", "B.before_model",
                   "A.wrap>", "B.wrap>", "B.wrap<", "A.wrap<", "A.after_model", "B.after_model",
                   "A.after_agent", "B.after_agent"]


def test_runner_stops_when_before_agent_sets_final():
    class StopEarly(Middleware):
        def before_agent(self, state):
            state.final = Composer({}).abstain(state, "TEST")

    llm = ScriptedLlm([])
    result = AgentRunner(llm, [StopEarly()], Composer({}), lambda s: LlmRequest(messages=[])).run(new_state())
    assert result.fallback_reason == "TEST" and llm.requests == []


def test_runner_turns_model_outage_into_busy_answer():
    def down(request):
        raise LlmError("down", retryable=False)

    result = AgentRunner(ScriptedLlm([down]), [], Composer({}), lambda s: LlmRequest(messages=[])).run(new_state())
    assert result.fallback_reason == "MODEL_ERROR" and "thử lại" in result.answer


# ------------------------------------------------------------------ từng middleware


def test_input_guard_rejects_empty_and_too_long():
    guard = InputGuardMiddleware(MemoryConversationStore(), max_chars=1000)
    for text in ("   ", "x" * 1001):
        with pytest.raises(TurnError) as raised:
            guard.before_agent(new_state(text))
        assert raised.value.code == "INVALID_MESSAGE"


def test_input_guard_masks_personal_data():
    state = new_state("Số tôi 0912345678, email a@b.vn, nạp ví thế nào?")
    InputGuardMiddleware(MemoryConversationStore()).before_agent(state)
    assert "0912345678" not in state.safe_question and "a@b.vn" not in state.safe_question


def _store_with_conversation(owner=USER, ended=False):
    store = MemoryConversationStore()
    saved = store.save_turn(TurnRecord(owner, "STUDENT", None, "", "câu đầu", "", "đáp", "POLICY", "FULL", None,
                                       {}, [], [], "m", "v1", now=FIXED_NOW))
    if ended:
        store.end(saved.conversation_id, FIXED_NOW)
    return store, saved.conversation_id


def test_input_guard_hides_other_users_conversation_as_404():
    store, conversation_id = _store_with_conversation(owner=OTHER)
    with pytest.raises(TurnError) as raised:
        InputGuardMiddleware(store).before_agent(new_state(conversation_id=conversation_id))
    assert raised.value.status == 404


def test_input_guard_rejects_ended_conversation_with_409():
    store, conversation_id = _store_with_conversation(ended=True)
    with pytest.raises(TurnError) as raised:
        InputGuardMiddleware(store).before_agent(new_state(conversation_id=conversation_id))
    assert (raised.value.status, raised.value.code) == (409, "CONVERSATION_ENDED")


def test_budget_blocks_tool_calls_over_limit(settings):
    budget = BudgetMiddleware(settings, Composer({}))
    state = new_state()
    budget.before_agent(state)
    state.budget.tool_calls = settings.max_tool_calls
    record = budget.wrap_tool_call(state, LlmToolCall("1", "get_my_wallet", {}),
                                   lambda call: pytest.fail("không được gọi tool"))
    assert record.blocked_reason == "BUDGET"


def test_budget_stops_before_model_when_exhausted(settings):
    budget = BudgetMiddleware(settings, Composer({}))
    state = new_state()
    budget.before_agent(state)
    state.budget.model_calls = settings.max_model_calls
    budget.before_model(state, LlmRequest(messages=[]))
    assert state.final is not None and state.final.fallback_reason == "BUDGET"


def test_model_call_retries_once_on_retryable_error():
    attempts = []

    def flaky(request):
        attempts.append(1)
        if len(attempts) == 1:
            raise LlmError("timeout", retryable=True, timeout=True)
        return LlmResponse(content="ok")

    middleware = ModelCallMiddleware(timeout_s=15, max_output_tokens=500)
    response = middleware.wrap_model_call(new_state(), LlmRequest(messages=[], max_output_tokens=9999), flaky)
    assert response.content == "ok" and len(attempts) == 2


def test_context_injection_abstains_without_context_and_tools(settings):
    services = make_services(settings)
    middleware = ContextInjectionMiddleware(services.routes, ApproxTokenCounter(), Composer({}))
    state = new_state()
    state.retrieval = RetrievalResult(no_context=True)
    middleware.before_model(state, LlmRequest(messages=[]))
    assert state.final is not None and state.final.fallback_reason == "NO_CONTEXT"


def test_context_injection_drops_oldest_history_first(settings):
    middleware = ContextInjectionMiddleware(RouteCatalog([]), ApproxTokenCounter(), Composer({}),
                                            history_budget=60)
    state = new_state()
    state.retrieval = RetrievalResult(no_context=False)
    state.history = [HistoryTurn(f"câu hỏi số {i} " + "chữ " * 20, "trả lời " * 10) for i in range(5)]
    request = middleware.before_model(state, LlmRequest(messages=[]))
    contents = " ".join(message.content for message in request.messages)
    assert "câu hỏi số 4" in contents and "câu hỏi số 0" not in contents
    assert state.layer_tokens["history_turns_dropped"] >= 1
    assert request.messages[0].role == "system"


def test_safe_args_keep_ids_drop_free_text():
    assert _safe_args({"contractId": "3b2e-11", "status": "ACTIVE", "note": "gọi 0912 345 678 nhé", "page": 0}) == {
        "contractId": "3b2e-11", "status": "ACTIVE", "page": 0}


# ------------------------------------------------------------------ cả harness


def test_full_turn_is_persisted_with_trace_and_without_token(settings):
    services = make_services(settings, ScriptedLlm([actor_json()]))
    result = build_runner(services).run(new_state("Nạp ví tối thiểu bao nhiêu? SĐT 0912345678"))
    record = services.conversations.saved[-1]
    assert result.conversation_id is not None and result.grounded == "FULL"
    assert "0912345678" not in record.question
    names = {span["name"]: span["status"] for span in record.trace["spans"]}
    assert names["actor#1"] == "OK" and names["verify"] == "SKIPPED" and names["persist"] == "OK"
    assert record.trace["model_calls"] == 1 and len(record.trace["config_hash"]) == 12
    assert "secret-token" not in json.dumps(record.__dict__, default=str)
    assert any(candidate["in_context"] for candidate in record.candidates)


def test_tool_call_without_tools_is_blocked_and_answered(settings):
    tool_step = LlmResponse(tool_calls=[LlmToolCall("c1", "get_my_wallet", {})])
    services = make_services(settings, ScriptedLlm([tool_step, actor_json()]))
    result = build_runner(services).run(new_state())
    record: ToolCallRecord = services.conversations.saved[-1].tool_logs[0]
    assert record["blocked_reason"] == "NOT_ALLOWED" and result.grounded == "FULL"
