"""Tầng 1 của lớp chặn ngoài phạm vi: luật (thiết kế 04 mục 4.11).

Không tốn lời gọi nào. So khớp trên câu đã bỏ dấu, chữ thường (để bắt cả câu gõ không dấu).

- **Chào hỏi / cảm ơn** (cả câu chỉ có vậy) → `CHITCHAT`, trả câu mẫu.
- **Rõ ràng ngoài phạm vi**: giải bài, biểu thức toán, viết văn, viết code, dịch, thời tiết… → `OUT_OF_SCOPE`.
  **Trừ khi** câu có nhắc tới nghiệp vụ PTutor (gia sư, hợp đồng, học phí, ví…): đó là câu lẫn ("tìm gia sư toán thế
  nào, giải luôn bài này") — để tầng sau xử lý, Actor trả lời phần thuộc nền tảng và từ chối phần còn lại.

Luật lưu ở `kb_setting.scope_rules` (admin sửa được); giá trị dưới đây là mặc định khi chưa có.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from app.text import collapse_spaces, strip_accents

DEFAULT_RULES: dict[str, list[str]] = {
    "chitchat": [
        r"^(xin chao|chao|hello|hi|helo|alo|hey|cam on|thanks?|thank you|ok|oke|okay|tam biet|bye|good ?bye)"
        r"( (ban|ad|admin|tro ly|bot|nhe|nha|nhieu|a|ah|nhe ban|ptutor|em|anh|chi))*[ !.?~]*$",
    ],
    "out_of_scope": [
        r"\d+\s*[x\^]\s*\d*\s*[\+\-]",                       # x^2 - 5x + 6, 2x + 3
        r"\d+\s*[\+\-\*/]\s*\d+\s*=",                          # 12 + 7 = ?
        r"\bphuong trinh\b", r"\bdao ham\b", r"\btich phan\b", r"\bbat phuong trinh\b",
        r"\bgiai (giup|ho|dum|gium)?\s*(minh|em|toi|to)?\s*(bai|de|cau|phuong|he)\b",
        r"\blam (giup|ho|dum)?\s*(minh|em|toi)?\s*bai tap\b",
        r"\bviet (giup|ho|dum)?\s*(minh|em|toi)?\s*(mot )?(bai|doan) (van|tho|luan|nghi luan)\b",
        r"\b(bai|doan) van (ta|ke|nghi luan|ve)\b", r"\bviet (bai )?(essay|luan van|tieu luan)\b",
        r"\b(viet|sua|debug) (giup|ho)?\s*(minh|em|toi)?\s*(doan )?code\b", r"\blap trinh (python|java|c\+\+)\b",
        r"\bdich (giup|ho)?\s*(minh|em|toi)?\s*(doan|cau|bai|van ban|sang|qua) ", r"\btranslate\b",
        r"\bthoi tiet\b", r"\bdu bao thoi tiet\b", r"\bxo so\b", r"\bty gia\b", r"\bgia vang\b",
        r"\bke (mot )?(chuyen|truyen) cuoi\b", r"\bbong da\b",
    ],
    # Có một trong các cụm này → không chặn bằng luật (câu lẫn hoặc câu về nền tảng).
    "platform_terms": [
        "ptutor", "gia su", "hoc vien", "hop dong", "hoc phi", "nap vi", "so du", "vi cua", "nap tien", "rut tien",
        "khieu nai",
        "hoan tien", "dang tin", "tin dang", "tin tim", "buoi hoc", "chung chi", "ho so", "tai khoan", "thanh toan",
        "vnpay", "de nghi", "de xuat", "don dang ky", "ky hoc phi", "thong bao", "yeu cau",
    ],
}


@dataclass
class ScopeRules:
    chitchat: list[re.Pattern] = field(default_factory=list)
    out_of_scope: list[re.Pattern] = field(default_factory=list)
    platform_terms: list[str] = field(default_factory=list)

    @staticmethod
    def from_setting(value: dict | None) -> ScopeRules:
        merged = {**DEFAULT_RULES, **(value or {})}
        return ScopeRules(
            chitchat=[re.compile(pattern) for pattern in merged["chitchat"]],
            out_of_scope=[re.compile(pattern) for pattern in merged["out_of_scope"]],
            platform_terms=list(merged["platform_terms"]),
        )


def normalize_for_rules(text: str) -> str:
    return " " + collapse_spaces(strip_accents(text).lower()) + " "


def classify_by_rules(question: str, rules: ScopeRules) -> str | None:
    """`CHITCHAT`, `OUT_OF_SCOPE` hoặc None (luật không quyết được → sang tầng 2)."""
    text = normalize_for_rules(question)
    if any(pattern.search(text.strip()) for pattern in rules.chitchat):
        return "CHITCHAT"
    mentions_platform = any(term in text for term in rules.platform_terms)
    if not mentions_platform and any(pattern.search(text) for pattern in rules.out_of_scope):
        return "OUT_OF_SCOPE"
    return None
