"""Chia tập dev / test theo **tài liệu** (thiết kế 13 mục 13.9): khoảng 60 / 40, câu cùng tài liệu luôn cùng phía.

Chia theo băm `doc_id` (tất định, không phụ thuộc thứ tự file): chạy lại luôn ra cùng kết quả. Thêm tài liệu mới
không làm đổi phía của tài liệu cũ.

    .venv/Scripts/python -m eval.make_splits          # ghi eval/datasets/SPLITS.yaml

Quy tắc dùng (13 mục 13.9): **dev** để hiệu chỉnh ngưỡng, sửa prompt; **test** chỉ để báo cáo và cổng hồi quy —
không xem lỗi từng câu của test để sửa. Buộc phải sửa vì một câu test → chuyển câu đó sang dev và ghi lại.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import yaml

from eval.hit_at_k import DEFAULT_CSV, load_questions

SPLITS_FILE = Path(__file__).resolve().parent / "datasets" / "SPLITS.yaml"
DEV_PERCENT = 60


def split_of(doc_id: str) -> str:
    bucket = int(hashlib.sha256(doc_id.encode("utf-8")).hexdigest(), 16) % 100
    return "dev" if bucket < DEV_PERCENT else "test"


def build_splits(csv_path: Path = DEFAULT_CSV) -> dict:
    doc_ids = sorted({question.doc_id for question in load_questions(csv_path)})
    return {
        "rule": f"sha256(doc_id) % 100 < {DEV_PERCENT} → dev, còn lại → test (13 mục 13.9)",
        "documents": {"dev": [d for d in doc_ids if split_of(d) == "dev"],
                      "test": [d for d in doc_ids if split_of(d) == "test"]},
        "moved_to_dev": [],  # câu test buộc phải xem để sửa → chuyển sang đây kèm lý do
    }


def load_splits(path: Path = SPLITS_FILE) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    splits = build_splits()
    SPLITS_FILE.write_text(yaml.safe_dump(splits, allow_unicode=True, sort_keys=False), encoding="utf-8")
    print(f"dev: {len(splits['documents']['dev'])} tài liệu, test: {len(splits['documents']['test'])} tài liệu")


if __name__ == "__main__":
    main()
