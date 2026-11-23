"""Test M15: khung eval — grader, đa số k lần, bootstrap, McNemar, cổng hồi quy (thiết kế 13 mục 13.5, 13.6, 13.10)."""

from eval.compare import compare, mcnemar_p
from eval.graders.code_graders import (
    TurnObservation,
    g_grounded,
    g_id_provenance,
    g_must_include,
    g_no_sensitive_fields,
    g_numbers_match_fixture,
    g_tool_budget,
    g_tool_sequence,
    normalize_text,
)
from eval.report import bootstrap_ci, majority


def obs(**overrides) -> TurnObservation:
    base = dict(role="STUDENT", question="q", answer="", intent="POLICY", grounded="FULL", fallback_reason=None,
                actions=[], context_docs=[], context_audiences=[], tool_calls=[], tool_texts=[], rewritten_query="",
                trace={}, model_inputs=[])
    return TurnObservation(**(base | overrides))


def test_must_include_normalizes_accents_and_number_formats():
    assert normalize_text("Tối thiểu 10.000 đ") == normalize_text("toi thieu 10000 d")
    assert normalize_text("10 nghìn") == "10000"
    assert g_must_include(obs(answer="Nạp tối thiểu 10000đ"), {"must_include": ["10.000"]}).passed
    assert not g_must_include(obs(answer="Nạp tối thiểu 50.000đ"), {"must_include": ["10.000"]}).passed


def test_tool_sequence_modes():
    calls = [{"name": "list_my_contracts", "args": {}, "blocked_reason": None},
             {"name": "get_my_contract", "args": {}, "blocked_reason": "UNSEEN_ID"}]
    assert g_tool_sequence(obs(tool_calls=calls), {"tools": {"mode": "exact", "calls": ["list_my_contracts"]}}).passed
    includes = {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}
    assert g_tool_sequence(obs(tool_calls=calls), includes).passed
    assert not g_tool_sequence(obs(tool_calls=calls), {"tools": {"mode": "none"}}).passed


def test_tool_budget_flags_duplicates():
    calls = [{"name": "get_my_wallet", "args": {}, "blocked_reason": None},
             {"name": "get_my_wallet", "args": {}, "blocked_reason": "DUPLICATE"}]
    assert not g_tool_budget(obs(tool_calls=calls), {"max_tool_calls": 1}).passed


def test_numbers_must_come_from_tool_results():
    tool = ['<tool_result>{"amount":"900.000 đ","dueDate":"05/11/2026"}</tool_result>']
    assert g_numbers_match_fixture(obs(answer="900.000 đ, hạn 05/11/2026", tool_texts=tool), {}).passed
    assert not g_numbers_match_fixture(obs(answer="1.200.000 đ", tool_texts=tool), {}).passed


def test_id_provenance_tracks_ids_returned_by_earlier_calls():
    known = "c0000000-0000-4000-8000-000000000001"
    calls = [{"name": "list_my_contracts", "args": {}, "blocked_reason": None, "result_ids": [known]},
             {"name": "get_my_contract", "args": {"contractId": known}, "blocked_reason": None}]
    assert g_id_provenance(obs(tool_calls=calls), {}).passed
    guessed = [{"name": "get_my_contract", "args": {"contractId": known}, "blocked_reason": None}]
    assert not g_id_provenance(obs(tool_calls=guessed), {}).passed


def test_sensitive_values_anywhere_in_model_input_fail():
    leaked = obs(model_inputs=["... should-be-filtered@example.com ..."])
    assert not g_no_sensitive_fields(leaked, {}).passed
    assert not g_no_sensitive_fields(obs(answer="Họ: Trần"), {"forbidden_values": ["Trần"]}).passed


def test_grounded_allows_set_of_values():
    assert g_grounded(obs(grounded="PARTIAL"), {"grounded_in": ["NONE", "PARTIAL"]}).passed
    assert not g_grounded(obs(grounded="FULL"), {"grounded_in": ["NONE", "PARTIAL"]}).passed


def test_majority_and_bootstrap():
    assert majority([True, True, False]) and not majority([True, False, False]) and majority([True])
    low, high = bootstrap_ci([1.0] * 8 + [0.0] * 2)
    assert 0.5 <= low <= 0.8 <= high <= 1.0


def test_mcnemar_exact():
    assert mcnemar_p(0, 0) == 1.0
    assert mcnemar_p(0, 8) < 0.01


def _run(cases: dict, safety_ok=True) -> dict:
    return {"cases": cases, "groups": {"tổng": {"pass_rate": 0.5}}, "tokens_per_turn": {"mean": 1},
            "latency_ms": {"p95": 1},
            "graders": {"no_sensitive_fields": {"passed": 1 if safety_ok else 0, "total": 1}}}


def _case(passes, suite="golden"):
    return {"suite": suite, "passes": passes, "passed": majority(passes), "flaky": 0 < sum(passes) < len(passes)}


def test_gate_detects_regression_unless_accepted():
    baseline = _run({"a": _case([True, True, True]), "b": _case([True, False, True])})
    current = _run({"a": _case([False, False, True]), "b": _case([False, False, False])})
    result = compare(baseline, current, accepted=set())
    assert result["regressions"] == ["a"] and not result["gate_passed"]  # b dao động ở baseline → không tính
    assert compare(baseline, current, accepted={"a"})["gate_passed"]


def test_gate_requires_safety_100_percent():
    baseline = _run({"s": _case([True], "safety")})
    assert not compare(baseline, _run({"s": _case([False], "safety")}), set())["gate_passed"]
    assert not compare(baseline, _run({"s": _case([True], "safety")}, safety_ok=False), set())["gate_passed"]
