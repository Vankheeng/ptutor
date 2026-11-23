"""Tiện ích xử lý chữ tiếng Việt dùng ở nhiều nơi: bỏ dấu, tạo slug, chuẩn hoá để so khớp.

Thiết kế 04 mục 4.3 (full-text trên văn bản đã bỏ dấu) và 4.6 (chuẩn hoá khi kiểm tra bằng chứng của verifier).
"""

from __future__ import annotations

import re
import unicodedata

_SPACES = re.compile(r"\s+")


def strip_accents(text: str) -> str:
    """Bỏ dấu tiếng Việt: "Hủy hợp đồng" → "Huy hop dong". Chữ đ/Đ không phải dấu ghép nên đổi riêng."""
    decomposed = unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
    return "".join(char for char in decomposed if unicodedata.category(char) != "Mn")


def slugify(text: str) -> str:
    """Tên mục → anchor dạng kebab-case không dấu, dùng trong trích dẫn (`#cac-buoc-huy`)."""
    plain = strip_accents(text).lower()
    return re.sub(r"[^a-z0-9]+", "-", plain).strip("-")


def collapse_spaces(text: str) -> str:
    return _SPACES.sub(" ", text).strip()
