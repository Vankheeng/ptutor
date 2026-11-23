"""Test T1 M12: nạp câu mẫu lớp chặn vào bảng scope_exemplar và đọc lại theo model embedding."""

import pytest

from app.providers.embedding import FakeEmbedding
from app.scope.exemplars import PgExemplarStore, load_into_db

pytestmark = pytest.mark.db


def test_load_dataset_exemplars_and_read_back_by_model(session_factory):
    counts = load_into_db(session_factory, FakeEmbedding())
    assert counts["IN"] > 100 and counts["OUT"] >= 60
    store = PgExemplarStore(session_factory, "fake-hash-v1")
    assert len(store.all()) == counts["IN"] + counts["OUT"]
    assert PgExemplarStore(session_factory, "bge-m3-int8").all() == []  # vector model khác → không dùng
    assert load_into_db(session_factory, FakeEmbedding()) == counts  # nạp lại không nhân đôi
