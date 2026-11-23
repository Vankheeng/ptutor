"""Đọc một tài liệu kho tri thức (Markdown + front matter YAML) thành đối tượng `KbDocument`.

Thiết kế 03 mục 3.3 (định dạng tài liệu). Front matter là phần giữa hai dòng `---` ở đầu file; phần còn lại là
thân bài Markdown. Trường `id` trong front matter chính là `doc_key` trong DB.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


class DocumentFormatError(ValueError):
    """File không đúng định dạng (thiếu front matter, YAML hỏng...)."""


@dataclass
class KbDocument:
    doc_key: str
    title: str
    type: str
    audience: list[str]
    body: str  # thân bài Markdown, không gồm front matter
    features: list[str] = field(default_factory=list)
    routes: list[str] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)
    source_refs: list[Any] = field(default_factory=list)
    error_codes: list[str] = field(default_factory=list)
    todo: list[str] = field(default_factory=list)


def split_front_matter(text: str) -> tuple[dict[str, Any], str]:
    """Tách front matter và thân bài. File phải bắt đầu bằng dòng `---`."""
    text = text.lstrip("﻿")  # bỏ BOM nếu file được lưu từ Windows
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        raise DocumentFormatError("Thiếu front matter: dòng đầu phải là ---")
    try:
        end = next(index for index in range(1, len(lines)) if lines[index].strip() == "---")
    except StopIteration as error:
        raise DocumentFormatError("Front matter không có dòng --- đóng") from error
    try:
        meta = yaml.safe_load("\n".join(lines[1:end])) or {}
    except yaml.YAMLError as error:
        raise DocumentFormatError(f"Front matter không phải YAML hợp lệ: {error}") from error
    body = "\n".join(lines[end + 1:]).strip() + "\n"
    return meta, body


def parse_document(text: str) -> KbDocument:
    meta, body = split_front_matter(text)
    return KbDocument(
        doc_key=str(meta.get("id") or ""),
        title=str(meta.get("title") or ""),
        type=str(meta.get("type") or ""),
        audience=_as_list(meta.get("audience")),
        body=body,
        features=_as_list(meta.get("features")),
        routes=_as_list(meta.get("routes")),
        keywords=_as_list(meta.get("keywords")),
        source_refs=list(meta.get("source") or []),
        error_codes=_as_list(meta.get("error_codes")),
        todo=_as_list(meta.get("todo")),
    )


def parse_file(path: Path) -> KbDocument:
    return parse_document(path.read_text(encoding="utf-8"))


def find_document_files(root: Path) -> list[Path]:
    """Mọi file `.md` của kho, trừ file bắt đầu bằng `_` (danh mục, báo cáo) và thư mục bắt đầu bằng `_`."""
    return sorted(
        path for path in root.rglob("*.md")
        if not any(part.startswith("_") for part in path.relative_to(root).parts)
    )


def _as_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item) for item in value]
    return [str(value)]
