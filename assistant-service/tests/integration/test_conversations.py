"""Test T1 M8: lưu hội thoại trên PostgreSQL thật, xoá có kiểm chứng (06 mục 6.4, 13 mục 13.1)."""

import uuid

import pytest
from sqlalchemy import select

from app.agent.harness import build_runner
from app.agent.state import Caller, TurnState
from app.db.conversations import PgConversationStore
from app.db.models import KbChunk
from app.kb.parser import parse_document
from app.kb.service import KnowledgeBaseService
from app.kb.tokens import ApproxTokenCounter
from app.providers.embedding import FakeEmbedding
from app.providers.llm import LlmResponse, LlmToolCall, ScriptedLlm
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.store import PgKnowledgeStore
from tests.fakes import FIXED_NOW, make_services
from tests.unit.test_answer_with_citations import actor_json
from tests.unit.test_kb_chunking import SAMPLE
from tests.unit.test_retrieval import NoRerank

pytestmark = pytest.mark.db
USER = uuid.UUID("11111111-1111-1111-1111-000000000001")
TABLES = ["conversation", "message", "message_citation", "tool_call_log", "turn_trace", "message_feedback"]


@pytest.fixture
def services(settings, session_factory):
    kb = KnowledgeBaseService(session_factory, ApproxTokenCounter(), lambda: {"/student/wallet"}, FakeEmbedding())
    kb.publish(kb.save_draft(parse_document(SAMPLE), origin="IMPORT").document_id)
    tool_step = LlmResponse(tool_calls=[LlmToolCall("c1", "get_my_wallet", {"page": 0})])
    built = make_services(settings, ScriptedLlm([tool_step, actor_json()]))
    built.retriever = Retriever(PgKnowledgeStore(session_factory), NoRerank(), RetrievalConfig())
    built.conversations = PgConversationStore(session_factory)
    return built


def test_full_turn_saved_then_purged_from_all_six_tables(services, session_factory):
    state = TurnState(caller=Caller(USER, "STUDENT", "Bearer t"), question="Nạp ví tối thiểu bao nhiêu?",
                      page_path="/student/wallet", now=FIXED_NOW)
    result = build_runner(services).run(state)
    store: PgConversationStore = services.conversations
    store.save_feedback(result.message_id, USER, -1, "WRONG", None)
    conversation_id = result.conversation_id
    assert store.remaining_rows(conversation_id) >= 6  # có dữ liệu ở cả 6 bảng
    assert store.history(conversation_id, 6)[0].question == "Nạp ví tối thiểu bao nhiêu?"
    with session_factory() as session:
        assert session.scalar(select(KbChunk.id)) is not None

    store.mark_deleted(conversation_id, FIXED_NOW)
    log = store.purge(conversation_id)
    assert log.verified and store.remaining_rows(conversation_id) == 0
    assert set(log.rows_deleted) == set(TABLES) and log.rows_deleted["message"] == 2
