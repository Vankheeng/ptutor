"""`Retriever`: truy xuất lai cho một câu hỏi (thiết kế 04 mục 4.3, tham số 4.9).

Các bước, theo đúng bảng "Truy xuất lai":
1. Lọc audience = {PUBLIC, vai trò người hỏi}: học viên không bao giờ thấy tài liệu chỉ dành cho gia sư.
2. Tìm theo vector (top 20) và theo full-text (top 20).
3. Gộp bằng RRF (k = 60) → top 20. Đoạn có `routes` khớp trang đang mở được cộng điểm nhỏ.
4. Rerank top 20 nếu bật; tắt / lỗi → giữ thứ tự RRF.
5. Ngưỡng: rerank < 0,3, hoặc (không rerank) cosine < 0,45 **và** full-text không khớp → `NO_CONTEXT`.
6. Small-to-big: mở rộng thành tối đa 6 mục `[D1]…[D6]`.

`mode = "vector"` chỉ dùng tìm theo vector — để chạy thí nghiệm E1 (10 mục 10.5).
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field

from app.kb.routes import route_pattern_matches
from app.retrieval.expand import Section, expand_to_sections
from app.retrieval.rerank import Reranker
from app.retrieval.rrf import rrf_merge
from app.retrieval.store import ChunkRecord, KnowledgeStore, text_coverage


@dataclass
class RetrievalConfig:
    mode: str = "hybrid"  # hybrid | vector
    k_each: int = 20
    rerank_k: int = 20
    context_k: int = 6
    rrf_k: int = 60
    min_rerank_score: float = 0.3
    min_cosine: float = 0.45
    # "Full-text khớp" = đoạn tốt nhất chứa ≥ 50% từ khoá của câu hỏi (thiết kế chưa định nghĩa; hiệu chỉnh trên dev).
    min_text_coverage: float = 0.5
    page_boost: float = 0.004  # ≈ 1/4 điểm RRF của hạng 1: đủ để phân thắng thua giữa hai đoạn sát điểm


@dataclass
class Candidate:
    """Một đoạn trong top sau gộp; các điểm được lưu vào `message_citation` để phân tích (06 mục 6.4)."""

    chunk: ChunkRecord
    rank: int
    vector_score: float | None = None
    text_score: float | None = None
    rrf_score: float = 0.0
    rerank_score: float | None = None
    in_context: bool = False


@dataclass
class RetrievalResult:
    candidates: list[Candidate] = field(default_factory=list)
    sections: list[Section] = field(default_factory=list)
    no_context: bool = True
    rerank_used: bool = False
    best_cosine: float = 0.0
    best_text_coverage: float = 0.0
    best_rerank: float | None = None
    timings_ms: dict[str, int] = field(default_factory=dict)  # retrieve_sql, rerank — cho trace (B2)


class Retriever:
    def __init__(self, store: KnowledgeStore, reranker: Reranker, config: RetrievalConfig):
        self._store = store
        self._reranker = reranker
        self.config = config

    def retrieve(self, question: str, vector: list[float], role: str, page_path: str = "") -> RetrievalResult:
        config = self.config
        audiences = ["PUBLIC", role]
        started = time.perf_counter()
        by_vector = self._store.vector_search(vector, audiences, config.k_each)
        by_text = self._store.text_search(question, audiences, config.k_each) if config.mode == "hybrid" else []
        result = RetrievalResult(timings_ms={"retrieve_sql": _elapsed_ms(started), "rerank": 0})

        candidates = self._fuse(by_vector, by_text, page_path)
        started = time.perf_counter()
        rerank_scores = self._reranker.scores(question, [candidate.chunk.search_text for candidate in candidates])
        result.timings_ms["rerank"] = _elapsed_ms(started)
        if rerank_scores is not None and len(rerank_scores) == len(candidates):
            for candidate, score in zip(candidates, rerank_scores, strict=True):
                candidate.rerank_score = score
            candidates.sort(key=lambda candidate: candidate.rerank_score or 0.0, reverse=True)
            result.rerank_used = True
        for rank, candidate in enumerate(candidates, start=1):
            candidate.rank = rank
        result.candidates = candidates

        result.best_cosine = max((item.score for item in by_vector), default=0.0)
        result.best_text_coverage = max((text_coverage(question, candidate.chunk.search_text)
                                         for candidate in candidates), default=0.0)
        result.best_rerank = candidates[0].rerank_score if candidates and result.rerank_used else None
        result.no_context = self._below_threshold(result)
        if not result.no_context:
            result.sections = expand_to_sections([candidate.chunk for candidate in candidates], self._store,
                                                 context_k=config.context_k)
            in_context = {chunk_id for section in result.sections for chunk_id in section.chunk_ids}
            for candidate in candidates:
                candidate.in_context = candidate.chunk.id in in_context
        return result

    def _fuse(self, by_vector, by_text, page_path: str) -> list[Candidate]:
        """Gộp hai danh sách bằng RRF, cộng điểm ưu tiên trang đang mở, giữ top `rerank_k`."""
        chunks = {item.chunk.id: item.chunk for item in by_vector + by_text}
        vector_scores = {item.chunk.id: item.score for item in by_vector}
        text_scores = {item.chunk.id: item.score for item in by_text}
        fused = rrf_merge({"vector": [item.chunk.id for item in by_vector],
                           "text": [item.chunk.id for item in by_text]}, k=self.config.rrf_k)
        for item in fused:
            if page_path and any(route_pattern_matches(route, page_path) for route in chunks[item.chunk_id].routes):
                item.score += self.config.page_boost
        fused = sorted(fused, key=lambda item: item.score, reverse=True)[:self.config.rerank_k]
        return [Candidate(chunk=chunks[item.chunk_id], rank=0, vector_score=vector_scores.get(item.chunk_id),
                          text_score=text_scores.get(item.chunk_id), rrf_score=item.score) for item in fused]

    def _below_threshold(self, result: RetrievalResult) -> bool:
        if not result.candidates:
            return True
        if result.rerank_used:
            return (result.best_rerank or 0.0) < self.config.min_rerank_score
        return (result.best_cosine < self.config.min_cosine
                and result.best_text_coverage < self.config.min_text_coverage)


def _elapsed_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
