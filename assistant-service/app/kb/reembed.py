"""Embed lại mọi đoạn đang dùng khi đổi model embedding (thiết kế 06 mục 6.3).

    .venv/Scripts/python -m app.kb.reembed            # chỉ đoạn có embedding_model khác model hiện tại
    .venv/Scripts/python -m app.kb.reembed --all      # mọi đoạn is_active

Cột `embedding_model` cho biết đoạn nào còn vector của model cũ. Số chiều khác 1024 cần migration đổi cột trước.
"""

from __future__ import annotations

import argparse

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from app.db.models import KbChunk
from app.providers.embedding import EmbeddingProvider

BATCH = 32


def reembed(session_factory: sessionmaker[Session], embedder: EmbeddingProvider, *, everything: bool = False) -> int:
    with session_factory.begin() as session:
        query = select(KbChunk).where(KbChunk.is_active)
        if not everything:
            query = query.where(KbChunk.embedding_model.is_distinct_from(embedder.model_name))
        chunks = list(session.scalars(query))
        for start in range(0, len(chunks), BATCH):
            batch = chunks[start:start + BATCH]
            result = embedder.embed([chunk.search_text for chunk in batch])
            for chunk, vector in zip(batch, result.vectors, strict=True):
                chunk.embedding = vector
                chunk.embedding_model = result.model
        return len(chunks)


def main() -> None:
    from app.config import Settings
    from app.db.session import make_engine, make_session_factory
    from app.providers.factory import build_embedder

    parser = argparse.ArgumentParser(description="Embed lại các đoạn đang dùng")
    parser.add_argument("--all", action="store_true")
    arguments = parser.parse_args()
    settings = Settings()
    count = reembed(make_session_factory(make_engine(settings.database_url)), build_embedder(settings),
                    everything=arguments.all)
    print(f"Đã embed lại {count} đoạn.")


if __name__ == "__main__":
    main()
