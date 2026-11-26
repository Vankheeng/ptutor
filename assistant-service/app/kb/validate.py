"""Kiểm tra một tài liệu trước khi lưu / công bố, và xem trước cách chia đoạn.

Thiết kế 03 mục 3.3 (bảng kiểm tra) và 07 mục 7.3 (`/draft/validate`: kiểm tra hợp lệ + xem trước chia đoạn).
Kiểm tra trùng `doc_key` cần DB nên nằm ở `KnowledgeBaseService`, không ở đây.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from app.kb.chunker import MAX_CHUNK_TOKENS, ChunkDraft, chunk_document
from app.kb.parser import KbDocument
from app.kb.tokens import TokenCounter

ALLOWED_TYPES = {"policy", "guide", "faq", "glossary", "error"}
ALLOWED_AUDIENCES = {"PUBLIC", "STUDENT", "TUTOR"}
# Mã chức năng ở thiết kế 03 mục 3.7.
ALLOWED_FEATURES = {
    "account", "studying-request", "teaching-request", "matching", "contract", "payment", "wallet", "withdrawal",
    "tutor-profile", "certificate", "lesson", "refund", "complaint", "review", "notification",
}


@dataclass
class ValidationResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    chunks: list[ChunkDraft] = field(default_factory=list)
    token_counter: str = ""

    @property
    def ok(self) -> bool:
        return not self.errors


def validate_document(document: KbDocument, counter: TokenCounter, allowed_routes: set[str],
                      *, allow_open_todo: bool = False) -> ValidationResult:
    """`allow_open_todo=True` chỉ dùng cho import eval/demo (`--allow-todo`); khi đó `todo` thành cảnh báo."""
    result = ValidationResult(token_counter=counter.name)
    for name in ("doc_key", "title", "type"):
        if not getattr(document, name):
            result.errors.append(f"Thiếu trường bắt buộc: {name}")
    if not document.audience:
        result.errors.append("Thiếu trường bắt buộc: audience")
    if document.type and document.type not in ALLOWED_TYPES:
        result.errors.append(f"type không hợp lệ: {document.type}")
    for value in document.audience:
        if value not in ALLOWED_AUDIENCES:
            result.errors.append(f"audience không hợp lệ: {value}")
    for value in document.features:
        if value not in ALLOWED_FEATURES:
            result.errors.append(f"features không hợp lệ: {value}")
    for route in document.routes:
        if route not in allowed_routes:
            result.errors.append(f"Route không có trong danh sách route cho phép: {route}")
    if not any(line.startswith("## ") for line in document.body.splitlines()):
        result.errors.append("Cần ít nhất một heading ## để chia đoạn")
    if document.todo:
        message = f"Còn {len(document.todo)} câu hỏi todo chưa trả lời"
        (result.warnings if allow_open_todo else result.errors).append(message)

    result.chunks = chunk_document(document, counter)
    for chunk in result.chunks:
        if chunk.token_count > MAX_CHUNK_TOKENS:
            result.errors.append(f"Đoạn {chunk.ordinal + 1} ({chunk.heading_path}) dài {chunk.token_count} token "
                                 f"> {MAX_CHUNK_TOKENS}; cần tách heading")
    return result
