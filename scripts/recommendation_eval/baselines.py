"""Reference rankings computed offline (design 09, 9.4 and the plan of 21/10/2026, 2.4).

All of them reorder the candidates that passed the hard filters in the V2-a run (logged, up to 200 per search):

    Random       random order                                              floor
    Popularity   tutors.recommendation_score (logged as q)                 "best tutors first"
                 tutor side: offers the studying posting already received  "most wanted students first"
    Semantic     cosine of the search text and the posting only (f_sem)    plain semantic search
    V1-old       the earlier design (D:\\DATN\\AI_Tutor_Recommendation\\docs\\04): a linear sum of 8 parts with the
                 PROFILE embeddings of student and tutor (not the postings)
    V2-linear    linear sum of the V2 features (scoring.v1_rerank): the ablation of the gates and the product

V1-old reads what it needs from the evaluation database; its parts follow the old formulas (4.3):
semantic 0.30, quality 0.20, style 0.15, price 0.10, location 0.10, experience 0.10, subject/grade 0.10,
schedule 0.05 — the old table sums to 1.10, so the weights are divided by 1.10.
"""

import math
import random

import numpy as np

from common import DATABASE, query_json, uid
from scoring import Params, v1_rerank

V1_OLD_WEIGHTS = {"semantic": 0.30, "quality": 0.20, "style": 0.15, "price": 0.10, "location": 0.10,
                  "experience": 0.10, "grade_fit": 0.10, "schedule": 0.05}
NEUTRAL = 0.5


def candidate_ids(run: dict, query_id: str) -> list:
    return [c["candidateId"] for c in (run["results"][query_id].get("candidates") or [])]


def random_ranking(run: dict, query: dict, seed: int) -> list:
    ids = candidate_ids(run, query["id"])
    random.Random(f"{seed}/{query['id']}").shuffle(ids)
    return ids[:20]


def popularity_ranking(run: dict, query: dict, offers: dict | None = None) -> list:
    """Most wanted first. A student looking for tutors: by the tutor's score Q. A tutor looking for students:
    students have no Q (every logged q is null, so this reference used to repeat the V2-a order exactly); by the
    number of offers tutors already sent to the studying posting, the observable sign that it is sought after
    (design 13, 13.4-13.5, 26/10/2026). Ties keep the logged order."""
    candidates = run["results"][query["id"]].get("candidates") or []
    if query["side"] == "T2S":
        offers = tutor_offers() if offers is None else offers
        key = lambda pair: (-offers.get(pair[1]["candidateId"], 0), pair[0])
    else:
        key = lambda pair: (-(pair[1].get("q") or 0), pair[0])
    return [candidate["candidateId"] for _, candidate in sorted(enumerate(candidates), key=key)][:20]


_OFFERS: dict | None = None


def tutor_offers() -> dict:
    """Offers per studying posting in the seed copy (the state every run starts from), read once."""
    global _OFFERS
    if _OFFERS is None:
        rows = query_json("SELECT studying_request_id AS id, count(*) AS n FROM tutor_student_requests GROUP BY 1",
                          database=source_database())
        _OFFERS = {row["id"]: row["n"] for row in rows}
    return _OFFERS


def seed_exists() -> bool:
    return bool(query_json(f"SELECT 1 AS x FROM pg_database WHERE datname = '{DATABASE}_seed'"))


def source_database(exists=seed_exists) -> str:
    """The seed copy when there is one: every search started from it, while market.py and latency.py leave their
    own contracts, decisions and scores in the working database afterwards (found 28/10/2026, step 43b-3)."""
    return f"{DATABASE}_seed" if exists() else DATABASE


def semantic_ranking(run: dict, query: dict) -> list:
    """f_sem of the V2-a run: the cosine of the search text and the posting, no rerank (V2-a has it off)."""
    candidates = run["results"][query["id"]].get("candidates") or []
    ordered = sorted(enumerate(candidates),
                     key=lambda pair: (-((pair[1].get("features") or {}).get("sem") or 0), pair[0]))
    return [candidate["candidateId"] for _, candidate in ordered][:20]


def linear_ranking(run: dict, query: dict) -> list:
    return v1_rerank(run["results"][query["id"]].get("candidates") or [], query["side"], Params())[:20]


class V1Old:
    """The earlier design, rebuilt from its document on the data of the evaluation database (its seed copy)."""

    def __init__(self):
        def vector(text):
            return None if text is None else np.array(json_list(text), dtype=float)

        database = source_database()

        def read(sql):
            return query_json(sql, database)

        self.students = {row["id"]: vector(row["v"]) for row in read(
            "SELECT id, profile_embedding::text AS v FROM students")}
        self.tutors = {row["id"]: row for row in read("""
            SELECT id, profile_embedding::text AS v, average_rating, acceptance_rate, experience_years,
                   completed_contracts_count FROM tutors""")}
        for row in self.tutors.values():
            row["vector"] = vector(row.pop("v"))
        self.postings = {row["id"]: row for row in read("""
            SELECT tr.id, tr.tutor_id, tr.expected_price, tr.teaching_mode,
                   (SELECT count(*) FROM grade_teaching_requests g WHERE g.teaching_request_id = tr.id) AS grades,
                   COALESCE((SELECT json_agg(d.district_id) FROM teaching_request_districts d
                             WHERE d.teaching_request_id = tr.id), '[]') AS districts
            FROM teaching_requests tr""")}
        self.styles = {}
        for row in read("""
                SELECT et.entity_id, t.code FROM recommendation_entity_tags et
                JOIN recommendation_tags t ON t.id = et.tag_id
                WHERE et.deleted_at IS NULL AND t.tag_group = 'STYLE'"""):
            self.styles.setdefault(row["entity_id"], set()).add(row["code"])
        self.slots = {}
        for table, column in (("teaching_request_availabilities", "teaching_request_id"),
                              ("studying_request_availabilities", "studying_request_id")):
            for row in read(f"SELECT {column} AS owner, day_of_week, start_time::text AS s, end_time::text AS e "
                                  f"FROM {table}"):
                self.slots.setdefault(row["owner"], []).append((row["day_of_week"], row["s"][:5], row["e"][:5]))
        self.homes = {row["id"]: row["district_id"] for row in read(
            "SELECT s.id, u.district_id FROM students s JOIN users u ON u.id = s.user_id")}

    def ranking(self, run: dict, query: dict, student: dict) -> list:
        from world import schedule_overlap

        student_id = student["id"]
        own = uid("eval", student["posting"]["key"]) if student.get("has_posting") else None
        knows_budget = query["kind"] in ("full", "posting")
        knows_slots = query["kind"] in ("full", "posting")
        wanted_styles = self.styles.get(student_id, set()) | self.styles.get(own, set())
        student_vector = self.students.get(student_id)
        scored = []
        for index, posting_id in enumerate(candidate_ids(run, query["id"])):
            posting = self.postings.get(posting_id)
            if posting is None:
                continue
            tutor = self.tutors[posting["tutor_id"]]
            parts = {
                "semantic": cosine(student_vector, tutor["vector"]),
                "quality": quality(tutor),
                "style": jaccard(wanted_styles, self.styles.get(tutor["id"], set())
                                 | self.styles.get(posting_id, set())),
                "price": price_fit(float(posting["expected_price"] or 0), student["budget"]) if knows_budget
                else NEUTRAL,
                "location": 1.0 if posting["teaching_mode"] == "ONLINE"
                else float(self.homes.get(student_id) in json_list(posting["districts"])),
                "experience": experience(tutor),
                "grade_fit": max(0.0, 1 - (int(posting["grades"] or 1) - 1) / 6),
                "schedule": schedule_overlap(student["slots"], self.slots.get(posting_id, []))
                if knows_slots and self.slots.get(posting_id) else NEUTRAL,
            }
            total = sum(V1_OLD_WEIGHTS[name] * value for name, value in parts.items()) / sum(V1_OLD_WEIGHTS.values())
            scored.append((total, -index, posting_id))
        return [posting_id for _, _, posting_id in sorted(scored, reverse=True)][:20]


def json_list(value):
    import json
    return value if isinstance(value, list) else json.loads(value)


def cosine(left, right) -> float:
    if left is None or right is None:
        return NEUTRAL
    value = float(left @ right / (np.linalg.norm(left) * np.linalg.norm(right)))
    return max(0.0, value)


def quality(tutor: dict) -> float:
    rating = float(tutor["average_rating"] or 0) / 5
    acceptance = float(tutor["acceptance_rate"] or 0)
    acceptance = acceptance / 100 if acceptance > 1 else acceptance
    return 0.7 * rating + 0.3 * acceptance


def price_fit(price: float, budget: float) -> float:
    """1 within budget, down to 0 at 30% over."""
    if price <= budget:
        return 1.0
    return max(0.0, 1 - (price - budget) / (0.3 * budget))


def experience(tutor: dict) -> float:
    years = float(tutor["experience_years"] or 0)
    contracts = float(tutor["completed_contracts_count"] or 0)
    return 0.5 * min(1.0, math.log1p(years) / math.log1p(10)) + 0.5 * min(1.0, math.log1p(contracts) / math.log1p(20))


def jaccard(left: set, right: set) -> float:
    if not left or not right:
        return NEUTRAL
    return len(left & right) / len(left | right)
