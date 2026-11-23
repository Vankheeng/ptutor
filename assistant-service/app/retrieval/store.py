"""Nơi tìm đoạn tài liệu: `KnowledgeStore` và hai bản cài đặt.

Thiết kế 04 mục 4.3 (bảng "Truy xuất lai"): lọc đoạn của bản đang công bố theo audience → tìm theo vector (cosine)
và theo full-text trên văn bản đã bỏ dấu, mỗi loại lấy top 20.

- `PgKnowledgeStore`: chạy SQL trên PostgreSQL + pgvector (bản dùng thật).
- `MemoryKnowledgeStore`: cùng thuật toán nhưng chạy trong bộ nhớ, dựng từ thư mục Markdown. Dùng cho test T0 và để
  đo hit@k / chạy eval khi chưa có Postgres. Điểm full-text của bản này **không** giống hệt `ts_rank_cd`, nên số
  liệu báo cáo chính thức phải chạy trên `PgKnowledgeStore`.
"""

from __future__ import annotations

import math
import re
import unicodedata
import uuid
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

from app.kb.chunker import chunk_document
from app.kb.parser import KbDocument, find_document_files, parse_file
from app.kb.tokens import TokenCounter
from app.providers.embedding import EmbeddingProvider, cosine
from app.text import strip_accents

# Từ quá phổ biến trong câu hỏi tiếng Việt, bỏ khi tìm full-text. Viết **có dấu** và lọc trước khi bỏ dấu: nếu
# lọc sau khi bỏ dấu thì "ví" trùng "vì", "tối" trùng "tôi" và từ khoá quan trọng bị bỏ nhầm.
# Câu hỏi gõ không dấu ("nap vi toi thieu") vì thế giữ nguyên mọi từ — an toàn hơn bỏ nhầm.
STOPWORDS = {
    "tôi", "bạn", "mình", "của", "là", "có", "không", "được", "bao", "nhiêu", "thế", "nào", "gì", "thì", "và",
    "cho", "với", "như", "khi", "để", "ở", "tại", "sao", "vì", "này", "đó", "đã", "đang", "sẽ", "cần", "phải",
    "một", "các", "những", "làm", "về", "trong", "hay", "hoặc", "nếu", "rồi", "chưa", "à", "ừ", "nhé", "vậy",
    "ra", "lên", "đi", "đến", "từ", "bị", "còn", "nữa", "ai", "lúc", "bây", "giờ", "ạ", "nhỉ", "ơi",
}


@dataclass
class ChunkRecord:
    id: uuid.UUID
    version_id: uuid.UUID
    doc_key: str
    title: str
    heading_path: str
    anchor: str
    content: str
    search_text: str
    ordinal: int
    token_count: int
    audience: list[str]
    features: list[str]
    routes: list[str]
    type: str


@dataclass
class ScoredChunk:
    chunk: ChunkRecord
    score: float


def query_terms(question: str) -> list[str]:
    """Các từ dùng cho full-text: bỏ dấu, chữ thường, bỏ từ dừng, giữ thứ tự, không trùng."""
    seen: list[str] = []
    for word in re.findall(r"\w+", unicodedata.normalize("NFC", question).lower()):
        if word in STOPWORDS:
            continue
        plain = strip_accents(word)
        if re.fullmatch(r"[a-z0-9]+", plain) and plain not in seen:
            seen.append(plain)
    return seen


def text_coverage(question: str, chunk_text: str) -> float:
    """Tỉ lệ từ khoá của câu hỏi có mặt trong đoạn — dùng làm tiêu chí "full-text khớp" ở ngưỡng NO_CONTEXT."""
    terms = query_terms(question)
    if not terms:
        return 0.0
    present = set(re.findall(r"[a-z0-9]+", strip_accents(chunk_text).lower()))
    return sum(term in present for term in terms) / len(terms)


class KnowledgeStore:
    def vector_search(self, vector: list[float], audiences: list[str], k: int) -> list[ScoredChunk]:
        raise NotImplementedError

    def text_search(self, question: str, audiences: list[str], k: int) -> list[ScoredChunk]:
        raise NotImplementedError

    def section_chunks(self, version_id: uuid.UUID, heading_path: str) -> list[ChunkRecord]:
        """Mọi đoạn cùng phiên bản và cùng mục, theo thứ tự — cho small-to-big."""
        raise NotImplementedError


_CHUNK_COLUMNS = """
    c.id, c.version_id, d.doc_key, v.title, c.heading_path, c.anchor, c.content, c.search_text, c.ordinal,
    c.token_count, c.audience, c.features, c.routes, c.type
"""
_ACTIVE_JOIN = """
    FROM assistant.kb_chunk c
    JOIN assistant.kb_document_version v ON v.id = c.version_id
    JOIN assistant.kb_document d ON d.id = v.document_id
"""


class PgKnowledgeStore(KnowledgeStore):
    def __init__(self, session_factory: sessionmaker[Session]):
        self._sessions = session_factory

    def vector_search(self, vector: list[float], audiences: list[str], k: int) -> list[ScoredChunk]:
        sql = text(f"""
            SELECT {_CHUNK_COLUMNS}, 1 - (c.embedding <=> CAST(:vector AS vector)) AS score
            {_ACTIVE_JOIN}
            WHERE c.is_active AND c.audience && CAST(:audiences AS varchar[]) AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> CAST(:vector AS vector)
            LIMIT :k
        """)
        return self._run(sql, {"vector": str(vector), "audiences": audiences, "k": k})

    def text_search(self, question: str, audiences: list[str], k: int) -> list[ScoredChunk]:
        terms = query_terms(question)
        if not terms:
            return []
        # Nối các từ bằng OR: câu hỏi tự nhiên hiếm khi chứa đúng mọi từ của tài liệu; ts_rank_cd xếp đoạn
        # khớp nhiều từ, khớp gần nhau lên trước.
        sql = text(f"""
            SELECT {_CHUNK_COLUMNS}, ts_rank_cd(c.tsv, to_tsquery('simple', :query)) AS score
            {_ACTIVE_JOIN}
            WHERE c.is_active AND c.audience && CAST(:audiences AS varchar[])
              AND c.tsv @@ to_tsquery('simple', :query)
            ORDER BY score DESC
            LIMIT :k
        """)
        return self._run(sql, {"query": " | ".join(terms), "audiences": audiences, "k": k})

    def section_chunks(self, version_id: uuid.UUID, heading_path: str) -> list[ChunkRecord]:
        sql = text(f"""
            SELECT {_CHUNK_COLUMNS}, 0 AS score {_ACTIVE_JOIN}
            WHERE c.version_id = :version_id AND c.heading_path = :heading_path
            ORDER BY c.ordinal
        """)
        return [item.chunk for item in self._run(sql, {"version_id": version_id, "heading_path": heading_path})]

    def _run(self, sql, parameters: dict) -> list[ScoredChunk]:
        with self._sessions() as session:
            rows = session.execute(sql, parameters).mappings().all()
        return [ScoredChunk(_record(row), float(row["score"])) for row in rows]


def _record(row) -> ChunkRecord:
    return ChunkRecord(**{name: row[name] for name in ChunkRecord.__dataclass_fields__})


@dataclass
class _MemoryChunk:
    record: ChunkRecord
    vector: list[float]
    term_counts: Counter = field(default_factory=Counter)


class MemoryKnowledgeStore(KnowledgeStore):
    """Kho trong bộ nhớ. `add_document` chia đoạn + embed như khi công bố (bỏ qua kiểm tra todo)."""

    def __init__(self, embedder: EmbeddingProvider, counter: TokenCounter):
        self._embedder = embedder
        self._counter = counter
        self._chunks: list[_MemoryChunk] = []

    @classmethod
    def from_directory(cls, root: Path, embedder: EmbeddingProvider, counter: TokenCounter) -> MemoryKnowledgeStore:
        store = cls(embedder, counter)
        for path in find_document_files(root):
            store.add_document(parse_file(path))
        return store

    def add_document(self, document: KbDocument) -> None:
        version_id = uuid.uuid5(uuid.NAMESPACE_URL, "kb:" + document.doc_key)
        drafts = chunk_document(document, self._counter)
        vectors = self._embedder.embed([draft.search_text for draft in drafts]).vectors
        for draft, vector in zip(drafts, vectors, strict=True):
            record = ChunkRecord(
                id=uuid.uuid5(version_id, str(draft.ordinal)), version_id=version_id, doc_key=document.doc_key,
                title=document.title, heading_path=draft.heading_path, anchor=draft.anchor, content=draft.content,
                search_text=draft.search_text, ordinal=draft.ordinal, token_count=draft.token_count,
                audience=list(document.audience), features=list(document.features), routes=list(document.routes),
                type=document.type,
            )
            searchable = strip_accents(draft.search_text + " " + " ".join(document.keywords)).lower()
            self._chunks.append(_MemoryChunk(record, vector, Counter(re.findall(r"[a-z0-9]+", searchable))))

    @property
    def chunk_count(self) -> int:
        return len(self._chunks)

    def _visible(self, audiences: list[str]) -> list[_MemoryChunk]:
        return [item for item in self._chunks if set(item.record.audience) & set(audiences)]

    def vector_search(self, vector: list[float], audiences: list[str], k: int) -> list[ScoredChunk]:
        scored = [ScoredChunk(item.record, cosine(vector, item.vector)) for item in self._visible(audiences)]
        return sorted(scored, key=lambda item: item.score, reverse=True)[:k]

    def text_search(self, question: str, audiences: list[str], k: int) -> list[ScoredChunk]:
        terms = query_terms(question)
        results = []
        for item in self._visible(audiences):
            # Gần giống ts_rank_cd: mỗi từ khớp đóng góp tăng dần theo số lần xuất hiện nhưng bão hoà.
            score = sum(math.log1p(item.term_counts[term]) for term in terms if item.term_counts[term])
            if score > 0:
                results.append(ScoredChunk(item.record, score))
        return sorted(results, key=lambda item: item.score, reverse=True)[:k]

    def section_chunks(self, version_id: uuid.UUID, heading_path: str) -> list[ChunkRecord]:
        return sorted((item.record for item in self._chunks
                       if item.record.version_id == version_id and item.record.heading_path == heading_path),
                      key=lambda record: record.ordinal)
