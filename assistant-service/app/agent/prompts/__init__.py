"""Nạp prompt theo tên + phiên bản (thiết kế 02 mục 2.5: "system prompt ... có phiên bản").

Đổi nội dung prompt = tạo file mới (`actor_v2.md`) và đổi `PROMPT_VERSIONS`, không sửa file cũ: kết quả eval cũ vẫn
gắn đúng với phiên bản prompt đã sinh ra nó (`message.prompt_version`, `config_hash`).
"""

from __future__ import annotations

from functools import cache
from pathlib import Path

PROMPT_DIR = Path(__file__).resolve().parent
PROMPT_VERSIONS = {"actor": "actor_v1", "verifier": "verifier_v1", "rewrite": "rewrite_v1"}


@cache
def load_prompt(kind: str) -> str:
    return (PROMPT_DIR / f"{PROMPT_VERSIONS[kind]}.md").read_text(encoding="utf-8")
