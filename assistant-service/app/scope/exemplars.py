"""Câu mẫu cho tầng 2 của lớp chặn ngoài phạm vi (thiết kế 04 mục 4.11, bảng `assistant.scope_exemplar`).

- `IN` (trong phạm vi): câu hỏi của **tập dev** trong `knowledge-base/_eval/cau-hoi-mau.csv` (+ admin bổ sung).
  Không lấy tập test, để số đo trên test không bị "học tủ".
- `OUT` (ngoài phạm vi): `app/scope/out_of_scope_seed.yaml` (+ admin bổ sung).

Vài trăm dòng → so khớp bằng quét tuần tự trong bộ nhớ, không cần chỉ mục.

    .venv/Scripts/python -m app.scope.exemplars --load      # nạp lại câu mẫu nguồn DATASET (giữ câu ADMIN)
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import yaml
from sqlalchemy import delete, select
from sqlalchemy.orm import Session, sessionmaker

from app.db.models import ScopeExemplar
from app.providers.embedding import EmbeddingProvider, cosine

SEED_FILE = Path(__file__).resolve().parent / "out_of_scope_seed.yaml"
REPO_ROOT = Path(__file__).resolve().parents[3]


@dataclass
class Exemplar:
    text: str
    label: str  # IN | OUT
    vector: list[float]


@dataclass
class ScopeScores:
    best_in: float
    best_out: float
    nearest_in: str
    nearest_out: str


def dataset_texts(repo_root: Path = REPO_ROOT) -> dict[str, list[str]]:
    """Câu mẫu nguồn DATASET: IN = câu hỏi tập dev, OUT = file seed."""
    from eval.hit_at_k import load_questions
    from eval.make_splits import load_splits

    dev_docs = set(load_splits()["documents"]["dev"])
    questions = load_questions(repo_root / "knowledge-base" / "_eval" / "cau-hoi-mau.csv")
    in_texts = sorted({question.text for question in questions if question.doc_id in dev_docs})
    out_texts = yaml.safe_load(SEED_FILE.read_text(encoding="utf-8"))["out"]
    return {"IN": in_texts, "OUT": out_texts}


def embed_exemplars(texts: dict[str, list[str]], embedder: EmbeddingProvider) -> list[Exemplar]:
    exemplars: list[Exemplar] = []
    for label, items in texts.items():
        vectors = embedder.embed(items).vectors if items else []
        exemplars += [Exemplar(text, label, vector) for text, vector in zip(items, vectors, strict=True)]
    return exemplars


def score(vector: list[float], exemplars: list[Exemplar], exclude_text: str | None = None) -> ScopeScores:
    """`in` = cosine lớn nhất tới tập IN, `out` = tới tập OUT. `exclude_text`: bỏ chính câu đó (đo kiểu leave-one-out
    trên tập dev, vì câu dev cũng là câu mẫu IN)."""
    best = {"IN": (-1.0, ""), "OUT": (-1.0, "")}
    for exemplar in exemplars:
        if exclude_text is not None and exemplar.text == exclude_text:
            continue
        similarity = cosine(vector, exemplar.vector)
        if similarity > best[exemplar.label][0]:
            best[exemplar.label] = (similarity, exemplar.text)
    return ScopeScores(best["IN"][0], best["OUT"][0], best["IN"][1], best["OUT"][1])


class ExemplarStore:
    def all(self) -> list[Exemplar]:
        raise NotImplementedError


class MemoryExemplarStore(ExemplarStore):
    def __init__(self, exemplars: list[Exemplar]):
        self._exemplars = exemplars

    def all(self) -> list[Exemplar]:
        return self._exemplars


class PgExemplarStore(ExemplarStore):
    """Đọc từ DB một lần rồi giữ trong bộ nhớ; chỉ lấy câu mẫu embed bằng đúng model hiện tại."""

    def __init__(self, session_factory: sessionmaker[Session], embedding_model: str):
        self._sessions = session_factory
        self._model = embedding_model
        self._cache: list[Exemplar] | None = None

    def all(self) -> list[Exemplar]:
        if self._cache is None:
            with self._sessions() as session:
                rows = session.scalars(select(ScopeExemplar).where(ScopeExemplar.embedding_model == self._model)).all()
            self._cache = [Exemplar(row.text, row.label, list(row.embedding)) for row in rows]
        return self._cache

    def refresh(self) -> None:
        self._cache = None


def load_into_db(session_factory: sessionmaker[Session], embedder: EmbeddingProvider) -> dict[str, int]:
    exemplars = embed_exemplars(dataset_texts(), embedder)
    model = embedder.embed(["kiểm tra model"]).model
    with session_factory.begin() as session:
        session.execute(delete(ScopeExemplar).where(ScopeExemplar.source == "DATASET"))
        for exemplar in exemplars:
            session.add(ScopeExemplar(text=exemplar.text[:500], label=exemplar.label, source="DATASET",
                                      embedding=exemplar.vector, embedding_model=model))
    return {"IN": sum(e.label == "IN" for e in exemplars), "OUT": sum(e.label == "OUT" for e in exemplars)}


def main() -> None:
    from app.cli import use_utf8_output
    from app.config import Settings
    from app.db.session import make_engine, make_session_factory
    from app.providers.factory import build_embedder

    use_utf8_output()
    parser = argparse.ArgumentParser(description="Nạp câu mẫu cho lớp chặn ngoài phạm vi")
    parser.add_argument("--load", action="store_true")
    arguments = parser.parse_args()
    if arguments.load:
        settings = Settings()
        counts = load_into_db(make_session_factory(make_engine(settings.database_url)), build_embedder(settings))
        print(f"Đã nạp câu mẫu: {counts}")


if __name__ == "__main__":
    main()
