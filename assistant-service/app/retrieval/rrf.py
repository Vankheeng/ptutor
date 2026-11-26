"""Gộp nhiều danh sách xếp hạng bằng Reciprocal Rank Fusion (thiết kế 04 mục 4.3, 12 — RRF).

    score(đoạn) = Σ  1 / (k + hạng của đoạn trong danh sách i)      (hạng tính từ 1, k = 60)

Vì sao RRF: điểm cosine (0..1) và điểm full-text (ts_rank, không chặn trên) khác thang đo, cộng thẳng không có
nghĩa. RRF chỉ dùng **thứ hạng**, nên gộp được hai loại điểm mà không cần chuẩn hoá. Đoạn đứng cao ở cả hai danh
sách được cộng hai lần → lên đầu.

Ví dụ tính tay (k = 60): đoạn A hạng 1 ở vector, hạng 3 ở full-text → 1/61 + 1/63 ≈ 0,0323;
đoạn B hạng 2 ở vector, không có ở full-text → 1/62 ≈ 0,0161. A đứng trước B.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field


@dataclass
class FusedItem:
    chunk_id: uuid.UUID
    score: float = 0.0
    # Hạng trong từng danh sách nguồn ("vector", "text"); thiếu = không có mặt. Lưu để phân tích E1.
    ranks: dict[str, int] = field(default_factory=dict)


def rrf_merge(ranked_lists: dict[str, list[uuid.UUID]], k: int = 60, limit: int | None = None) -> list[FusedItem]:
    fused: dict[uuid.UUID, FusedItem] = {}
    for source, chunk_ids in ranked_lists.items():
        for rank, chunk_id in enumerate(chunk_ids, start=1):
            item = fused.setdefault(chunk_id, FusedItem(chunk_id))
            item.score += 1.0 / (k + rank)
            item.ranks[source] = rank
    ordered = sorted(fused.values(), key=lambda item: item.score, reverse=True)
    return ordered[:limit] if limit else ordered
