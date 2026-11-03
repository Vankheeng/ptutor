"""Test T1 M6: truy xuất trên PostgreSQL thật (pgvector cosine, tsvector + f_unaccent, small-to-big)."""

import pytest

from app.kb.parser import parse_document
from app.kb.service import KnowledgeBaseService
from app.kb.tokens import ApproxTokenCounter
from app.providers.embedding import FakeEmbedding
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.store import PgKnowledgeStore
from tests.unit.test_kb_chunking import SAMPLE
from tests.unit.test_retrieval import NoRerank

pytestmark = pytest.mark.db

TUTOR_DOC = """---
id: chung-chi-nop
title: Nộp chứng chỉ
type: guide
audience: [TUTOR]
features: [certificate]
routes: []
---

# Nộp chứng chỉ

## Cách nộp
Gia sư vào trang Chứng chỉ, tải tệp tối đa 10 MB rồi bấm Gửi duyệt. Chứng chỉ được duyệt trong vài ngày làm việc.
"""


@pytest.fixture
def retriever(session_factory):
    service = KnowledgeBaseService(session_factory, ApproxTokenCounter(), lambda: {"/student/wallet"},
                                   FakeEmbedding())
    for text in (SAMPLE, TUTOR_DOC):
        service.publish(service.save_draft(parse_document(text), origin="IMPORT").document_id)
    return Retriever(PgKnowledgeStore(session_factory), NoRerank(), RetrievalConfig())


def ask(retriever, question, role="STUDENT"):
    return retriever.retrieve(question, FakeEmbedding().vector(question), role)


def test_unaccented_question_matches_full_text(retriever):
    result = ask(retriever, "nap vi toi thieu bao nhieu")
    assert any(candidate.text_score for candidate in result.candidates)
    assert result.sections[0].doc_key == "vi-nap-tien"


def test_audience_filter_in_sql(retriever):
    student = ask(retriever, "nộp chứng chỉ tối đa bao nhiêu MB")
    assert "chung-chi-nop" not in {candidate.chunk.doc_key for candidate in student.candidates}
    tutor = ask(retriever, "nộp chứng chỉ tối đa bao nhiêu MB", role="TUTOR")
    assert tutor.sections[0].doc_key == "chung-chi-nop"


def test_section_expansion_reads_whole_section(retriever):
    result = ask(retriever, "các bước nạp ví VNPay")
    steps = next(section for section in result.sections if section.heading_path == "Nạp ví > Các bước")
    assert "Bước 1" in steps.content and "Bước 2" in steps.content
