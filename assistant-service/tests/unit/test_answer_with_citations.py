"""Test M7: trả lời có trích dẫn (thiết kế 04 mục 4.4–4.7)."""

import json

import pytest
from fastapi.testclient import TestClient

from app.agent.answer import ABSTAIN_ANSWER, number_citations, truncate_answer
from app.agent.output_schema import OutputSchemaError, parse_actor_output
from app.main import create_app
from app.providers.llm import ScriptedLlm
from app.retrieval.expand import Section
from tests.conftest import caller_headers
from tests.fakes import make_services


def actor_json(**overrides) -> str:
    body = {"intent": "POLICY", "claims": [{"text": "Nạp tối thiểu 10.000 đ.", "source": "D1"}],
            "answer": "Mỗi lần nạp ví tối thiểu 10.000 đ [D1].", "actions": [], "suggestions": []}
    return json.dumps(body | overrides, ensure_ascii=False)


def post(client, message, role="STUDENT", page=""):
    return client.post("/v1/chat", json={"message": message, "pagePath": page}, headers=caller_headers(role))


def test_answer_has_numbered_citation_pointing_to_section(settings):
    llm = ScriptedLlm([actor_json()])
    client = TestClient(create_app(services=make_services(settings, llm)))
    body = post(client, "Nạp ví tối thiểu bao nhiêu?").json()
    assert body["answer"] == "Mỗi lần nạp ví tối thiểu 10.000 đ [1]."
    assert body["citations"][0] | {"index": 1} == {"index": 1, "documentTitle": "Nạp ví",
                                                    "section": "Hạn mức nạp ví", "docKey": "vi-nap-tien",
                                                    "anchor": "han-muc-nap-vi"}
    assert body["grounded"] == "FULL"
    prompt = llm.requests[0].messages[1].content
    assert '<doc id="D1" title="Nạp ví"' in prompt and "<question>Nạp ví tối thiểu bao nhiêu?</question>" in prompt
    assert llm.requests[0].response_schema is not None


def test_no_context_abstains_without_calling_model(settings):
    llm = ScriptedLlm([])  # gọi model là test hỏng
    client = TestClient(create_app(services=make_services(settings, llm)))
    body = post(client, "Chính sách bảo mật dữ liệu của PTutor quy định gì?").json()
    assert body["answer"] == ABSTAIN_ANSWER and body["grounded"] == "NONE"
    assert llm.requests == []


def test_student_does_not_get_tutor_documents_in_prompt(settings):
    llm = ScriptedLlm([actor_json(claims=[], answer="Mình chưa có thông tin.")])
    client = TestClient(create_app(services=make_services(settings, llm)))
    post(client, "nộp chứng chỉ tối đa bao nhiêu MB, nạp ví tối thiểu")
    assert "Nộp chứng chỉ" not in llm.requests[0].messages[1].content


def test_invented_source_is_retried_then_abstains(settings):
    """Nguồn bịa (D9) bị bắt bằng code (chạy cả khi tắt verifier): sinh lại 1 lần, vẫn bịa → từ chối."""
    invented = actor_json(claims=[{"text": "x", "source": "D9"}], answer="Nạp tối thiểu 50.000 đ [D9].")
    llm = ScriptedLlm([invented, invented])
    client = TestClient(create_app(services=make_services(settings, llm)))
    body = post(client, "Nạp ví tối thiểu bao nhiêu?").json()
    assert body["answer"] == ABSTAIN_ANSWER and body["grounded"] == "NONE" and body["citations"] == []
    assert "D9" in llm.requests[1].messages[-1].content  # góp ý nêu đúng khẳng định sai


def test_invalid_json_twice_abstains(settings):
    llm = ScriptedLlm(["không phải JSON", "vẫn không phải JSON"])
    client = TestClient(create_app(services=make_services(settings, llm)))
    assert post(client, "Nạp ví tối thiểu bao nhiêu?").json()["answer"] == ABSTAIN_ANSWER
    assert "<feedback>" in llm.requests[1].messages[-1].content  # lần 2 có góp ý


def test_invalid_json_then_valid_answer(settings):
    client = TestClient(create_app(services=make_services(settings, ScriptedLlm(["{hỏng", actor_json()]))))
    assert post(client, "Nạp ví tối thiểu bao nhiêu?").json()["grounded"] == "FULL"


def test_out_of_scope_answer_uses_template_and_suggestions(settings):
    llm = ScriptedLlm([actor_json(intent="OUT_OF_SCOPE", claims=[], answer="Không")])
    client = TestClient(create_app(services=make_services(settings, llm)))
    body = post(client, "nạp ví giúp mình giải bài toán", page="/student/wallet").json()
    assert body["intent"] == "OUT_OF_SCOPE" and "Nạp ví tối thiểu bao nhiêu?" in body["suggestions"]


def test_unknown_route_actions_are_dropped(settings):
    actions = [{"label": "Ví", "route": "/student/wallet"}, {"label": "Lạ", "route": "https://evil.example"},
               {"label": "Gia sư", "route": "/tutor/wallet"}]
    client = TestClient(create_app(services=make_services(settings, ScriptedLlm([actor_json(actions=actions)]))))
    body = post(client, "Nạp ví tối thiểu bao nhiêu?").json()
    # Link ra ngoài luôn bị bỏ; kiểm tra theo danh sách route và vai trò là việc của RouteGuard (M13).
    assert "https://evil.example" not in [action["route"] for action in body["actions"]]


def test_message_too_long_is_invalid(settings):
    client = TestClient(create_app(services=make_services(settings, ScriptedLlm([]))))
    assert post(client, "a" * 1001).status_code == 400


def test_number_citations_orders_by_first_appearance_and_dedupes():
    sections = [Section(label=f"D{i}", doc_key=f"k{i}", title=f"T{i}", heading_path=f"T{i} > M{i}", anchor="a",
                        content="", token_count=1) for i in (1, 2, 3)]
    text, citations = number_citations("A [D3]. B [D1] [D1]. C [D3].", sections, ["D3", "D2"])
    assert text == "A [1]. B [2]. C [1]."
    assert [citation.docKey for citation in citations] == ["k3", "k1", "k2"]


def test_parse_accepts_code_fence_and_rejects_bad_intent():
    assert parse_actor_output("```json\n" + actor_json() + "\n```").intent == "POLICY"
    with pytest.raises(OutputSchemaError):
        parse_actor_output(actor_json(intent="SMALLTALK"))


def test_truncate_answer_cuts_at_paragraph():
    answer = "Đoạn một.\n\n" + "x" * 1300
    assert truncate_answer(answer) == "Đoạn một."
