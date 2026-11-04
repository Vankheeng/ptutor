"""Các phụ thuộc dùng chung cho router: cấu hình, kiểm tra token nội bộ, thông tin người hỏi.

Thiết kế 07 mục 7.4: chỉ backend gọi được `/v1/**`, bằng header `X-Internal-Token`; người hỏi được backend
xác thực rồi gửi sang qua `X-User-Id`, `X-User-Role`. Service này tin các header đó vì cổng chỉ mở nội bộ.
"""

from __future__ import annotations

import hmac
from dataclasses import dataclass
from uuid import UUID

from fastapi import Header, HTTPException, Request

from app.config import Settings


def settings_of(request: Request) -> Settings:
    return request.app.state.settings


def require_internal_token(request: Request, x_internal_token: str = Header(default="")) -> None:
    """Chặn mọi lời gọi không mang đúng token nội bộ.

    So sánh bằng `hmac.compare_digest` (thời gian hằng) để không đoán dần được token qua thời gian phản hồi
    (thiết kế 09, mối đe doạ T8). Token chưa cấu hình → 503 thay vì cho qua.
    """
    expected = settings_of(request).internal_token
    if not expected:
        raise HTTPException(status_code=503, detail="ASSISTANT_NOT_CONFIGURED")
    if not hmac.compare_digest(x_internal_token.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="INVALID_INTERNAL_TOKEN")


@dataclass(frozen=True)
class Caller:
    """Người hỏi, do backend xác thực. `authorization` chỉ dùng cho tool tra cứu, không bao giờ ghi log."""

    user_id: UUID
    role: str
    authorization: str = ""
    request_id: str = ""

    def __repr__(self) -> str:  # không để lộ token khi in đối tượng ra log
        return f"Caller(user_id={self.user_id}, role={self.role})"


ASSISTANT_ROLES = {"STUDENT", "TUTOR"}


def current_caller(
    x_user_id: str = Header(default=""),
    x_user_role: str = Header(default=""),
    authorization: str = Header(default=""),
    x_request_id: str = Header(default=""),
) -> Caller:
    try:
        user_id = UUID(x_user_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="INVALID_USER_HEADER") from error
    return Caller(user_id=user_id, role=x_user_role.upper(), authorization=authorization, request_id=x_request_id)


def assistant_caller(x_user_id: str = Header(default=""), x_user_role: str = Header(default=""),
                     authorization: str = Header(default=""), x_request_id: str = Header(default="")) -> Caller:
    """Người dùng widget hỏi đáp: chỉ học viên và gia sư (thiết kế 01 mục 1.3, QĐ9)."""
    caller = current_caller(x_user_id, x_user_role, authorization, x_request_id)
    if caller.role not in ASSISTANT_ROLES:
        raise HTTPException(status_code=403, detail="ROLE_NOT_ALLOWED")
    return caller


def staff_caller(x_user_id: str = Header(default=""), x_user_role: str = Header(default=""),
                 x_request_id: str = Header(default="")) -> Caller:
    """Số liệu cho trang yêu cầu hỗ trợ: ADMIN hoặc nhân viên. Backend đã kiểm chức năng `CUSTOMER_SERVICE` của nhân
    viên (`EmployeeFunctionAuthorizationFilter`); ở đây chặn học viên / gia sư."""
    caller = current_caller(x_user_id, x_user_role, "", x_request_id)
    if caller.role not in {"ADMIN", "EMPLOYEE"}:
        raise HTTPException(status_code=403, detail="ROLE_NOT_ALLOWED")
    return caller


def admin_caller(x_user_id: str = Header(default=""), x_user_role: str = Header(default=""),
                 x_request_id: str = Header(default="")) -> Caller:
    """API quản trị: kiểm lại `ADMIN` dù backend đã kiểm (thiết kế 07 mục 7.4)."""
    caller = current_caller(x_user_id, x_user_role, "", x_request_id)
    if caller.role != "ADMIN":
        raise HTTPException(status_code=403, detail="ROLE_NOT_ALLOWED")
    return caller
