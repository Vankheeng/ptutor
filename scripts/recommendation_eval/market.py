"""2.2: market simulation over 8 rounds through the real API (plan of 21/10/2026; design 09, 9.5).

    python scripts/recommendation_eval/market.py --scenario crowded V2-a V2-R V2-RC V2-full Popularity

Every configuration starts from the same database (the copy run.py setup leaves as <db>_seed). Each round, the
students without a tutor search, look at the top 10 (position bias, hidden relevance, a pull towards popular tutors)
and apply to up to 3 postings through the API; then every tutor decides on the pending applications through the API,
by the hidden acceptance model and within the places of the posting. The next round searches on that history, so
willingness (b) and the congestion factor work on data the run itself produced. "Popularity" searches with V2-a and
shows the candidates in order of recommendation_score.

Writes out/<scenario>/market/<config>.json and ket-qua/<scenario>/market.csv.
"""

import csv
import math
import random
import sys
import time

import numpy as np

from common import DATABASE, OUT, RESULTS, SEED, Api, psql, query_json, read_json, uid, write_json
from evaluate import gini
from run import CONFIGS, config_properties, search, start_backend, stop_backend, Catalog, index_world
from world import p_accept, rel

ROUNDS = 8
LOOK = 10
APPLY = 3
EXAMINE = 0.85
POPULAR_PULL = 2.0


def restore() -> None:
    psql(f"DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);", database="postgres")
    psql(f"CREATE DATABASE {DATABASE} TEMPLATE {DATABASE}_seed;", database="postgres")


def popularity_order(result: dict) -> list:
    rows = query_json(f"SELECT returned_candidate_ids AS c FROM recommendation_logs WHERE id = '{result['logId']}'")
    candidates = rows[0]["c"] if rows else []
    ordered = sorted(enumerate(candidates), key=lambda pair: (-(pair[1].get("q") or 0), pair[0]))
    return [candidate["candidateId"] for _, candidate in ordered]


def simulate(config: str, world: dict) -> dict:
    rng = random.Random(f"{SEED}/{config}")
    backend_config = "V2-a" if config == "Popularity" else config
    restore()
    properties = config_properties(backend_config)
    properties["app.tutor-recommendation.recalculation-cron"] = "0 * * * * *"
    process = start_backend(properties, f"market-{config}")
    try:
        catalog = Catalog()
        index = index_world(world)
        postings = {uid("eval", posting["key"]): (posting, tutor) for tutor in world["tutors"]
                    for posting in tutor["postings"]}
        students = [student for student in world["students"]]
        tutors = {tutor["key"]: tutor for tutor in world["tutors"]}
        sessions = {student["key"]: Api().login(f"eval.{student['key']}@ptutor.local") for student in students}
        tutor_sessions = {}
        matched_round, rejections, applied = {}, {s["key"]: 0 for s in students}, {s["key"]: set() for s in students}
        matched_rel, exposures, new_shown, shown_total = [], {}, 0, 0
        pending = []  # (application id, posting id, student key)
        for round_number in range(1, ROUNDS + 1):
            started = time.time()
            waiting = [s for s in students if s["key"] not in matched_round]
            rng.shuffle(waiting)
            for student in waiting:
                query = {"side": "S2T", "owner": student["key"], "kind": "full"}
                result = search(sessions[student["key"]], query, index, catalog)
                ranking = popularity_order(result) if config == "Popularity" else [i["id"] for i in result["items"]]
                ranking = [candidate for candidate in ranking if candidate in postings][:LOOK]
                for candidate in ranking:
                    exposures[candidate] = exposures.get(candidate, 0) + 1
                    shown_total += 1
                    new_shown += bool(postings[candidate][1].get("new"))
                choices = []
                for position, candidate in enumerate(ranking):
                    if candidate in applied[student["key"]]:
                        continue
                    posting, tutor = postings[candidate]
                    value = rel(student, posting, tutor)
                    attraction = 1 / (1 + math.exp(-2.0 * (value - 1.2)))
                    weight = EXAMINE ** position * attraction * (1 + POPULAR_PULL * bool(tutor.get("famous")))
                    choices.append((weight, candidate))
                for candidate in pick(choices, APPLY, rng):
                    posting, tutor = postings[candidate]
                    try:
                        response = sessions[student["key"]].call(
                            "POST", f"/api/v1/students/me/teaching-requests/{candidate}/student-tutor-requests",
                            {"gradeId": catalog.grades[student["grade"]], "proposedPrice": student["budget"],
                             "learningMode": posting["mode"], "message": "Em muốn đăng ký học."})
                    except RuntimeError:
                        continue  # the posting filled up or closed since the search
                    applied[student["key"]].add(candidate)
                    pending.append((response["data"]["id"], candidate, student["key"]))
            # Tutors answer, first come first served, within the places of the posting.
            still = []
            by_student = {s["key"]: s for s in students}
            for application, candidate, student_key in pending:
                posting, tutor = postings[candidate]
                student = by_student[student_key]
                if student_key in matched_round:
                    still.append((application, candidate, student_key))  # left waiting; the student has a tutor
                    continue
                if tutor["key"] not in tutor_sessions:
                    tutor_sessions[tutor["key"]] = Api().login(f"eval.{tutor['key']}@ptutor.local")
                accept = rng.random() < p_accept(student, posting, tutor)
                try:
                    tutor_sessions[tutor["key"]].call(
                        "PATCH", f"/api/v1/tutors/me/teaching-requests/{candidate}/student-requests/{application}/status",
                        {"status": "ACCEPTED" if accept else "REJECTED"})
                except RuntimeError:
                    accept = False  # no place left: the posting is MATCHED
                    try:
                        tutor_sessions[tutor["key"]].call(
                            "PATCH",
                            f"/api/v1/tutors/me/teaching-requests/{candidate}/student-requests/{application}/status",
                            {"status": "REJECTED"})
                    except RuntimeError:
                        pass
                if accept:
                    matched_round[student_key] = round_number
                    matched_rel.append(rel(student, posting, tutor))
                else:
                    rejections[student_key] += 1
            pending = []
            print(f"  {config} round {round_number}: {len(matched_round)}/{len(students)} matched "
                  f"({time.time() - started:.0f} s)", flush=True)
            time.sleep(65)  # let the per-minute job refresh acceptance rates and Q before the next round
        rounds = [matched_round.get(s["key"], ROUNDS + 1) for s in students]
        pool = list(postings)
        summary = {
            "config": config,
            "match_rate": len(matched_round) / len(students),
            "mean_rounds_to_match": float(np.mean([r for r in rounds if r <= ROUNDS])) if matched_round else None,
            "mean_rounds_censored": float(np.mean(rounds)),
            "rejections_per_student": float(np.mean(list(rejections.values()))),
            "gini_exposure": gini([exposures.get(p, 0) for p in pool]),
            "new_tutor_exposure_share": new_shown / shown_total if shown_total else 0.0,
            "new_tutor_posting_share": sum(bool(t.get("new")) for _, t in postings.values()) / len(postings),
            "matched_rel": float(np.mean(matched_rel)) if matched_rel else None,
        }
        write_json(OUT / "market" / f"{config}.json", {"summary": summary, "matched_round": matched_round,
                                                       "rejections": rejections, "exposures": exposures})
        return summary
    finally:
        stop_backend(process)


def pick(choices: list, count: int, rng: random.Random) -> list:
    chosen = []
    choices = [c for c in choices if c[0] > 0]
    while choices and len(chosen) < count:
        total = sum(weight for weight, _ in choices)
        point = rng.random() * total
        for position, (weight, candidate) in enumerate(choices):
            point -= weight
            if point <= 0:
                chosen.append(candidate)
                choices.pop(position)
                break
    return chosen


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    names = sys.argv[1:] or ["Popularity", "V2-a", "V2-R", "V2-RC", "V2-full"]
    unknown = [name for name in names if name != "Popularity" and name not in CONFIGS]
    if unknown:
        raise SystemExit(f"unknown configurations: {unknown}")
    rows = [simulate(name, world) for name in names]
    RESULTS.mkdir(parents=True, exist_ok=True)
    path = RESULTS / "market.csv"
    existing = {}
    if path.exists():
        with path.open(encoding="utf-8") as handle:
            existing = {row["config"]: row for row in csv.DictReader(handle)}
    for row in rows:
        existing[row["config"]] = row
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in existing.values():
            writer.writerow({k: (f"{v:.4f}" if isinstance(v, float) else v) for k, v in row.items()})
    for row in rows:
        print(row)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
