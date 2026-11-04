"""Dữ liệu đi qua một lượt hỏi đáp: `TurnState`, ngân sách, quyết định, kết quả (thiết kế 02 mục 2.3).

`TurnState` là "phiếu theo dõi" của một lượt: mỗi middleware đọc và ghi vào đây. Không middleware nào gọi trực
tiếp middleware khác — chúng chỉ trao đổi qua `TurnState`, nên thêm / bỏ một middleware không phải sửa cái khác.
"""

from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any

from app.agent.output_schema import ActorOutput
from app.providers.llm import ChatMessage, LlmToolCall
from app.retrieval.retriever import RetrievalResult


class TurnError(Exception):
    """Lỗi trả cho backend dưới dạng HTTP (400 INVALID_MESSAGE, 404, 409...) — thiết kế 07 mục 7.2."""

    def __init__(self, status: int, code: str):
        super().__init__(code)
        self.status = status
        self.code = code


class DecisionKind(Enum):
    ACCEPT = "ACCEPT"
    RETRY = "RETRY"
    ABSTAIN = "ABSTAIN"


@dataclass(frozen=True)
class Decision:
    """Kết luận của một middleware ở `after_model`: chấp nhận, cho sinh lại kèm góp ý, hoặc từ chối trả lời."""

    kind: DecisionKind
    feedback: str = ""
    reason: str = ""  # fallback_reason khi ABSTAIN: SCHEMA_INVALID, VERIFY_FAILED, BUDGET...

    @staticmethod
    def accept() -> Decision:
        return Decision(DecisionKind.ACCEPT)

    @staticmethod
    def retry(feedback: str, reason: str = "") -> Decision:
        return Decision(DecisionKind.RETRY, feedback=feedback, reason=reason)

    @staticmethod
    def abstain(reason: str) -> Decision:
        return Decision(DecisionKind.ABSTAIN, reason=reason)


@dataclass
class Budget:
    """Hạn mức mỗi lượt (thiết kế 04 mục 4.9). Không tính lời gọi verifier vào `max_model_calls`."""

    max_model_calls: int = 4
    max_tool_calls: int = 3
    max_tokens: int = 40_000
    max_seconds: float = 25.0
    model_calls: int = 0
    tool_calls: int = 0
    tokens: int = 0
    started_at: float = field(default_factory=time.monotonic)

    def elapsed(self) -> float:
        return time.monotonic() - self.started_at

    def exhausted(self) -> str | None:
        """Lý do đã chạm trần, hoặc None. Dùng trước khi gọi model / tool."""
        if self.model_calls >= self.max_model_calls:
            return "MODEL_CALLS"
        if self.tokens >= self.max_tokens:
            return "TOKENS"
        if self.elapsed() >= self.max_seconds:
            return "TIME"
        return None


@dataclass
class Span:
    """Một bước của lượt, cho trace / benchmark B2 (06 `turn_trace.spans`, 13 mục 13.11)."""

    name: str
    kind: str  # model | tool | retrieval | verify | step
    start_ms: int
    duration_ms: int
    tokens_in: int = 0
    tokens_out: int = 0
    status: str = "OK"  # OK | ERROR | SKIPPED | BLOCKED
    detail: dict[str, Any] = field(default_factory=dict)


@dataclass
class ToolCallRecord:
    """Một lời gọi tool trong lượt; ghi vào `tool_call_log` (không có nội dung kết quả)."""

    call: LlmToolCall
    blocked_reason: str | None = None
    http_status: int | None = None
    latency_ms: int = 0
    result_items: int | None = None
    result_text: str = ""  # kết quả đã lọc, chỉ để đưa model trong lượt này — KHÔNG lưu DB
    result_data: Any = None  # dữ liệu đã lọc (dict/list) cho kiểm tra số liệu
    focus_items: list[FocusItem] = field(default_factory=list)  # đối tượng có ID trong kết quả → session focus


@dataclass
class HistoryTurn:
    question: str
    answer: str


@dataclass
class FocusItem:
    """Một đối tượng đang bàn (thiết kế 04 mục 4.10): chỉ ID + nhãn ngắn, chỉ lấy từ kết quả tool."""

    type: str
    id: str
    label: str
    source: str
    turn: int
    last_turn: int
    # Lượt gần nhất người dùng gọi đúng tên đối tượng ("hợp đồng Toán"). Chỉ dùng để xếp thứ tự các đối tượng đã có
    # trong sổ tay — lời người dùng không bao giờ thêm được đối tượng / ID mới.
    mentioned_turn: int = 0

    def rank(self) -> tuple[int, int]:
        """Mức "đang bàn": nhắc gần hơn trước, cùng lượt thì đối tượng người dùng gọi tên trước."""
        return (self.last_turn, self.mentioned_turn)


@dataclass
class SessionState:
    focus: list[FocusItem] = field(default_factory=list)

    def seen_ids(self) -> set[str]:
        return {item.id for item in self.focus}

    def to_json(self) -> dict:
        return {"focus": [item.__dict__ for item in self.focus]}

    @staticmethod
    def from_json(data: dict | None) -> SessionState:
        return SessionState(focus=[FocusItem(**item) for item in (data or {}).get("focus", [])])


@dataclass
class Caller:
    user_id: uuid.UUID
    role: str
    authorization: str = ""  # token của người hỏi — chỉ ToolGate dùng, không log, không lưu

    def __repr__(self) -> str:
        return f"Caller(user_id={self.user_id}, role={self.role})"


@dataclass
class TurnResult:
    """Kết quả một lượt, trước khi đổi sang `ChatResponse`."""

    answer: str
    intent: str = "POLICY"
    grounded: str = "NONE"  # FULL | PARTIAL | NONE
    fallback_reason: str | None = None
    citations: list = field(default_factory=list)
    actions: list[dict] = field(default_factory=list)
    suggestions: list[str] = field(default_factory=list)
    conversation_id: uuid.UUID | None = None
    message_id: uuid.UUID | None = None
    show_support_button: bool = False
    suggest_new_conversation: bool = False  # hội thoại vượt 20 lượt → widget gợi ý mở cuộc mới (04 mục 4.10)


@dataclass
class TurnState:
    caller: Caller
    question: str  # câu hỏi gốc người dùng gõ
    page_path: str
    now: datetime
    request_id: str = ""
    conversation_id: uuid.UUID | None = None
    safe_question: str = ""  # câu hỏi đã che thông tin cá nhân — bản duy nhất được gửi ra ngoài / lưu
    rewritten_query: str = ""
    query_vector: list[float] | None = None
    retrieval: RetrievalResult | None = None
    history: list[HistoryTurn] = field(default_factory=list)
    turn_number: int = 1
    session_state: SessionState = field(default_factory=SessionState)
    messages: list[ChatMessage] = field(default_factory=list)  # hội thoại model ↔ tool trong lượt này
    tool_calls: list[ToolCallRecord] = field(default_factory=list)
    output: ActorOutput | None = None
    budget: Budget = field(default_factory=Budget)
    schema_retries: int = 0
    verify_retries: int = 0
    retry_feedback: str = ""
    verification: dict[str, Any] = field(default_factory=dict)
    spans: list[Span] = field(default_factory=list)
    layer_tokens: dict[str, int] = field(default_factory=dict)
    final: TurnResult | None = None  # middleware đặt giá trị này để kết thúc lượt sớm
    started_at: float = field(default_factory=time.monotonic)

    @property
    def query(self) -> str:
        """Câu dùng để tìm tài liệu: câu đã viết lại (nếu có), không thì câu đã che."""
        return self.rewritten_query or self.safe_question

    def elapsed_ms(self) -> int:
        return int((time.monotonic() - self.started_at) * 1000)
