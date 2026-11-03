"""Đo và hiệu chỉnh lớp chặn ngoài phạm vi (thiết kế 04 mục 4.11; chỉ số 10 mục 10.2).

- **Chặn đúng (sớm)**: tỉ lệ câu ngoài phạm vi bị chặn ở tầng 1–2 (không tốn lời gọi Actor).
- **Chặn nhầm**: tỉ lệ câu trong phạm vi bị chặn ở tầng 1–2. Mục tiêu ≤ 3% (chặn nhầm tệ hơn bỏ lọt).

Câu trong phạm vi = câu hỏi của `cau-hoi-mau.csv` thuộc split + câu sát ranh giới. Câu dev đồng thời là câu mẫu IN,
nên khi đo trên dev mỗi câu được so **bỏ chính nó** (leave-one-out) — nếu không, cosine = 1 và chặn nhầm luôn = 0.

    .venv/Scripts/python -m eval.scope_eval                 # dò lưới τ_in × δ trên dev, báo cáo trên test
    .venv/Scripts/python -m eval.scope_eval --tau 0.55 --delta 0.05   # đo một cặp ngưỡng
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import yaml

from app.agent.middleware.scope_guard import scope_decision
from app.config import Settings
from app.providers.embedding import EmbeddingProvider
from app.providers.factory import build_embedder
from app.scope.exemplars import MemoryExemplarStore, dataset_texts, embed_exemplars
from app.scope.rules import ScopeRules
from eval.hit_at_k import DEFAULT_CSV, load_questions
from eval.make_splits import load_splits

SCOPE_FILE = Path(__file__).resolve().parent / "datasets" / "scope" / "scope_eval.yaml"
TAUS = [round(0.30 + 0.05 * step, 2) for step in range(13)]  # 0,30 … 0,90
DELTAS = [0.0, 0.02, 0.05, 0.10, 0.15, 0.20]
MAX_FALSE_BLOCK = 0.03


@dataclass
class Case:
    text: str
    in_scope: bool
    vector: list[float]
    is_exemplar: bool  # câu IN đồng thời là câu mẫu → đo leave-one-out
    rule: str | None = None  # quyết định của tầng 1 (không phụ thuộc ngưỡng)
    best_in: float = 0.0
    best_out: float = 0.0


def build_cases(split: str, embedder: EmbeddingProvider) -> list[Case]:
    data = yaml.safe_load(SCOPE_FILE.read_text(encoding="utf-8"))
    docs = set(load_splits()["documents"][split])
    in_texts = [question.text for question in load_questions(DEFAULT_CSV) if question.doc_id in docs]
    in_texts += data["boundary_in_scope"][split]
    out_texts = data["out_of_scope"][split]
    vectors = embedder.embed(in_texts + out_texts).vectors
    exemplar_texts = set(dataset_texts()["IN"])
    cases = [Case(text, True, vector, text in exemplar_texts) for text, vector in zip(in_texts, vectors, strict=False)]
    cases += [Case(text, False, vector, False) for text, vector in zip(out_texts, vectors[len(in_texts):], strict=True)]
    return cases


def precompute(cases: list[Case], store: MemoryExemplarStore, rules: ScopeRules) -> None:
    """Điểm tầng 1–2 của mỗi câu không phụ thuộc ngưỡng → tính một lần, dò lưới chỉ so sánh số.
    Dùng chính `scope_decision` của middleware (ngưỡng τ_in = 2 để luôn lấy được điểm in / out)."""
    for case in cases:
        decision, tier, detail = scope_decision(case.text, case.vector, rules, store, 2.0, 2.0,
                                                exclude_text=case.text if case.is_exemplar else None)
        case.rule = decision if tier == "RULE" else None
        case.best_in, case.best_out = detail.get("in", 0.0), detail.get("out", 0.0)


def decide(case: Case, tau: float, delta: float) -> tuple[bool, str]:
    """Cùng quy tắc với `scope_decision`: luật trước; rồi in ≥ τ_in → qua; out − in ≥ δ → chặn."""
    if case.rule is not None:
        return case.rule == "OUT_OF_SCOPE", "RULE"
    if case.best_in >= tau:
        return False, "EMBEDDING"
    return case.best_out - case.best_in >= delta, "EMBEDDING"


def measure(cases: list[Case], tau: float, delta: float) -> dict:
    blocked_in = blocked_out = by_rule = 0
    false_examples: list[str] = []
    for case in cases:
        blocked, tier = decide(case, tau, delta)
        if case.in_scope and blocked:
            blocked_in += 1
            false_examples.append(case.text)
        if not case.in_scope and blocked:
            blocked_out += 1
            by_rule += tier == "RULE"
    n_in = sum(case.in_scope for case in cases) or 1
    n_out = sum(not case.in_scope for case in cases) or 1
    return {"tau_in": tau, "delta": delta, "n_in": n_in, "n_out": n_out,
            "block_rate": blocked_out / n_out, "block_by_rule": by_rule / n_out,
            "false_block": blocked_in / n_in, "false_block_examples": false_examples[:5]}


def tune(cases: list[Case]) -> dict:
    """Chặn nhầm ≤ 3% trước; trong số đó chặn đúng cao nhất; hoà thì gần mặc định thiết kế (0,55 / 0,05) nhất."""
    results = [measure(cases, tau, delta) for tau in TAUS for delta in DELTAS]
    allowed = [row for row in results if row["false_block"] <= MAX_FALSE_BLOCK] or results
    return max(allowed, key=lambda row: (row["block_rate"], -row["false_block"],
                                         -abs(row["tau_in"] - 0.55) - abs(row["delta"] - 0.05)))


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    parser = argparse.ArgumentParser(description="Đo / hiệu chỉnh lớp chặn ngoài phạm vi")
    parser.add_argument("--tau", type=float)
    parser.add_argument("--delta", type=float)
    parser.add_argument("--out", type=Path)
    arguments = parser.parse_args()

    settings = Settings()
    embedder = build_embedder(settings)
    store = MemoryExemplarStore(embed_exemplars(dataset_texts(), embedder))
    rules = ScopeRules.from_setting(None)
    dev, test = build_cases("dev", embedder), build_cases("test", embedder)
    precompute(dev, store, rules)
    precompute(test, store, rules)
    if arguments.tau is not None and arguments.delta is not None:
        chosen = {"tau_in": arguments.tau, "delta": arguments.delta}
    else:
        chosen = tune(dev)
    report = {
        "embedding": embedder.model_name,
        "chosen_on_dev": {"tau_in": chosen["tau_in"], "delta": chosen["delta"]},
        "dev": measure(dev, chosen["tau_in"], chosen["delta"]),
        "test": measure(test, chosen["tau_in"], chosen["delta"]),
        "design_default_on_dev": measure(dev, 0.55, 0.05),
        "design_default_on_test": measure(test, 0.55, 0.05),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if arguments.out:
        arguments.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
