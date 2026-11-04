"""E1 không cần model: so cấu hình truy xuất và hiệu chỉnh ngưỡng NO_CONTEXT theo dev / test (thiết kế 10 mục 10.5).

Khác `eval.hit_at_k` (toàn bộ 220 câu, một cấu hình): công cụ này chia **dev / test** theo `SPLITS.yaml`, chạy nhiều
cấu hình trên cùng câu hỏi, và dò ngưỡng NO_CONTEXT trên dev rồi báo cáo trên test.

Cấu hình (`--configs`, mặc định cả bốn):
- `fake-hybrid`: embedding giả lập + full-text (cấu hình T0–T2 / T3 đã chạy);
- `bge-vector`, `bge-hybrid`, `bge-rerank`: bge-m3 qua ai-service; `bge-rerank` thêm bge-reranker-v2-m3.
  Cần `AI_SERVICE_URL`, `AI_INTERNAL_TOKEN`.

Ngưỡng NO_CONTEXT (từ chối không gọi model) có ba nhóm câu:
- **trả lời được** (POLICY / HOWTO / FAQ / ERROR): bị NO_CONTEXT là **từ chối nhầm** — mục tiêu ≤ 2%;
- **không có đáp án** (NO_ANSWER): NO_CONTEXT là đúng nhưng không bắt buộc (Actor vẫn tự nói "chưa có thông tin");
- **ngoài phạm vi** (lọt qua lớp chặn phạm vi): NO_CONTEXT là đúng.
Chọn trên dev: từ chối nhầm ≤ 2% trước; trong đó NO_CONTEXT đúng (NO_ANSWER + ngoài phạm vi) cao nhất; hoà → ngưỡng
gần mặc định.

    .venv/Scripts/python -m eval.retrieval_eval --out eval/reports/bge/retrieval.json
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, replace
from pathlib import Path

import yaml

from app.config import Settings
from app.kb.tokens import load_token_counter
from app.providers.embedding import EmbeddingProvider, FakeEmbedding
from app.providers.factory import build_embedder, build_retriever
from app.retrieval.retriever import Retriever
from app.retrieval.store import MemoryKnowledgeStore
from eval.hit_at_k import DEFAULT_CSV, DEFAULT_KB, KS, first_rank, load_questions
from eval.make_splits import load_splits
from eval.runner import EVAL_DIR, CachedEmbedding
from eval.scope_eval import SCOPE_FILE

CONFIGS = {
    "fake-hybrid": {"embedding_provider": "fake", "retrieval_mode": "hybrid", "rerank_enabled": False},
    "bge-vector": {"embedding_provider": "ai-service", "retrieval_mode": "vector", "rerank_enabled": False},
    "bge-hybrid": {"embedding_provider": "ai-service", "retrieval_mode": "hybrid", "rerank_enabled": False},
    "bge-rerank": {"embedding_provider": "ai-service", "retrieval_mode": "hybrid", "rerank_enabled": True},
}
ANSWERABLE = {"POLICY", "HOWTO", "FAQ", "ERROR"}
MAX_FALSE_REFUSAL = 0.02
COSINES = [round(0.30 + 0.05 * step, 2) for step in range(11)]  # 0,30 … 0,80
COVERAGES = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1.01]  # 1,01 = bỏ điều kiện phủ từ khoá
RERANK_SCORES = [0.0, 0.01, 0.02, 0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5]


@dataclass
class Item:
    text: str
    role: str
    kind: str  # loại câu, hoặc OUT_OF_SCOPE
    doc_id: str
    rank: int | None = None  # hạng tài liệu đúng khi bỏ ngưỡng
    best_cosine: float = 0.0
    best_text_coverage: float = 0.0
    best_rerank: float | None = None


def load_items(split: str) -> list[Item]:
    docs = set(load_splits()["documents"][split])
    items = [Item(q.text, q.role, q.kind, q.doc_id) for q in load_questions(DEFAULT_CSV) if q.doc_id in docs]
    scope = yaml.safe_load(SCOPE_FILE.read_text(encoding="utf-8"))
    items += [Item(text, "STUDENT", "OUT_OF_SCOPE", "") for text in scope["out_of_scope"][split]]
    return items


def observe(items: list[Item], retriever: Retriever, embedder: EmbeddingProvider) -> None:
    loose = Retriever(retriever.store, retriever.reranker,
                      replace(retriever.config, min_cosine=-1.0, min_text_coverage=-1.0, min_rerank_score=-1e9))
    vectors = embedder.embed([item.text for item in items]).vectors
    for item, vector in zip(items, vectors, strict=True):
        result = loose.retrieve(item.text, vector, item.role)
        item.rank = first_rank([section.doc_key for section in result.sections], item.doc_id) if item.doc_id else None
        item.best_cosine, item.best_text_coverage = result.best_cosine, result.best_text_coverage
        item.best_rerank = result.best_rerank


def refused(item: Item, threshold: dict) -> bool:
    """Cùng quy tắc với `Retriever._below_threshold`."""
    if "min_rerank_score" in threshold:
        return (item.best_rerank or 0.0) < threshold["min_rerank_score"]
    return item.best_cosine < threshold["min_cosine"] and item.best_text_coverage < threshold["min_text_coverage"]


def quality(items: list[Item], threshold: dict) -> dict:
    answerable = [item for item in items if item.kind in ANSWERABLE]
    no_answer = [item for item in items if item.kind == "NO_ANSWER"]
    outside = [item for item in items if item.kind == "OUT_OF_SCOPE"]
    with_doc = [item for item in items if item.doc_id]

    def rate(group: list[Item]) -> float:
        return sum(refused(item, threshold) for item in group) / len(group) if group else 0.0

    row = {"threshold": threshold, "n_answerable": len(answerable), "n_no_answer": len(no_answer),
           "n_out_of_scope": len(outside), "false_refusal": rate(answerable), "no_answer_refused": rate(no_answer),
           "out_of_scope_refused": rate(outside),
           "false_refusal_examples": [item.text for item in answerable if refused(item, threshold)][:5]}
    for k in KS:
        row[f"recall@{k}"] = sum(1 for item in with_doc if item.rank and item.rank <= k) / len(with_doc)
    row["mrr"] = sum(1 / item.rank for item in with_doc if item.rank) / len(with_doc)
    row["recall@6_answerable"] = sum(1 for item in answerable if item.rank and item.rank <= 6) / len(answerable)
    # Recall@6 thực tế: tài liệu đúng trong ngữ cảnh VÀ không bị NO_CONTEXT chặn.
    row["recall@6_with_threshold"] = sum(1 for item in with_doc if item.rank and item.rank <= 6
                                         and not refused(item, threshold)) / len(with_doc)
    return row


def grid(rerank: bool) -> list[dict]:
    if rerank:
        return [{"min_rerank_score": score} for score in RERANK_SCORES]
    return [{"min_cosine": cosine, "min_text_coverage": coverage} for cosine in COSINES for coverage in COVERAGES]


def tune(items: list[Item], rerank: bool, default: dict) -> dict:
    rows = [quality(items, threshold) for threshold in grid(rerank)]
    allowed = [row for row in rows if row["false_refusal"] <= MAX_FALSE_REFUSAL] or rows

    def distance(threshold: dict) -> float:
        return sum(abs(threshold[key] - default[key]) for key in threshold)

    best = max(allowed, key=lambda row: (row["no_answer_refused"] * row["n_no_answer"]
                                         + row["out_of_scope_refused"] * row["n_out_of_scope"],
                                         -row["false_refusal"], -distance(row["threshold"])))
    return best["threshold"]


def run_config(name: str, overrides: dict, splits: dict[str, list[Item]]) -> dict:
    settings = Settings(**overrides)
    inner = FakeEmbedding() if settings.embedding_provider == "fake" else build_embedder(settings)
    embedder = CachedEmbedding(inner, EVAL_DIR / ".cache")
    store = MemoryKnowledgeStore.from_directory(DEFAULT_KB, embedder, load_token_counter(settings.tokenizer_path))
    retriever = build_retriever(settings, store)
    observed = {split: [replace(item) for item in items] for split, items in splits.items()}
    for items in observed.values():
        observe(items, retriever, embedder)
    embedder.save()
    rerank = settings.rerank_enabled
    default = ({"min_rerank_score": settings.min_rerank_score} if rerank
               else {"min_cosine": settings.min_cosine, "min_text_coverage": settings.min_text_coverage})
    chosen = tune(observed["dev"], rerank, default)
    return {"config": name, "embedding": embedder.model_name, **overrides, "chosen_on_dev": chosen,
            "default": default, "dev_default": quality(observed["dev"], default),
            "dev": quality(observed["dev"], chosen), "test_default": quality(observed["test"], default),
            "test": quality(observed["test"], chosen),
            "test_misses": [{"q": item.text, "doc": item.doc_id} for item in observed["test"]
                            if item.doc_id and not (item.rank and item.rank <= 6)][:10],
            # Điểm thô từng câu: dò lại ngưỡng sau này không phải chạy lại (rerank trên CPU mất ~30 phút).
            "items": {split: [{"kind": item.kind, "rank": item.rank, "cosine": round(item.best_cosine, 4),
                               "coverage": round(item.best_text_coverage, 4),
                               "rerank": None if item.best_rerank is None else round(item.best_rerank, 4)}
                              for item in items] for split, items in observed.items()}}


def to_markdown(results: list[dict]) -> str:
    lines = ["| Cấu hình | Tập | Recall@1 | Recall@3 | Recall@6 | MRR | Ngưỡng | Từ chối nhầm | NO_ANSWER từ chối | "
             "Ngoài phạm vi từ chối | Recall@6 sau ngưỡng |", "|---" * 11 + "|"]
    for result in results:
        for split in ("dev", "test"):
            row = result[split]
            threshold = ", ".join(f"{key}={value}" for key, value in row["threshold"].items())
            lines.append(f"| {result['config']} | {split} | {row['recall@1']:.1%} | {row['recall@3']:.1%} | "
                         f"{row['recall@6']:.1%} | {row['mrr']:.3f} | {threshold} | {row['false_refusal']:.1%} | "
                         f"{row['no_answer_refused']:.1%} | {row['out_of_scope_refused']:.1%} | "
                         f"{row['recall@6_with_threshold']:.1%} |")
    return "\n".join(lines)


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    parser = argparse.ArgumentParser(description="E1 truy xuất theo dev / test + hiệu chỉnh ngưỡng NO_CONTEXT")
    parser.add_argument("--configs", default=",".join(CONFIGS))
    parser.add_argument("--out", type=Path)
    arguments = parser.parse_args()
    splits = {split: load_items(split) for split in ("dev", "test")}
    results = [run_config(name, CONFIGS[name], splits) for name in arguments.configs.split(",")]
    print(to_markdown(results))
    if arguments.out:
        arguments.out.parent.mkdir(parents=True, exist_ok=True)
        arguments.out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
        arguments.out.with_suffix(".md").write_text(to_markdown(results) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
