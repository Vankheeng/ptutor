"""Small-to-big: tìm bằng đoạn nhỏ, đưa cho model cả mục chứa đoạn (thiết kế 03 mục 3.5, 04 mục 4.3).

Với mỗi đoạn được chọn: lấy mọi đoạn cùng phiên bản + cùng `heading_path`, ghép theo `ordinal`. Mục ≤ ~1.200 token →
lấy cả mục. Mục dài hơn → lấy đoạn được chọn rồi nới dần sang hai bên tới giới hạn. Nhiều đoạn cùng một mục → gộp
làm một. Kết quả tối đa `context_k` mục, tổng ≤ ~3.000 token (vượt → bỏ mục hạng thấp, giữ tối thiểu 2).
"""

from __future__ import annotations

import re
import uuid
from dataclasses import dataclass, field

from app.retrieval.store import ChunkRecord, KnowledgeStore

SECTION_TOKEN_LIMIT = 1200
CONTEXT_TOKEN_LIMIT = 3000
MIN_SECTIONS = 2


@dataclass
class Section:
    """Một mục đưa vào ngữ cảnh model, đánh nhãn `D1`, `D2`... theo thứ hạng."""

    label: str
    doc_key: str
    title: str
    heading_path: str
    anchor: str
    content: str
    token_count: int
    chunk_ids: list[uuid.UUID] = field(default_factory=list)
    audience: list[str] = field(default_factory=list)
    features: list[str] = field(default_factory=list)
    routes: list[str] = field(default_factory=list)
    type: str = ""

    @property
    def section_name(self) -> str:
        """Tên mục để hiện trong trích dẫn: phần sau tiêu đề tài liệu."""
        parts = self.heading_path.split(" > ")
        return " > ".join(parts[1:]) if len(parts) > 1 else self.title


def expand_to_sections(ranked: list[ChunkRecord], store: KnowledgeStore, *, context_k: int = 6,
                       section_limit: int = SECTION_TOKEN_LIMIT,
                       context_limit: int = CONTEXT_TOKEN_LIMIT) -> list[Section]:
    sections: list[Section] = []
    seen: dict[tuple[uuid.UUID, str], Section] = {}
    for chunk in ranked:
        key = (chunk.version_id, chunk.heading_path)
        if key in seen:
            continue  # mục đã có (từ một đoạn hạng cao hơn của cùng mục)
        if len(sections) == context_k:
            break
        members = _pick_members(store.section_chunks(chunk.version_id, chunk.heading_path) or [chunk],
                                chunk, section_limit)
        section = Section(
            label="", doc_key=chunk.doc_key, title=chunk.title, heading_path=chunk.heading_path, anchor=chunk.anchor,
            content=join_overlapping([member.content for member in members]),
            token_count=sum(member.token_count for member in members),
            chunk_ids=[member.id for member in members], audience=chunk.audience, features=chunk.features,
            routes=chunk.routes, type=chunk.type,
        )
        seen[key] = section
        sections.append(section)
    sections = _fit_total(sections, context_limit)
    for index, section in enumerate(sections, start=1):
        section.label = f"D{index}"
    return sections


def _pick_members(members: list[ChunkRecord], chosen: ChunkRecord, limit: int) -> list[ChunkRecord]:
    if sum(member.token_count for member in members) <= limit:
        return members
    index = next(i for i, member in enumerate(members) if member.id == chosen.id)
    left, right = index, index
    total = members[index].token_count
    # Nới sang hai bên luân phiên (phải trước, vì điều kiện / ngoại lệ thường nằm sau).
    while True:
        grew = False
        for direction in (1, -1):
            nxt = right + 1 if direction == 1 else left - 1
            if 0 <= nxt < len(members) and total + members[nxt].token_count <= limit:
                total += members[nxt].token_count
                right, left = (nxt, left) if direction == 1 else (right, nxt)
                grew = True
        if not grew:
            return members[left:right + 1]


def _fit_total(sections: list[Section], limit: int) -> list[Section]:
    while len(sections) > MIN_SECTIONS and sum(section.token_count for section in sections) > limit:
        sections = sections[:-1]
    return sections


def join_overlapping(pieces: list[str]) -> str:
    """Ghép các phần của một mục dài. Phần sau mở đầu bằng đoạn chồng lấn (lặp cuối phần trước) → bỏ phần lặp."""
    if not pieces:
        return ""
    result = pieces[0]
    for piece in pieces[1:]:
        result_words, piece_words = result.split(), piece.split()
        overlap = 0
        for size in range(min(len(result_words), len(piece_words), 80), 0, -1):
            if result_words[-size:] == piece_words[:size]:
                overlap = size
                break
        remainder = _drop_leading_words(piece, overlap) if overlap else piece
        result = result + "\n\n" + remainder if remainder else result
    return result


def _drop_leading_words(text: str, count: int) -> str:
    """Bỏ `count` từ đầu nhưng giữ nguyên xuống dòng của phần còn lại (danh sách, bước làm)."""
    matches = list(re.finditer(r"\S+", text))
    return text[matches[count - 1].end():].lstrip() if count <= len(matches) else ""
