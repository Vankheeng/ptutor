"""Truy vấn chỉ-đọc cho trang quản trị kho tri thức (thiết kế 07 mục 7.3, 08 mục 8.2, 06 mục 6.5).

Tách khỏi `KnowledgeBaseService` (lo vòng đời: lưu, công bố, khôi phục) để mỗi file làm một việc.
- Danh sách tài liệu có bộ lọc và cờ "có nháp", "còn todo", "công bố khi còn todo".
- Chi tiết một tài liệu: bản đang công bố, bản nháp, lịch sử phiên bản.
- Câu hỏi chưa trả lời được (06 mục 6.5): câu trả lời không FULL hoặc bị 👎, 30 ngày; **không** trả `user_id`.
- Thống kê (07 mục 7.3): số lượt, tỉ lệ grounded, lý do từ chối, 👍 / 👎, độ trễ p50 / p95, token.
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, aliased, sessionmaker

from app.db.models import KbDocument, KbDocumentVersion, Message, MessageFeedback, TurnTrace


def _version_json(version: KbDocumentVersion | None, include_content: bool = True) -> dict[str, Any] | None:
    if version is None:
        return None
    data: dict[str, Any] = {
        "id": str(version.id), "version": version.version, "state": version.state, "title": version.title,
        "type": version.type, "audience": version.audience, "features": version.features, "routes": version.routes,
        "keywords": version.keywords, "errorCodes": version.error_codes, "sourceRefs": version.source_refs,
        "todo": version.todo, "origin": version.origin, "editedAt": version.edited_at,
        "publishedAt": version.published_at, "restoredFromVersion": version.restored_from_version,
        "publishedWithOpenTodo": version.published_with_open_todo,
    }
    if include_content:
        data["contentMd"] = version.content_md
    return data


class KbAdminQueries:
    def __init__(self, session_factory: sessionmaker[Session]):
        self._sessions = session_factory

    def list_documents(self, *, status: str = "", type_: str = "", feature: str = "", audience: str = "",
                       keyword: str = "", page: int = 0, size: int = 50) -> dict[str, Any]:
        with self._sessions() as session:
            documents = session.scalars(select(KbDocument).order_by(KbDocument.doc_key)).all()
            versions = session.scalars(select(KbDocumentVersion).where(
                KbDocumentVersion.state.in_(["DRAFT", "PUBLISHED", "ARCHIVED"]))).all()
        by_document: dict[uuid.UUID, dict[str, KbDocumentVersion]] = {}
        for version in versions:
            by_document.setdefault(version.document_id, {})[version.state] = version
        rows = []
        for document in documents:
            states = by_document.get(document.id, {})
            current = states.get("DRAFT") or states.get("PUBLISHED") or states.get("ARCHIVED")
            if current is None:
                continue
            published = states.get("PUBLISHED")
            row = {
                "id": str(document.id), "docKey": document.doc_key, "status": document.status, "title": current.title,
                "type": current.type, "audience": current.audience, "features": current.features,
                "hasDraft": "DRAFT" in states, "openTodo": len(current.todo or []),
                "publishedVersion": published.version if published else None,
                "publishedWithOpenTodo": bool(published and published.published_with_open_todo),
                "editedAt": current.edited_at,
            }
            if _matches(row, status, type_, feature, audience, keyword):
                rows.append(row)
        return {"content": rows[page * size:(page + 1) * size], "totalElements": len(rows), "page": page,
                "size": size}

    def document_detail(self, document_id: uuid.UUID) -> dict[str, Any] | None:
        with self._sessions() as session:
            document = session.get(KbDocument, document_id)
            if document is None:
                return None
            versions = session.scalars(select(KbDocumentVersion).where(KbDocumentVersion.document_id == document_id)
                                       .order_by(KbDocumentVersion.version.desc())).all()
        draft = next((version for version in versions if version.state == "DRAFT"), None)
        published = next((version for version in versions if version.state == "PUBLISHED"), None)
        return {
            "id": str(document.id), "docKey": document.doc_key, "status": document.status,
            "published": _version_json(published), "draft": _version_json(draft),
            "versions": [_version_json(version, include_content=False) for version in versions],
        }

    def version_detail(self, document_id: uuid.UUID, version: int) -> dict[str, Any] | None:
        with self._sessions() as session:
            row = session.scalar(select(KbDocumentVersion).where(KbDocumentVersion.document_id == document_id,
                                                                 KbDocumentVersion.version == version))
            return _version_json(row)

    def doc_key_exists(self, doc_key: str) -> bool:
        with self._sessions() as session:
            return session.scalar(select(KbDocument.id).where(KbDocument.doc_key == doc_key)) is not None

    def unanswered(self, since: datetime, limit: int = 200) -> list[dict[str, Any]]:
        """06 mục 6.5. Nội dung đã che từ lúc lưu; cố ý không chọn cột user_id."""
        question = aliased(Message)
        with self._sessions() as session:
            rows = session.execute(
                select(question.content, question.rewritten_query, Message.grounded, Message.fallback_reason,
                       Message.intent, MessageFeedback.reason, MessageFeedback.rating, Message.created_at,
                       Message.id)
                .join(question, question.id == Message.reply_to_message_id)
                .outerjoin(MessageFeedback, MessageFeedback.message_id == Message.id)
                .where(Message.sender == "ASSISTANT", Message.created_at > since,
                       or_(Message.grounded != "FULL", MessageFeedback.rating == -1))
                .order_by(Message.created_at.desc()).limit(limit)).all()
        return [{"question": row[0], "rewrittenQuery": row[1], "grounded": row[2], "fallbackReason": row[3],
                 "intent": row[4], "feedbackReason": row[5], "rating": row[6], "createdAt": row[7],
                 "messageId": str(row[8])} for row in rows if row[0]]

    def stats(self, start: datetime, end: datetime) -> dict[str, Any]:
        with self._sessions() as session:
            answers = session.execute(
                select(Message.grounded, Message.fallback_reason, Message.intent, TurnTrace.total_ms,
                       TurnTrace.tokens_in, TurnTrace.tokens_out)
                .outerjoin(TurnTrace, TurnTrace.message_id == Message.id)
                .where(Message.sender == "ASSISTANT", Message.created_at >= start, Message.created_at < end)).all()
            feedback = dict(session.execute(
                select(MessageFeedback.rating, func.count()).join(Message, Message.id == MessageFeedback.message_id)
                .where(Message.created_at >= start, Message.created_at < end)
                .group_by(MessageFeedback.rating)).all())
        latencies = sorted(row[3] for row in answers if row[3] is not None)
        tokens = [(row[4] or 0) + (row[5] or 0) for row in answers]
        return {
            "turns": len(answers),
            "grounded": _count(row[0] for row in answers),
            "fallbackReasons": _count(row[1] for row in answers if row[1]),
            "intents": _count(row[2] for row in answers),
            "feedback": {"up": feedback.get(1, 0), "down": feedback.get(-1, 0)},
            "latencyMs": {"p50": _percentile(latencies, 0.5), "p95": _percentile(latencies, 0.95)},
            "tokensPerTurn": sum(tokens) / len(tokens) if tokens else 0,
        }


def _matches(row: dict, status: str, type_: str, feature: str, audience: str, keyword: str) -> bool:
    text = (row["docKey"] + " " + row["title"]).lower()
    return ((not status or row["status"] == status) and (not type_ or row["type"] == type_)
            and (not feature or feature in row["features"]) and (not audience or audience in row["audience"])
            and (not keyword or keyword.lower() in text))


def _count(values) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        counts[str(value)] = counts.get(str(value), 0) + 1
    return counts


def _percentile(values: list[int], q: float) -> int:
    return values[min(len(values) - 1, int(round(q * (len(values) - 1))))] if values else 0
