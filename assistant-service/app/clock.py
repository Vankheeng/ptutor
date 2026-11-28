"""Đồng hồ tiêm được (thiết kế 13 mục 13.3: "Đồng hồ được tiêm (`Clock`) với `now` cố định của test case").

Câu trả lời có ngày (hạn học phí, "hôm nay") phụ thuộc thời điểm chạy; eval và test dùng `FixedClock` để kết quả
lặp lại được. Giờ hiển thị cho người dùng luôn là giờ Việt Nam.
"""

from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

VIETNAM = ZoneInfo("Asia/Ho_Chi_Minh")


class Clock:
    def now(self) -> datetime:
        return datetime.now(VIETNAM)


class FixedClock(Clock):
    def __init__(self, moment: datetime):
        self._moment = moment if moment.tzinfo else moment.replace(tzinfo=VIETNAM)

    def now(self) -> datetime:
        return self._moment.astimezone(VIETNAM)
