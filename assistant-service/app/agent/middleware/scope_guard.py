"""`ScopeGuardMiddleware` — hook ① (thiết kế 04 mục 4.11, QĐ14).

Chạy **sau** khi viết lại câu hỏi và embed (để câu tiếp nối được xét bằng câu đầy đủ), **trước** truy xuất.

Ba tầng, từ rẻ đến đắt:
1. **Luật**: chào hỏi → CHITCHAT; mẫu rõ ràng ngoài phạm vi (không nhắc nghiệp vụ PTutor) → OUT_OF_SCOPE.
2. **Câu mẫu**: `in` = cosine lớn nhất tới câu mẫu trong phạm vi, `out` = tới câu mẫu ngoài phạm vi.
   `in ≥ τ_in` → cho qua; `in < τ_in` và `out − in ≥ δ` → OUT_OF_SCOPE; còn lại "chưa chắc" → cho qua.
3. **Actor**: câu chưa chắc đi tiếp bình thường; Actor tự có intent OUT_OF_SCOPE.

Đây là lớp về **phạm vi và chi phí**, không phải lớp bảo mật: bị vượt thì trợ lý vẫn không làm được gì ngoài phạm vi
người dùng tự xem được. Vì vậy ưu tiên **không chặn nhầm** (câu bỏ lọt vẫn bị tầng 3 xử lý).
"""

from __future__ import annotations

from collections.abc import Callable

from app.agent.compose import Composer
from app.agent.middleware.base import Middleware
from app.agent.state import TurnState
from app.agent.trace import timed
from app.scope.exemplars import ExemplarStore, score
from app.scope.rules import ScopeRules, classify_by_rules


def scope_decision(question: str, vector: list[float], rules: ScopeRules, exemplars: ExemplarStore,
                   tau_in: float, delta: float, exclude_text: str | None = None) -> tuple[str | None, str, dict]:
    """Trả (quyết định, tầng, chi tiết). Quyết định: `CHITCHAT`, `OUT_OF_SCOPE` hoặc None (cho qua).
    Tách thành hàm thuần để script đo (`eval/scope_eval.py`) dùng đúng logic của middleware."""
    by_rule = classify_by_rules(question, rules)
    if by_rule is not None:
        return by_rule, "RULE", {}
    items = exemplars.all()
    if not items:
        return None, "NONE", {}
    scores = score(vector, items, exclude_text=exclude_text)
    detail = {"in": round(scores.best_in, 4), "out": round(scores.best_out, 4)}
    if scores.best_in >= tau_in:
        return None, "EMBEDDING", detail
    if scores.best_out - scores.best_in >= delta:
        return "OUT_OF_SCOPE", "EMBEDDING", detail
    return None, "EMBEDDING", detail


class ScopeGuardMiddleware(Middleware):
    name = "scope_guard"

    def __init__(self, rules: Callable[[], ScopeRules], exemplars: ExemplarStore, composer: Composer, *,
                 tau_in: float, delta: float):
        self._rules = rules
        self._exemplars = exemplars
        self._composer = composer
        self._tau_in = tau_in
        self._delta = delta

    def before_agent(self, state: TurnState) -> None:
        assert state.query_vector is not None, "QueryMiddleware phải chạy trước ScopeGuard"
        with timed(state, "scope_guard") as span:
            decision, tier, detail = scope_decision(state.query, state.query_vector, self._rules(),
                                                    self._exemplars, self._tau_in, self._delta)
            span.detail = {"tier": tier, "decision": decision or "PASS", **detail}
        if decision == "CHITCHAT":
            state.final = self._composer.chitchat(state)
        elif decision == "OUT_OF_SCOPE":
            state.final = self._composer.out_of_scope(state, f"OUT_OF_SCOPE_{tier}")
