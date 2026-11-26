"""Tiện ích cho các lệnh chạy bằng `python -m ...`.

Console Windows mặc định dùng mã hoá cp1252, in chữ tiếng Việt sẽ lỗi `UnicodeEncodeError`. Mọi lệnh CLI gọi
`use_utf8_output()` đầu tiên.
"""

from __future__ import annotations

import sys


def use_utf8_output() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8")
