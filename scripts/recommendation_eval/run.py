"""26a/26b: runs the backend in each configuration of design 09, 9.4 and searches through the real API.

    python scripts/recommendation_eval/run.py setup --scenario base          # rebuild ptutor_eval and fill it
    python scripts/recommendation_eval/run.py search V2-full --scenario base # one configuration
    python scripts/recommendation_eval/run.py search all --scenario base     # every configuration
    python scripts/recommendation_eval/run.py search rerank-async --scenario text-only --drain
    python scripts/recommendation_eval/run.py search rerank-async --scenario text-only --keep-cache --as rerank-async-2

setup drops and recreates the evaluation database (ptutor_eval, never the dev database), lets the backend create
the schema and the catalog, loads out/<scenario>/world.json (load.py), waits for the embedding, A1 and the score Q,
then simulates the browsing history for H1. Results go to out/<scenario>/runs/. Two frozen copies: <db>_a1 once A1,
the embedding and Q are done (setup --from-a1 starts the browsing again from it without paying A1 twice), and
<db>_seed at the end.

Text-only runs empty the rerank cache first unless --keep-cache (a second pass on what the first one scored).
--drain keeps the backend up after the searches until its background rerank worker has emptied its queue
(rerank.mode = ASYNC, design 13, 13.2); --as names the output of a pass of the same configuration.

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

from common import DATABASE, DB_URL, OUT, SCENARIO, REPO, SEED, Api, clone, psql, query_json, read_json, uid, wait_for_api, write_json
import fallback
from world import rel

JAR = REPO / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar"
BASE = {
    "spring.datasource.url": DB_URL,
    "app.recommendation.log-candidate-limit": "200",
    "app.recommendation.learning.enabled": "false",
    "app.recommendation.proactive.enabled": "false",
    # Thousands of postings at once: the default worker (5 jobs every 30 s) needs hours for A1. Calls stay one at a time.
    "app.recommendation.enrichment.batch-size": "20",
    "app.recommendation.enrichment.worker-interval-ms": "1000",
    # Phase and relaxation timings for step 27 (design 09, 9.5).
    "logging.level.com.ptutor.backend.service.RecommendationService": "DEBUG",
    "logging.level.com.ptutor.backend.service.StudentRecommendationService": "DEBUG",
    "logging.level.com.ptutor.backend.service.RelaxationService": "DEBUG",
    # "queue empty" tells --drain when the background reranking is done.
    "logging.level.com.ptutor.backend.service.RerankQueue": "DEBUG",
}
OFF = "false"
# Reranking is off by default since 26/10/2026 (design 13, 13.2): the configurations that include it say how, and
# need the AI service started with AI_RERANK_ENABLED=true. ASYNC ranks without waiting; its scores only count once
# the worker has scored the pairs (run.py search ... --drain, then a second pass with --keep-cache).
WITH_RERANK = {"rerank.mode": "ASYNC"}
CONFIGS = {
    "V2-a": {"reciprocal.enabled": OFF, "willingness.congestion-enabled": OFF, "rerank.enabled": OFF,
             "exploration.enabled": OFF, "history.enabled": OFF},
    "V2-R": {"willingness.congestion-enabled": OFF, "rerank.enabled": OFF, "exploration.enabled": OFF,
             "history.enabled": OFF},
    "V2-RC": {"rerank.enabled": OFF, "exploration.enabled": OFF, "history.enabled": OFF},
    "V2-RCX": {**WITH_RERANK, "exploration.enabled": OFF, "history.enabled": OFF},
    "V2-full": {**WITH_RERANK},
    "V2-full-H1": {**WITH_RERANK, "history.enabled": OFF},
    "V2-full-A1": {**WITH_RERANK, "enrichment.use-llm-tags": OFF},
    # latency.py, warm pass: pairs waiting over a minute are scored between the searches (worst case of 13.2).
    "V2-full-busy": {**WITH_RERANK, "rerank.worker-max-wait-ms": "60000"},
}
# Step 39 (design 13, 13.4-13.5), crowded: the sweep runs the way the system will run, both parts on. λ is swept with
# the reciprocal of before (harmonic), α with the congestion factor of before; the formulas of before are the controls.
# Not part of "all".
OLD_CONGESTION = {"willingness.congestion-formula": "EVERY_APPLICATION", "willingness.congestion-lambda": "0.5"}
HARMONIC = {"reciprocal.mode": "HARMONIC"}
SWEEP = {
    "V2-R-harm": {**CONFIGS["V2-R"], **HARMONIC},
    "V2-RC-old": {**CONFIGS["V2-RC"], **OLD_CONGESTION, **HARMONIC},
    **{f"V2-RC-s{lam}": {**CONFIGS["V2-RC"], "willingness.congestion-formula": "ON_SHORTAGE",
                         "willingness.congestion-lambda": lam, **HARMONIC} for lam in ("0.25", "0.5", "1")},
    **{f"V2-RC-g{alpha}": {**CONFIGS["V2-RC"], **OLD_CONGESTION, "reciprocal.mode": "GEOMETRIC",
                           "reciprocal.alpha": alpha} for alpha in ("0.5", "0.65", "0.8")},
    # The best λ and α of the two sweeps together, to confirm them (the way the system would run).
    "V2-RC-s0.5-g0.65": {**CONFIGS["V2-RC"], "willingness.congestion-formula": "ON_SHORTAGE",
                         "willingness.congestion-lambda": "0.5", "reciprocal.mode": "GEOMETRIC",
                         "reciprocal.alpha": "0.65"},
}
CONFIGS.update(SWEEP)
# Step 40 (design 13, 13.8): the final score is R x (0.7 + 0.3 Q) x G. Each part is switched off on the defaults of
# 26/10/2026 (congestion ON_SHORTAGE λ 0.5, reciprocal GEOMETRIC α 0.65): no quality factor, no schedule gate, no
# gate for a recent rejection. On base, the old congestion formula too, the check 13.4 asked for.
DEFAULTS = {**CONFIGS["V2-RC"], "willingness.congestion-formula": "ON_SHORTAGE", "willingness.congestion-lambda": "0.5",
            "reciprocal.mode": "GEOMETRIC", "reciprocal.alpha": "0.65"}
ABLATION = {
    "V2-RC-noQ": {**DEFAULTS, "quality.base": "1", "quality.weight": "0"},
    "V2-RC-noSchedGate": {**DEFAULTS, "gate.no-schedule-overlap": "1"},
    "V2-RC-noRejectGate": {**DEFAULTS, "gate.recent-reject": "1"},
    "V2-RC-oldCongestion": {**DEFAULTS, "willingness.congestion-formula": "EVERY_APPLICATION"},
}
CONFIGS.update(ABLATION)
SWEEP.update(ABLATION)  # not part of "all" either
# Text-only scenario (2.3): cosine only against rerank, a sweep of the rerank weight and of the weight of f_sem.
# Exploration and history are off so that only the reading of the text changes. Not part of "all".
QUIET = {"exploration.enabled": OFF, "history.enabled": OFF}
# The runs of 22/10/2026 waited for the model in the search.
WAITED = {**QUIET, "rerank.mode": "ONLINE"}
SEM_045 = {"weights.sem": "0.45", "weights.need": "0.1571", "weights.price": "0.1571", "weights.style": "0.0786",
           "weights.sched": "0.0786", "weights.area": "0.0393", "weights.segment": "0.0393"}
TEXT_CONFIGS = {
    "cosine-only": {**QUIET, "rerank.enabled": OFF},
    "rerank-w0.3": {**WAITED, "semantic.rerank-weight": "0.3"},
    "rerank-w0.5": {**WAITED, "semantic.rerank-weight": "0.5"},
    "rerank-w0.7": {**WAITED, "semantic.rerank-weight": "0.7"},
    "rerank-w0.9": {**WAITED, "semantic.rerank-weight": "0.9"},
    "cosine-only-sem0.45": {**QUIET, "rerank.enabled": OFF, **SEM_045},
    "rerank-w0.7-sem0.45": {**WAITED, "semantic.rerank-weight": "0.7", **SEM_045},
    # The 1.5 s budget cuts many searches short (10 pairs take ~1.75 s): rerank with room to score all ten.
    "rerank-w0.7-t4000": {**WAITED, "semantic.rerank-weight": "0.7", "rerank.timeout-ms": "4000"},
    # 26/10/2026 (design 13, 13.2): waiting for the model in the search, against scoring in the background.
    # 350 searches queue up to 3 500 pairs at once, more than the default room for 2 000.
    "rerank-online": {**QUIET, "rerank.mode": "ONLINE", "rerank.queue-capacity": "5000"},
    "rerank-async": {**QUIET, "rerank.mode": "ASYNC", "rerank.queue-capacity": "5000"},
}
PAGE_SIZE = 20


def start_backend(properties: dict, log_name: str) -> subprocess.Popen:
    if not JAR.exists():
        raise SystemExit(f"{JAR} is missing: run mvnw package -DskipTests in backend first")
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
    settings = CONFIGS[name] if name in CONFIGS else TEXT_CONFIGS[name]
    properties.update({f"app.recommendation.{key}": value for key, value in settings.items()})
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


def wait_for_rerank_queue(log: Path, timeout: float = 4 * 3600) -> None:
    """Until the background worker has scored every queued pair; it starts once searches are quiet for 2 s."""
    time.sleep(5)
    deadline = time.time() + timeout
    started = time.time()
    while not fallback.queue_drained(log.read_text(encoding="utf-8", errors="replace")):
        if time.time() > deadline:
            raise SystemExit(f"the rerank queue is still busy after {timeout / 3600:.0f} h")
        time.sleep(15)
    print(f"  rerank queue drained after {(time.time() - started) / 60:.0f} min", flush=True)


def run_config(name: str, world: dict, side: str | None = None, run_name: str | None = None,
               keep_cache: bool = False, drain: bool = False) -> None:
    run_name = run_name or name
    if SCENARIO == "text-only" and not keep_cache and DATABASE not in ("ptutor", "ptutor_test"):
        # Every rerank setting starts cold: scores cached by the previous one would hide the time budget.
        psql("DELETE FROM recommendation_rerank_cache;")
    process = start_backend(config_properties(name), run_name)
    try:
        catalog = Catalog()
        sessions = logins(world)
        index = index_world(world)
        results = {}
        queries = [query for query in world["queries"] if side is None or query["side"] == side]
        for number, query in enumerate(queries, start=1):
            results[query["id"]] = search(sessions[query["owner"]], query, index, catalog)
            if number % 50 == 0:
                print(f"  {name}: {number}/{len(queries)}", flush=True)
        logs = export_logs([result["logId"] for result in results.values()])
        for result in results.values():
            log = logs.get(result["logId"], {})
            result["latencyMs"] = log.get("latency_ms")
            result["queryContext"] = log.get("query_context")
            result["candidates"] = log.get("returned_candidate_ids")
        write_json(OUT / "runs" / f"{run_name}.json", {"config": name, "properties": config_properties(name),
                                                        "results": results})
        print(f"{run_name}: {len(results)} searches -> out/runs/{run_name}.json", flush=True)
        if drain:
            wait_for_rerank_queue(OUT / f"backend-{run_name}.log")
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


def wait_for_pipeline(timeout: float = 6 * 3600) -> None:
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


def wait_for_catalog(timeout: float = 300) -> None:
    """The web server answers before the start-up runners have created the subjects, grades and tags."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        state = query_json("SELECT (SELECT count(*) FROM grades) AS grades, (SELECT count(*) FROM subjects) AS subjects, "
                           "(SELECT count(*) FROM recommendation_tags WHERE tag_group = 'TOPIC') AS topics")[0]
        if state["grades"] >= 12 and state["subjects"] > 0 and state["topics"] > 0:
            return
        time.sleep(3)
    raise TimeoutError("the catalog was not created")


def setup(world: dict, resume: bool = False, from_a1: bool = False) -> None:
    """resume: continue on the database a stopped setup left (no reset, no new load, so A1 is not paid twice);
    from_a1: start again from the copy taken after A1 and the embedding (<db>_a1), only the browsing is redone."""
    import load

    if from_a1:
        clone(f"{DATABASE}_a1", DATABASE)
    elif not resume:
        reset_database()
    properties = config_properties("V2-full")
    # The browsing history is drawn with the defaults of the platform: no reranking since 26/10/2026 (13.2).
    properties["app.recommendation.rerank.mode"] = "OFF"
    properties["app.tutor-recommendation.recalculation-cron"] = "0 * * * * *"
    # Queue the embedding and enrichment of the new rows soon after loading them.
    properties["app.recommendation.embedding.backfill-initial-delay-ms"] = "60000"
    process = start_backend(properties, "setup")
    try:
        wait_for_catalog()
        if resume or from_a1:
            # Jobs a stopped backend left half done go back to the queue.
            psql("UPDATE ai_enrichment_jobs SET status = 'PENDING' WHERE status = 'PROCESSING';")
            print("resuming on the existing database", flush=True)
        elif SCENARIO == "text-only":
            # The free-text needs must not be expressible as tags (textonly.py): switch the overlapping ones off
            # before A1 runs.
            from textonly import EXCLUDED_TAGS
            codes = ", ".join(f"'{code}'" for code in EXCLUDED_TAGS)
            psql(f"UPDATE recommendation_tags SET status = 'INACTIVE' WHERE code IN ({codes});")
        if not resume and not from_a1:
            psql(load.build_sql(world))
            print("loaded", load.counts(), flush=True)
        wait_for_pipeline()
    finally:
        stop_backend(process)
    if not from_a1:
        clone(DATABASE, f"{DATABASE}_a1")
        print(f"A1 and embedding done: copied to {DATABASE}_a1", flush=True)
    process = start_backend(properties, "setup-browse")
    try:
        wait_for_api()
        wait_for_catalog()
        browse(world)
        time.sleep(30)
        profiles = query_json("SELECT count(*) AS n FROM recommendation_user_profiles p JOIN users u ON u.id = p.user_id "
                              "WHERE u.email LIKE 'eval.%'")[0]["n"]
        print(f"prepared: {profiles} preference profiles", flush=True)
    finally:
        stop_backend(process)
    # A frozen copy: market.py starts every configuration from exactly this state. A workers.py helper may still
    # hold connections; clone() closes them.
    clone(DATABASE, f"{DATABASE}_seed")
    print(f"copied to {DATABASE}_seed", flush=True)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    if len(sys.argv) < 2 or sys.argv[1] not in ("setup", "search"):
        print(__doc__)
        return 1
    if sys.argv[1] == "setup":
        setup(world, resume="--resume" in sys.argv, from_a1="--from-a1" in sys.argv)
        return 0
    side = None
    if "--side" in sys.argv:
        position = sys.argv.index("--side")
        side = sys.argv[position + 1]
        del sys.argv[position:position + 2]
    run_name = None
    if "--as" in sys.argv:
        position = sys.argv.index("--as")
        run_name = sys.argv[position + 1]
        del sys.argv[position:position + 2]
    flags = {flag for flag in ("--keep-cache", "--drain") if flag in sys.argv}
    sys.argv = [argument for argument in sys.argv if argument not in flags]
    every = [name for name in CONFIGS if name != "V2-full-busy" and name not in SWEEP]
    names = every if len(sys.argv) < 3 or sys.argv[2] == "all" else sys.argv[2:]
    for name in names:
        run_config(name, world, side, run_name, keep_cache="--keep-cache" in flags, drain="--drain" in flags)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
