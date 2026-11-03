"""Test M3: đọc tài liệu và chia đoạn (thiết kế 03 mục 3.3, 3.5)."""

from pathlib import Path

import pytest

from app.kb.chunker import MAX_CHUNK_TOKENS, chunk_document, split_long_text
from app.kb.parser import DocumentFormatError, find_document_files, parse_document, parse_file
from app.kb.tokens import ApproxTokenCounter
from app.kb.validate import validate_document

KB_ROOT = Path(__file__).resolve().parents[3] / "knowledge-base"
COUNTER = ApproxTokenCounter()

SAMPLE = """---
id: vi-nap-tien
title: Nạp ví
type: policy
audience: [STUDENT, TUTOR]
features: [wallet]
routes: [/student/wallet]
keywords: [nạp ví]
todo: []
---

# Nạp ví

## Hạn mức

Mỗi lần nạp tối thiểu 10.000 đ, tối đa 100.000.000 đ. Số tiền phải là số chẵn đồng, không có phần lẻ.
Bạn nạp được nhiều lần trong ngày, không giới hạn số lần. Tiền vào ví ngay khi VNPay xác nhận thành công.

## Các bước

### Bước 1
Mở trang Ví.

### Bước 2
Nhập số tiền rồi bấm Nạp qua VNPay. Trang VNPay mở ở thẻ mới, bạn thanh toán trong 15 phút kể từ lúc tạo link.
Sau khi thanh toán xong, quay lại PTutor để xem số dư mới trong mục giao dịch của trang Ví.
"""


def test_parse_front_matter_and_body():
    document = parse_document(SAMPLE)
    assert document.doc_key == "vi-nap-tien"
    assert document.audience == ["STUDENT", "TUTOR"]
    assert document.body.startswith("# Nạp ví")


def test_missing_front_matter_is_rejected():
    with pytest.raises(DocumentFormatError):
        parse_document("# Không có front matter\n")


def test_chunks_carry_title_and_heading_path():
    chunks = chunk_document(parse_document(SAMPLE), COUNTER)
    assert chunks[0].heading_path == "Nạp ví > Hạn mức"
    assert chunks[0].anchor == "han-muc"
    assert chunks[0].search_text.startswith("Nạp ví > Hạn mức\n")
    assert [chunk.ordinal for chunk in chunks] == list(range(len(chunks)))


def test_short_subsection_is_merged_into_next_one_of_same_parent():
    chunks = chunk_document(parse_document(SAMPLE), COUNTER)
    steps = [chunk for chunk in chunks if "Các bước" in chunk.heading_path]
    assert len(steps) == 1  # "Bước 1" quá ngắn nên gộp với "Bước 2"
    assert steps[0].heading_path == "Nạp ví > Các bước"
    assert "**Bước 1**" in steps[0].content and "**Bước 2**" in steps[0].content


def test_long_text_is_split_with_overlap_and_within_budget():
    paragraphs = [f"Đoạn {index}: " + " ".join(f"chữ{index}x{word}" for word in range(60)) for index in range(10)]
    pieces = split_long_text("\n\n".join(paragraphs), 200, COUNTER)
    assert len(pieces) > 1
    assert all(COUNTER.count(piece) <= 200 for piece in pieces)
    # Phần sau mở đầu bằng khoảng 40 token cuối của phần trước (chồng lấn).
    tail_of_first = " ".join(pieces[0].split()[-3:])
    assert pieces[1].startswith(" ".join(pieces[0].split()[-30:])) and tail_of_first in pieces[1]


def test_validate_reports_todo_unless_allowed():
    text = SAMPLE.replace("todo: []", "todo: ['Hỏi admin']")
    document = parse_document(text)
    blocked = validate_document(document, COUNTER, {"/student/wallet"})
    assert not blocked.ok and "todo" in blocked.errors[0]
    allowed = validate_document(document, COUNTER, {"/student/wallet"}, allow_open_todo=True)
    assert allowed.ok and allowed.warnings


def test_validate_rejects_unknown_route_type_and_missing_heading():
    document = parse_document(SAMPLE.replace("type: policy", "type: blog").replace("## ", "#### "))
    errors = " | ".join(validate_document(document, COUNTER, set()).errors)
    assert "type không hợp lệ" in errors and "Route không có" in errors and "heading ##" in errors


@pytest.mark.parametrize("name", ["chung/vi-nap-tien-quy-dinh.md", "chung/trang-thai-hop-dong.md",
                                  "hoc-vien/ky-hoc-phi-quy-dinh.md"])
def test_real_documents_chunk_within_limit(name):
    chunks = chunk_document(parse_file(KB_ROOT / name), COUNTER)
    assert chunks and all(chunk.token_count <= MAX_CHUNK_TOKENS for chunk in chunks)


def test_every_knowledge_base_file_parses():
    files = find_document_files(KB_ROOT)
    assert len(files) == 65
    assert all(parse_file(path).doc_key for path in files)
