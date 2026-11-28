"""Che thông tin cá nhân trong văn bản: email, số điện thoại, dãy số dài (số tài khoản, CCCD).

Thiết kế 04 mục 4.2 (che trước khi gửi LLM), 09 mục 9.2 (T6) và `RedactMiddleware` (che trước khi lưu log).

Hai thứ **không** được che nhầm, vì trợ lý cần chúng để trả lời đúng:
- Số tiền viết có dấu chấm ngăn cách hàng nghìn ("100.000.000 đ", "10.000"): khớp mẫu nhóm 3 chữ số.
- Mã UUID (mã hợp đồng, mã yêu cầu): dãy số trong UUID nối với nhau bằng gạch ngang, không phải số tài khoản.
"""

from __future__ import annotations

import re

EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
UUID = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
# Số điện thoại Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx, cho phép dấu cách / chấm / gạch giữa các nhóm.
PHONE = re.compile(r"(?<![\w.])(?:\+84|0)(?:[\s.-]?\d){8,10}(?!\w)")
# Dãy chữ số dài (≥ 9 chữ số, có thể cách nhau bằng dấu cách): số tài khoản ngân hàng, CCCD (12 số).
LONG_NUMBER = re.compile(r"(?<![\w.])\d(?:[\s]?\d){8,}(?![\w.])")
MONEY = re.compile(r"^\d{1,3}(?:\.\d{3})+$")

EMAIL_MASK = "[email]"
PHONE_MASK = "[số điện thoại]"
NUMBER_MASK = "[dãy số]"


def redact(text: str) -> str:
    if not text:
        return text
    protected: list[str] = []

    def protect(match: re.Match) -> str:
        protected.append(match.group(0))
        return f"\x00{len(protected) - 1}\x00"

    working = UUID.sub(protect, text)
    working = EMAIL.sub(EMAIL_MASK, working)
    working = PHONE.sub(lambda match: match.group(0) if MONEY.match(match.group(0)) else PHONE_MASK, working)
    working = LONG_NUMBER.sub(NUMBER_MASK, working)
    return re.sub(r"\x00(\d+)\x00", lambda match: protected[int(match.group(1))], working)


def contains_personal_data(text: str) -> bool:
    return redact(text) != text
