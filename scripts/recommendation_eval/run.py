"""26a/26b: runs the backend in each configuration of design 09, 9.4 and searches through the real API.

    python scripts/recommendation_eval/run.py setup --scenario base          # rebuild ptutor_eval and fill it
    python scripts/recommendation_eval/run.py search V2-full --scenario base # one configuration
    python scripts/recommendation_eval/run.py search all --scenario base     # every configuration

setup drops and recreates the evaluation database (ptutor_eval, never the dev database), lets the backend create
the schema and the catalog, loads out/<scenario>/world.json (load.py), waits for the embedding, A1 and the score Q,
then simulates the browsing history for H1. Results go to out/<scenario>/runs/.

The configurations are switched with the backend's own properties (no special code path), on the jar in
backend/target. Every run uses the prior weights (L1 off, design 09, 9.4) and logs up to 200 candidates per
search, so that 26c can recompute scores offline (design 09, 9.9.3). Stop any backend on port 8080 first.
"""

import json
import math
import random
import subprocess
import sys
import time
from pathlib import Path

from common import DATABASE, DB_URL, OUT, REPO, SEED, Api, psql, query_json, read_json, uid, wait_for_api, write_json
from world import rel

JAR = REPO / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar"
BASE = {
    "spring.datasource.url": DB_URL,
    "app.recommendation.log-candidate-limit": "200",
    "app.recommendation.learning.enabled": "false",
    "app.recommendation.proactive.enabled": "false",
    # Phase and relaxation timings for step 27 (design 09, 9.5).
    "logging.level.com.ptutor.backend.service.RecommendationService": "DEBUG",
    "logging.level.com.ptutor.backend.service.StudentRecommendationService": "DEBUG",
    "logging.level.com.ptutor.backend.service.RelaxationService": "DEBUG",
}
OFF = "false"
CONFIGS = {
    "V2-a": {"reciprocal.enabled": OFF, "willingness.congestion-enabled": OFF, "rerank.enabled": OFF,
             "exploration.enabled": OFF, "history.enabled": OFF},
    "V2-R": {"willingness.congestion-enabled": OFF, "rerank.enabled": OFF, "exploration.enabled": OFF,
             "history.enabled": OFF},
    "V2-RC": {"rerank.enabled": OFF, "exploration.enabled": OFF, "history.enabled": OFF},
    "V2-RCX": {"exploration.enabled": OFF, "history.enabled": OFF},
    "V2-full": {},
    "V2-full-H1": {"history.enabled": OFF},
    "V2-full-A1": {"enrichment.use-llm-tags": OFF},
}
PAGE_SIZE = 20


def start_backend(properties: dict, log_name: str) -> subprocess.Popen:
    args = ["java", "-Xmx768m", "-jar", str(JAR)] + [f"--{key}={value}" for key, value in properties.items()]
    log = open(OUT / f"backend-{log_name}.log", "w", encoding="utf-8")
    process = subprocess.Popen(args, cwd=REPO / "backend", stdout=log, stderr=subprocess.STDOUT)
    wait_for_api()
    return process


def stop_backend(process: subprocess.Popen) -> None:
    process.terminate()
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        process.kill()


def config_properties(name: str) -> dict:
    properties = dict(BASE)
    properties.update({f"app.recommendation.{key}": value for key, value in CONFIGS[name].items()})
    return properties


class Catalog:
    """Ids of subjects, grades, districts and tags, read once from the database."""

    def __init__(self):
        self.subjects = {row["name"]: row["id"] for row in query_json("SELECT id, name FROM subjects")}
        self.grades = {row["level"]: row["id"] for row in query_json(
            "SELECT DISTINCT ON (level) id, level FROM grades ORDER BY level, name")}
        self.districts = {(row["city"], row["name"]): row["id"] for row in query_json(
            "SELECT DISTINCT ON (p.name, d.name) d.id, d.name, p.name AS city FROM districts d "
            "JOIN provinces p ON p.id = d.province_id ORDER BY p.name, d.name, d.id")}
        self.provinces = {row["name"]: row["id"] for row in query_json("SELECT id, name FROM provinces")}


def student_body(query: dict, student: dict, catalog: Catalog) -> dict:
    subject = catalog.subjects[student["subject"]]
    grade = catalog.grades[student["grade"]]
    if query["kind"] == "posting":
        return {"useStudyingRequestId": uid("eval", student["posting"]["key"])}
    if query["kind"] == "subject_grade":
        return {"filters": {"subjectId": subject, "gradeId": grade}}
    if query["kind"] == "semantic":
        return {"filters": {"subjectId": subject, "gradeId": grade},
                "semanticText": f"{student['profile']['current_level']}, yếu {student['profile']['weak_points']}. "
                                f"{student['profile']['goals']}"}
    mode = student["mode"]
    if mode == "OFFLINE":
        area = {"scope": "DISTRICTS", "districtIds": [catalog.districts[(student["city"], student["home"])]]}
    elif mode == "ANY":
        area = {"scope": "PROVINCE", "provinceId": catalog.provinces[student["city"]]}
    else:
        area = {"scope": "ANY"}
    return {
        "filters": {
            "subjectId": subject, "gradeId": grade, "learningMode": mode, "area": area,
            "price": {"max": student["budget"]},
            "availabilities": [{"dayOfWeek": day, "startTime": start, "endTime": end}
                               for day, start, end in student["slots"]],
            "styleTagCodes": student["styles"], "goalTagCodes": [student["goal"]],
            "topicTagCodes": student["topics"],
        },
        "semanticText": student["profile"]["weak_points"],
    }


def search(api: Api, query: dict, world_index: dict, catalog: Catalog) -> dict:
    if query["side"] == "S2T":
        student = world_index["students"][query["owner"]]
        body = student_body(query, student, catalog)
        path = "/api/v1/students/me/tutor-recommendations"
        id_field = "teachingRequestId"
    else:
        body = {"useTeachingRequestId": uid("eval", query["posting"])}
        path = "/api/v1/tutors/me/student-recommendations"
        id_field = "studyingRequestId"
    started = time.perf_counter()
    response = api.call("POST", f"{path}?page=0&size={PAGE_SIZE}", body)["data"]
    elapsed = (time.perf_counter() - started) * 1000
    return {
        "logId": response["logId"],
        "items": [{"rank": item["rank"], "id": item[id_field], "score": item["score"],
                   "exploration": item["isExploration"]} for item in response["items"]],
        "total": response["totalElements"],
        "lowConfidence": response["lowConfidence"],
        "clientMs": round(elapsed),
    }


def index_world(world: dict) -> dict:
    return {"students": {student["key"]: student for student in world["students"]},
            "tutors": {tutor["key"]: tutor for tutor in world["tutors"]}}


def logins(world: dict) -> dict:
    sessions = {}
    for entity in world["students"] + world["tutors"]:
        sessions[entity["key"]] = Api().login(f"eval.{entity['key']}@ptutor.local")
    return sessions


def export_logs(log_ids) -> dict:
    ids = ",".join(f"'{log_id}'" for log_id in log_ids)
    rows = query_json(f"SELECT id, latency_ms, query_context, returned_candidate_ids FROM recommendation_logs "
                      f"WHERE id IN ({ids})")
    return {row["id"]: row for row in rows}


def run_config(name: str, world: dict) -> None:
    process = start_backend(config_properties(name), name)
    try:
        catalog = Catalog()
        sessions = logins(world)
        index = index_world(world)
        results = {}
        for number, query in enumerate(world["queries"], start=1):
            results[query["id"]] = search(sessions[query["owner"]], query, index, catalog)
            if number % 50 == 0:
                print(f"  {name}: {number}/{len(world['queries'])}", flush=True)
        logs = export_logs([result["logId"] for result in results.values()])
        for result in results.values():
            log = logs.get(result["logId"], {})
            result["latencyMs"] = log.get("latency_ms")
            result["queryContext"] = log.get("query_context")
            result["candidates"] = log.get("returned_candidate_ids")
        write_json(OUT / "runs" / f"{name}.json", {"config": name, "properties": config_properties(name),
                                                    "results": results})
        print(f"{name}: {len(results)} searches -> out/runs/{name}.json", flush=True)
    finally:
        stop_backend(process)


# ---- prepare: wait for the pipeline, then browsing history for H1 (design 09, 9.3)

PIPELINE_SQL = """
SELECT
  (SELECT count(*) FROM teaching_requests tr JOIN tutors t ON t.id = tr.tutor_id JOIN users u ON u.id = t.user_id
    WHERE u.email LIKE 'eval.%' AND tr.embedding IS NULL) AS postings_without_vector,
  (SELECT count(*) FROM studying_requests sr JOIN students s ON s.id = sr.student_id JOIN users u ON u.id = s.user_id
    WHERE u.email LIKE 'eval.%' AND sr.embedding IS NULL) AS studying_without_vector,
  (SELECT count(*) FROM ai_enrichment_jobs WHERE status IN ('PENDING', 'PROCESSING')
    AND job_type IN ('ENRICH', 'EMBED')) AS jobs_waiting,
  (SELECT count(*) FROM tutors t JOIN users u ON u.id = t.user_id
    WHERE u.email LIKE 'eval.%' AND t.score_updated_at IS NULL) AS tutors_without_score
"""


def wait_for_pipeline(timeout: float = 3600) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        state = query_json(PIPELINE_SQL)[0]
        print("  pipeline:", state, flush=True)
        if not any(state.values()):
            return
        time.sleep(30)
    raise TimeoutError("embedding / enrichment / score did not finish")


def browse(world: dict, sessions_per_student: int = 4) -> None:
    """Click model: examine position k with 0.85^k, attracted by hidden relevance and the hidden taste."""
    rng = random.Random(SEED + 2)
    catalog = Catalog()
    index = index_world(world)
    sessions = logins(world)
    postings = {uid("eval", posting["key"]): (posting, tutor) for tutor in world["tutors"]
                for posting in tutor["postings"]}
    events = 0
    for round_number in range(sessions_per_student):
        for student in world["students"]:
            query = {"side": "S2T", "owner": student["key"], "kind": "subject_grade"}
            result = search(sessions[student["key"]], query, index, catalog)
            for item in result["items"][:10]:
                if rng.random() > 0.85 ** (item["rank"] - 1):
                    break
                posting, tutor = postings.get(item["id"], (None, None))
                if posting is None:
                    continue
                value = rel(student, posting, tutor)
                attraction = 1 / (1 + math.exp(-(2.0 * (value - 1.2) + (1.2 if student["taste"] in tutor["styles"]
                                                                        else 0))))
                action = None
                if rng.random() < attraction:
                    action = "FAVORITED" if value >= 2 and rng.random() < 0.4 else "CLICKED"
                elif value == 0 and rng.random() < 0.3:
                    action = "DISMISSED"
                if action:
                    sessions[student["key"]].call("POST", f"/api/v1/recommendations/{result['logId']}/events",
                                                  {"action": action, "rank": item["rank"], "candidateId": item["id"]})
                    events += 1
        print(f"  browsing round {round_number + 1}: {events} events so far", flush=True)
        time.sleep(15)  # let ProfileUpdateWorker (every 10 s) fold the round into the profiles


def reset_database() -> None:
    if DATABASE in ("ptutor", "ptutor_test"):
        raise SystemExit(f"refusing to drop {DATABASE}")
    psql(f"DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);", database="postgres")
    psql(f"CREATE DATABASE {DATABASE};", database="postgres")


def setup(world: dict) -> None:
    import load

    reset_database()
    properties = config_properties("V2-full")
    properties["app.tutor-recommendation.recalculation-cron"] = "0 * * * * *"
    # Queue the embedding and enrichment of the new rows soon after loading them.
    properties["app.recommendation.embedding.backfill-initial-delay-ms"] = "60000"
    process = start_backend(properties, "setup")
    try:
        psql(load.build_sql(world))
        print("loaded", load.counts(), flush=True)
        wait_for_pipeline()
        browse(world)
        time.sleep(30)
        profiles = query_json("SELECT count(*) AS n FROM recommendation_user_profiles p JOIN users u ON u.id = p.user_id "
                              "WHERE u.email LIKE 'eval.%'")[0]["n"]
        print(f"prepared: {profiles} preference profiles", flush=True)
    finally:
        stop_backend(process)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    if len(sys.argv) < 2 or sys.argv[1] not in ("setup", "search"):
        print(__doc__)
        return 1
    if sys.argv[1] == "setup":
        setup(world)
        return 0
    names = list(CONFIGS) if len(sys.argv) < 3 or sys.argv[2] == "all" else sys.argv[2:]
    for name in names:
        run_config(name, world)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
