"""Dựng ngữ cảnh gửi Actor (thiết kế 04 mục 4.4): system prompt theo vai trò, ngữ cảnh người dùng, các mục tài liệu.

Mỗi mục tài liệu được bọc trong thẻ `<doc id="D1" ...>` để (1) model trích dẫn bằng mã, (2) tách rõ "dữ liệu" khỏi
"lệnh" — chỉ dẫn chèn trong tài liệu chỉ là chữ trong thẻ (quy tắc 6 của prompt; biện pháp thật nằm ở code).
Không gửi họ tên, email của người hỏi: model chỉ biết vai trò, trang đang mở và ngày hiện tại.
"""

from __future__ import annotations

from datetime import datetime
from html import escape

from app.agent.prompts import load_prompt
from app.retrieval.expand import Section

ROLE_LABELS = {"STUDENT": "học viên", "TUTOR": "gia sư"}
WEEKDAYS = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"]


def system_prompt(role: str) -> str:
    return load_prompt("actor").format(role_label=ROLE_LABELS.get(role, role.lower()))


def user_context(role: str, page_path: str, now: datetime, routes: list[str]) -> str:
    day = f"{WEEKDAYS[now.weekday()]}, {now:%d/%m/%Y %H:%M}"
    lines = [
        "<user_context>",
        f"Vai trò: {ROLE_LABELS.get(role, role)}",
        f"Trang đang mở: {page_path or '(không rõ)'}",
        f"Thời điểm hiện tại (giờ Việt Nam): {day}",
        "</user_context>",
        "<routes>" + ", ".join(routes) + "</routes>",
    ]
    return "\n".join(lines)


def format_sections(sections: list[Section]) -> str:
    """`<doc id="D1" title="Hủy hợp đồng" section="Các bước hủy">nội dung</doc>` — thiết kế 04 mục 4.4."""
    if not sections:
        return "<docs>(không có tài liệu liên quan)</docs>"
    blocks = []
    for section in sections:
        blocks.append(f'<doc id="{section.label}" title="{escape(section.title)}" '
                      f'section="{escape(section.section_name)}">\n{section.content}\n</doc>')
    return "<docs>\n" + "\n\n".join(blocks) + "\n</docs>"
