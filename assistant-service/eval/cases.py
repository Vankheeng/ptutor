"""Đọc test case eval (thiết kế 13 mục 13.4): một lượt (`question`) hoặc nhiều lượt (`turns`).

    eval/datasets/golden/*.yaml      câu hỏi một lượt (sinh từ cau-hoi-mau.csv + bổ sung)
    eval/datasets/multiturn/*.yaml   hội thoại nhiều lượt
    eval/datasets/safety/*.yaml      bộ an toàn (13 mục 13.7) — luôn chạy đủ, không chia dev / test, phải đạt 100%
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

import yaml

DATASETS = Path(__file__).resolve().parent / "datasets"
DEFAULT_NOW = "2026-10-20T09:00:00+07:00"


@dataclass
class Turn:
    question: str
    expect: dict[str, Any] = field(default_factory=dict)


@dataclass
class Case:
    id: str
    suite: str  # golden | multiturn | safety
    split: str  # dev | test | all
    category: str
    role: str
    user: str
    scenario: str
    page: str
    turns: list[Turn]
    now: datetime
    config_overrides: dict[str, Any] = field(default_factory=dict)
    tags: list[str] = field(default_factory=list)


def _case(raw: dict, suite: str) -> Case:
    turns = [Turn(item["question"], item.get("expect") or {}) for item in raw["turns"]] if "turns" in raw \
        else [Turn(raw["question"], raw.get("expect") or {})]
    return Case(
        id=raw["id"], suite=suite, split=raw.get("split", "all"), category=raw.get("category", suite.upper()),
        role=raw["role"], user=raw.get("user", "student_a"), scenario=raw.get("scenario", "student_two_contracts"),
        page=raw.get("page", ""), turns=turns, now=datetime.fromisoformat(raw.get("now", DEFAULT_NOW)),
        config_overrides=raw.get("config_overrides") or {}, tags=raw.get("tags") or [],
    )


def load_cases(suites: list[str], split: str | None = None, root: Path = DATASETS) -> list[Case]:
    cases: list[Case] = []
    for suite in suites:
        for file in sorted((root / suite).glob("*.yaml")):
            for raw in yaml.safe_load(file.read_text(encoding="utf-8")) or []:
                case = _case(raw, suite)
                if split and case.split not in {split, "all"}:
                    continue
                cases.append(case)
    ids = [case.id for case in cases]
    duplicates = {case_id for case_id in ids if ids.count(case_id) > 1}
    if duplicates:
        raise ValueError(f"Trùng id case: {sorted(duplicates)}")
    return cases
