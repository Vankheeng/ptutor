"""Chuyển bộ câu hỏi mẫu `knowledge-base/_eval/cau-hoi-mau.csv` (222 dòng, 220 câu) về định dạng case của thiết kế
13 mục 13.4, ghi `eval/datasets/golden/<features>.yaml`.

Quy tắc chuẩn hoá (ghi lại để người duyệt kiểm):
- `split` theo `SPLITS.yaml` (chia theo tài liệu, 13 mục 13.9).
- `expect.docs = [doc_id]` (tài liệu phải nằm trong ngữ cảnh) — trừ `NO_ANSWER` mà tài liệu cố ý không có câu trả lời:
  vẫn giữ `docs` để đo truy xuất, nhưng kỳ vọng chính là **không khẳng định sai** (`grounded ∈ {NONE, PARTIAL}`).
- `expect.intent`: POLICY / HOWTO giữ nguyên; FAQ, ERROR → POLICY (hỏi đáp về quy định / lỗi); NO_ANSWER → không chấm.
- Câu có đáp án (POLICY, HOWTO, FAQ, ERROR): `grounded ∈ {FULL, PARTIAL}` — từ chối một câu có đáp án là trượt
  (thêm ở chặng 8: trước đó câu không có con số trong `must_include` vẫn "đạt" khi trợ lý từ chối).
- `must_include`: chỉ các **con số** (số tiền, phần trăm, số ngày…) rút từ cột `y_bat_buoc` — cột này là mô tả ý, không
  phải cụm chính xác, nên phần còn lại để chấm tay (`reference`, `key_points`).
- `role` theo cột `vai_tro`; `page` = route đầu tiên của tài liệu khớp vai trò (để có ưu tiên ngữ cảnh như thật).

    .venv/Scripts/python -m eval.build_datasets
"""

from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

import yaml

from app.kb.parser import find_document_files, parse_file
from eval.hit_at_k import DEFAULT_CSV, DEFAULT_KB, load_questions
from eval.make_splits import load_splits

GOLDEN_DIR = Path(__file__).resolve().parent / "datasets" / "golden"
NUMBER = re.compile(r"\d{1,3}(?:\.\d{3})+|\d+(?:[.,]\d+)?\s?%|\b\d+\s?(?:ngày|giờ|phút|tháng|buổi)\b")
USER_BY_ROLE = {"STUDENT": "student_a", "TUTOR": "tutor_a"}
SCENARIO_BY_ROLE = {"STUDENT": "student_two_contracts", "TUTOR": "tutor_basic"}


def _intent(kind: str) -> str | None:
    return {"POLICY": "POLICY", "HOWTO": "HOWTO", "FAQ": "POLICY", "ERROR": "POLICY"}.get(kind)


def build() -> dict[str, list[dict]]:
    documents = {doc.doc_key: doc for doc in (parse_file(path) for path in find_document_files(DEFAULT_KB))}
    dev_docs = set(load_splits()["documents"]["dev"])
    counters: dict[str, int] = defaultdict(int)
    by_feature: dict[str, list[dict]] = defaultdict(list)
    for question in load_questions(DEFAULT_CSV):
        document = documents[question.doc_id]
        feature = document.features[0] if document.features else "general"
        counters[question.doc_id] += 1
        routes = [route for route in document.routes if route.startswith(f"/{question.role.lower()}")]
        expect: dict = {"docs": [question.doc_id], "reference": question.required.strip(),
                        "key_points": [part.strip() for part in re.split(r"[;]", question.required) if part.strip()]}
        if question.kind == "NO_ANSWER":
            expect["grounded_in"] = ["NONE", "PARTIAL"]
        else:
            expect["intent"] = _intent(question.kind)
            expect["grounded_in"] = ["FULL", "PARTIAL"]
            expect["must_include"] = sorted({match.group(0).strip() for match in NUMBER.finditer(question.required)})
        by_feature[feature].append({
            "id": f"{question.doc_id}-{counters[question.doc_id]:02d}",
            "split": "dev" if question.doc_id in dev_docs else "test",
            "category": question.kind,
            "role": question.role,
            "user": USER_BY_ROLE[question.role],
            "scenario": SCENARIO_BY_ROLE[question.role],
            "page": routes[0] if routes else "",
            "question": question.text,
            "features": list(document.features),
            "expect": expect,
            "tags": ["source:cau-hoi-mau.csv"],
        })
    return by_feature


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    GOLDEN_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for feature, cases in sorted(build().items()):
        (GOLDEN_DIR / f"{feature}.yaml").write_text(
            "# Sinh tự động từ knowledge-base/_eval/cau-hoi-mau.csv bằng eval/build_datasets.py — không sửa tay.\n"
            + yaml.safe_dump(cases, allow_unicode=True, sort_keys=False, width=120), encoding="utf-8")
        total += len(cases)
    print(f"Đã ghi {total} case vào {GOLDEN_DIR}")


if __name__ == "__main__":
    main()
