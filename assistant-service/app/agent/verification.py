"""Phần kiểm tra bằng code của bước kiểm chứng trích dẫn (thiết kế 04 mục 4.6).

- `normalize_for_evidence`: chuẩn hoá hai phía trước khi so chuỗi con — NFC, chữ thường, gộp khoảng trắng, thống nhất
  dấu ngoặc kép / gạch nối; **giữ nguyên dấu tiếng Việt và chữ số** để không che sai lệch số liệu.
- `normalize_verdict`: verifier khai `SUPPORTED` mà `evidence` không phải chuỗi con của đoạn nguồn → coi là
  `UNSUPPORTED`. Chặn trường hợp chính verifier bịa bằng chứng.
- `claim_matches_tool_data`: khẳng định lấy từ tool → số tiền, ngày, mã trạng thái trong khẳng định phải có trong kết
  quả tool của lượt này (kiểm bằng code, không cần LLM).
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

VERIFIER_SCHEMA: dict = {
    "type": "object",
    "additionalProperties": False,
    "required": ["verdicts"],
    "properties": {
        "verdicts": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["claim", "verdict", "evidence", "reason"],
                "properties": {
                    "claim": {"type": "integer"},
                    "verdict": {"type": "string", "enum": ["SUPPORTED", "PARTIAL", "UNSUPPORTED"]},
                    "evidence": {"type": "string"},
                    "reason": {"type": "string"},
                },
            },
        }
    },
}

_QUOTES = str.maketrans({"“": '"', "”": '"', "„": '"', "‘": "'", "’": "'", "–": "-", "—": "-", "‑": "-"})
_SPACES = re.compile(r"\s+")
_MARKUP = re.compile(r"[*_`]")


def normalize_for_evidence(text: str) -> str:
    text = unicodedata.normalize("NFC", text).translate(_QUOTES).lower()
    text = _MARKUP.sub("", text)  # **đậm** trong Markdown không làm hỏng việc so chuỗi
    return _SPACES.sub(" ", text).strip(" .;:,")


@dataclass
class Verdict:
    claim: int
    verdict: str  # SUPPORTED | PARTIAL | UNSUPPORTED
    evidence: str = ""
    reason: str = ""


def normalize_verdict(verdict: Verdict, source_text: str | None) -> Verdict:
    """Bằng chứng phải là chuỗi con của đoạn nguồn; không thì hạ xuống UNSUPPORTED."""
    if verdict.verdict == "UNSUPPORTED":
        return verdict
    evidence = normalize_for_evidence(verdict.evidence)
    if not evidence or source_text is None or evidence not in normalize_for_evidence(source_text):
        reason = "Bằng chứng không có nguyên văn trong đoạn nguồn"
        return Verdict(verdict.claim, "UNSUPPORTED", verdict.evidence, reason)
    return verdict


# ---------------------------------------------------------------- khẳng định lấy từ tool

_DATE = re.compile(r"\b\d{1,2}/\d{1,2}/\d{4}\b")
_AMOUNT = re.compile(r"\b\d{1,3}(?:[.,]\d{3})+\b|\b\d{4,}\b")
_ENUM = re.compile(r"\b[A-Z][A-Z_]{3,}\b")


def _digits(value: str) -> str:
    return re.sub(r"[.,\s]", "", value)


def facts_in(text: str) -> set[str]:
    """Các "sự kiện kiểm được" trong một câu: ngày (dd/MM/yyyy), số tiền / số lớn (bỏ dấu phân cách), mã enum."""
    found = set(_DATE.findall(text))
    found |= {_digits(amount) for amount in _AMOUNT.findall(text)}
    found |= set(_ENUM.findall(text))
    return found


def claim_matches_tool_data(claim_text: str, tool_texts: list[str]) -> tuple[bool, set[str]]:
    """Mọi ngày / số / mã trạng thái trong khẳng định phải xuất hiện trong kết quả tool đã lọc của lượt này.
    Trả (đạt?, các giá trị không tìm thấy)."""
    available: set[str] = set()
    for text in tool_texts:
        available |= facts_in(text)
    missing = {fact for fact in facts_in(claim_text) if fact not in available}
    return not missing, missing
