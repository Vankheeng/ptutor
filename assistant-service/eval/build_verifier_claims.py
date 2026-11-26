"""Sinh bộ dữ liệu đo riêng verifier `eval/datasets/verifier/claims.jsonl` (thiết kế 13 mục 13.8).

    .venv/Scripts/python -m eval.build_verifier_claims

Tạo tất định từ các đoạn của kho tri thức (cùng cách chia đoạn với lúc nạp), chỉ lấy đoạn có con số — loại khẳng định
mà verifier phải bắt chính xác nhất. Mỗi đoạn nguồn sinh vài loại:

| kind | label | Cách tạo |
|---|---|---|
| `verbatim` | SUPPORTED | Chép một câu có con số trong đoạn |
| `reformatted` | SUPPORTED | Câu đó nhưng đổi cách viết (bỏ in đậm, "đ" → "đồng", viết hoa) |
| `altered_number` | UNSUPPORTED | Đổi một chữ số trong câu |
| `misattributed` | UNSUPPORTED | Câu của đoạn này gắn với một đoạn của tài liệu khác |
| `partial` | PARTIAL | Câu đúng + một vế bịa thêm |

Nhãn gán **theo cách tạo**, chưa qua hai người gán độc lập như 13.8 yêu cầu — báo cáo ghi rõ; khi nhóm duyệt xong thì
sửa nhãn trong file (giữ `id`). Chưa có `paraphrase` (diễn đạt lại thật sự cần người viết) — đây là điểm yếu nhất của
verifier "so chuỗi con" và là lý do cần chạy lại bộ này với model thật (T3).
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from app.cli import use_utf8_output
from app.kb.chunker import chunk_document
from app.kb.parser import find_document_files, parse_file
from app.kb.tokens import ApproxTokenCounter

ROOT = Path(__file__).resolve().parent
KB = ROOT.parent.parent / "knowledge-base"
OUT = ROOT / "datasets" / "verifier" / "claims.jsonl"
SOURCE_CHUNKS = 28
NUMBER = re.compile(r"\d")
SENTENCE = re.compile(r"(?<=[.!?])\s+|\n+")
FAKE_CLAUSES = [
    "và được hoàn tiền trong vòng 24 giờ",
    "và không cần nhân viên duyệt",
    "và áp dụng cho cả tài khoản bị khóa",
    "và được miễn phí cho lần đầu tiên",
    "và gia sư nhận thêm 10% thưởng",
]


def load_chunks() -> dict[str, str]:
    """`doc_key#anchor` → nội dung đoạn (giống kb_chunk.content)."""
    chunks: dict[str, str] = {}
    counter = ApproxTokenCounter()
    for path in find_document_files(KB):
        document = parse_file(path)
        for chunk in chunk_document(document, counter):
            chunks.setdefault(f"{document.doc_key}#{chunk.anchor}", chunk.content)
    return chunks


def _number_sentence(content: str) -> str | None:
    for part in SENTENCE.split(content):
        sentence = part.strip(" -*")
        if 30 <= len(sentence) <= 220 and NUMBER.search(sentence) and not sentence.startswith(("#", "|")):
            return sentence
    return None


def _alter_number(sentence: str) -> str:
    match = NUMBER.search(sentence)
    assert match is not None
    digit = int(match.group(0))
    return sentence[: match.start()] + str((digit + 3) % 10) + sentence[match.end():]


def _reformat(sentence: str) -> str:
    text = sentence.replace("**", "")
    text = re.sub(r"(\d)\s?đ\b", r"\1 đồng", text)
    return text[:1].upper() + text[1:]


def build() -> list[dict]:
    chunks = load_chunks()
    picked = [(key, sentence) for key, content in sorted(chunks.items())
              if (sentence := _number_sentence(content)) is not None]
    step = max(1, len(picked) // SOURCE_CHUNKS)
    sources = picked[::step][:SOURCE_CHUNKS]
    claims: list[dict] = []

    def add(chunk_key: str, claim: str, label: str, kind: str) -> None:
        claims.append({"id": f"v-{len(claims) + 1:03d}", "chunk_key": chunk_key, "claim": claim, "label": label,
                       "kind": kind})

    for index, (key, sentence) in enumerate(sources):
        add(key, sentence, "SUPPORTED", "verbatim")
        if _reformat(sentence) != sentence and index % 2 == 0:
            add(key, _reformat(sentence), "SUPPORTED", "reformatted")
        if index % 3 != 2:
            add(key, _alter_number(sentence), "UNSUPPORTED", "altered_number")
        if index % 2 == 1:
            others = sources[index + 1:] + sources[:index]
            other_key = next(k for k, _ in others if k.split("#")[0] != key.split("#")[0])
            add(other_key, sentence, "UNSUPPORTED", "misattributed")
        if index % 3 == 0:
            add(key, f"{sentence.rstrip('.')} {FAKE_CLAUSES[index % len(FAKE_CLAUSES)]}.", "PARTIAL", "partial")
    return claims


def main() -> None:
    use_utf8_output()
    claims = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("".join(json.dumps(claim, ensure_ascii=False) + "\n" for claim in claims), encoding="utf-8")
    labels = {label: sum(claim["label"] == label for claim in claims) for label in ("SUPPORTED", "UNSUPPORTED",
                                                                                     "PARTIAL")}
    print(f"{len(claims)} khẳng định → {OUT}: {labels}")


if __name__ == "__main__":
    main()
