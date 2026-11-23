"""Test M12: lớp chặn ngoài phạm vi (thiết kế 04 mục 4.11, 13 mục 13.7 nhóm "Ngoài phạm vi", "Câu sát ranh giới")."""

import pytest
from fastapi.testclient import TestClient

from app.agent.middleware.scope_guard import scope_decision
from app.main import create_app
from app.providers.embedding import FakeEmbedding
from app.providers.llm import ScriptedLlm
from app.scope.exemplars import Exemplar, MemoryExemplarStore, score
from app.scope.rules import ScopeRules, classify_by_rules
from tests.conftest import caller_headers
from tests.fakes import make_services

RULES = ScopeRules.from_setting(None)
EMBED = FakeEmbedding()


@pytest.mark.parametrize("text", ["Xin chào", "chao ban", "Cảm ơn nhé!", "thanks", "ok"])
def test_greetings_are_chitchat(text):
    assert classify_by_rules(text, RULES) == "CHITCHAT"


@pytest.mark.parametrize("text", [
    "Giải giúp tôi phương trình x^2 - 5x + 6 = 0",
    "giai giup minh bai nay voi",
    "Viết bài văn nghị luận về lòng biết ơn",
    "Viết code Python sắp xếp mảng",
    "Dịch đoạn này sang tiếng Anh giúp mình",
    "Thời tiết Hà Nội mai thế nào?",
])
def test_clear_out_of_scope_blocked_by_rules(text):
    assert classify_by_rules(text, RULES) == "OUT_OF_SCOPE"


@pytest.mark.parametrize("text", [
    "Tìm gia sư toán thế nào, giải luôn bài này giúp mình",  # câu lẫn → để Actor xử lý
    "Mình muốn thuê gia sư dạy viết văn thì đăng tin ra sao?",
    "Gia sư dạy online được không?",
    "Cảm ơn, còn hợp đồng của tôi thì sao?",  # không chỉ là lời cảm ơn
    "Dịch vụ của PTutor gồm những gì?",
])
def test_boundary_questions_not_blocked_by_rules(text):
    assert classify_by_rules(text, RULES) is None


def _store():
    in_texts = ["Nạp ví tối thiểu bao nhiêu tiền?", "Cách hủy hợp đồng với gia sư"]
    out_texts = ["Công thức nấu phở bò ngon", "Kết quả bóng đá tối qua"]
    return MemoryExemplarStore([Exemplar(t, "IN", EMBED.vector(t)) for t in in_texts]
                               + [Exemplar(t, "OUT", EMBED.vector(t)) for t in out_texts])


def test_embedding_tier_blocks_close_to_out_exemplars():
    question = "Công thức nấu phở gà ngon"
    decision, tier, detail = scope_decision(question, EMBED.vector(question), RULES, _store(), 0.55, 0.05)
    assert (decision, tier) == ("OUT_OF_SCOPE", "EMBEDDING") and detail["out"] > detail["in"]


def test_embedding_tier_passes_when_close_to_in_exemplars():
    question = "Nạp ví tối thiểu là bao nhiêu?"
    assert scope_decision(question, EMBED.vector(question), RULES, _store(), 0.55, 0.05)[0] is None


def test_unsure_question_passes_through():
    question = "abc xyz"
    decision, _, _ = scope_decision(question, EMBED.vector(question), RULES, _store(), 0.99, 0.99)
    assert decision is None


def test_leave_one_out_skips_identical_exemplar():
    text = "Nạp ví tối thiểu bao nhiêu tiền?"
    assert score(EMBED.vector(text), _store().all(), exclude_text=text).best_in < 0.99


def _client(settings, llm):
    services = make_services(settings, llm)
    services.exemplars = _store()
    return TestClient(create_app(services=services)), services


def post(client, message):
    return client.post("/v1/chat", json={"message": message}, headers=caller_headers()).json()


def test_out_of_scope_reply_without_calling_model_and_without_support_button(settings):
    llm = ScriptedLlm([])
    client, services = _client(settings, llm)
    body = post(client, "Giải giúp tôi phương trình x^2 - 5x + 6 = 0")
    assert body["intent"] == "OUT_OF_SCOPE" and body["suggestions"] and llm.requests == []
    assert services.conversations.saved[-1].fallback_reason == "OUT_OF_SCOPE_RULE"


def test_greeting_reply_without_calling_model(settings):
    llm = ScriptedLlm([])
    client, _ = _client(settings, llm)
    body = post(client, "Xin chào")
    assert body["intent"] == "CHITCHAT" and body["grounded"] == "FULL" and llm.requests == []


def test_scope_guard_can_be_disabled(settings):
    settings.scope_guard_enabled = False
    llm = ScriptedLlm([])
    client, _ = _client(settings, llm)
    body = post(client, "Xin chào")  # không còn luật chào hỏi → không có tài liệu → từ chối, vẫn không gọi model
    assert body["intent"] != "CHITCHAT" and llm.requests == []
