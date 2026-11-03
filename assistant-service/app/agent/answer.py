"""Hoàn thiện câu trả lời gửi người dùng: đánh số trích dẫn, câu từ chối mẫu (thiết kế 04 mục 4.5–4.7, 07 mục 7.2).

Model đánh dấu nguồn bằng mã nội bộ `[D1]`, `[D3]`. Người dùng thấy số thứ tự `[1]`, `[2]` theo lần xuất hiện, và
danh sách `citations` cho biết mỗi số là tài liệu / mục nào. Đánh số lại bằng code (không nhờ model) để số luôn liên
tục và luôn khớp danh sách.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.retrieval.expand import Section

ABSTAIN_ANSWER = ("Mình chưa có thông tin chính thức về vấn đề này. Bạn có thể gửi câu hỏi cho nhân viên hỗ trợ "
                  "để được giải đáp.")
OUT_OF_SCOPE_ANSWER = ("Mình chỉ hỗ trợ các câu hỏi về việc sử dụng PTutor: tìm gia sư / học viên, hợp đồng, thanh "
                       "toán, ví, khiếu nại, tài khoản… Bạn có thể hỏi, ví dụ:")
CHITCHAT_ANSWER = "Chào bạn! Mình là trợ lý hỗ trợ của PTutor. Bạn cần hỏi gì về việc dùng PTutor?"
MAX_ANSWER_CHARS = 1200

_DOC_MARK = re.compile(r"\[(D\d+)\]")


@dataclass
class CitationOut:
    index: int
    documentTitle: str
    section: str
    docKey: str
    anchor: str


def number_citations(answer: str, sections: list[Section], claim_sources: list[str]) -> tuple[str, list[CitationOut]]:
    """Đổi `[D3]` → `[1]` theo thứ tự xuất hiện; mã không tồn tại (nguồn bịa) bị xoá khỏi câu trả lời."""
    by_label = {section.label: section for section in sections}
    order: list[str] = []
    for label in _DOC_MARK.findall(answer) + [source for source in claim_sources if source in by_label]:
        if label in by_label and label not in order:
            order.append(label)
    numbers = {label: index for index, label in enumerate(order, start=1)}

    def replace(match: re.Match) -> str:
        label = match.group(1)
        return f"[{numbers[label]}]" if label in numbers else ""

    text = _DOC_MARK.sub(replace, answer)
    text = re.sub(r"(\[\d+\])(\s*\1)+", r"\1", text)  # "[1] [1]" → "[1]"
    citations = [CitationOut(numbers[label], by_label[label].title, by_label[label].section_name,
                             by_label[label].doc_key, by_label[label].anchor) for label in order]
    return text.strip(), citations


def truncate_answer(answer: str, limit: int = MAX_ANSWER_CHARS) -> str:
    """Câu trả lời quá dài → cắt theo đoạn (thiết kế 04 mục 4.7), không cắt giữa câu."""
    if len(answer) <= limit:
        return answer
    kept: list[str] = []
    for paragraph in answer.split("\n\n"):
        if len("\n\n".join(kept + [paragraph])) > limit:
            break
        kept.append(paragraph)
    return "\n\n".join(kept) if kept else answer[:limit].rsplit(" ", 1)[0] + "…"
