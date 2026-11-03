"""Lắp harness: danh sách middleware theo đúng thứ tự đăng ký + `AgentRunner` (thiết kế 02 mục 2.3).

Thứ tự đăng ký = thứ tự chạy ở mọi hook (wrap_* thì lớp đăng ký trước bọc ngoài). Đọc danh sách trong
`build_middlewares` là thấy toàn bộ "dây chuyền" của một lượt.
"""

from __future__ import annotations

from app.agent.compose import Composer
from app.agent.config_hash import config_hash
from app.agent.middleware.base import Middleware
from app.agent.middleware.budget import BudgetMiddleware
from app.agent.middleware.citation_verifier import CitationVerifierMiddleware
from app.agent.middleware.context_injection import ContextInjectionMiddleware
from app.agent.middleware.input_guard import InputGuardMiddleware
from app.agent.middleware.memory import MemoryMiddleware
from app.agent.middleware.model_call import ModelCallMiddleware
from app.agent.middleware.output_schema import OutputSchemaMiddleware
from app.agent.middleware.persistence import PersistenceMiddleware
from app.agent.middleware.query import QueryMiddleware
from app.agent.middleware.redact import RedactMiddleware
from app.agent.middleware.retrieval import RetrievalMiddleware
from app.agent.middleware.route_guard import RouteGuardMiddleware
from app.agent.middleware.scope_guard import ScopeGuardMiddleware
from app.agent.middleware.trace import TraceMiddleware
from app.agent.output_schema import ACTOR_OUTPUT_SCHEMA
from app.agent.runner import AgentRunner
from app.agent.state import TurnState
from app.providers.llm import LlmRequest
from app.services import Services


def build_middlewares(services: Services, composer: Composer) -> list[Middleware]:
    settings = services.settings
    assert services.retriever is not None and services.llm is not None
    middlewares: list[Middleware] = [
        TraceMiddleware(),                                                    # ①③⑤⑥ mở / đóng trace
        BudgetMiddleware(settings, composer),                                 # ①②④⑤ ngân sách
        InputGuardMiddleware(services.conversations, settings.max_message_chars),  # ① đầu vào, sở hữu, che
        MemoryMiddleware(services.conversations, settings.history_turns),    # ①⑥ lịch sử + sổ tay phiên
        QueryMiddleware(services.llm, services.embedder, settings.effective_rewrite_model,
                        rewrite_enabled=settings.rewrite_enabled),            # ① viết lại câu hỏi + embed
        RetrievalMiddleware(services.retriever, services.embedder),           # ① truy xuất lai
        ContextInjectionMiddleware(services.routes, services.counter, composer,
                                   context_token_cap=settings.context_token_cap,
                                   history_budget=settings.history_token_budget),  # ② dựng ngữ cảnh
        ModelCallMiddleware(settings.llm_timeout_s, settings.llm_max_output_tokens),  # ③ timeout, thử lại
        OutputSchemaMiddleware(),                                             # ④ đúng schema JSON
        CitationVerifierMiddleware(services.llm, settings.effective_verifier_model,
                                   max_retries=settings.max_verify_retries,
                                   use_llm_verifier=settings.verifier_enabled),  # ④' kiểm chứng trích dẫn
        RouteGuardMiddleware(services.routes),                                # ④' chỉ route hợp lệ, ID đã thấy
        RedactMiddleware(),                                                   # ⑥ che trước khi lưu
        PersistenceMiddleware(services.conversations, settings.llm_model,
                              lambda: config_hash(settings)),                 # ⑥ lưu
    ]
    if settings.scope_guard_enabled:
        # ① chặn ngoài phạm vi: sau Query (cần vector, câu đã viết lại), trước Retrieval (không tốn truy xuất).
        position = next(index for index, item in enumerate(middlewares) if isinstance(item, RetrievalMiddleware))
        middlewares.insert(position, ScopeGuardMiddleware(lambda: services.scope_rules, services.exemplars, composer,
                                                          tau_in=settings.scope_tau_in, delta=settings.scope_delta))
    return middlewares


def build_runner(services: Services) -> AgentRunner:
    assert services.llm is not None
    settings = services.settings
    composer = Composer(services.suggestions)

    def base_request(state: TurnState) -> LlmRequest:
        return LlmRequest(messages=[], model=settings.llm_model, response_schema=ACTOR_OUTPUT_SCHEMA,
                          temperature=settings.llm_temperature, max_output_tokens=settings.llm_max_output_tokens,
                          timeout_s=settings.llm_timeout_s, purpose="actor")

    return AgentRunner(services.llm, build_middlewares(services, composer), composer, base_request)
