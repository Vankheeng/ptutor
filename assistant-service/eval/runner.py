"""Chạy bộ eval trên harness thật (thiết kế 13 mục 13.3, 13.6): mỗi case chạy k lần, chấm bằng code, ghi kết quả.

Môi trường cố định để kết quả lặp lại được (13 mục 13.3):
- kho tri thức nạp từ thư mục snapshot (mặc định `knowledge-base/`), công bố toàn bộ (kể cả tài liệu còn todo);
- tool ở chế độ **mock** theo `scenario` của case; đồng hồ cố định theo `now` của case;
- embedding có cache theo `(model, sha256(văn bản))` ở `eval/.cache/`;
- cấu hình harness từ `eval/configs/*.yaml`, `config_hash` ghi vào kết quả.

Giới hạn chi phí cả lần chạy: `max_llm_calls`, `max_tokens` — chạm trần thì dừng, ghi phần đã chạy.
"""

from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from app.agent.config_hash import config_hash
from app.agent.harness import build_runner
from app.agent.state import Caller, TurnState
from app.clock import FixedClock
from app.config import Settings
from app.db.conversations import MemoryConversationStore
from app.kb.routes import RouteCatalog, load_routes_file
from app.kb.tokens import load_token_counter
from app.providers.embedding import EmbeddingProvider, EmbeddingResult
from app.providers.factory import build_embedder, build_retriever
from app.providers.llm import LlmProvider, LlmRequest, LlmResponse, OpenAiLlm, ScriptedLlm
from app.retrieval.store import KnowledgeStore, MemoryKnowledgeStore, PgKnowledgeStore
from app.scope.exemplars import MemoryExemplarStore, dataset_texts, embed_exemplars
from app.services import Services
from app.tools.backend_client import MockBackendClient
from app.tools.executor import ToolExecutor
from app.tools.registry import ToolRegistry
from eval.cases import DATASETS, Case
from eval.graders.code_graders import METRIC_ONLY, GradeResult, TurnObservation, grade_turn
from eval.scripted_policy import scripted_policy

EVAL_DIR = Path(__file__).resolve().parent
REPO = EVAL_DIR.parents[1]
FAKE_TOKEN = "Bearer eval-token"  # mock không kiểm token; grader kiểm token không lọt vào ngữ cảnh / log


class RunBudgetExceeded(Exception):
    """Vượt giới hạn tổng của lần chạy eval (số lời gọi model hoặc token)."""


class GuardedLlm(LlmProvider):
    """Bọc LLM: ghi lại nội dung gửi model của lượt hiện tại (cho grader rò dữ liệu) và đếm chi phí cả lần chạy."""

    def __init__(self, inner: LlmProvider, max_calls: int, max_tokens: int):
        self._inner = inner
        self.max_calls, self.max_tokens = max_calls, max_tokens
        self.calls = 0
        self.tokens = 0
        self.turn_inputs: list[str] = []

    def complete(self, request: LlmRequest) -> LlmResponse:
        if self.calls >= self.max_calls or self.tokens >= self.max_tokens:
            raise RunBudgetExceeded(f"Đã dùng {self.calls} lời gọi / {self.tokens} token")
        self.turn_inputs.extend(message.content for message in request.messages)
        response = self._inner.complete(request)
        self.calls += 1
        self.tokens += response.tokens_in + response.tokens_out
        return response


class CachedEmbedding(EmbeddingProvider):
    """Cache vector theo (model, sha256) — eval chạy lại không phải embed lại (13 mục 13.3)."""

    def __init__(self, inner: EmbeddingProvider, cache_dir: Path):
        self._inner = inner
        self.model_name = inner.model_name
        self._file = cache_dir / f"{inner.model_name}.json"
        self._cache: dict[str, list[float]] = json.loads(self._file.read_text()) if self._file.is_file() else {}
        self._dirty = False

    def embed(self, texts: list[str]) -> EmbeddingResult:
        keys = [hashlib.sha256(text.encode("utf-8")).hexdigest() for text in texts]
        missing = [text for text, key in zip(texts, keys, strict=True) if key not in self._cache]
        if missing:
            result = self._inner.embed(missing)
            for text, vector in zip(missing, result.vectors, strict=True):
                self._cache[hashlib.sha256(text.encode("utf-8")).hexdigest()] = vector
            self._dirty = True
        return EmbeddingResult([self._cache[key] for key in keys], self.model_name)

    def save(self) -> None:
        if self._dirty:
            self._file.parent.mkdir(parents=True, exist_ok=True)
            self._file.write_text(json.dumps(self._cache))


@dataclass
class EvalConfig:
    name: str
    llm: str = "scripted"  # scripted | openai
    store: str = "memory"  # memory | pg
    kb_snapshot: str = "../knowledge-base"
    settings: dict[str, Any] = field(default_factory=dict)

    @staticmethod
    def load(path: Path) -> EvalConfig:
        raw = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return EvalConfig(name=raw.get("name", path.stem), llm=raw.get("llm", "scripted"),
                          store=raw.get("store", "memory"), kb_snapshot=raw.get("kb_snapshot", "../knowledge-base"),
                          settings=raw.get("settings") or {})


@dataclass
class Environment:
    settings: Settings
    services: Services
    llm: GuardedLlm
    client: MockBackendClient
    embedder: CachedEmbedding
    config: EvalConfig


def build_environment(config: EvalConfig, *, max_llm_calls: int, max_tokens: int) -> Environment:
    settings = Settings(**{**config.settings, "tools_mode": "mock", "internal_token": "eval"})
    embedder = CachedEmbedding(build_embedder(settings), EVAL_DIR / ".cache")
    counter = load_token_counter(settings.tokenizer_path)
    store: KnowledgeStore
    if config.store == "pg":
        from app.db.session import make_engine, make_session_factory

        store = PgKnowledgeStore(make_session_factory(make_engine(settings.database_url)))
    else:
        store = MemoryKnowledgeStore.from_directory(Path(config.kb_snapshot), embedder, counter)
    if config.llm == "openai":
        if not settings.llm_enabled:
            raise SystemExit("Cấu hình llm: openai nhưng chưa có OPENAI_API_KEY / ASSISTANT_LLM_MODEL")
        inner: LlmProvider = OpenAiLlm(settings.openai_api_key, settings.openai_base_url)
    else:
        inner = ScriptedLlm(policy=scripted_policy)
        settings.llm_model = settings.llm_model or "scripted-extractive"
    llm = GuardedLlm(inner, max_llm_calls, max_tokens)
    registry = ToolRegistry.from_file(settings.tool_catalog_path)
    client = MockBackendClient(registry, EVAL_DIR / "fixtures" / "scenarios", "student_two_contracts")
    services = Services(
        settings=settings, llm=llm, embedder=embedder, retriever=build_retriever(settings, store),
        routes=RouteCatalog(load_routes_file(Path(settings.routes_file))), conversations=MemoryConversationStore(),
        counter=counter, exemplars=MemoryExemplarStore(embed_exemplars(dataset_texts(), embedder)),
        tool_registry=registry, tool_executor=ToolExecutor(registry, client, settings.backend_jvm_time_zone),
    )
    return Environment(settings, services, llm, client, embedder, config)


def load_users() -> dict[str, str]:
    data = yaml.safe_load((EVAL_DIR / "fixtures" / "users.yaml").read_text(encoding="utf-8"))
    return {name: info["id"] for name, info in data["users"].items()}


@dataclass
class TurnRun:
    question: str
    passed: bool
    grades: list[GradeResult]
    observation: TurnObservation


@dataclass
class CaseRun:
    case_id: str
    run_index: int
    passed: bool
    turns: list[TurnRun]
    error: str = ""


def run_case(env: Environment, case: Case, run_index: int, users: dict[str, str]) -> CaseRun:
    services = env.services
    services.settings = env.settings.model_copy(update=case.config_overrides)
    services.clock = FixedClock(case.now)
    env.client.scenario = case.scenario
    user_id = uuid.UUID(users[case.user])
    conversation_id = None
    seen_before: set[str] = set()
    turns: list[TurnRun] = []
    for turn in case.turns:
        env.llm.turn_inputs = []
        state = TurnState(caller=Caller(user_id, case.role, FAKE_TOKEN), question=turn.question, page_path=case.page,
                          now=services.clock.now(), request_id=f"eval-{case.id}-{run_index}",
                          conversation_id=conversation_id)
        result = build_runner(services).run(state)
        conversation_id = result.conversation_id
        assert isinstance(services.conversations, MemoryConversationStore)
        saved = services.conversations.saved[-1]
        observation = _observe(case, state, result, saved, env.llm.turn_inputs, seen_before)
        grades = grade_turn(observation, turn.expect, services.routes)
        turns.append(TurnRun(turn.question, all(g.passed for g in grades if g.grader not in METRIC_ONLY),
                             grades, observation))
        seen_before = {item["id"] for item in saved.session_state.get("focus", [])}
    services.settings = env.settings
    return CaseRun(case.id, run_index, all(turn.passed for turn in turns), turns)


def _observe(case: Case, state: TurnState, result, saved, inputs: list[str], seen_before: set[str]) -> TurnObservation:
    sections = state.retrieval.sections if state.retrieval else []
    calls = [{"name": record.call.name, "args": record.call.arguments, "blocked_reason": record.blocked_reason,
              "http_status": record.http_status, "result_ids": [item.id for item in record.focus_items]}
             for record in state.tool_calls]
    return TurnObservation(
        role=case.role, question=state.question, answer=result.answer, intent=result.intent,
        grounded=result.grounded, fallback_reason=result.fallback_reason, actions=result.actions,
        context_docs=[section.doc_key for section in sections],
        context_audiences=[section.audience for section in sections], tool_calls=calls,
        tool_texts=[record.result_text for record in state.tool_calls if record.blocked_reason is None],
        rewritten_query=state.rewritten_query, trace=saved.trace, model_inputs=list(inputs),
        seen_ids_before=seen_before, focus_after=saved.session_state.get("focus", []),
        layer_tokens=dict(state.layer_tokens),
    )


def dataset_version(root: Path = DATASETS) -> str:
    digest = hashlib.sha256()
    for file in sorted(root.rglob("*.yaml")):
        digest.update(file.read_bytes())
    return digest.hexdigest()[:12]


def harness_hash(env: Environment) -> str:
    return config_hash(env.settings)
