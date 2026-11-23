"""Endpoint `/health` (thiết kế 07 mục 7.5): báo từng thành phần đang sẵn sàng hay không.

Không cần token nội bộ, để Docker và backend kiểm tra được. Không trả thông tin bí mật.
"""

from __future__ import annotations

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
def health(request: Request) -> dict:
    components = request.app.state.health_checks
    report = {name: check() for name, check in components.items()}
    report["status"] = overall_status(report)
    return report


def overall_status(report: dict) -> str:
    """`error` khi DB lỗi (không làm được gì); `degraded` khi một thành phần phụ lỗi hoặc tắt; còn lại `ready`.

    LLM tắt (chưa có khoá) tính là `degraded`, vì service vẫn chạy và trả 503 có kiểm soát cho /v1/chat.
    """
    if report.get("db") == "error":
        return "error"
    statuses = [value for key, value in report.items() if key in {"db", "llm", "embedding", "rerank"}]
    return "ready" if all(value == "ready" for value in statuses) else "degraded"
