"""Lưu và đọc hội thoại: `ConversationStore` với bản PostgreSQL và bản trong bộ nhớ (thiết kế 06 mục 6.4).

Mọi thao tác đọc đều đi kèm kiểm tra quyền sở hữu ở tầng gọi (InputGuard / API): hội thoại không thuộc người hỏi
được coi như không tồn tại (404), để không lộ việc mã đó có thật (09 mục 9.3 "Cách ly").
"""

from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session, sessionmaker

from app.agent.state import HistoryTurn
from app.db.models import (
    Conversation,
    DeletionLog,
    Message,
    MessageCitation,
    MessageFeedback,
    ToolCallLog,
    TurnTrace,
)


@dataclass
class ConversationInfo:
    id: uuid.UUID
    user_id: uuid.UUID
    role: str
    title: str
    created_at: datetime
    last_message_at: datetime
    ended_at: datetime | None
    deleted_at: datetime | None
    session_state: dict | None
    turn_count: int


@dataclass
class TurnRecord:
    """Mọi thứ cần lưu sau một lượt (PersistenceMiddleware dựng). Không chứa kết quả tool hay token người dùng."""

    user_id: uuid.UUID
    role: str
    conversation_id: uuid.UUID | None
    page_path: str
    question: str  # đã che thông tin cá nhân
    rewritten_query: str
    answer: str  # đã che thông tin cá nhân
    intent: str
    grounded: str
    fallback_reason: str | None
    verification: dict
    actions: list[dict]
    citations: list[dict]
    llm_model: str
    prompt_version: str
    candidates: list[dict] = field(default_factory=list)  # chunk_id, rank, các điểm, in_context, cited
    tool_logs: list[dict] = field(default_factory=list)
    trace: dict = field(default_factory=dict)
    session_state: dict = field(default_factory=dict)
    now: datetime | None = None


@dataclass
class SavedTurn:
    conversation_id: uuid.UUID
    user_message_id: uuid.UUID
    assistant_message_id: uuid.UUID


class ConversationStore:
    def get(self, conversation_id: uuid.UUID) -> ConversationInfo | None:
        raise NotImplementedError

    def history(self, conversation_id: uuid.UUID, max_turns: int) -> list[HistoryTurn]:
        raise NotImplementedError

    def save_turn(self, record: TurnRecord) -> SavedTurn:
        raise NotImplementedError

    def end(self, conversation_id: uuid.UUID, when: datetime) -> None:
        raise NotImplementedError

    def turn_lock(self, conversation_id: uuid.UUID) -> threading.Lock:
        """Khoá theo hội thoại: mỗi lúc chỉ một lượt của một phiên được xử lý (04 mục 4.10, 409 BUSY)."""
        raise NotImplementedError

    def list_for_user(self, user_id: uuid.UUID, page: int, size: int) -> tuple[list[ConversationInfo], int]:
        raise NotImplementedError

    def messages(self, conversation_id: uuid.UUID) -> list[dict[str, Any]]:
        raise NotImplementedError

    def mark_deleted(self, conversation_id: uuid.UUID, when: datetime) -> None:
        raise NotImplementedError

    def assistant_message_owner(self, message_id: uuid.UUID) -> uuid.UUID | None:
        raise NotImplementedError

    def save_feedback(self, message_id: uuid.UUID, user_id: uuid.UUID, rating: int, reason: str | None,
                      comment: str | None) -> None:
        raise NotImplementedError


def _title(question: str) -> str:
    return question if len(question) <= 80 else question[:77].rstrip() + "…"


# ---------------------------------------------------------------------------------------------- PostgreSQL


class PgConversationStore(ConversationStore):
    def __init__(self, session_factory: sessionmaker[Session]):
        self._sessions = session_factory
        self._locks: dict[uuid.UUID, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    def get(self, conversation_id: uuid.UUID) -> ConversationInfo | None:
        with self._sessions() as session:
            row = session.get(Conversation, conversation_id)
            if row is None:
                return None
            turns = session.scalar(select(func.count()).select_from(Message).where(
                Message.conversation_id == conversation_id, Message.sender == "USER")) or 0
            return ConversationInfo(row.id, row.user_id, row.user_role, row.title, row.created_at,
                                    row.last_message_at, row.ended_at, row.deleted_at, row.session_state, turns)

    def history(self, conversation_id: uuid.UUID, max_turns: int) -> list[HistoryTurn]:
        """Các cặp hỏi – đáp gần nhất, cũ trước. Chỉ câu hỏi đã che + câu trả lời cuối (không kết quả tool)."""
        with self._sessions() as session:
            answers = session.execute(
                select(Message.reply_to_message_id, Message.content).where(
                    Message.conversation_id == conversation_id, Message.sender == "ASSISTANT")
                .order_by(Message.created_at.desc()).limit(max_turns)).all()
            question_ids = [row[0] for row in answers if row[0] is not None]
            questions = dict(session.execute(select(Message.id, Message.content)
                                             .where(Message.id.in_(question_ids))).all())
        turns = [HistoryTurn((questions.get(reply_to) if reply_to else None) or "", content or "")
                 for reply_to, content in answers]
        return [turn for turn in reversed(turns) if turn.question or turn.answer]

    def save_turn(self, record: TurnRecord) -> SavedTurn:
        with self._sessions.begin() as session:
            conversation_id = record.conversation_id
            if conversation_id is None:
                conversation = Conversation(user_id=record.user_id, user_role=record.role,
                                            title=_title(record.question))
                session.add(conversation)
                session.flush()
                conversation_id = conversation.id
            user_message = Message(conversation_id=conversation_id, sender="USER", content=record.question,
                                   rewritten_query=record.rewritten_query or None, page_path=record.page_path)
            session.add(user_message)
            session.flush()
            answer = Message(
                conversation_id=conversation_id, sender="ASSISTANT", reply_to_message_id=user_message.id,
                content=record.answer, intent=record.intent, grounded=record.grounded,
                fallback_reason=record.fallback_reason, verification=record.verification,
                actions=record.actions, citations=record.citations, page_path=record.page_path,
                llm_model=record.llm_model, prompt_version=record.prompt_version,
            )
            session.add(answer)
            session.flush()
            for candidate in record.candidates:
                session.add(MessageCitation(message_id=answer.id, **candidate))
            for log in record.tool_logs:
                session.add(ToolCallLog(message_id=answer.id, **log))
            if record.trace:
                session.add(TurnTrace(message_id=answer.id, **record.trace))
            session.execute(update(Conversation).where(Conversation.id == conversation_id).values(
                last_message_at=func.now(), session_state=record.session_state))
            return SavedTurn(conversation_id, user_message.id, answer.id)

    def end(self, conversation_id: uuid.UUID, when: datetime) -> None:
        with self._sessions.begin() as session:
            session.execute(update(Conversation).where(Conversation.id == conversation_id,
                                                       Conversation.ended_at.is_(None)).values(ended_at=when))

    def turn_lock(self, conversation_id: uuid.UUID) -> threading.Lock:
        # Một tiến trình assistant-service → khoá trong bộ nhớ là đủ. Chạy nhiều bản thì đổi sang
        # pg_advisory_lock (ghi ở hướng phát triển).
        with self._locks_guard:
            return self._locks.setdefault(conversation_id, threading.Lock())

    # ------------------------------------------------------------------ dùng cho API và job dọn dẹp

    def list_for_user(self, user_id: uuid.UUID, page: int, size: int) -> tuple[list[ConversationInfo], int]:
        with self._sessions() as session:
            base = select(Conversation).where(Conversation.user_id == user_id, Conversation.deleted_at.is_(None))
            total = session.scalar(select(func.count()).select_from(base.subquery())) or 0
            rows = session.scalars(base.order_by(Conversation.last_message_at.desc())
                                   .offset(page * size).limit(size)).all()
        return [ConversationInfo(row.id, row.user_id, row.user_role, row.title, row.created_at, row.last_message_at,
                                 row.ended_at, row.deleted_at, None, 0) for row in rows], total

    def messages(self, conversation_id: uuid.UUID) -> list[dict[str, Any]]:
        with self._sessions() as session:
            rows = session.scalars(select(Message).where(Message.conversation_id == conversation_id)
                                   .order_by(Message.created_at, Message.sender.desc())).all()
            ids_with_feedback = dict(session.execute(select(MessageFeedback.message_id, MessageFeedback.rating).where(
                MessageFeedback.message_id.in_([row.id for row in rows]))).all())
        return [{"id": row.id, "sender": row.sender, "content": row.content, "intent": row.intent,
                 "grounded": row.grounded, "citations": row.citations or [], "actions": row.actions or [],
                 "createdAt": row.created_at, "rating": ids_with_feedback.get(row.id)} for row in rows]

    def mark_deleted(self, conversation_id: uuid.UUID, when: datetime) -> None:
        with self._sessions.begin() as session:
            session.execute(update(Conversation).where(Conversation.id == conversation_id)
                            .values(deleted_at=when, ended_at=func.coalesce(Conversation.ended_at, when)))

    def assistant_message_owner(self, message_id: uuid.UUID) -> uuid.UUID | None:
        with self._sessions() as session:
            return session.scalar(select(Conversation.user_id).join(Message, Message.conversation_id == Conversation.id)
                                  .where(Message.id == message_id, Message.sender == "ASSISTANT",
                                         Conversation.deleted_at.is_(None)))

    def save_feedback(self, message_id: uuid.UUID, user_id: uuid.UUID, rating: int, reason: str | None,
                      comment: str | None) -> None:
        """Gửi lại → ghi đè (07 mục 7.2)."""
        with self._sessions.begin() as session:
            session.execute(delete(MessageFeedback).where(MessageFeedback.message_id == message_id))
            session.add(MessageFeedback(message_id=message_id, user_id=user_id, rating=rating, reason=reason,
                                        comment=comment))

    def purge(self, conversation_id: uuid.UUID) -> DeletionLog:
        """Xoá thật mọi mảnh của một hội thoại rồi kiểm chứng = 0 dòng trên cả 6 bảng (06 mục 6.4, 09 mục 9.3)."""
        with self._sessions.begin() as session:
            message_ids = select(Message.id).where(Message.conversation_id == conversation_id).scalar_subquery()
            counts = {
                "message_feedback": _count(session, MessageFeedback, MessageFeedback.message_id.in_(message_ids)),
                "turn_trace": _count(session, TurnTrace, TurnTrace.message_id.in_(message_ids)),
                "tool_call_log": _count(session, ToolCallLog, ToolCallLog.message_id.in_(message_ids)),
                "message_citation": _count(session, MessageCitation, MessageCitation.message_id.in_(message_ids)),
                "message": _count(session, Message, Message.conversation_id == conversation_id),
            }
            requested_at = session.scalar(select(Conversation.deleted_at).where(Conversation.id == conversation_id))
            counts["conversation"] = _count(session, Conversation, Conversation.id == conversation_id)
            session.execute(delete(Conversation).where(Conversation.id == conversation_id))
        verified = self.remaining_rows(conversation_id) == 0
        log = DeletionLog(conversation_id=conversation_id, requested_at=requested_at, rows_deleted=counts,
                          verified=verified)
        with self._sessions.begin() as session:
            session.add(log)
        return log

    def remaining_rows(self, conversation_id: uuid.UUID) -> int:
        with self._sessions() as session:
            message_ids = select(Message.id).where(Message.conversation_id == conversation_id).scalar_subquery()
            return sum([
                _count(session, Conversation, Conversation.id == conversation_id),
                _count(session, Message, Message.conversation_id == conversation_id),
                _count(session, MessageCitation, MessageCitation.message_id.in_(message_ids)),
                _count(session, ToolCallLog, ToolCallLog.message_id.in_(message_ids)),
                _count(session, TurnTrace, TurnTrace.message_id.in_(message_ids)),
                _count(session, MessageFeedback, MessageFeedback.message_id.in_(message_ids)),
            ])


def _count(session: Session, model, condition) -> int:
    return session.scalar(select(func.count()).select_from(model).where(condition)) or 0


# ---------------------------------------------------------------------------------------------- Trong bộ nhớ


class MemoryConversationStore(ConversationStore):
    """Bản giả cho test T0 và eval không cần DB. Giữ đúng các quy tắc quan trọng (sở hữu, kết thúc, lịch sử)."""

    def __init__(self) -> None:
        self.conversations: dict[uuid.UUID, ConversationInfo] = {}
        self.turns: dict[uuid.UUID, list[TurnRecord]] = {}
        self.saved: list[TurnRecord] = []
        self.feedback: dict[uuid.UUID, dict] = {}
        self._answer_owner: dict[uuid.UUID, tuple[uuid.UUID, int]] = {}  # message_id → (hội thoại, chỉ số lượt)
        self._locks: dict[uuid.UUID, threading.Lock] = {}

    def get(self, conversation_id: uuid.UUID) -> ConversationInfo | None:
        info = self.conversations.get(conversation_id)
        if info is not None:
            info.turn_count = len(self.turns.get(conversation_id, []))
        return info

    def history(self, conversation_id: uuid.UUID, max_turns: int) -> list[HistoryTurn]:
        records = self.turns.get(conversation_id, [])[-max_turns:] if max_turns > 0 else []
        return [HistoryTurn(record.question, record.answer) for record in records]

    def save_turn(self, record: TurnRecord) -> SavedTurn:
        conversation_id = record.conversation_id
        now = record.now or datetime.now()
        if conversation_id is None:
            conversation_id = uuid.uuid4()
            self.conversations[conversation_id] = ConversationInfo(
                conversation_id, record.user_id, record.role, _title(record.question), now, now, None, None, None, 0)
        info = self.conversations[conversation_id]
        info.session_state = record.session_state
        info.last_message_at = now
        turns = self.turns.setdefault(conversation_id, [])
        turns.append(record)
        self.saved.append(record)
        answer_id = uuid.uuid4()
        self._answer_owner[answer_id] = (conversation_id, len(turns) - 1)
        return SavedTurn(conversation_id, uuid.uuid4(), answer_id)

    def end(self, conversation_id: uuid.UUID, when: datetime) -> None:
        info = self.conversations.get(conversation_id)
        if info is not None and info.ended_at is None:
            info.ended_at = when

    def turn_lock(self, conversation_id: uuid.UUID) -> threading.Lock:
        return self._locks.setdefault(conversation_id, threading.Lock())

    def list_for_user(self, user_id: uuid.UUID, page: int, size: int) -> tuple[list[ConversationInfo], int]:
        mine = [info for info in self.conversations.values() if info.user_id == user_id and info.deleted_at is None]
        mine.sort(key=lambda info: info.last_message_at, reverse=True)
        return mine[page * size:(page + 1) * size], len(mine)

    def messages(self, conversation_id: uuid.UUID) -> list[dict[str, Any]]:
        answers = {index: message_id for message_id, (owner, index) in self._answer_owner.items()
                   if owner == conversation_id}
        result: list[dict[str, Any]] = []
        for index, record in enumerate(self.turns.get(conversation_id, [])):
            answer_id = answers.get(index)
            result.append({"id": None, "sender": "USER", "content": record.question, "intent": None,
                           "grounded": None, "citations": [], "actions": [], "createdAt": record.now, "rating": None})
            result.append({"id": answer_id, "sender": "ASSISTANT", "content": record.answer,
                           "intent": record.intent, "grounded": record.grounded, "citations": record.citations,
                           "actions": record.actions, "createdAt": record.now,
                           "rating": self.feedback.get(answer_id, {}).get("rating") if answer_id else None})
        return result

    def mark_deleted(self, conversation_id: uuid.UUID, when: datetime) -> None:
        info = self.conversations.get(conversation_id)
        if info is not None:
            info.deleted_at = when
            info.ended_at = info.ended_at or when

    def assistant_message_owner(self, message_id: uuid.UUID) -> uuid.UUID | None:
        owner = self._answer_owner.get(message_id)
        if owner is None:
            return None
        info = self.conversations[owner[0]]
        return None if info.deleted_at is not None else info.user_id

    def save_feedback(self, message_id: uuid.UUID, user_id: uuid.UUID, rating: int, reason: str | None,
                      comment: str | None) -> None:
        self.feedback[message_id] = {"user_id": user_id, "rating": rating, "reason": reason, "comment": comment}
