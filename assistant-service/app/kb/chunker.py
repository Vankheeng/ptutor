"""Chia một tài liệu thành các đoạn (chunk) để tìm kiếm.

Thiết kế 03 mục 3.5:
- Chia theo heading `##` / `###`; một đoạn = nội dung dưới một heading.
- Mỗi đoạn ≤ 400 token (tính trên văn bản đem embed). Mục dài hơn → chia theo đoạn văn, chồng lấn ~40 token.
- Mục ngắn hơn ~40 token → gộp với mục kế (cùng mục `##` cha) để vector không quá "nghèo" thông tin.
- Văn bản đem embed (`search_text`) = "Tiêu đề > mục cha > mục" + nội dung, để đoạn tự hiểu được khi đứng riêng.

Đoạn chỉ dùng để *tìm*. Khi trả lời, model được đọc cả mục chứa đoạn (small-to-big, `retrieval/expand.py`).
"""

from __future__ import annotations

from dataclasses import dataclass

from app.kb.parser import KbDocument
from app.kb.tokens import TokenCounter
from app.text import slugify

MAX_CHUNK_TOKENS = 400
MIN_CHUNK_TOKENS = 40
OVERLAP_TOKENS = 40


@dataclass
class Section:
    """Phần thân bài dưới một heading. `h2` rỗng = phần mở đầu trước heading `##` đầu tiên."""

    h2: str
    h3: str
    text: str
    extra_headings: tuple[str, ...] = ()  # tên các mục ngắn đã gộp vào mục này


@dataclass
class ChunkDraft:
    ordinal: int
    heading_path: str
    anchor: str
    content: str
    search_text: str
    token_count: int


def split_sections(document: KbDocument) -> list[Section]:
    """Duyệt thân bài theo dòng, cắt ở mỗi heading `##`/`###` (bỏ qua heading nằm trong khối code)."""
    sections: list[Section] = []
    h2, h3 = "", ""
    buffer: list[str] = []
    in_code_block = False

    def flush() -> None:
        text = "\n".join(buffer).strip()
        if text:
            sections.append(Section(h2=h2, h3=h3, text=text))
        buffer.clear()

    for line in document.body.splitlines():
        if line.strip().startswith("```"):
            in_code_block = not in_code_block
        if not in_code_block and line.startswith("## "):
            flush()
            h2, h3 = line[3:].strip(), ""
        elif not in_code_block and line.startswith("### "):
            flush()
            h3 = line[4:].strip()
        elif not in_code_block and line.startswith("# "):
            continue  # tiêu đề tài liệu đã có trong front matter
        else:
            buffer.append(line)
    flush()
    return sections


def merge_short_sections(sections: list[Section], counter: TokenCounter) -> list[Section]:
    """Gộp mục quá ngắn vào mục kế (hoặc mục trước) cùng mục `##` cha.

    Khi gộp hai mục `###` khác nhau, mục mới mang heading của mục cha `##`, và tên mục con được giữ lại
    trong nội dung (in đậm) để không mất nghĩa.
    """
    result = list(sections)
    index = 0
    while index < len(result):
        section = result[index]
        if counter.count(section.text) >= MIN_CHUNK_TOKENS:
            index += 1
            continue
        if index + 1 < len(result) and result[index + 1].h2 == section.h2:
            result[index + 1] = _combine(section, result[index + 1])
            del result[index]
            continue
        if index > 0 and result[index - 1].h2 == section.h2:
            result[index - 1] = _combine(result[index - 1], section)
            del result[index]
            continue
        index += 1
    return result


def _combine(first: Section, second: Section) -> Section:
    same_subsection = first.h3 == second.h3
    parts = []
    for section in (first, second):
        label = section.h3 if not same_subsection else ""
        parts.append(f"**{label}**\n{section.text}" if label else section.text)
    return Section(
        h2=first.h2,
        h3=first.h3 if same_subsection else "",
        text="\n\n".join(parts),
        extra_headings=first.extra_headings + second.extra_headings,
    )


def heading_path(title: str, section: Section) -> str:
    return " > ".join(part for part in (title, section.h2, section.h3) if part)


def chunk_document(document: KbDocument, counter: TokenCounter) -> list[ChunkDraft]:
    chunks: list[ChunkDraft] = []
    for section in merge_short_sections(split_sections(document), counter):
        path = heading_path(document.title, section)
        anchor = slugify(section.h3 or section.h2 or document.title)
        prefix = path + "\n"
        budget = MAX_CHUNK_TOKENS - counter.count(prefix)
        for piece in split_long_text(section.text, budget, counter):
            search_text = prefix + piece
            chunks.append(ChunkDraft(
                ordinal=len(chunks),
                heading_path=path,
                anchor=anchor,
                content=piece,
                search_text=search_text,
                token_count=counter.count(search_text),
            ))
    return chunks


def split_long_text(text: str, budget: int, counter: TokenCounter) -> list[str]:
    """Văn bản vừa ngân sách → giữ nguyên. Dài hơn → xếp các đoạn văn vào từng phần, phần sau lặp lại
    khoảng 40 token cuối của phần trước (chồng lấn) để điều kiện nằm ở ranh giới không bị cắt đôi."""
    if counter.count(text) <= budget:
        return [text]
    units = _small_units(text, budget, counter)
    pieces: list[str] = []
    current: list[str] = []
    for unit in units:
        candidate = "\n\n".join(current + [unit])
        if current and counter.count(candidate) > budget:
            pieces.append("\n\n".join(current))
            overlap = _tail(pieces[-1], OVERLAP_TOKENS, counter)
            current = [overlap, unit] if counter.count(overlap + "\n\n" + unit) <= budget else [unit]
        else:
            current.append(unit)
    if current:
        pieces.append("\n\n".join(current))
    return pieces


def _small_units(text: str, budget: int, counter: TokenCounter) -> list[str]:
    """Tách thành đoạn văn (dòng trống); đoạn văn vẫn quá dài thì tách tiếp theo dòng, rồi theo từ."""
    units: list[str] = []
    for paragraph in (part.strip() for part in text.split("\n\n")):
        if not paragraph:
            continue
        if counter.count(paragraph) <= budget:
            units.append(paragraph)
            continue
        for line in paragraph.splitlines():
            if counter.count(line) <= budget:
                units.append(line)
            else:
                units.extend(_split_words(line, budget, counter))
    return units


def _split_words(line: str, budget: int, counter: TokenCounter) -> list[str]:
    words = line.split()
    parts: list[str] = []
    current: list[str] = []
    for word in words:
        if current and counter.count(" ".join(current + [word])) > budget:
            parts.append(" ".join(current))
            current = []
        current.append(word)
    if current:
        parts.append(" ".join(current))
    return parts


def _tail(text: str, tokens: int, counter: TokenCounter) -> str:
    """Lấy phần cuối của văn bản dài khoảng `tokens` token, cắt ở ranh giới từ."""
    words = text.split()
    tail: list[str] = []
    while words and counter.count(" ".join([words[-1]] + tail)) <= tokens:
        tail.insert(0, words.pop())
    return " ".join(tail)
