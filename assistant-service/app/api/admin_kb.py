"""API quản trị kho tri thức `/v1/admin/**` (thiết kế 07 mục 7.3; vòng đời 03 mục 3.2; giao diện 08 mục 8.2).

Chỉ backend gọi (qua `AdminAssistantController`, đã kiểm `ROLE_ADMIN`); ở đây **kiểm lại** `X-User-Role = ADMIN`
(07 mục 7.4). Admin chỉ sửa được **nội dung** kho tri thức và vài cài đặt (route, gợi ý, luật chặn) — không sửa được
allowlist công cụ, ngân sách hay các cơ chế bảo vệ (09 mục 9.1).
"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.api.deps import Caller, admin_caller, require_internal_token
from app.kb.parser import KbDocument as ParsedDocument
from app.kb.service import KbError
from app.kb.validate import validate_document
from app.services import Services

router = APIRouter(prefix="/v1/admin", dependencies=[Depends(require_internal_token)])
EDITABLE_SETTINGS = {"routes", "suggestions", "scope_rules"}


class DocumentPayload(BaseModel):
    docKey: str = Field(default="", max_length=120, pattern=r"^[a-z0-9-]*$")
    title: str = Field(default="", max_length=255)
    type: str = ""
    audience: list[str] = []
    features: list[str] = []
    routes: list[str] = []
    keywords: list[str] = []
    errorCodes: list[str] = []
    sourceRefs: list[Any] = []
    todo: list[str] = []
    contentMd: str = ""

    def to_parsed(self, doc_key: str) -> ParsedDocument:
        return ParsedDocument(doc_key=doc_key, title=self.title, type=self.type, audience=self.audience,
                              body=self.contentMd, features=self.features, routes=self.routes,
                              keywords=self.keywords, source_refs=self.sourceRefs, error_codes=self.errorCodes,
                              todo=self.todo)


def _services(request: Request) -> Services:
    services: Services = request.app.state.services
    if services.kb_service is None or services.kb_queries is None:
        raise HTTPException(status_code=503, detail="KB_STORE_UNAVAILABLE")  # quản trị cần PostgreSQL
    return services


def _kb_error(error: KbError) -> JSONResponse:
    status = 404 if error.code.endswith("NOT_FOUND") else 409
    return JSONResponse(status_code=status, content={"detail": error.code, "errors": error.details})


def _detail_or_404(services: Services, document_id: uuid.UUID) -> dict:
    assert services.kb_queries is not None
    detail = services.kb_queries.document_detail(document_id)
    if detail is None:
        raise HTTPException(status_code=404, detail="DOCUMENT_NOT_FOUND")
    return detail


@router.get("/documents")
def list_documents(request: Request, status: str = "", type: str = "", feature: str = "",  # noqa: A002
                   audience: str = "", q: str = "", page: int = Query(0, ge=0), size: int = Query(50, ge=1, le=200),
                   caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    assert services.kb_queries is not None
    return services.kb_queries.list_documents(status=status, type_=type, feature=feature, audience=audience,
                                              keyword=q, page=page, size=size)


@router.post("/documents", status_code=201)
def create_document(body: DocumentPayload, request: Request, caller: Caller = Depends(admin_caller)):
    services = _services(request)
    assert services.kb_service is not None and services.kb_queries is not None
    if not body.docKey:
        raise HTTPException(status_code=400, detail="DOC_KEY_REQUIRED")
    if services.kb_queries.doc_key_exists(body.docKey):
        raise HTTPException(status_code=409, detail="DOC_KEY_EXISTS")
    saved = services.kb_service.save_draft(body.to_parsed(body.docKey), origin="ADMIN", editor=caller.user_id)
    return _detail_or_404(services, saved.document_id)


@router.get("/documents/{document_id}")
def document_detail(document_id: uuid.UUID, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    return _detail_or_404(_services(request), document_id)


@router.put("/documents/{document_id}/draft")
def save_draft(document_id: uuid.UUID, body: DocumentPayload, request: Request,
               caller: Caller = Depends(admin_caller)):
    services = _services(request)
    assert services.kb_service is not None
    detail = _detail_or_404(services, document_id)
    services.kb_service.save_draft(body.to_parsed(detail["docKey"]), origin="ADMIN", editor=caller.user_id)
    return _detail_or_404(services, document_id)


@router.post("/documents/{document_id}/draft/validate")
def validate_draft(document_id: uuid.UUID, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    """Kiểm tra hợp lệ + xem trước cách chia đoạn (số đoạn, token mỗi đoạn) — 03 mục 3.2, 08 mục 8.2."""
    services = _services(request)
    assert services.kb_service is not None
    try:
        result = services.kb_service.validate_draft(document_id)
    except KbError as error:
        raise HTTPException(status_code=404, detail=error.code) from error
    return {"ok": result.ok, "errors": result.errors, "warnings": result.warnings,
            "tokenCounter": result.token_counter,
            "chunks": [{"ordinal": chunk.ordinal, "headingPath": chunk.heading_path, "tokenCount": chunk.token_count}
                       for chunk in result.chunks]}


@router.post("/documents/preview")
def preview(body: DocumentPayload, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    """Xem trước cách chia đoạn khi đang gõ, chưa lưu (không ghi DB)."""
    services = _services(request)
    result = validate_document(body.to_parsed(body.docKey or "preview"), services.counter,
                               services.routes.all_paths())
    return {"ok": result.ok, "errors": result.errors, "warnings": result.warnings,
            "chunks": [{"ordinal": c.ordinal, "headingPath": c.heading_path, "tokenCount": c.token_count}
                       for c in result.chunks]}


@router.post("/documents/{document_id}/publish")
def publish(document_id: uuid.UUID, request: Request, caller: Caller = Depends(admin_caller)):
    services = _services(request)
    assert services.kb_service is not None
    try:
        services.kb_service.publish(document_id, publisher=caller.user_id)  # luôn chặn khi còn todo (03 mục 3.3)
    except KbError as error:
        return _kb_error(error)
    return _detail_or_404(services, document_id)


@router.post("/documents/{document_id}/archive")
def archive(document_id: uuid.UUID, request: Request, caller: Caller = Depends(admin_caller)):
    services = _services(request)
    assert services.kb_service is not None
    try:
        services.kb_service.archive(document_id)
    except KbError as error:
        return _kb_error(error)
    return _detail_or_404(services, document_id)


@router.delete("/documents/{document_id}/draft")
def discard_draft(document_id: uuid.UUID, request: Request, caller: Caller = Depends(admin_caller)):
    services = _services(request)
    assert services.kb_service is not None
    try:
        services.kb_service.discard_draft(document_id)
    except KbError as error:
        return _kb_error(error)
    return _detail_or_404(services, document_id)


@router.get("/documents/{document_id}/versions/{version}")
def version_detail(document_id: uuid.UUID, version: int, request: Request,
                   caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    assert services.kb_queries is not None
    detail = services.kb_queries.version_detail(document_id, version)
    if detail is None:
        raise HTTPException(status_code=404, detail="VERSION_NOT_FOUND")
    return detail


@router.post("/documents/{document_id}/versions/{version}/restore")
def restore(document_id: uuid.UUID, version: int, request: Request, caller: Caller = Depends(admin_caller)):
    """Khôi phục = công bố lại nội dung phiên bản cũ thành **phiên bản mới** (không sửa lịch sử)."""
    services = _services(request)
    assert services.kb_service is not None
    try:
        services.kb_service.restore(document_id, version, actor=caller.user_id)
    except KbError as error:
        return _kb_error(error)
    return _detail_or_404(services, document_id)


@router.get("/unanswered")
def unanswered(request: Request, days: int = Query(30, ge=1, le=90), caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    assert services.kb_queries is not None
    return {"items": services.kb_queries.unanswered(datetime.now(UTC) - timedelta(days=days))}


@router.get("/stats")
def stats(request: Request, start: datetime | None = Query(None, alias="from"),
          end: datetime | None = Query(None, alias="to"), caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    assert services.kb_queries is not None
    end = end or datetime.now(UTC)
    start = start or end - timedelta(days=30)
    return services.kb_queries.stats(start, end)


@router.get("/settings/{key}")
def get_setting(key: str, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    if key not in EDITABLE_SETTINGS:
        raise HTTPException(status_code=404, detail="SETTING_NOT_FOUND")
    return {"key": key, "value": services.read_setting(key)}


@router.put("/settings/{key}")
def put_setting(key: str, body: dict, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    services = _services(request)
    if key not in EDITABLE_SETTINGS:
        raise HTTPException(status_code=404, detail="SETTING_NOT_FOUND")
    services.write_setting(key, body.get("value"), caller.user_id)
    return {"key": key, "value": services.read_setting(key)}


class ExemplarPayload(BaseModel):
    text: str = Field(min_length=3, max_length=500)
    label: str = Field(pattern="^(IN|OUT)$")


@router.post("/scope-exemplars", status_code=201)
def add_exemplar(body: ExemplarPayload, request: Request, caller: Caller = Depends(admin_caller)) -> dict:
    """Chuyển một câu bị chặn nhầm thành câu mẫu trong phạm vi (04 mục 4.11)."""
    services = _services(request)
    services.add_scope_exemplar(body.text, body.label, caller.user_id)
    return {"text": body.text, "label": body.label}
