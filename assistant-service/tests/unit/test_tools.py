"""Test M14: công cụ tra cứu — registry, BackendClient, lọc kết quả, ToolGate, LoopGuard, ToolSelection, cả lượt.

Thiết kế 05, 09 (T1 xem dữ liệu người khác, T7 lộ token), 13 mục 13.1 / 13.7.
"""

import json
import uuid

import pytest

from app.agent.harness import build_runner
from app.agent.middleware.loop_guard import LoopGuardMiddleware
from app.agent.middleware.tool_gate import ToolGateMiddleware
from app.agent.middleware.tool_selection import ToolSelectionMiddleware, is_personal_question
from app.agent.state import Caller, FocusItem, SessionState, ToolCallRecord, TurnState
from app.providers.llm import LlmRequest, LlmResponse, LlmToolCall, ScriptedLlm
from app.tools.backend_client import ForbiddenRequest, MockBackendClient
from app.tools.registry import ToolRegistry
from app.tools.result_filter import FilterContext, format_instant, select_items, shape_result
from tests.fakes import CATALOG_FILE, FIXED_NOW, FIXTURES_DIR, make_services
from tests.unit.test_answer_with_citations import actor_json

USER = uuid.UUID("11111111-1111-1111-1111-000000000001")
MATH = "c0000000-0000-4000-8000-000000000001"
ENG = "c0000000-0000-4000-8000-000000000002"
REGISTRY = ToolRegistry.from_file(CATALOG_FILE)


def state(question="Kỳ học phí tiếp theo của tôi khi nào?", role="STUDENT", focus=()) -> TurnState:
    turn = TurnState(caller=Caller(USER, role, "Bearer user-token"), question=question, page_path="", now=FIXED_NOW)
    turn.safe_question = question
    turn.session_state = SessionState(list(focus))
    return turn


def must_not_be_called(call):
    pytest.fail("Không được gọi backend")


# ------------------------------------------------------------------ registry và BackendClient


def test_registry_loads_only_ready_get_tools_from_catalog():
    # 36 công cụ có sẵn trên dev + 2 công cụ yêu cầu hỗ trợ của M17 (thiết kế 14 mục 14.7).
    assert len(REGISTRY.all()) == 38
    assert all(tool.method == "GET" and tool.reversibility == "READ_ONLY" for tool in REGISTRY.all())
    support = REGISTRY.get("list_my_support_requests")
    assert support is not None and support.path == "/api/v1/users/me/support-requests"
    assert "requestId" in REGISTRY.get("get_my_support_request").parameters_schema()["properties"]


def test_fixed_source_payment_is_hidden_from_model():
    tool = REGISTRY.get("list_my_transactions")
    assert tool.fixed_params["source"] == "PAYMENT"
    assert "source" not in tool.parameters_schema()["properties"]


def test_backend_client_refuses_writes_and_paths_outside_allowlist():
    client = MockBackendClient(REGISTRY, FIXTURES_DIR, "student_two_contracts")
    with pytest.raises(ForbiddenRequest):
        client.request("POST", "list_my_contracts", "/api/v1/users/me/contracts", {}, "Bearer t")
    with pytest.raises(ForbiddenRequest):
        client.get("x", "/api/v1/admin/users", {}, "Bearer t")
    assert client.calls == []


# ------------------------------------------------------------------ lọc kết quả


def test_filter_drops_personal_fields_and_formats_values():
    client = MockBackendClient(REGISTRY, FIXTURES_DIR, "student_two_contracts")
    data = client.get("list_my_contracts", "/api/v1/users/me/contracts", {}, "t").data
    shaped = shape_result(REGISTRY.get("list_my_contracts"), data, FilterContext(str(USER)))
    text = json.dumps(shaped.data, ensure_ascii=False)
    assert "should-be-filtered" not in text and "Nguyễn" not in text and "Trần" not in text
    first = shaped.data["items"][0]
    assert first["price"] == "1.200.000 đ" and first["status_label"] == "Đang hoạt động"
    assert first["startDate"] == "05/09/2026" and first["createdByMe"] is True
    assert shaped.data["total"] == 2 and shaped.data["counts"] == {"ACTIVE": 2}
    assert {item.id for item in shaped.focus_items} == {MATH, ENG}
    assert next(item for item in shaped.focus_items if item.id == ENG).label == "Tiếng Anh · Lớp 7"


def test_time_zones_follow_field_labels():
    from zoneinfo import ZoneInfo
    vietnam = ZoneInfo("Asia/Ho_Chi_Minh")
    assert format_instant("2026-11-03T02:15:00", "UTC", vietnam) == "03/11/2026 09:15"  # now(clock) → đổi
    assert format_instant("2026-11-03T09:15:00", "BACKEND_JVM", vietnam) == "03/11/2026 09:15"  # JVM giờ VN
    assert format_instant("2026-11-03T02:15:00", "BACKEND_JVM", ZoneInfo("UTC")) == "03/11/2026 09:15"


def test_installments_unpaid_nearest_due_first_and_lessons_pending_first():
    items = [{"status": "PAID", "dueDate": "2026-09-01"}, {"status": "PENDING", "dueDate": "2026-12-01"},
             {"status": "PENDING", "dueDate": None}, {"status": "PENDING", "dueDate": "2026-11-01"}]
    assert [i["dueDate"] for i in select_items(items, "installments_due_first", 10)] == [
        "2026-11-01", "2026-12-01", None, "2026-09-01"]
    lessons = [{"status": "COMPLETED", "date": "2026-10-01"}] + [
        {"status": "PENDING_CONFIRMATION", "date": f"2026-10-{d:02d}"} for d in range(1, 13)] + [
        {"status": "SCHEDULED", "date": "2026-10-25"}]
    chosen = select_items(lessons, "lessons_pending_first", 10)
    assert len(chosen) == 12 and all(i["status"] == "PENDING_CONFIRMATION" for i in chosen)  # giữ đủ nhóm chờ


# ------------------------------------------------------------------ ToolGate, LoopGuard


def test_tool_gate_blocks_unseen_id():
    """Đúng ví dụ ở thiết kế 13 mục 13.1: focus rỗng → seen_ids rỗng → chặn, không gọi backend."""
    call = LlmToolCall("1", "get_my_contract", {"contractId": "3b2e1f00-0000-4000-8000-000000000000"})
    result = ToolGateMiddleware(REGISTRY).wrap_tool_call(state(), call, must_not_be_called)
    assert result.blocked_reason == "UNSEEN_ID" and "list_my_contracts" in result.result_text


def test_tool_gate_allows_id_from_focus_and_strips_fixed_params():
    seen: list[LlmToolCall] = []
    gate = ToolGateMiddleware(REGISTRY)
    turn = state(focus=[FocusItem("contract", MATH, "Toán", "tool:list_my_contracts", 1, 1)])
    gate.wrap_tool_call(turn, LlmToolCall("1", "list_my_payment_installments", {"contractId": MATH}),
                        lambda call: seen.append(call) or ToolCallRecord(call=call))
    gate.wrap_tool_call(turn, LlmToolCall("2", "list_my_transactions", {"source": "WALLET_TRANSACTION"}),
                        lambda call: seen.append(call) or ToolCallRecord(call=call))
    assert seen[0].arguments == {"contractId": MATH} and "source" not in seen[1].arguments


@pytest.mark.parametrize(("role", "tool", "arguments", "reason"), [
    ("TUTOR", "list_my_payment_installments", {"contractId": MATH}, "NOT_ALLOWED"),  # tool chỉ của học viên
    ("STUDENT", "delete_my_contract", {}, "NOT_ALLOWED"),  # không có tool ghi
    ("STUDENT", "list_my_contracts", {"status": "ALL_USERS"}, "INVALID_ARGS"),
    ("STUDENT", "get_my_contract", {"contractId": "không-phải-uuid"}, "INVALID_ARGS"),
])
def test_tool_gate_rejections(role, tool, arguments, reason):
    result = ToolGateMiddleware(REGISTRY).wrap_tool_call(state(role=role), LlmToolCall("1", tool, arguments),
                                                         must_not_be_called)
    assert result.blocked_reason == reason


def test_loop_guard_blocks_same_call_twice_in_a_turn():
    turn = state()
    turn.tool_calls.append(ToolCallRecord(call=LlmToolCall("1", "get_my_wallet", {})))
    result = LoopGuardMiddleware().wrap_tool_call(turn, LlmToolCall("2", "get_my_wallet", {}), must_not_be_called)
    assert result.blocked_reason == "DUPLICATE"


def test_loop_guard_removes_tools_when_budget_used():
    turn = state()
    turn.budget.tool_calls = turn.budget.max_tool_calls
    request = LoopGuardMiddleware().before_model(turn, LlmRequest(messages=[], tools=["x"]))  # type: ignore[list-item]
    assert request.tools == []


# ------------------------------------------------------------------ ToolSelection


def test_policy_only_question_gets_no_tools():
    assert not is_personal_question("Nạp ví tối thiểu bao nhiêu?")  # "ví tối" không phải "ví tôi"
    assert is_personal_question("vi toi con bao nhieu")  # gõ không dấu vẫn nhận ra
    assert is_personal_question("Ví của tôi còn bao nhiêu?")
    request = ToolSelectionMiddleware(REGISTRY).before_model(state("Nạp ví tối thiểu bao nhiêu?"),
                                                             LlmRequest(messages=[]))
    assert request.tools == []


def test_personal_question_gets_at_most_six_tools_including_list_tool():
    request = ToolSelectionMiddleware(REGISTRY).before_model(state("Kỳ học phí tiếp theo của tôi khi nào?"),
                                                             LlmRequest(messages=[]))
    names = [tool.name for tool in request.tools]
    assert 0 < len(names) <= 6 and "list_my_payment_installments" in names and "list_my_contracts" in names
    assert all(REGISTRY.get(name) and "STUDENT" in REGISTRY.get(name).roles for name in names)


# ------------------------------------------------------------------ cả lượt LOOKUP (13 mục 4.8 "Tra cứu")


def call(name, **arguments):
    return LlmResponse(tool_calls=[LlmToolCall(f"c-{name}", name, arguments)])


def test_lookup_turn_two_tools_then_answer_with_numbers_from_tool(settings):
    answer = actor_json(intent="LOOKUP",
                        claims=[{"text": "Kỳ học phí tiếp theo 1.200.000 đ, hạn 20/10/2026.",
                                 "source": "tool:list_my_payment_installments"}],
                        answer="Kỳ học phí tiếp theo của hợp đồng Toán là 1.200.000 đ, hạn 20/10/2026.",
                        actions=[{"label": "Xem hợp đồng", "route": f"/student/contracts/{MATH}"}])
    llm = ScriptedLlm([call("list_my_contracts"), call("list_my_payment_installments", contractId=MATH), answer])
    services = make_services(settings, llm, tools_scenario="student_two_contracts")
    result = build_runner(services).run(state("Kỳ học phí tiếp theo của hợp đồng Toán của tôi khi nào?"))
    assert result.grounded == "FULL" and result.actions[0]["route"].endswith(MATH)
    saved = services.conversations.saved[-1]
    assert [log["tool_name"] for log in saved.tool_logs] == ["list_my_contracts", "list_my_payment_installments"]
    assert saved.tool_logs[1]["args"] == {"contractId": MATH} and saved.tool_logs[1]["result_items"] == 3
    assert {item["id"] for item in saved.session_state["focus"]} >= {MATH, ENG}
    everything = json.dumps(saved.__dict__, default=str, ensure_ascii=False)
    assert "user-token" not in everything and "should-be-filtered" not in everything  # T7, lọc trường
    second_prompt = llm.requests[1].messages
    assert any(message.role == "tool" and "Tiếng Anh" in message.content for message in second_prompt)


def test_number_not_in_tool_result_is_caught(settings):
    wrong = actor_json(intent="LOOKUP", claims=[{"text": "Kỳ tiếp theo 2.000.000 đ.",
                                                 "source": "tool:list_my_contracts"}], answer="2.000.000 đ")
    llm = ScriptedLlm([call("list_my_contracts"), wrong, wrong])
    services = make_services(settings, llm, tools_scenario="student_two_contracts")
    result = build_runner(services).run(state("Hợp đồng của tôi giá bao nhiêu?"))
    assert result.grounded == "NONE"


def test_guessed_contract_id_is_blocked_even_if_model_insists(settings):
    """13 mục 13.7 "Đoán ID": không bịa nội dung hợp đồng của người khác."""
    other = "99999999-9999-4999-8999-999999999999"
    final = actor_json(intent="LOOKUP", claims=[], answer="Mình không tìm thấy hợp đồng này trong tài khoản của bạn.")
    llm = ScriptedLlm([call("get_my_contract", contractId=other), final])
    services = make_services(settings, llm, tools_scenario="student_two_contracts")
    build_runner(services).run(state(f"Cho tôi xem hợp đồng {other} của tôi"))
    assert services.conversations.saved[-1].tool_logs[0]["blocked_reason"] == "UNSEEN_ID"
    assert services.tool_executor._client.calls == []  # backend không hề bị gọi


def test_backend_down_answers_without_inventing_data(settings):
    final = actor_json(intent="LOOKUP", claims=[], answer="Tạm thời chưa tra cứu được ví của bạn.")
    llm = ScriptedLlm([call("get_my_wallet"), final])
    services = make_services(settings, llm, tools_scenario="student_tool_down")
    result = build_runner(services).run(state("Ví của tôi còn bao nhiêu?"))
    saved = services.conversations.saved[-1]
    assert saved.tool_logs[0]["http_status"] == 503 and "Tạm thời chưa tra cứu" in llm.requests[1].messages[-1].content
    assert result.answer.startswith("Tạm thời")


def test_repeated_wallet_checks_call_backend_once(settings):
    """13 mục 13.7 "Ép gọi lặp": ≤ 1 lời gọi get_my_wallet."""
    final = actor_json(intent="LOOKUP", claims=[], answer="Số dư ví của bạn là 250.000 đ.")
    llm = ScriptedLlm([call("get_my_wallet"), call("get_my_wallet"), call("get_my_wallet"), final])
    services = make_services(settings, llm, tools_scenario="student_two_contracts")
    build_runner(services).run(state("Kiểm tra lại ví của tôi 10 lần"))
    assert [c[0] for c in services.tool_executor._client.calls] == ["get_my_wallet"]
    assert [log["blocked_reason"] for log in services.conversations.saved[-1].tool_logs] == [
        None, "DUPLICATE", "DUPLICATE"]
