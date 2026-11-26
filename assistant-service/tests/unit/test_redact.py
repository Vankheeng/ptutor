"""Test che thông tin cá nhân (thiết kế 04 mục 4.2, 09 T6)."""

import pytest

from app.privacy.redact import redact


@pytest.mark.parametrize(("text", "expected"), [
    ("Email tôi là an.nguyen+1@gmail.com nhé", "Email tôi là [email] nhé"),
    ("Gọi 0912 345 678 giúp", "Gọi [số điện thoại] giúp"),
    ("SĐT +84912345678", "SĐT [số điện thoại]"),
    ("số 0912.345.678", "số [số điện thoại]"),
    ("STK 1903 6688 9999 Techcombank", "STK [dãy số] Techcombank"),
    ("CCCD 001204012345", "CCCD [dãy số]"),
])
def test_personal_data_is_masked(text, expected):
    assert redact(text) == expected


@pytest.mark.parametrize("text", [
    "Nạp tối đa 100.000.000 đ",
    "Phí 50.000đ mỗi tin",
    "Hợp đồng 11111111-1111-1111-1111-000000000002 thế nào?",
    "Hạn 05/11/2026 lúc 09:00",
    "Mã yêu cầu HT-000123",
])
def test_amounts_dates_and_ids_are_kept(text):
    assert redact(text) == text
