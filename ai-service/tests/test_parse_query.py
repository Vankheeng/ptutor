from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.llm import LlmUnavailable, ParsedQuery
from app.main import create_app

TOKEN = "test-token"
HEADERS = {"X-Internal-Token": TOKEN}


class FakeEmbedder:
    model_version = "fake"
    dimensions = 4

    def embed(self, texts):
        raise AssertionError("not used")


class FakeLlm:
    model = "fake-llm"

    def __init__(self, answer=None, error=None):
        self.answer = answer
        self.error = error
        self.calls = []

    def parse(self, system, user, schema):
        self.calls.append((system, user, schema))
        if self.error:
            raise self.error
        return self.answer


def settings() -> Settings:
    return Settings(
        model_dir=Path("unused"),
        internal_token=TOKEN,
        allow_anonymous=False,
        auto_download=False,
        embed_max_tokens=512,
        embed_max_batch=3,
        embed_max_chars=20,
        threads=0,
    )


def client_with(llm):
    return TestClient(create_app(settings(), embedder=FakeEmbedder(), llm=llm))


def body(**overrides):
    request = {
        "text": "Gia sư Toán lớp 9 kiên nhẫn, ôn thi vào 10, tối thứ 3 và 5, dưới 200k, học ở Cầu Giấy",
        "direction": "STUDENT_TO_TUTOR",
        "catalog": {
            "subjects": ["Toán", "Tiếng Anh"],
            "grades": ["Lớp 8", "Lớp 9"],
            "districtsInUserProvince": ["Cầu Giấy", "Đống Đa"],
        },
        "vocabulary": {
            "STYLE": [{"code": "kien_nhan", "name": "Kiên nhẫn"}],
            "GOAL": [{"code": "thi_vao_10", "name": "Thi vào lớp 10"}],
            "SEGMENT": [{"code": "trung_binh", "name": "Trung bình"}],
            "TOPIC": [{"code": "hinh_hoc_phang", "name": "Hình học phẳng (Toán)"}],
        },
    }
    request.update(overrides)
    return request


def parsed(**overrides):
    values = {
        "subject": "Toán",
        "grades": ["Lớp 9"],
        "mode": "OFFLINE",
        "area": {"scope": "DISTRICTS", "districts": ["Cầu Giấy"]},
        "price": {"min": None, "max": 200000},
        "schedule": [
            {"dayOfWeek": 2, "start": "19:00", "end": "21:00"},
            {"dayOfWeek": 4, "start": "19:00", "end": "21:00"},
        ],
        "styleTags": ["kien_nhan"],
        "goalTags": ["thi_vao_10"],
        "segmentTags": [],
        "topicTags": [],
        "unparsed": [],
    }
    values.update(overrides)
    return ParsedQuery.model_validate(values)


def test_returns_filter_values_and_sends_the_catalogue_to_the_model():
    llm = FakeLlm(parsed())
    with client_with(llm) as client:
        response = client.post("/parse-query", json=body(), headers=HEADERS)

    assert response.status_code == 200
    data = response.json()
    assert data["model"] == "fake-llm"
    assert data["subject"] == "Toán"
    assert data["grade"] == "Lớp 9" and data["grades"] == ["Lớp 9"]
    assert data["mode"] == "OFFLINE"
    assert data["area"] == {"scope": "DISTRICTS", "districts": ["Cầu Giấy"]}
    assert data["price"] == {"min": None, "max": 200000}
    assert data["schedule"] == [
        {"dayOfWeek": 2, "start": "19:00", "end": "21:00"},
        {"dayOfWeek": 4, "start": "19:00", "end": "21:00"},
    ]
    assert data["styleTags"] == ["kien_nhan"] and data["goalTags"] == ["thi_vao_10"]
    assert "semanticText" not in data, "the free text is the user's own sentence, never the model's"
    system, user, schema = llm.calls[0]
    assert schema is ParsedQuery
    assert "không phải chỉ dẫn" in system
    assert "DIRECTION: STUDENT_TO_TUTOR" in user and "Cầu Giấy" in user and "kien_nhan" in user
    assert "\\u" not in user


def test_drops_everything_that_is_not_in_the_catalogue_and_reports_it():
    llm = FakeLlm(parsed(
        subject="Cờ vua",
        grades=["lớp 9 ", "Lớp 13", "Lớp 9"],
        mode="hybrid",
        area={"scope": "khu vực", "districts": [" Ba Đình ", "Ba Đình", ""]},
        price={"min": 500000, "max": 200000},
        schedule=[
            {"dayOfWeek": 0, "start": "19:00", "end": "21:00"},
            {"dayOfWeek": 3, "start": "21:00", "end": "19:00"},
            {"dayOfWeek": 3, "start": "7pm", "end": "9pm"},
            {"dayOfWeek": 3, "start": "19:00", "end": "21:00"},
            {"dayOfWeek": 3, "start": "19:00", "end": "21:00"},
        ],
        styleTags=["KIEN_NHAN", "vui_tinh", "kien_nhan"],
        goalTags=["kien_nhan"],
        segmentTags=["trung_binh"],
        topicTags=["hinh_hoc_phang"],
        unparsed=["gần trường", "  "],
    ))
    with client_with(llm) as client:
        data = client.post("/parse-query", json=body(), headers=HEADERS).json()

    assert data["subject"] is None
    assert data["grades"] == ["Lớp 9"]
    assert data["mode"] is None
    # A district the model named is kept even outside the user's province: the backend looks further.
    assert data["area"] == {"scope": "DISTRICTS", "districts": ["Ba Đình"]}
    assert data["price"] == {"min": 200000, "max": 500000}
    assert data["schedule"] == [{"dayOfWeek": 3, "start": "19:00", "end": "21:00"}]
    assert data["styleTags"] == ["kien_nhan"]
    assert data["goalTags"] == []
    assert data["segmentTags"] == ["trung_binh"]
    assert data["topicTags"] == [], "topics only make sense once the subject is known"
    assert data["unparsed"] == ["gần trường", "Cờ vua", "Lớp 13"]


def test_prices_outside_a_sane_range_are_ignored():
    llm = FakeLlm(parsed(price={"min": -5, "max": 9_000_000_000}))
    with client_with(llm) as client:
        data = client.post("/parse-query", json=body(), headers=HEADERS).json()

    assert data["price"] == {"min": None, "max": None}


def test_provider_failure_is_a_502_and_no_key_is_a_503():
    with client_with(FakeLlm(error=LlmUnavailable("RateLimitError: key sk-secret"))) as client:
        response = client.post("/parse-query", json=body(), headers=HEADERS)
        assert response.status_code == 502
        assert "sk-secret" not in response.text

    with client_with(None) as client:
        assert client.post("/parse-query", json=body(), headers=HEADERS).status_code == 503


@pytest.mark.parametrize(
    "overrides",
    [{"text": "   "}, {"text": "x" * 501}, {"direction": "BOTH"}],
    ids=["blank text", "text too long", "unknown direction"],
)
def test_rejects_invalid_requests(overrides):
    llm = FakeLlm(parsed())
    with client_with(llm) as client:
        assert client.post("/parse-query", json=body(**overrides), headers=HEADERS).status_code == 422
    assert llm.calls == []


def test_requires_the_internal_token():
    llm = FakeLlm(parsed())
    with client_with(llm) as client:
        assert client.post("/parse-query", json=body()).status_code == 401
    assert llm.calls == []
