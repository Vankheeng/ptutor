"""Test M10 (phía assistant-service): API hội thoại, đánh giá, gợi ý; cách ly giữa người dùng (09 mục 9.3)."""

from fastapi.testclient import TestClient

from app.main import create_app
from app.providers.llm import ScriptedLlm
from tests.conftest import caller_headers
from tests.fakes import make_services
from tests.unit.test_answer_with_citations import actor_json

OTHER = "22222222-2222-2222-2222-000000000002"


def client_with_one_turn(settings):
    client = TestClient(create_app(services=make_services(settings, ScriptedLlm(policy=lambda r: _ok()))))
    body = client.post("/v1/chat", json={"message": "Nạp ví tối thiểu bao nhiêu?"}, headers=caller_headers()).json()
    return client, body["conversationId"], body["messageId"]


def _ok():
    from app.providers.llm import LlmResponse
    return LlmResponse(content=actor_json())


def test_list_and_read_own_conversation(settings):
    client, conversation_id, message_id = client_with_one_turn(settings)
    listing = client.get("/v1/conversations", headers=caller_headers()).json()
    assert listing["totalElements"] == 1 and listing["content"][0]["id"] == conversation_id
    messages = client.get(f"/v1/conversations/{conversation_id}/messages", headers=caller_headers()).json()
    assert [message["sender"] for message in messages["messages"]] == ["USER", "ASSISTANT"]
    assert messages["messages"][1]["id"] == message_id
    assert messages["messages"][1]["citations"][0]["index"] == 1


def test_other_user_sees_nothing(settings):
    client, conversation_id, message_id = client_with_one_turn(settings)
    other = caller_headers(user_id=OTHER)
    assert client.get("/v1/conversations", headers=other).json()["totalElements"] == 0
    assert client.get(f"/v1/conversations/{conversation_id}/messages", headers=other).status_code == 404
    assert client.delete(f"/v1/conversations/{conversation_id}", headers=other).status_code == 404
    assert client.post(f"/v1/messages/{message_id}/feedback", json={"rating": -1}, headers=other).status_code == 404


def test_deleted_conversation_disappears_immediately(settings):
    client, conversation_id, _ = client_with_one_turn(settings)
    assert client.delete(f"/v1/conversations/{conversation_id}", headers=caller_headers()).status_code == 200
    assert client.get(f"/v1/conversations/{conversation_id}/messages", headers=caller_headers()).status_code == 404
    again = client.post("/v1/chat", json={"message": "Tiếp tục", "conversationId": conversation_id},
                        headers=caller_headers())
    assert again.status_code == 404
    assert client.get("/v1/conversations", headers=caller_headers()).json()["totalElements"] == 0


def test_feedback_overwrites_and_masks_comment(settings):
    client, conversation_id, message_id = client_with_one_turn(settings)
    client.post(f"/v1/messages/{message_id}/feedback", json={"rating": 1}, headers=caller_headers())
    response = client.post(f"/v1/messages/{message_id}/feedback",
                           json={"rating": -1, "reason": "WRONG", "comment": "gọi tôi 0912345678"},
                           headers=caller_headers())
    assert response.status_code == 200
    store = client.app.state.services.conversations
    import uuid
    saved = store.feedback[uuid.UUID(message_id)]
    assert saved["rating"] == -1 and "0912345678" not in saved["comment"]


def test_feedback_rating_must_be_plus_or_minus_one(settings):
    client, _, message_id = client_with_one_turn(settings)
    assert client.post(f"/v1/messages/{message_id}/feedback", json={"rating": 5},
                       headers=caller_headers()).status_code == 422


def test_suggestions_by_role_and_page(settings):
    client = TestClient(create_app(services=make_services(settings)))
    body = client.get("/v1/suggestions", params={"role": "STUDENT", "pagePath": "/student/wallet"},
                      headers=caller_headers()).json()
    assert body["suggestions"][0] == "Nạp ví tối thiểu bao nhiêu?"


def test_excerpt_for_support_request_is_owned_bounded_and_redacted(settings):
    """Thiết kế 14 mục 14.10: /excerpt từ chối hội thoại của người khác; đoạn trích đã che thông tin cá nhân."""
    client = TestClient(create_app(services=make_services(settings, ScriptedLlm(policy=lambda r: _ok()))))
    first = client.post("/v1/chat", json={"message": "Số của tôi 0912345678, nạp ví thế nào?"},
                        headers=caller_headers()).json()
    conversation_id = first["conversationId"]
    last_id = first["messageId"]
    for index in range(7):
        last_id = client.post("/v1/chat", json={"message": f"Câu hỏi {index}", "conversationId": conversation_id},
                              headers=caller_headers()).json()["messageId"]

    url = f"/v1/conversations/{conversation_id}/excerpt"
    short = client.get(url, params={"upTo": first["messageId"]}, headers=caller_headers()).json()
    assert [turn["role"] for turn in short["turns"]] == ["user", "assistant"]
    assert "0912345678" not in short["turns"][0]["content"]

    full = client.get(url, params={"upTo": last_id}, headers=caller_headers()).json()
    assert len(full["turns"]) == 12 and full["turns"][-1]["role"] == "assistant"

    assert client.get(url, params={"upTo": last_id}, headers=caller_headers(user_id=OTHER)).status_code == 404
    unknown = client.get(url, params={"upTo": OTHER}, headers=caller_headers())
    assert unknown.status_code == 404 and unknown.json()["detail"] == "MESSAGE_NOT_FOUND"
