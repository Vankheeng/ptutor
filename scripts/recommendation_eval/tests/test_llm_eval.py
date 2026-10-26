import json
from pathlib import Path

import llm_eval


def test_the_independent_set_reads_its_direction_from_side():
    data = {"schedule": [{"text": "t7 sáng", "expected": [[6, "08:00", "11:00"]]}],
            "search": [{"text": "gs toán 9", "expected": {"side": "S", "subject": "Toán", "grades": [9]}},
                       {"text": "tìm học sinh lớp 6", "expected": {"side": "T", "grades": [6]}}]}

    cases = llm_eval.independent_cases(data)

    assert cases["a1_tags"] == []
    assert cases["a1_schedule"] == data["schedule"]
    assert [case["direction"] for case in cases["a2"]] == ["STUDENT_TO_TUTOR", "TUTOR_TO_STUDENT"]
    assert cases["a2"][0]["expected"] == {"subject": "Toán", "grades": [9]}, "side is not a field to score"


def test_the_independent_file_has_the_sets_the_team_sent():
    data = json.loads((Path(llm_eval.__file__).parent / "llm_eval_independent.json").read_text(encoding="utf-8"))
    cases = llm_eval.independent_cases(data)

    assert len(cases["a1_schedule"]) == 13
    assert len(cases["a2"]) == 23
    assert sum(case["direction"] == "TUTOR_TO_STUDENT" for case in cases["a2"]) == 1
    assert all(set(case["expected"]) <= set(llm_eval.A2_FIELDS) for case in cases["a2"]), "unknown field in a label"


def test_rescoring_applies_a_fixed_label_to_the_stored_answers_without_calling_anything():
    labels = {"a1_tags": [], "a1_schedule": [{"text": "t7 sáng", "expected": [[6, "08:00", "11:00"]]}],
              "a2": [{"text": "toán 9 ở Đống Đa", "direction": "STUDENT_TO_TUTOR",
                      "expected": {"subject": "Toán", "grades": [9], "mode": "OFFLINE", "districts": ["Đống Đa"]}},
                     {"text": "câu mới chưa chạy", "direction": "STUDENT_TO_TUTOR", "expected": {}}]}
    stored = {field: None for field in llm_eval.A2_FIELDS}
    stored.update(subject="Toán", grades=[9], mode="OFFLINE", districts=["Đống Đa"], styleTags=[], goalTags=[],
                  segmentTags=[], days=[])
    saved = {"a1_schedule": [{"text": "t7 sáng", "got": [[6, "08:00", "11:00"]]}],
             "a2": [{"text": "toán 9 ở Đống Đa", "got": stored}]}

    (tags, _), (schedule, _), (a2, rows) = llm_eval.rescore(labels, saved)

    assert tags is None
    assert schedule["exact_days"] == 1.0
    assert a2["all_fields_right"] == 1.0, "the fixed label (mode OFFLINE) now matches the stored answer"
    assert [row["text"] for row in rows] == ["toán 9 ở Đống Đa"], "a sentence without a stored answer is left out"
