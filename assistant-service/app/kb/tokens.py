"""Đếm token theo tokenizer của bge-m3 — model embedding của `ai-service`.

Thiết kế 03 mục 3.5: mỗi đoạn ≤ 400 token vì `/embed` cắt ở 512 token. Phải đếm bằng đúng tokenizer của model
embedding, không phải tokenizer của LLM.

- `HfTokenCounter`: dùng file `tokenizer.json` của bge-m3 (tải một lần, ~17 MB) → đếm chính xác.
- `ApproxTokenCounter`: khi không có file (test, máy chưa tải) → ước lượng theo số chữ. Hệ số 1,3 đo trên chính
  kho tri thức (21.773 chữ → 28.110 token bge-m3, ngày 2026-11-03).

Tải tokenizer:  .venv/Scripts/python -m app.kb.tokens --download
"""

from __future__ import annotations

import argparse
import math
import urllib.request
from pathlib import Path

TOKENIZER_URL = "https://huggingface.co/BAAI/bge-m3/resolve/main/tokenizer.json"
DEFAULT_TOKENIZER_PATH = Path(__file__).resolve().parents[2] / "models" / "bge-m3-tokenizer.json"
TOKENS_PER_WORD = 1.3


class TokenCounter:
    name = "base"

    def count(self, text: str) -> int:
        raise NotImplementedError


class ApproxTokenCounter(TokenCounter):
    name = "approx-1.3x-words"

    def count(self, text: str) -> int:
        return math.ceil(len(text.split()) * TOKENS_PER_WORD)


class HfTokenCounter(TokenCounter):
    name = "bge-m3"

    def __init__(self, tokenizer_path: Path):
        from tokenizers import Tokenizer

        self._tokenizer = Tokenizer.from_file(str(tokenizer_path))

    def count(self, text: str) -> int:
        return len(self._tokenizer.encode(text, add_special_tokens=False).ids)


def load_token_counter(tokenizer_path: str | Path = "") -> TokenCounter:
    """Có file tokenizer thì đếm chính xác, không có thì ước lượng (ghi rõ tên bộ đếm vào kết quả xem trước)."""
    path = Path(tokenizer_path) if tokenizer_path else DEFAULT_TOKENIZER_PATH
    return HfTokenCounter(path) if path.is_file() else ApproxTokenCounter()


def _download(target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(TOKENIZER_URL, target)  # noqa: S310 — URL cố định của Hugging Face
    print(f"Đã tải {TOKENIZER_URL} → {target}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Tải tokenizer bge-m3 để đếm token chính xác")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--path", type=Path, default=DEFAULT_TOKENIZER_PATH)
    arguments = parser.parse_args()
    if arguments.download:
        _download(arguments.path)
