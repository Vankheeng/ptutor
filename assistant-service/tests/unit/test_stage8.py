"""Test các thay đổi của chặng 8 (đánh giá đầy đủ): sửa harness do eval phát hiện + công cụ eval mới."""

from __future__ import annotations

import csv
import json

from app.agent.middleware.memory import update_focus
from app.agent.middleware.scope_guard import scope_decision
from app.agent.middleware.tool_selection import is_personal_question
from app.agent.state import FocusItem, SessionState
from app.scope.exemplars import ExemplarStore
from app.scope.rules import ScopeRules
from eval.build_lookup_cases import LOOKUP, lookup_cases, split_for
from eval.graders.manual_grading import _passes, score_sheet
from eval.verifier import summarize


class _State:
    """Đủ trường cho `update_focus` (không cần dựng cả TurnState)."""

    def __init__(self, question: str, turn: int):
        self.question = question
        self.turn_number = turn
        self.tool_calls = []


def _contracts() -> SessionState:
    return SessionState(focus=[
        FocusItem("contract", "c1", "Toán · Lớp 9", "tool:list_my_contracts", 1, 1),
        FocusItem("contract", "c2", "Tiếng Anh · Lớp 7", "tool:list_my_contracts", 1, 1),
    ])


def test_focus_puts_item_named_by_user_last():
    session = _contracts()
    update_focus(session, _State("Hợp đồng Toán của tôi thế nào rồi?", 1))
    assert [item.id for item in session.focus] == ["c2", "c1"]  # mục cuối = đang bàn nhất
    session = _contracts()
    update_focus(session, _State("hop dong tieng anh cua toi", 1))
    assert session.focus[-1].id == "c2"


def test_focus_never_adds_items_from_user_text():
    session = _contracts()
    update_focus(session, _State("Hợp đồng Ngữ văn c9 của tôi", 2))
    assert {item.id for item in session.focus} == {"c1", "c2"}


def test_platform_terms_are_never_blocked_by_exemplars():
    rules = ScopeRules.from_setting(None)
    decision, tier, _ = scope_decision("Tôi có thông báo nào mới?", [1.0, 0.0], rules, ExemplarStore(),
                                       tau_in=0.99, delta=0.0)
    assert decision is None and tier == "PLATFORM_TERM"


def test_new_personal_markers():
    assert is_personal_question("Có ai khiếu nại tôi không?")
    assert is_personal_question("rut tien thang nay xong chua")
    assert is_personal_question("Tiền thu nhập buổi dạy gần nhất cộng vào ví chưa?")
    assert not is_personal_question("Nạp ví tối thiểu bao nhiêu?")


def test_lookup_cases_split_by_tool():
    cases = lookup_cases()
    assert len(cases) == len(LOOKUP)
    by_tool: dict[str, set[str]] = {}
    for case in cases:
        by_tool.setdefault(case["tags"][0], set()).add(case["split"])
    assert all(len(splits) == 1 for splits in by_tool.values())  # cùng công cụ → cùng một phía
    assert split_for("x") == split_for("x")


def test_manual_rubric_pass_rule(tmp_path):
    assert _passes([2, 1], False) and not _passes([2, 0], False) and not _passes([1, 1], False)
    assert not _passes([2, 2], True)
    sheet, key = tmp_path / "sheet.csv", tmp_path / "key.json"
    key.write_text(json.dumps({"M001": {"config": "baseline", "case": "a", "run": "r", "n_key_points": 2},
                               "M002": {"config": "e2", "case": "a", "run": "r2", "n_key_points": 2}}),
                   encoding="utf-8")
    with sheet.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["blind_id", "kp1", "kp2", "contradiction", "unsupported_claims"])
        writer.writeheader()
        writer.writerow({"blind_id": "M001", "kp1": "2", "kp2": "1", "contradiction": "", "unsupported_claims": "0"})
        writer.writerow({"blind_id": "M002", "kp1": "2", "kp2": "2", "contradiction": "1", "unsupported_claims": "1"})
    result = score_sheet(sheet, key)
    assert result["baseline"]["pass_rate"] == 1.0
    assert result["e2"]["pass_rate"] == 0.0 and result["e2"]["unsupported_claims"] == 1


def test_verifier_metrics():
    rows = [{"label": "SUPPORTED", "predicted": "SUPPORTED", "kind": "verbatim"},
            {"label": "SUPPORTED", "predicted": "UNSUPPORTED", "kind": "reformatted"},
            {"label": "UNSUPPORTED", "predicted": "UNSUPPORTED", "kind": "altered_number"},
            {"label": "PARTIAL", "predicted": "SUPPORTED", "kind": "partial"}]
    summary = summarize(rows)
    assert summary["catch_recall"] == 0.5 and summary["false_reject_rate"] == 0.5
    assert summary["by_kind"]["partial"] == {"SUPPORTED": 1}


def test_cost_cap_and_shared_ledger_stop_before_next_call(tmp_path):
    import pytest

    from app.providers.llm import ChatMessage, LlmRequest, LlmResponse, ScriptedLlm
    from eval.runner import BudgetLedger, GuardedLlm, RunBudgetExceeded

    def costly(_request):
        return LlmResponse(content="{}", tokens_in=1_000_000, tokens_out=0, model="m")

    request = LlmRequest(messages=[ChatMessage("user", "x")], model="m")
    guarded = GuardedLlm(ScriptedLlm(policy=costly), 100, 10**12, price=(0.4, 1.6), max_cost_usd=1.0)
    guarded.complete(request)
    guarded.complete(request)
    guarded.complete(request)  # 1,2 $ sau lần này
    with pytest.raises(RunBudgetExceeded):
        guarded.complete(request)

    path = tmp_path / "ledger.json"
    first = BudgetLedger(path, limit_usd=1.0)
    first.record("run-1", 0.9, 3, 2_250_000, 0)
    second = GuardedLlm(ScriptedLlm(policy=costly), 100, 10**12, price=(0.4, 1.6), ledger=BudgetLedger(path, 99))
    second.complete(request)  # 0,9 + 0 < 1 → được gọi; sau đó 1,3 $
    with pytest.raises(RunBudgetExceeded):
        second.complete(request)
    assert BudgetLedger(path, 99).limit_usd == 1.0  # giữ trần của sổ, không đổi theo lần chạy sau


def test_answer_never_repeats_ids_the_system_did_not_return():
    from app.agent.middleware.route_guard import UNKNOWN_ID, mask_unseen_ids

    known = {"c0000000-0000-4000-8000-000000000001"}
    text = ("Hợp đồng c0000000-0000-4000-8000-000000000001 đang học; "
            "không có hợp đồng 99999999-9999-4999-8999-999999999999.")
    masked = mask_unseen_ids(text, known)
    assert "c0000000-0000-4000-8000-000000000001" in masked
    assert "99999999-9999" not in masked and UNKNOWN_ID in masked


def test_out_of_credit_stops_the_eval_run_instead_of_recording_model_errors():
    """Eval T3: hết credit giữa chừng → 429 insufficient_quota. Trước đây mỗi lượt thành MODEL_ERROR và lần chạy vẫn
    "xong" với kết quả rác; nay lỗi tài khoản là `fatal` và GuardedLlm dừng cả lần chạy."""
    import httpx
    import openai
    import pytest

    from app.providers.llm import ChatMessage, LlmError, LlmRequest, OpenAiLlm, ScriptedLlm
    from eval.runner import GuardedLlm, ProviderUnavailable

    response = httpx.Response(429, request=httpx.Request("POST", "https://api.openai.com/v1/chat/completions"))

    class NoCredit:
        class chat:  # noqa: N801 — giả lập client.chat.completions.create
            class completions:  # noqa: N801
                @staticmethod
                def create(**_kwargs):
                    raise openai.RateLimitError("You have no credits remaining.", response=response,
                                                body={"code": "insufficient_quota"})

    request = LlmRequest(messages=[ChatMessage("user", "x")], model="m")
    with pytest.raises(LlmError) as caught:
        OpenAiLlm("k", client=NoCredit()).complete(request)
    assert caught.value.fatal and not caught.value.retryable

    def fatal(_request):
        raise LlmError("no credits", retryable=False, fatal=True)

    def transient(_request):
        raise LlmError("busy", retryable=True)

    with pytest.raises(ProviderUnavailable):
        GuardedLlm(ScriptedLlm(policy=fatal), 10, 10**6).complete(request)
    with pytest.raises(LlmError):  # lỗi thoáng qua vẫn để ModelCallMiddleware thử lại / trả MODEL_ERROR như cũ
        GuardedLlm(ScriptedLlm(policy=transient), 10, 10**6).complete(request)
