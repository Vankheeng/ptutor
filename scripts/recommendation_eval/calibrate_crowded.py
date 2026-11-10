"""Picks the hidden-model parameters of the crowded scenario before its database is rebuilt (design 13, 13.3).

    python scripts/recommendation_eval/calibrate_crowded.py

Draws the crowded world in memory (no database, no backend, no LLM) and estimates the checks of 13.3 on an
approximation of the candidates a search returns (same subject and grade, a compatible mode and district, a price
under the hard limit): median number of candidates on both sides, mean P(accept) and EA@10 of a random top 10, and
the correlation between rel and an estimate of Q (the expected review, minus a step for unreliable tutors, who draw
founded complaints). The real checks are measured again on the rebuilt database (crowded_check.py).
"""

import os
import random
import statistics
import sys

os.environ.setdefault("EVAL_SCENARIO", "crowded")

import numpy as np

import world as W
from common import SCENARIO

PRICE_HARD_FACTOR = 1.5  # app.recommendation.price.hard-factor


def candidates_for_student(student: dict, postings: list) -> list:
    found = []
    for posting, tutor in postings:
        if posting["subject"] != student["subject"] or student["grade"] not in posting["grades"]:
            continue
        if student["mode"] != "ANY" and posting["mode"] not in (student["mode"], "BOTH"):
            continue
        if posting["mode"] == "OFFLINE" and (tutor["city"] != student["city"]
                                             or student["home"] not in posting["districts"]):
            continue
        if posting["price"] > student["budget"] * PRICE_HARD_FACTOR:
            continue
        found.append((posting, tutor))
    return found


def candidates_for_posting(posting: dict, tutor: dict, students: list) -> list:
    return [student for student in students if student["has_posting"] and student["subject"] == posting["subject"]
            and student["grade"] in posting["grades"]
            and (student["mode"] == "ANY" or posting["mode"] in (student["mode"], "BOTH"))
            and (posting["mode"] != "OFFLINE" or student["home"] in posting["districts"])]


def expected_q(tutor: dict) -> float:
    if tutor.get("new"):
        return 0.5
    if tutor.get("famous"):
        return 1.0
    stars = min(5, max(1, 1 + 4 * tutor["quality"])) if W.S.get("rating_from_quality") else (
        4.67 if tutor["reliable"] else 3.0)
    return (stars - (0 if tutor["reliable"] else 1.5)) / 5


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    if SCENARIO != "crowded":
        raise SystemExit("run with EVAL_SCENARIO=crowded")
    world = W.build()
    postings = [(posting, tutor) for tutor in world["tutors"] for posting in tutor["postings"]]
    rng = random.Random(1)
    sizes, accepts, random_ea, qs, rels = [], [], [], [], []
    for student in world["students"]:
        found = candidates_for_student(student, postings)
        sizes.append(len(found))
        if not found:
            continue
        accepts += [W.p_accept(student, posting, tutor) for posting, tutor in found]
        sample = rng.sample(found, min(10, len(found)))
        random_ea.append(np.mean([W.p_accept(student, posting, tutor) for posting, tutor in sample]))
        for posting, tutor in found:
            qs.append(expected_q(tutor))
            rels.append(W.rel(student, posting, tutor))
    tutor_side = [len(candidates_for_posting(posting, tutor, world["students"])) for posting, tutor in postings]
    print(f"students {len(world['students'])}, studying postings {sum(s['has_posting'] for s in world['students'])}")
    print(f"median candidates S2T {statistics.median(sizes)}, T2S {statistics.median(tutor_side)}")
    print(f"b0 {W.BETA['b0']}: mean P(accept) {np.mean(accepts):.3f}, random EA@10 {np.mean(random_ea):.3f}")
    print(f"quality bonus at {W.S.get('quality_bonus_at')}: corr(Q estimate, rel) {np.corrcoef(qs, rels)[0, 1]:.3f}, "
          f"rel 3 share {np.mean(np.array(rels) == 3):.2f}, rel > 0 share {np.mean(np.array(rels) > 0):.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
