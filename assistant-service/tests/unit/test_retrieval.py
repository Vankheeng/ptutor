"""Test M6: RRF, small-to-big, truy xuất lai trên kho trong bộ nhớ (thiết kế 04 mục 4.3)."""

import uuid

import pytest

from app.kb.parser import parse_document
from app.kb.routes import route_pattern_matches
from app.kb.tokens import ApproxTokenCounter
from app.providers.embedding import FakeEmbedding
from app.retrieval.expand import join_overlapping
from app.retrieval.rerank import Reranker
from app.retrieval.retriever import RetrievalConfig, Retriever
from app.retrieval.rrf import rrf_merge
from app.retrieval.store import MemoryKnowledgeStore, query_terms, text_coverage

A, B, C = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()


def test_rrf_matches_hand_calculation():
    fused = rrf_merge({"vector": [A, B], "text": [C, B, A]}, k=60)
    scores = {item.chunk_id: item.score for item in fused}
    assert scores[A] == pytest.approx(1 / 61 + 1 / 63)
    assert scores[B] == pytest.approx(1 / 62 + 1 / 62)
    # A: 1/61 + 1/63 ≈ 0,032266 nhỉnh hơn B: 2/62 ≈ 0,032258 → A đứng đầu.
    assert fused[0].chunk_id == A and fused[1].ranks == {"vector": 2, "text": 2}


def test_rrf_limit():
    assert len(rrf_merge({"x": [A, B, C]}, limit=2)) == 2


def test_join_overlapping_removes_repeated_words_and_keeps_line_breaks():
    first = "Bước 1 mở trang ví.\nBước 2 nhập số tiền cần nạp"
    second = "nhập số tiền cần nạp\nBước 3 bấm nạp qua VNPay"
    assert join_overlapping([first, second]) == first + "\n\nBước 3 bấm nạp qua VNPay"


def test_route_pattern_matches_parameters_only_in_place():
    assert route_pattern_matches("/student/contracts/:contractId", "/student/contracts/abc")
    assert not route_pattern_matches("/student/contracts/:contractId", "/student/contracts")
    assert not route_pattern_matches("/student/wallet", "/tutor/wallet")


def test_query_terms_drop_accents_and_stopwords():
    assert query_terms("Tôi nạp ví tối thiểu bao nhiêu?") == ["nap", "vi", "toi", "thieu"]  # "tôi" bị bỏ, "tối" giữ
    assert text_coverage("nạp ví tối thiểu", "Nạp ví > Hạn mức\nTối thiểu 10.000 đ") == 1.0


def _doc(key: str, title: str, audience: str, body: str, routes: str = "[]") -> str:
    return (f"---\nid: {key}\ntitle: {title}\ntype: policy\naudience: [{audience}]\nfeatures: [wallet]\n"
            f"routes: {routes}\n---\n\n# {title}\n\n{body}\n")


@pytest.fixture
def store():
    memory = MemoryKnowledgeStore(FakeEmbedding(), ApproxTokenCounter())
    memory.add_document(parse_document(_doc(
        "vi-nap-tien", "Nạp ví", "STUDENT, TUTOR",
        "## Hạn mức nạp ví\nMỗi lần nạp ví tối thiểu 10.000 đ và tối đa 100.000.000 đ, số tiền chẵn đồng.\n\n"
        "## Cách nạp\nVào trang Ví, nhập số tiền, bấm nạp qua VNPay và thanh toán trong 15 phút.",
        routes="[/student/wallet]")))
    memory.add_document(parse_document(_doc(
        "chung-chi-nop", "Nộp chứng chỉ gia sư", "TUTOR",
        "## Cách nộp chứng chỉ\nGia sư vào trang Chứng chỉ, tải tệp tối đa 10 MB rồi bấm Gửi duyệt.")))
    return memory


def retrieve(store, question, role="STUDENT", **config):
    retriever = Retriever(store, NoRerank(), RetrievalConfig(**config))
    return retriever.retrieve(question, FakeEmbedding().vector(question), role)


class NoRerank(Reranker):
    def scores(self, query, documents):
        return None


class ReverseRerank(Reranker):
    """Rerank giả: đảo ngược thứ tự, để thấy kết quả rerank được dùng."""

    def scores(self, query, documents):
        return [index / len(documents) for index in range(len(documents))]


def test_hybrid_retrieval_finds_policy_section_with_labels(store):
    result = retrieve(store, "nạp ví tối thiểu bao nhiêu tiền")
    assert not result.no_context
    assert result.sections[0].label == "D1" and result.sections[0].doc_key == "vi-nap-tien"
    assert result.sections[0].section_name == "Hạn mức nạp ví"
    assert any(candidate.in_context for candidate in result.candidates)


def test_student_never_sees_tutor_only_documents(store):
    result = retrieve(store, "cách nộp chứng chỉ gia sư tối đa bao nhiêu MB", role="STUDENT")
    assert all("TUTOR" not in section.audience or "STUDENT" in section.audience for section in result.sections)
    assert "chung-chi-nop" not in {candidate.chunk.doc_key for candidate in result.candidates}
    tutor = retrieve(store, "cách nộp chứng chỉ gia sư tối đa bao nhiêu MB", role="TUTOR")
    assert tutor.sections[0].doc_key == "chung-chi-nop"


def test_unrelated_question_is_no_context(store):
    result = retrieve(store, "thời tiết Hà Nội ngày mai thế nào")
    assert result.no_context and result.sections == []


def test_rerank_order_is_used_when_available(store):
    retriever = Retriever(store, ReverseRerank(), RetrievalConfig(min_rerank_score=0.0))
    question = "nạp ví tối thiểu"
    result = retriever.retrieve(question, FakeEmbedding().vector(question), "STUDENT")
    assert result.rerank_used
    scores = [candidate.rerank_score for candidate in result.candidates]
    assert scores == sorted(scores, reverse=True)


def test_rerank_threshold_marks_no_context(store):
    class LowRerank(Reranker):
        def scores(self, query, documents):
            return [0.1] * len(documents)

    retriever = Retriever(store, LowRerank(), RetrievalConfig(min_rerank_score=0.3))
    question = "nạp ví tối thiểu"
    assert retriever.retrieve(question, FakeEmbedding().vector(question), "STUDENT").no_context


def test_vector_mode_skips_full_text(store):
    result = retrieve(store, "nạp ví tối thiểu", mode="vector")
    assert all(candidate.text_score is None for candidate in result.candidates)
