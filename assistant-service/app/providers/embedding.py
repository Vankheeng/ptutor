"""Biến văn bản thành vector (embedding): `EmbeddingProvider` và hai bản cài đặt.

Thiết kế 02 mục 2.1, QĐ8 (bge-m3 qua `ai-service /embed`, 1024 chiều) và 13 mục 13.1 (`FakeEmbedding`).

- `AiServiceEmbedding`: gọi `POST /embed` của `ai-service` (nhánh recommendation), header `X-Internal-Token`,
  tối đa 32 văn bản mỗi lần (`AI_EMBED_MAX_BATCH`).
- `FakeEmbedding`: không cần mạng, tất định. Thay vì vector ngẫu nhiên theo băm cả câu, bản này **băm từng từ và
  từng cụm 3 ký tự** (đã bỏ dấu) vào 1024 chiều ("feature hashing"). Nhờ vậy hai câu dùng chung nhiều từ có cosine
  cao — vector search vẫn có ý nghĩa (theo từ ngữ, không theo nghĩa) khi chưa có `ai-service`. Kết quả đo bằng bản
  giả **không** thay được kết quả bge-m3; báo cáo phải ghi rõ đang dùng bản nào.

Mặc định dùng `fake` (quyết định của người dùng: `ai-service` chưa merge vào `dev`).
"""

from __future__ import annotations

import hashlib
import math
from dataclasses import dataclass

import httpx

from app.text import strip_accents

DIMENSIONS = 1024


@dataclass
class EmbeddingResult:
    vectors: list[list[float]]
    model: str  # giá trị lưu vào kb_chunk.embedding_model


class EmbeddingError(Exception):
    """Không embed được (ai-service tắt, lỗi, quá giờ). Công bố tài liệu thất bại; lượt hỏi trả 503."""


class EmbeddingProvider:
    model_name = "base"

    def embed(self, texts: list[str]) -> EmbeddingResult:
        raise NotImplementedError

    def status(self) -> str:
        return "ready"


class FakeEmbedding(EmbeddingProvider):
    model_name = "fake-hash-v1"

    def embed(self, texts: list[str]) -> EmbeddingResult:
        return EmbeddingResult([self.vector(text) for text in texts], self.model_name)

    def vector(self, text: str) -> list[float]:
        values = [0.0] * DIMENSIONS
        for feature, weight in _features(text):
            digest = hashlib.blake2b(feature.encode("utf-8"), digest_size=8).digest()
            index = int.from_bytes(digest[:4], "little") % DIMENSIONS
            sign = 1.0 if digest[4] % 2 == 0 else -1.0  # dấu ngẫu nhiên để các đặc trưng trùng ô triệt tiêu nhau
            values[index] += sign * weight
        norm = math.sqrt(sum(value * value for value in values)) or 1.0
        return [value / norm for value in values]


def _features(text: str) -> list[tuple[str, float]]:
    words = strip_accents(text).lower().split()
    words = ["".join(char for char in word if char.isalnum()) for word in words]
    words = [word for word in words if word]
    features = [("w:" + word, 1.0) for word in words]
    features += [("b:" + first + "_" + second, 1.0) for first, second in zip(words, words[1:], strict=False)]
    for word in words:
        padded = f"#{word}#"
        features += [("c:" + padded[i:i + 3], 0.3) for i in range(len(padded) - 2)]
    return features


class AiServiceEmbedding(EmbeddingProvider):
    """Gọi `ai-service`. Đúng API trên nhánh `feat/tutor-recommendation`: `{"texts": [...]}` →
    `{"model", "dimensions", "embeddings"}`; 503 khi model đang tải; 422 khi quá 32 văn bản."""

    model_name = "bge-m3"

    # bge-m3 chạy CPU: một câu hỏi mất < 1 giây, nhưng một lô 32 đoạn dài có thể mất hơn 10 giây. Timeout tính theo
    # cỡ lô để lúc hỏi đáp vẫn ngắn (lỗi nhanh) mà lúc công bố tài liệu không bị cắt oan.
    BASE_TIMEOUT_S = 5.0
    TIMEOUT_PER_TEXT_S = 1.5

    def __init__(self, base_url: str, internal_token: str, *, batch_size: int = 32,
                 client: httpx.Client | None = None):
        self._client = client or httpx.Client(base_url=base_url)
        self._headers = {"X-Internal-Token": internal_token}
        self._batch_size = batch_size

    def embed(self, texts: list[str]) -> EmbeddingResult:
        vectors: list[list[float]] = []
        model = self.model_name
        for start in range(0, len(texts), self._batch_size):
            batch = texts[start:start + self._batch_size]
            try:
                timeout = self.BASE_TIMEOUT_S + self.TIMEOUT_PER_TEXT_S * len(batch)
                response = self._client.post("/embed", json={"texts": batch}, headers=self._headers, timeout=timeout)
                response.raise_for_status()
            except httpx.HTTPError as error:
                raise EmbeddingError(f"ai-service /embed lỗi: {error}") from error
            body = response.json()
            if body.get("dimensions") != DIMENSIONS:
                raise EmbeddingError(f"Số chiều {body.get('dimensions')} khác {DIMENSIONS}; cần migration đổi cột")
            vectors.extend(body["embeddings"])
            model = body.get("model", model)
        return EmbeddingResult(vectors, model)

    def health(self) -> dict:
        try:
            response = self._client.get("/health", timeout=3.0)
            return response.json()
        except (httpx.HTTPError, ValueError):
            return {}

    def status(self) -> str:
        return "ready" if self.health().get("status") == "ready" else "error"


def cosine(first: list[float], second: list[float]) -> float:
    dot = sum(a * b for a, b in zip(first, second, strict=True))
    norm = math.sqrt(sum(a * a for a in first)) * math.sqrt(sum(b * b for b in second))
    return dot / norm if norm else 0.0
