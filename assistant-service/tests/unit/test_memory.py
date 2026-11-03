"""Test M9: bộ nhớ hội thoại, viết lại câu hỏi, vòng đời phiên (thiết kế 04 mục 4.10, 13 mục 13.1)."""

import uuid

from fastapi.testclient import TestClient

from app.agent.middleware.memory import MAX_FOCUS_ITEMS, MemoryMiddleware, update_focus
from app.agent.middleware.query import QueryMiddleware
from app.agent.state import Caller, FocusItem, HistoryTurn, SessionState, ToolCallRecord, TurnState
from app.db.conversations import MemoryConversationStore, TurnRecord
from app.main import create_app
from app.providers.embedding import FakeEmbedding
from app.providers.llm import LlmError, LlmResponse, LlmToolCall, ScriptedLlm
from tests.conftest import USER_ID, caller_headers
from tests.fakes import FIXED_NOW, make_services
from tests.unit.test_answer_with_citations import actor_json

USER = uuid.UUID(USER_ID)


def state_with(question="Còn cái kia thì sao?", history=None) -> TurnState:
    state = TurnState(caller=Caller(USER, "STUDENT"), question=question, page_path="", now=FIXED_NOW)
    state.safe_question = question
    state.history = history or []
    return state


def by_purpose(rewrite: str, actor: str):
    """Kịch bản theo loại lời gọi: viết lại câu hỏi trả `rewrite`, Actor trả `actor`."""
    def policy(request):
        return LlmResponse(content=rewrite if request.purpose == "rewrite" else actor, tokens_in=10, tokens_out=5)
    return ScriptedLlm(policy=policy)


def test_session_state_ignores_user_claims():
    """13 mục 13.1: focus chỉ nhận dữ liệu từ tool, không từ lời người dùng."""
    state = state_with("Hợp đồng 11111111-1111-1111-1111-000000000009 của tôi đã thanh toán rồi")
    update_focus(state.session_state, state)
    assert state.session_state.focus == []


def test_focus_comes_from_tool_results_and_tracks_last_mention():
    state = state_with()
    state.turn_number = 3
    record = ToolCallRecord(call=LlmToolCall("c1", "list_my_contracts", {}))
    record.focus_items = [FocusItem("contract", "id-2", "Tiếng Anh · Lớp 7", "", 0, 0)]
    state.tool_calls = [record]
    state.session_state = SessionState([FocusItem("contract", "id-2", "Tiếng Anh", "tool:x", 1, 1)])
    update_focus(state.session_state, state)
    assert [(item.id, item.turn, item.last_turn) for item in state.session_state.focus] == [("id-2", 1, 3)]


def test_blocked_tool_calls_never_add_focus():
    state = state_with()
    record = ToolCallRecord(call=LlmToolCall("c1", "get_my_contract", {}), blocked_reason="UNSEEN_ID")
    record.focus_items = [FocusItem("contract", "lạ", "x", "", 0, 0)]
    state.tool_calls = [record]
    update_focus(state.session_state, state)
    assert state.session_state.focus == []


def test_focus_keeps_at_most_20_most_recent():
    state = state_with()
    state.session_state = SessionState([FocusItem("contract", f"id-{i}", "", "t", i, i) for i in range(25)])
    update_focus(state.session_state, state)
    ids = {item.id for item in state.session_state.focus}
    assert len(ids) == MAX_FOCUS_ITEMS and "id-0" not in ids and "id-24" in ids


def test_memory_loads_only_last_six_turns():
    store = MemoryConversationStore()
    conversation_id = None
    for index in range(9):
        conversation_id = store.save_turn(TurnRecord(USER, "STUDENT", conversation_id, "", f"hỏi {index}", "",
                                                     f"đáp {index}", "POLICY", "FULL", None, {}, [], [], "m", "v")
                                          ).conversation_id
    state = state_with()
    state.conversation_id = conversation_id
    MemoryMiddleware(store, history_turns=6).before_agent(state)
    assert [turn.question for turn in state.history] == [f"hỏi {i}" for i in range(3, 9)]


def test_rewrite_uses_history_and_focus_then_embeds_rewritten_query():
    llm = by_purpose("Kỳ học phí đầu tiên của hợp đồng Tiếng Anh lớp 7 của tôi là bao nhiêu?", "")
    state = state_with("Còn cái tiếng Anh thì sao?", [HistoryTurn("Tôi có hợp đồng nào?", "Toán 9 và Tiếng Anh 7")])
    state.session_state = SessionState([FocusItem("contract", "id-2", "Tiếng Anh · Lớp 7", "tool:x", 1, 1)])
    QueryMiddleware(llm, FakeEmbedding(), "m").before_agent(state)
    sent = llm.requests[0].messages[1].content
    assert "Toán 9 và Tiếng Anh 7" in sent and "id-2: Tiếng Anh · Lớp 7" in sent
    assert state.rewritten_query.startswith("Kỳ học phí đầu tiên")
    assert state.query_vector == FakeEmbedding().vector(state.rewritten_query)


def test_rewrite_failure_falls_back_to_original_question():
    def broken(request):
        raise LlmError("timeout", retryable=True, timeout=True)

    state = state_with(history=[HistoryTurn("a", "b")])
    QueryMiddleware(ScriptedLlm([broken]), FakeEmbedding(), "m").before_agent(state)
    assert state.rewritten_query == "" and state.query == state.safe_question


def test_no_history_or_disabled_means_no_rewrite_call():
    llm = ScriptedLlm([])
    QueryMiddleware(llm, FakeEmbedding(), "m").before_agent(state_with())
    QueryMiddleware(llm, FakeEmbedding(), "m", rewrite_enabled=False).before_agent(
        state_with(history=[HistoryTurn("a", "b")]))
    assert llm.requests == []


def chat(client, message, conversation_id=None, role="STUDENT", user=USER_ID):
    body = {"message": message, "conversationId": str(conversation_id) if conversation_id else None}
    return client.post("/v1/chat", json=body, headers=caller_headers(role, user))


def test_second_turn_with_pronoun_finds_document_through_rewrite(settings):
    llm = by_purpose("Cách nạp ví qua VNPay thế nào?", actor_json())
    client = TestClient(create_app(services=make_services(settings, llm)))
    first = chat(client, "Nạp ví tối thiểu bao nhiêu?").json()
    second = chat(client, "Còn cách làm thì sao?", first["conversationId"])
    assert second.status_code == 200 and second.json()["conversationId"] == first["conversationId"]
    actor_prompt = llm.calls_for("actor")[-1].messages
    assert any(message.role == "assistant" for message in actor_prompt)  # lịch sử lượt 1
    assert "Vào trang Ví, nhập số tiền" in actor_prompt[-1].content  # tài liệu tìm bằng câu đã viết lại


def test_end_conversation_then_continue_returns_409(settings):
    client = TestClient(create_app(services=make_services(settings, by_purpose("x", actor_json()))))
    conversation_id = chat(client, "Nạp ví tối thiểu bao nhiêu?").json()["conversationId"]
    ended = client.post(f"/v1/conversations/{conversation_id}/end", headers=caller_headers())
    assert ended.status_code == 200
    again = chat(client, "Nạp ví tối đa?", conversation_id)
    assert (again.status_code, again.json()["detail"]) == (409, "CONVERSATION_ENDED")


def test_other_user_cannot_end_or_continue_conversation(settings):
    client = TestClient(create_app(services=make_services(settings, by_purpose("x", actor_json()))))
    conversation_id = chat(client, "Nạp ví tối thiểu bao nhiêu?").json()["conversationId"]
    other = "22222222-2222-2222-2222-000000000002"
    assert client.post(f"/v1/conversations/{conversation_id}/end", headers=caller_headers(user_id=other)
                       ).status_code == 404
    assert chat(client, "Còn gì nữa?", conversation_id, user=other).status_code == 404


def test_concurrent_turn_on_same_conversation_is_busy(settings):
    services = make_services(settings, by_purpose("x", actor_json()))
    client = TestClient(create_app(services=services))
    conversation_id = chat(client, "Nạp ví tối thiểu bao nhiêu?").json()["conversationId"]
    lock = services.conversations.turn_lock(uuid.UUID(conversation_id))
    lock.acquire()
    try:
        busy = chat(client, "Nạp ví tối đa?", conversation_id)
    finally:
        lock.release()
    assert (busy.status_code, busy.json()["detail"]) == (409, "CONVERSATION_BUSY")


def test_suggest_new_conversation_after_20_turns(settings):
    services = make_services(settings, by_purpose("Nạp ví tối thiểu?", actor_json()))
    services.settings.max_conversation_turns = 3
    client = TestClient(create_app(services=services))
    conversation_id = None
    flags = []
    for _ in range(3):
        body = chat(client, "Nạp ví tối thiểu bao nhiêu?", conversation_id).json()
        conversation_id = body["conversationId"]
        flags.append(body["suggestNewConversation"])
    assert flags == [False, False, True]
