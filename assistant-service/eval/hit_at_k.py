"""Đo chất lượng truy xuất trên bộ câu hỏi mẫu `knowledge-base/_eval/cau-hoi-mau.csv` (KE-HOACH M6; thiết kế 10.2).

Mỗi câu có `doc_id` = tài liệu chứa (hoặc lẽ ra chứa) câu trả lời. Chỉ số:
- hit@k: tài liệu đúng nằm trong k mục đầu đưa vào ngữ cảnh ([D1]…[Dk]); hit@6 chính là Recall@6 của thiết kế.
- MRR: trung bình 1 / thứ hạng của mục đầu tiên thuộc tài liệu đúng.
- Cột "bỏ ngưỡng": tính khi bỏ qua ngưỡng NO_CONTEXT — tách lỗi "tìm sai" khỏi lỗi "ngưỡng quá chặt".

    .venv/Scripts/python -m eval.hit_at_k                          # kho trong bộ nhớ, embedding theo cấu hình
    .venv/Scripts/python -m eval.hit_at_k --mode vector            # chỉ vector (so sánh E1)
    .venv/Scripts/python -m eval.hit_at_k --store pg               # kho đã công bố trong PostgreSQL
    .venv/Scripts/python -m eval.hit_at_k --misses 10              # in 10 câu tìm sai để phân tích
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from dataclasses import dataclass, field, replace
from pathlib import Path

from app.config import Settings
from app.kb.tokens import load_token_counter
from app.providers.factory import build_embedder, build_retriever
from app.retrieval.retriever import Retriever
from app.retrieval.store import KnowledgeStore, MemoryKnowledgeStore, PgKnowledgeStore

DEFAULT_CSV = Path(__file__).resolve().parents[2] / "knowledge-base" / "_eval" / "cau-hoi-mau.csv"
DEFAULT_KB = DEFAULT_CSV.parents[1]
KS = (1, 3, 6)


@dataclass
class Question:
    doc_id: str
    role: str
    text: str
    kind: str
    required: str = ""  # cột y_bat_buoc: ý bắt buộc của câu trả lời (để chấm tay)


@dataclass
class Outcome:
    question: Question
    rank: int | None  # thứ hạng mục đầu tiên thuộc tài liệu đúng (có ngưỡng); None = không có
    rank_ignoring_threshold: int | None
    no_context: bool
    top_docs: list[str] = field(default_factory=list)


def load_questions(path: Path) -> list[Question]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return [Question(row["doc_id"], row["vai_tro"], row["cau_hoi"], row["loai"], row.get("y_bat_buoc") or "")
                for row in csv.DictReader(file)]


def first_rank(doc_keys: list[str], expected: str) -> int | None:
    return next((index for index, key in enumerate(doc_keys, start=1) if key == expected), None)


def evaluate(questions: list[Question], retriever: Retriever, embedder) -> list[Outcome]:
    no_threshold = Retriever(retriever.store, retriever.reranker,  # cùng kho, chỉ khác ngưỡng
                             replace(retriever.config, min_cosine=-1.0, min_text_coverage=-1.0, min_rerank_score=-1.0))
    vectors = embedder.embed([question.text for question in questions]).vectors
    outcomes = []
    for question, vector in zip(questions, vectors, strict=True):
        result = retriever.retrieve(question.text, vector, question.role)
        loose = no_threshold.retrieve(question.text, vector, question.role)
        docs = [section.doc_key for section in result.sections]
        loose_docs = [section.doc_key for section in loose.sections]
        outcomes.append(Outcome(question, first_rank(docs, question.doc_id),
                                first_rank(loose_docs, question.doc_id), result.no_context, loose_docs))
    return outcomes


def summarize(outcomes: list[Outcome]) -> dict:
    def stats(items: list[Outcome]) -> dict:
        count = len(items) or 1
        row: dict = {"n": len(items)}
        for k in KS:
            row[f"hit@{k}"] = sum(1 for item in items if item.rank and item.rank <= k) / count
        row["mrr"] = sum(1 / item.rank for item in items if item.rank) / count
        row["hit@6_bo_nguong"] = sum(1 for item in items if item.rank_ignoring_threshold
                                     and item.rank_ignoring_threshold <= 6) / count
        row["no_context"] = sum(item.no_context for item in items) / count
        return row

    groups: dict[str, list[Outcome]] = defaultdict(list)
    for item in outcomes:
        groups["loai=" + item.question.kind].append(item)
        groups["vai_tro=" + item.question.role].append(item)
    return {"tong": stats(outcomes), **{name: stats(items) for name, items in sorted(groups.items())}}


def to_markdown(summary: dict, header: str) -> str:
    columns = ["n", "hit@1", "hit@3", "hit@6", "mrr", "hit@6_bo_nguong", "no_context"]
    lines = [header, "", "| Nhóm | " + " | ".join(columns) + " |", "|---" * (len(columns) + 1) + "|"]
    for name, row in summary.items():
        values = [str(row["n"])] + [f"{row[column]:.3f}" for column in columns[1:]]
        lines.append(f"| {name} | " + " | ".join(values) + " |")
    return "\n".join(lines)


def build_store(kind: str, settings: Settings, kb_root: Path, embedder) -> KnowledgeStore:
    if kind == "pg":
        from app.db.session import make_engine, make_session_factory

        return PgKnowledgeStore(make_session_factory(make_engine(settings.database_url)))
    return MemoryKnowledgeStore.from_directory(kb_root, embedder, load_token_counter(settings.tokenizer_path))


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    parser = argparse.ArgumentParser(description="Đo hit@k của truy xuất trên cau-hoi-mau.csv")
    parser.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--kb", type=Path, default=DEFAULT_KB)
    parser.add_argument("--store", choices=["memory", "pg"], default="memory")
    parser.add_argument("--mode", choices=["hybrid", "vector"], default=None)
    parser.add_argument("--rerank", choices=["on", "off"], default=None)
    parser.add_argument("--misses", type=int, default=0, help="In N câu không tìm thấy tài liệu đúng trong top 6")
    parser.add_argument("--out", type=Path, help="Ghi kết quả JSON")
    arguments = parser.parse_args()

    settings = Settings()
    if arguments.mode:
        settings.retrieval_mode = arguments.mode
    if arguments.rerank:
        settings.rerank_enabled = arguments.rerank == "on"
    embedder = build_embedder(settings)
    store = build_store(arguments.store, settings, arguments.kb, embedder)
    retriever = build_retriever(settings, store)
    outcomes = evaluate(load_questions(arguments.csv), retriever, embedder)
    summary = summarize(outcomes)
    header = (f"Truy xuất: kho={arguments.store}, embedding={embedder.model_name}, mode={settings.retrieval_mode}, "
              f"rerank={'bật' if settings.rerank_enabled else 'tắt'}, ngưỡng cosine={settings.min_cosine}, "
              f"phủ từ khoá={settings.min_text_coverage}")
    print(to_markdown(summary, header))
    misses = [item for item in outcomes if not (item.rank_ignoring_threshold and item.rank_ignoring_threshold <= 6)]
    for item in misses[:arguments.misses]:
        print(f"- [{item.question.role}] {item.question.text}  → cần {item.question.doc_id}, "
              f"tìm được {item.top_docs[:3]}")
    if arguments.out:
        arguments.out.write_text(json.dumps({"header": header, "summary": summary}, ensure_ascii=False, indent=2),
                                 encoding="utf-8")


if __name__ == "__main__":
    main()
