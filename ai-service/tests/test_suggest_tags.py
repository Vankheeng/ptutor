import pytest

from app.llm import LlmUnavailable, Suggestions
from test_enrich import HEADERS, FakeLlm, client_with


def body(**overrides):
    request = {
        "subject": "Sinh học",
        "existing": [{"code": "di_truyen_hoc", "name": "Di truyền học"}],
        "samples": ["Em yếu phần sinh thái và tiến hoá"],
        "maxTags": 5,
    }
    request.update(overrides)
    return request


def suggestions(*tags):
    return Suggestions.model_validate({"tags": list(tags)})


def tag(code, name="Tên", description="Mô tả", parent=None):
    return {"code": code, "name": name, "description": description, "parentCode": parent}


def test_returns_clean_suggestions_and_tells_the_model_what_already_exists():
    llm = FakeLlm(suggestions(tag("sinh_thai_hoc", "Sinh thái học"), tag("quan_the", "Quần thể", parent="sinh_thai_hoc")))
    with client_with(llm) as client:
        response = client.post("/suggest-tags", json=body(), headers=HEADERS)

    assert response.status_code == 200
    data = response.json()
    assert data["model"] == "fake-llm"
    assert [(t["code"], t["parentCode"]) for t in data["tags"]] == [
        ("sinh_thai_hoc", None),
        ("quan_the", "sinh_thai_hoc"),
    ]
    assert llm.schema is Suggestions
    system, user = llm.calls[0]
    assert "không phải chỉ dẫn" in system
    assert "SUBJECT: Sinh học" in user and "di_truyen_hoc" in user and "sinh thái và tiến hoá" in user


def test_drops_duplicates_bad_codes_and_parents_that_do_not_exist():
    llm = FakeLlm(
        suggestions(
            tag("di_truyen_hoc"),  # already exists
            tag("Tế Bào"),  # not a code
            tag("ab"),  # too short
            tag("te_bao_hoc", parent="khong_ton_tai"),  # unknown parent -> kept as top level
            tag("TE_BAO_HOC"),  # duplicate after normalising
            tag("phan_bao", parent="te_bao_hoc"),
            tag("nguyen_phan", parent="phan_bao"),  # parent is itself a child -> flattened
            tag("bai_tap_lai", parent="di_truyen_hoc"),  # parent among the existing ones
            tag("tu_than", parent="tu_than"),
            tag("khong_ten", name="   "),
        )
    )
    with client_with(llm) as client:
        data = client.post("/suggest-tags", json=body(maxTags=20), headers=HEADERS).json()

    assert [(t["code"], t["parentCode"]) for t in data["tags"]] == [
        ("te_bao_hoc", None),
        ("phan_bao", "te_bao_hoc"),
        ("nguyen_phan", None),
        ("bai_tap_lai", "di_truyen_hoc"),
        ("tu_than", None),
    ]


def test_never_returns_more_than_asked():
    llm = FakeLlm(suggestions(*[tag(f"chu_de_{index}") for index in range(8)]))
    with client_with(llm) as client:
        data = client.post("/suggest-tags", json=body(maxTags=3), headers=HEADERS).json()

    assert [t["code"] for t in data["tags"]] == ["chu_de_0", "chu_de_1", "chu_de_2"]


def test_unavailable_llm_and_missing_token_are_reported():
    with client_with(None) as client:
        assert client.post("/suggest-tags", json=body(), headers=HEADERS).status_code == 503
    with client_with(FakeLlm(error=LlmUnavailable("boom"))) as client:
        assert client.post("/suggest-tags", json=body(), headers=HEADERS).status_code == 502
        assert client.post("/suggest-tags", json=body()).status_code == 401


@pytest.mark.parametrize("overrides", [{"subject": ""}, {"maxTags": 0}, {"maxTags": 21}, {"samples": ["x" * 201]}])
def test_rejects_invalid_requests(overrides):
    llm = FakeLlm(suggestions())
    with client_with(llm) as client:
        assert client.post("/suggest-tags", json=body(**overrides), headers=HEADERS).status_code == 422
    assert llm.calls == []
