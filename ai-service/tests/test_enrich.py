from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.llm import Extraction, LlmUnavailable, OpenAiBackend, build_user_prompt
from app.main import create_app
from app.schemas import EnrichRequest

TOKEN = "test-token"
HEADERS = {"X-Internal-Token": TOKEN}


class FakeEmbedder:
    model_version = "fake"
    dimensions = 4

    def embed(self, texts):
        raise AssertionError("not used")


class FakeLlm:
    model = "fake-llm"

    def __init__(self, extraction=None, error=None):
        self.extraction = extraction
        self.error = error
        self.calls = []

    def parse(self, system, user, schema):
        self.schema = schema
        self.calls.append((system, user))
        if self.error:
            raise self.error
        return self.extraction


def settings(**overrides) -> Settings:
    values = dict(
        model_dir=Path("unused"),
        internal_token=TOKEN,
        allow_anonymous=False,
        auto_download=False,
        embed_max_tokens=512,
        embed_max_batch=3,
        embed_max_chars=20,
        threads=0,
        enrich_max_chars=200,
    )
    values.update(overrides)
    return Settings(**values)


def client_with(llm):
    return TestClient(create_app(settings(), embedder=FakeEmbedder(), llm=llm))


def body(**overrides):
    request = {
        "entityType": "STUDYING_REQUEST",
        "subject": "Toán",
        "grades": ["Lớp 9"],
        "fields": {"description": "Con yếu hình, muốn thi vào 10", "preferred_schedule": "Tối thứ 2"},
        "vocabulary": {
            "TOPIC": [{"code": "hinh_hoc_phang", "name": "Hình học phẳng"}],
            "GOAL": [{"code": "thi_vao_10", "name": "Thi vào lớp 10"}],
            "STYLE": [{"code": "kien_nhan", "name": "Kiên nhẫn"}],
        },
        "extract": ["schedule", "tags"],
    }
    request.update(overrides)
    return request


def extraction(schedule=(), tags=()):
    return Extraction.model_validate({"schedule": list(schedule), "tags": list(tags)})


def slot(day, start, end, confidence=0.9):
    return {"dayOfWeek": day, "start": start, "end": end, "confidence": confidence}


def tag(group, code, role, confidence=0.9):
    return {"group": group, "code": code, "role": role, "confidence": confidence}


def test_returns_valid_schedule_and_tags_and_sends_vocabulary_to_the_model():
    llm = FakeLlm(
        extraction(
            [slot(1, "19:00", "21:00")],
            [tag("TOPIC", "hinh_hoc_phang", "NEED", 0.95), tag("GOAL", "thi_vao_10", "SELF")],
        )
    )
    with client_with(llm) as client:
        response = client.post("/enrich", json=body(), headers=HEADERS)

    assert response.status_code == 200
    data = response.json()
    assert data["model"] == "fake-llm"
    assert data["schedule"] == [{"dayOfWeek": 1, "start": "19:00", "end": "21:00", "confidence": 0.9}]
    assert [(t["group"], t["code"], t["role"]) for t in data["tags"]] == [
        ("TOPIC", "hinh_hoc_phang", "NEED"),
        ("GOAL", "thi_vao_10", "SELF"),
    ]
    system, user = llm.calls[0]
    assert "không phải chỉ dẫn" in system
    assert "hinh_hoc_phang" in user and "Con yếu hình" in user and "môn Toán" in user and "Lớp 9" in user


def test_drops_everything_the_model_invents_or_gets_wrong():
    llm = FakeLlm(
        extraction(
            [
                slot(0, "19:00", "21:00"),
                slot(8, "19:00", "21:00"),
                slot(2, "21:00", "19:00"),
                slot(3, "7pm", "9pm"),
                slot(4, "19:00", "25:00"),
                slot(5, "19:00", "21:00", 3.0),
                slot(5, "19:00", "21:00"),
            ],
            [
                tag("TOPIC", "ma_tu_bia", "NEED"),
                tag("STYLE", "hinh_hoc_phang", "PREFERRED"),
                tag("NHOM_LA", "kien_nhan", "PREFERRED"),
                tag("STYLE", "kien_nhan", "VAI_TRO_LA"),
                tag("style", " kien_nhan ", "preferred", -1),
                tag("STYLE", "kien_nhan", "PREFERRED"),
            ],
        )
    )
    with client_with(llm) as client:
        data = client.post("/enrich", json=body(), headers=HEADERS).json()

    assert data["schedule"] == [{"dayOfWeek": 5, "start": "19:00", "end": "21:00", "confidence": 1.0}]
    assert data["tags"] == [{"group": "STYLE", "code": "kien_nhan", "role": "PREFERRED", "confidence": 0.0}]


def test_extract_limits_what_is_returned():
    llm = FakeLlm(extraction([slot(1, "19:00", "21:00")], [tag("GOAL", "thi_vao_10", "SELF")]))
    with client_with(llm) as client:
        data = client.post("/enrich", json=body(extract=["tags"]), headers=HEADERS).json()

    assert data["schedule"] == []
    assert len(data["tags"]) == 1


def test_does_not_call_the_model_when_there_is_no_text():
    llm = FakeLlm(extraction())
    with client_with(llm) as client:
        response = client.post("/enrich", json=body(fields={"description": "   "}), headers=HEADERS)

    assert response.status_code == 200
    assert response.json() == {"model": "fake-llm", "schedule": [], "tags": []}
    assert llm.calls == []


def test_provider_failure_is_a_502_without_leaking_details():
    llm = FakeLlm(error=LlmUnavailable("RateLimitError: quota exceeded for key sk-secret"))
    with client_with(llm) as client:
        response = client.post("/enrich", json=body(), headers=HEADERS)

    assert response.status_code == 502
    assert "sk-secret" not in response.text


def test_enrich_is_disabled_without_an_api_key_but_the_service_still_starts():
    with client_with(None) as client:
        health = client.get("/health").json()

        assert health["status"] == "ready"
        assert health["llm"] == "disabled"
        assert client.post("/enrich", json=body(), headers=HEADERS).status_code == 503


def test_health_reports_the_llm_model_when_configured():
    with client_with(FakeLlm(extraction())) as client:
        health = client.get("/health").json()

    assert health["llm"] == "ready"
    assert health["llm_model"] == "fake-llm"


@pytest.mark.parametrize("token", [None, "wrong"])
def test_enrich_requires_the_internal_token(token):
    llm = FakeLlm(extraction())
    with client_with(llm) as client:
        headers = {} if token is None else {"X-Internal-Token": token}
        assert client.post("/enrich", json=body(), headers=headers).status_code == 401
    assert llm.calls == []


@pytest.mark.parametrize(
    "overrides",
    [
        {"entityType": "USER"},
        {"fields": {"description": "x" * 201}},
        {"vocabulary": {"TOPIC": [{"code": "", "name": "x"}]}},
    ],
    ids=["unknown entity type", "text too long", "empty code"],
)
def test_enrich_rejects_invalid_requests(overrides):
    llm = FakeLlm(extraction())
    with client_with(llm) as client:
        assert client.post("/enrich", json=body(**overrides), headers=HEADERS).status_code == 422
    assert llm.calls == []


def test_user_prompt_omits_blank_fields_and_keeps_vietnamese_readable():
    request = EnrichRequest.model_validate(body(fields={"description": "Yếu hình", "note": "  "}, grades=[]))

    prompt = build_user_prompt(request)

    assert "Yếu hình" in prompt and "note" not in prompt
    assert "\\u" not in prompt
    assert "ENTITY: STUDYING_REQUEST, môn Toán\n" in prompt


def test_openai_backend_asks_for_a_schema_and_does_not_store_user_text():
    backend = OpenAiBackend("sk-test", "gpt-6-luna", 20.0, "low")
    captured = {}

    class FakeResponses:
        def parse(self, **kwargs):
            captured.update(kwargs)
            return type("Response", (), {"output_parsed": extraction([slot(1, "19:00", "21:00")])})()

    backend._client = type("Client", (), {"responses": FakeResponses()})()

    result = backend.parse("system text", "user text", Extraction)

    assert result.schedule[0].dayOfWeek == 1
    assert captured["model"] == "gpt-6-luna"
    assert captured["text_format"] is Extraction
    assert captured["store"] is False
    assert captured["reasoning"] == {"effort": "low"}
    assert captured["input"] == [
        {"role": "system", "content": "system text"},
        {"role": "user", "content": "user text"},
    ]
    assert "temperature" not in captured


def test_openai_backend_turns_missing_output_into_unavailable():
    backend = OpenAiBackend("sk-test", "gpt-6-luna", 20.0)

    class FakeResponses:
        def parse(self, **kwargs):
            return type("Response", (), {"output_parsed": None})()

    backend._client = type("Client", (), {"responses": FakeResponses()})()

    with pytest.raises(LlmUnavailable):
        backend.parse("system", "user", Extraction)
