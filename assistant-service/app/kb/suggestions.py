"""Câu hỏi gợi ý theo vai trò và trang đang mở (thiết kế 07 mục 7.5; lưu ở `kb_setting.suggestions`)."""

from __future__ import annotations

from app.kb.routes import route_pattern_matches

DEFAULT_SUGGESTIONS = {
    "STUDENT": {
        "default": ["Làm sao đăng tin tìm gia sư?", "Phí đăng tin là bao nhiêu?", "Hợp đồng của tôi thế nào rồi?"],
        "/student/wallet": ["Nạp ví tối thiểu bao nhiêu?", "Thanh toán VNPay bị lỗi thì sao?"],
        "/student/contracts": ["Hủy hợp đồng thế nào?", "Kỳ học phí tiếp theo khi nào?"],
    },
    "TUTOR": {
        "default": ["Hồ sơ của tôi đã được duyệt chưa?", "Làm sao nộp chứng chỉ?"],
        "/tutor/certificates": ["Chứng chỉ bị từ chối thì làm gì?"],
    },
}


def suggestions_for(role: str, page_path: str, table: dict | None = None, limit: int = 4) -> list[str]:
    """Gợi ý của trang (khớp cả route có tham số) đứng trước, rồi tới gợi ý mặc định của vai trò."""
    by_page = (table or DEFAULT_SUGGESTIONS).get(role, {})
    picked: list[str] = []
    for pattern, questions in by_page.items():
        if pattern != "default" and page_path and route_pattern_matches(pattern, page_path):
            picked.extend(questions)
    picked.extend(by_page.get("default", []))
    unique = list(dict.fromkeys(picked))
    return unique[:limit]
