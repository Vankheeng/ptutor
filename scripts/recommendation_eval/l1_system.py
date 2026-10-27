"""Step 43a (design 13, 13.7): the L1 loop end to end through the product, against hidden weights.

    set EVAL_SCENARIO=crowded and EVAL_DB=ptutor_eval_l1, then
    python scripts/recommendation_eval/l1_system.py setup     # ptutor_eval_l1 from ptutor_eval_seed
    python scripts/recommendation_eval/l1_system.py run       # the simulated days (resumes where it stopped)
    python scripts/recommendation_eval/l1_system.py analyze   # checks, tables and figures -> ket-qua/l1-he-thong
    python scripts/recommendation_eval/l1_system.py serve     # the backend on ptutor_eval_l1, for a demonstration

Simulated searchers have hidden suitability weights W_STAR and the tutors a hidden acceptance model BETA_STAR, both
inside the safety band [0.5; 2] x prior of the learned weights. Every simulated day:

1. 100 student searches through the real API (queries of the crowded world, none repeated before all were used);
2. the searcher reads the first page (position model and attraction of learning.py, on the features the backend
   logged) and reacts through the real API: VIEWED / FAVORITED / DISMISSED events, applications (then CONTACTED, as
   the frontend does), a few CONTACTED without an application;
3. the tutors answer the applications of the day through the real API, by BETA_STAR on the features of the decision
   snapshot the backend took; a few applications stay unanswered (implicit rejection after 14 days);
4. every time column of the database moves back one day (the day is over), checked column by column;
5. POST /api/v1/admin/recommendations/weights/learn runs the nightly job.

What the simulation did goes to out/crowded/l1/truth.jsonl; what the job learned from goes to the observation dump
(app.recommendation.learning.observation-dump-dir, out/crowded/l1/l1-observations, ignored by Git). `analyze`
rebuilds the observations from the truth independently and compares them row by row, replays each version in Python
(linear_bayes.py), and measures the distance to W_STAR, the loop (activated -> served -> logged) and, on the last
HOLDOUT_DAYS days, whether the learned weights rank better than the prior.

Sampling seeds are the same real day for every simulated day; since 27/10/2026 the Thompson seed includes the
version, so a new version still draws new noise (README of ket-qua/l1-he-thong).
"""

import json
import math
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("EVAL_DB", "ptutor_eval_l1")
os.environ.setdefault("EVAL_SCENARIO", "crowded")

import numpy as np

from common import DATABASE, OUT, REPO, SEED, Api, clone, free_ram_gb, psql, query_json, read_json, uid, wait_for_api
import linear_bayes
import learning
from scoring import A_S2T, B_S2T

L1_DB = "ptutor_eval_l1"
SOURCE = "ptutor_eval_seed"
DAYS = 45
SEARCHES_PER_DAY = 100
HOLDOUT_DAYS = 5
SHOWN = 10  # the searcher reads the first 10 of the page of 20
# Hidden weights, clearly different from the prior (‖w* − prior‖₁ = 0.32) but inside [0.5; 2] x prior, so that the
# learned weights can reach them (13.7: a prior turned upside down is not corrected).
PRIOR_A = np.array([0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05])
W_STAR = np.array([0.18, 0.24, 0.32, 0.08, 0.08, 0.05, 0.05])
PRIOR_B = np.array([0.5, 1 / 6, 1 / 6, 1 / 6])
# intercept, hist, fitSegment, fitSched, fitPrice: the direction of learning.py; the intercept set so that the mean
# acceptance of the applications is ~0.53 (applicants pick postings that fit, so -2 gave 0.93 on the first day of
# 27/10/2026 and the 1 836 places would have gone in a week). Only the direction is compared with the learned b.
BETA_STAR = np.array([-4.8, *learning.BETA_STAR[1:]])
B_STAR = BETA_STAR[1:] / BETA_STAR[1:].sum()  # its direction on the scale of the weights
MIN_RATIO, MAX_RATIO, MAX_STEP = 0.5, 2.0, 0.05
DISMISS = 0.15  # an examined posting that does not attract is dismissed this often
APPLY = 0.35  # a searcher applies to the first posting they love this often (one chance a search: with a chance
# at each loved posting, 74 of 100 searches applied on 27/10/2026 and the places would have gone); the other postings
# they love are contacted
UNANSWERED = 0.05  # applications the tutor never answers
# Rewards and sample weights of the backend's defaults (design 12, 12.4 to 12.6).
REWARDS = {"DISMISS": -0.2, "VIEW": 0.3, "FAVORITE": 0.6, "CONTACT": 0.8, "REQUEST": 1.0}
SIGNALS = ["DISMISS", "VIEW", "FAVORITE", "CONTACT", "REQUEST"]
ACTION_SIGNAL = {"VIEWED": "VIEW", "CLICKED": "VIEW", "FAVORITED": "FAVORITE", "CONTACTED": "CONTACT",
                 "DISMISSED": "DISMISS"}
REWARD_WINDOW_DAYS, DEPTH_MARGIN, EXPLORATION_WEIGHT, NEUTRAL = 7, 2, 2.0, 0.5
FAST_ACCEPT_HOURS, FAST_ACCEPT_WEIGHT, IMPLICIT_DAYS, IMPLICIT_WEIGHT = 24, 1.5, 14, 0.5
KAPPA, INTERCEPT_KAPPA, GAMMA = 50.0, 1.0, 0.99

FOLDER = OUT / "l1"
STATE = FOLDER / "state.json"
TRUTH = FOLDER / "truth.jsonl"
DUMPS = FOLDER / "l1-observations"
RESULTS = REPO / "docs" / "recommendation" / "ket-qua" / "l1-he-thong"


# ---------------------------------------------------------------- the simulated people


def utility(x: np.ndarray) -> np.ndarray:
    return x @ W_STAR


def reactions(x: np.ndarray, rng: np.random.Generator) -> list:
    """(position, action) of a searcher reading the first SHOWN postings: examined top-down, attracted by the
    hidden utility (learning.py); APPLY = an application (at most one), CONTACTED for the others they love."""
    count = min(SHOWN, len(x))
    u = utility(x[:count]) + rng.normal(0, learning.NOISE_U, count)
    examined = rng.random(count) < learning.EXAMINE ** np.arange(count)
    attracted = examined & (rng.random(count) < learning.attraction(u))
    levels = learning.level(u)
    out, applied = [], False
    for position in range(count):
        if attracted[position]:
            if levels[position] >= learning.REWARD["request"]:
                apply = not applied and rng.random() < APPLY
                applied = True  # only the first loved posting gets the chance
                out.append((position, "APPLY" if apply else "CONTACTED"))
            elif levels[position] >= learning.REWARD["favorite"]:
                out.append((position, "FAVORITED"))
            else:
                out.append((position, "VIEWED"))
        elif examined[position] and rng.random() < DISMISS:
            out.append((position, "DISMISSED"))
    return out


def p_accept(z) -> float:
    return float(1 / (1 + math.exp(-(BETA_STAR[0] + np.asarray(z, dtype=float) @ BETA_STAR[1:]))))


def vector(values: dict | None, names: list) -> list:
    values = values or {}
    return [NEUTRAL if values.get(name) is None else float(values[name]) for name in names]


# ---------------------------------------------------------------- moving the clock back


def time_columns(database: str = DATABASE) -> list:
    """(table, column, type) of every time column of the base tables of the schema, Flyway's history aside."""
    rows = query_json("""
        SELECT c.table_name AS table_name, c.column_name AS column_name, c.data_type AS data_type
        FROM information_schema.columns c
        JOIN information_schema.tables t USING (table_schema, table_name)
        WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND t.table_name <> 'flyway_schema_history'
          AND c.data_type IN ('timestamp without time zone', 'timestamp with time zone', 'date')
        ORDER BY 1, 2""", database)
    return [(row["table_name"], row["column_name"], row["data_type"]) for row in rows]


def shift_statements(columns: list) -> list:
    """One UPDATE per table moving all its time columns back one day."""
    by_table = {}
    for table, column, _ in columns:
        by_table.setdefault(table, []).append(column)
    return [f'UPDATE "{table}" SET ' + ", ".join(f'"{c}" = "{c}" - INTERVAL \'1 day\'' for c in names) + ";"
            for table, names in sorted(by_table.items())]


def maxima_sql(columns: list, tag: str) -> str:
    """One line per column: tag|table|column|max in seconds (dates in whole days)."""
    parts = []
    for table, column, kind in columns:
        value = (f'(MAX("{column}") - DATE \'2000-01-01\') * 86400.0' if kind == "date"
                 else f'EXTRACT(EPOCH FROM MAX("{column}"))')
        parts.append(f"SELECT '{tag}|{table}|{column}|' || COALESCE(({value})::text, '') FROM \"{table}\"")
    return "\nUNION ALL\n".join(parts) + ";"


def check_shift(before: dict, after: dict) -> list:
    """Problems of a one-day move: a column not moved by exactly a day, or an event time not in the past of the
    newest one before the move."""
    problems = []
    for key, value in before.items():
        moved = after.get(key)
        if value is None and moved is None:
            continue
        if value is None or moved is None or abs((value - moved) - 86400) > 1e-3:
            problems.append(f"{key[0]}.{key[1]}: {value} -> {moved}")
    newest = max((value for (_, column), value in before.items()
                  if value is not None and column in ("created_at", "updated_at")), default=None)
    if newest is not None:
        for (table, column), value in after.items():
            if column in ("created_at", "updated_at") and value is not None and value > newest - 86400 + 1e-3:
                problems.append(f"{table}.{column} is in the future after the move: {value} > {newest - 86400}")
    return problems


def parse_maxima(text: str) -> tuple:
    before, after = {}, {}
    for line in text.splitlines():
        parts = line.split("|")
        if len(parts) != 4:
            continue
        target = before if parts[0] == "before" else after
        target[(parts[1], parts[2])] = float(parts[3]) if parts[3] else None
    return before, after


def shift_one_day(database: str = DATABASE, attempts: int = 3) -> int:
    """Moves every time column back a day in one REPEATABLE READ transaction (a concurrent update aborts it, then it
    is retried) and checks every column moved."""
    columns = time_columns(database)
    script = ("BEGIN ISOLATION LEVEL REPEATABLE READ;\n" + maxima_sql(columns, "before") + "\n"
              + "\n".join(shift_statements(columns)) + "\n" + maxima_sql(columns, "after") + "\nCOMMIT;\n")
    for attempt in range(1, attempts + 1):
        try:
            before, after = parse_maxima(psql(script, database))
            break
        except RuntimeError as failure:
            if attempt == attempts or "could not serialize" not in str(failure):
                raise
            time.sleep(2)
    problems = check_shift(before, after)
    if len(before) != len(columns) or problems:
        raise SystemExit(f"time shift incomplete ({len(before)}/{len(columns)} columns): {problems[:5]}")
    return len(columns)


# ---------------------------------------------------------------- one simulated day


def state() -> dict:
    return read_json(STATE) if STATE.exists() else {"day": 0, "outcomes": []}


def save_state(value: dict) -> None:
    STATE.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(STATE)


def append_truth(record: dict) -> None:
    with TRUTH.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def query_order(world: dict) -> list:
    """S2T queries, a fresh permutation each time all have been used (learning.py --diverse)."""
    queries = [query for query in world["queries"] if query["side"] == "S2T"]
    rng = np.random.default_rng(SEED + 43)
    rounds = -(-DAYS * SEARCHES_PER_DAY // len(queries))
    return [queries[i] for _ in range(rounds) for i in rng.permutation(len(queries))]


def logged_candidates(log_id: str) -> dict:
    rows = query_json(f"SELECT returned_candidate_ids AS c FROM recommendation_logs WHERE id = '{log_id}'")
    return {entry["candidateId"]: entry for entry in (rows[0]["c"] or [])} if rows else {}


def snapshot_features(request_id: str) -> list | None:
    rows = query_json(f"SELECT features FROM recommendation_decision_snapshots WHERE request_id = '{request_id}' "
                      f"AND model_key = 'B_S2T'")
    return vector(rows[0]["features"], B_S2T) if rows else None


def wait_for_jobs(timeout: float = 120) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if psql("SELECT COUNT(*) FROM ai_enrichment_jobs WHERE status IN ('PENDING', 'RUNNING');").strip() == "0":
            return
        time.sleep(2)


def simulate_day(day: int, world: dict, sessions: dict, tutor_sessions: dict, admin: Api) -> dict:
    import run as runner
    rng = np.random.default_rng(SEED * 100 + day)
    index = runner.index_world(world)
    catalog = simulate_day.catalog = getattr(simulate_day, "catalog", None) or runner.Catalog()
    postings = {uid("eval", posting["key"]): (posting, tutor) for tutor in world["tutors"]
                for posting in tutor["postings"]}
    queries = query_order(world)[(day - 1) * SEARCHES_PER_DAY: day * SEARCHES_PER_DAY]
    applications, done = [], 0
    for query in queries:
        student = index["students"][query["owner"]]
        if query["owner"] not in sessions:
            sessions[query["owner"]] = Api().login(f"eval.{query['owner']}@ptutor.local")
        api = sessions[query["owner"]]
        try:
            result = runner.search(api, query, index, catalog)
        except RuntimeError as failure:
            append_truth({"type": "search_failed", "day": day, "query": query["id"], "error": str(failure)[:200]})
            continue
        # Every logged candidate (up to 50): an application from another search can reach any of them.
        logged = logged_candidates(result["logId"])
        shown = sorted(({"rank": entry.get("rank"), "id": candidate, "exploration": bool(entry.get("isExploration")),
                         "features": vector(entry.get("features"), A_S2T)} for candidate, entry in logged.items()),
                       key=lambda c: c["rank"] or 10 ** 6)
        page = {item["id"]: item["rank"] for item in result["items"]}
        if any(page.get(c["id"]) not in (None, c["rank"]) for c in shown):
            append_truth({"type": "rank_mismatch", "day": day, "logId": result["logId"]})
        x = np.array([candidate["features"] for candidate in shown]) if shown else np.zeros((0, len(A_S2T)))
        events, applied = [], []
        for position, action in reactions(x, rng):
            candidate = shown[position]["id"]
            if action == "APPLY":
                posting, _ = postings.get(candidate, (None, None))
                record = {"posting": candidate, "ok": False}
                if posting is not None:
                    try:
                        response = api.call(
                            "POST", f"/api/v1/students/me/teaching-requests/{candidate}/student-tutor-requests",
                            {"gradeId": catalog.grades[student["grade"]], "proposedPrice": student["budget"],
                             "learningMode": posting["mode"], "message": "Em muốn đăng ký học."})
                        record.update(ok=True, id=response["data"]["id"],
                                      snapshot=snapshot_features(response["data"]["id"]))
                        applications.append(record | {"student": query["owner"]})
                    except RuntimeError as failure:
                        record["error"] = str(failure)[:160]
                applied.append(record)
                if not record["ok"]:
                    continue
                action = "CONTACTED"  # the frontend reports the contact once the application went through
            ok = True
            try:
                api.call("POST", f"/api/v1/recommendations/{result['logId']}/events",
                         {"candidateId": candidate, "action": action})
            except RuntimeError:
                ok = False
            events.append({"candidate": candidate, "action": action, "ok": ok})
        append_truth({"type": "search", "day": day, "query": query["id"], "owner": query["owner"],
                      "logId": result["logId"], "shown": shown, "events": events, "applications": applied})
        done += 1
    # The tutors answer the day's applications.
    accepted = rejected = unanswered = 0
    for application in applications:
        posting, tutor = postings[application["posting"]]
        decision = {"type": "decision", "day": day, "request": application["id"], "status": "PENDING",
                    "p": None if application["snapshot"] is None else p_accept(application["snapshot"])}
        if rng.random() < UNANSWERED or application["snapshot"] is None:
            unanswered += 1
            append_truth(decision)
            continue
        status = "ACCEPTED" if rng.random() < decision["p"] else "REJECTED"
        if tutor["key"] not in tutor_sessions:
            tutor_sessions[tutor["key"]] = Api().login(f"eval.{tutor['key']}@ptutor.local")
        path = (f"/api/v1/tutors/me/teaching-requests/{application['posting']}/student-requests/"
                f"{application['id']}/status")
        try:
            tutor_sessions[tutor["key"]].call("PATCH", path, {"status": status})
        except RuntimeError:
            status = "REJECTED"  # no place left
            try:
                tutor_sessions[tutor["key"]].call("PATCH", path, {"status": status})
            except RuntimeError:
                status = "PENDING"
        decision["status"] = status
        accepted += status == "ACCEPTED"
        rejected += status == "REJECTED"
        unanswered += status == "PENDING"
        append_truth(decision)
    activity = {"day": day, "searches": done, "applications": len(applications), "accepted": accepted,
                "rejected": rejected, "unanswered": unanswered}
    append_truth({"type": "activity_done", **activity})
    return activity


def finish_day(activity: dict, admin: Api) -> dict:
    """The end of a day whose activity is done: wait for the background jobs, move the clock, learn."""
    wait_for_jobs()
    columns = shift_one_day()
    outcome = admin.call("POST", "/api/v1/admin/recommendations/weights/learn")["data"]
    return activity | {"shiftedColumns": columns, "learn": outcome, "freeRamGb": free_ram_gb()}


def day_activity(day: int, records: list) -> dict | None:
    """The activity of `day` if it is already in the truth (a run stopped between the activity and the end of the
    day), so that a resumed run does not do the day twice. Runs before 27/10/2026 13:30 wrote no activity_done
    record: a day with all its searches and the tutors' answers counts as done."""
    for record in records:
        if record["type"] == "activity_done" and record["day"] == day:
            return {key: record[key] for key in ("day", "searches", "applications", "accepted", "rejected",
                                                 "unanswered")}
    searches = [r for r in records if r["type"] == "search" and r["day"] == day]
    decisions = [r for r in records if r["type"] == "decision" and r["day"] == day]
    applied = sum(1 for r in searches for a in r["applications"] if a.get("ok"))
    if len(searches) == SEARCHES_PER_DAY and applied and len(decisions) == applied:
        statuses = [d["status"] for d in decisions]
        return {"day": day, "searches": len(searches), "applications": applied,
                "accepted": statuses.count("ACCEPTED"), "rejected": statuses.count("REJECTED"),
                "unanswered": statuses.count("PENDING")}
    return None


# ---------------------------------------------------------------- commands


def properties() -> dict:
    import run as runner
    values = dict(runner.BASE)
    values.update({
        "app.recommendation.learning.enabled": "true",
        "app.recommendation.learning.models.A_S2T.mode": "ACTIVE",
        "app.recommendation.learning.models.B_S2T.mode": "ACTIVE",
        "app.recommendation.learning.models.A_T2S.mode": "OFF",
        "app.recommendation.learning.models.B_T2S.mode": "OFF",
        "app.recommendation.learning.job-cron": "-",  # only the admin endpoint runs it
        "app.recommendation.learning.observation-dump-dir": str(DUMPS),
        "app.recommendation.log-candidate-limit": "50",
    })
    return values


def setup() -> None:
    if DATABASE != L1_DB:
        raise SystemExit(f"EVAL_DB must be {L1_DB}, not {DATABASE}")
    if FOLDER.exists() and any(FOLDER.iterdir()):
        raise SystemExit(f"{FOLDER} is not empty: move it away before a new setup")
    clone(SOURCE, L1_DB)
    # The browsing history of the seed (26/10/2026, for H1) was not made by the hidden weights: not learned from.
    # H1 reads the clicks through the logs without looking at deleted_at, so it keeps them.
    deleted = psql("UPDATE recommendation_logs SET deleted_at = NOW() WHERE deleted_at IS NULL RETURNING 1;")
    psql("DELETE FROM recommendation_learned_weights; DELETE FROM recommendation_learning_modes;")
    FOLDER.mkdir(parents=True, exist_ok=True)
    save_state({"day": 0, "outcomes": [], "seedLogsDeleted": len(deleted.split())})
    print(f"{L1_DB} ready; {len(deleted.split())} seed logs marked deleted", flush=True)


def run_days(last: int = DAYS) -> None:
    import run as runner
    if DATABASE != L1_DB:
        raise SystemExit(f"EVAL_DB must be {L1_DB}, not {DATABASE}")
    current = state()
    world = read_json(OUT / "world.json")
    process = runner.start_backend(properties(), "l1-system")
    try:
        wait_for_api()
        admin = Api().login("admin@ptutor.local", "admin")  # the account of migration V3 (local databases only)
        sessions, tutor_sessions = {}, {}
        for day in range(current["day"] + 1, last + 1):
            started = time.time()
            records = [json.loads(line) for line in TRUTH.read_text(encoding="utf-8").splitlines()]                 if TRUTH.exists() else []
            activity = day_activity(day, records)
            if activity is not None:
                print(f"day {day}: activity already done, finishing the day", flush=True)
            else:
                partial = sum(1 for r in records if r.get("day") == day)
                if partial:
                    print(f"day {day}: {partial} records of an unfinished day; the day runs again", flush=True)
                activity = simulate_day(day, world, sessions, tutor_sessions, admin)
            summary = finish_day(activity, admin)
            summary["seconds"] = round(time.time() - started)
            current["day"] = day
            current["outcomes"].append(summary)
            save_state(current)
            learned = ", ".join(f"{o['model']} v{o['version']} {'+' if o['activated'] else ''}"
                                for o in summary["learn"] if o.get("version"))
            print(f"day {day}: {summary['searches']} searches, {summary['applications']} applications "
                  f"({summary['accepted']} accepted), {summary['seconds']} s, RAM {summary['freeRamGb']} GB"
                  + (f"; learned {learned}" if learned else ""), flush=True)
    finally:
        runner.stop_backend(process)


# ---------------------------------------------------------------- what the job should have seen


def stronger(left: str | None, right: str) -> str:
    return right if left is None or SIGNALS.index(right) >= SIGNALS.index(left) else left


def expected_search(search: dict, searched_at: float, applications: list) -> dict | None:
    """Observations of one search by the rules of design 12, 12.4 and 12.5, from what the simulation did.
    applications: (posting, created_at) of the searcher, from every day."""
    signals = {}
    for event in search["events"]:
        if event["ok"]:
            signals[event["candidate"]] = stronger(signals.get(event["candidate"]), ACTION_SIGNAL[event["action"]])
    window = REWARD_WINDOW_DAYS * 86400
    for posting, at in applications:
        if searched_at <= at <= searched_at + window:
            signals[posting] = stronger(signals.get(posting), "REQUEST")
    ranks = {candidate["id"]: candidate for candidate in search["shown"]}
    deepest = max((ranks[c]["rank"] for c in signals if c in ranks), default=0)
    if deepest == 0:
        return None
    rows = []
    for candidate in sorted(search["shown"], key=lambda c: c["rank"]):
        if candidate["rank"] > deepest + DEPTH_MARGIN:
            continue
        signal = signals.get(candidate["id"])
        rows.append({"candidateId": candidate["id"], "rank": candidate["rank"], "features": candidate["features"],
                     "reward": 0.0 if signal is None else REWARDS[signal],
                     "weight": EXPLORATION_WEIGHT if candidate["exploration"] else 1.0})
    return {"logId": search["logId"], "candidates": rows}


def expected_decision(decision: dict, snapshot: list, requested_at: float, decided_at: float | None,
                      until: float) -> dict | None:
    """The willingness sample of an application (design 12, 12.6), or None when it gives none by `until`."""
    if decision["status"] == "ACCEPTED":
        fast = (decided_at - requested_at) / 3600 < FAST_ACCEPT_HOURS
        return {"label": 1.0, "weight": FAST_ACCEPT_WEIGHT if fast else 1.0, "at": decided_at, "features": snapshot}
    if decision["status"] == "REJECTED":
        return {"label": 0.0, "weight": 1.0, "at": decided_at, "features": snapshot}
    expired = requested_at + IMPLICIT_DAYS * 86400
    return None if expired > until else {"label": 0.0, "weight": IMPLICIT_WEIGHT, "at": expired, "features": snapshot}


def compare_rows(expected: list, dumped: list, keys=("rank", "reward", "weight")) -> list:
    """Differences between two lists of candidate rows (same order)."""
    problems = []
    if len(expected) != len(dumped):
        return [f"{len(expected)} rows expected, {len(dumped)} dumped"]
    for want, got in zip(expected, dumped):
        if want["candidateId"] != got["candidateId"]:
            problems.append(f"rank {want['rank']}: {want['candidateId']} expected, {got['candidateId']} dumped")
            continue
        for key in keys:
            if abs(float(want[key]) - float(got[key])) > 1e-9:
                problems.append(f"rank {want['rank']} {key}: {want[key]} expected, {got[key]} dumped")
        if np.max(np.abs(np.array(want["features"]) - np.array(got["features"]))) > 1e-9:
            problems.append(f"rank {want['rank']} features differ")
    return problems


def replay(dumps: list) -> list:
    """Python replay of the versions of one model (linear_bayes.py), in order; the largest difference of each."""
    out, previous = [], None
    for dump in dumps:
        features = [row["features"] for search in dump["training"]["searches"] for row in search["candidates"]] \
            + [row["features"] for row in dump["training"]["decisions"]]
        rewards = [row["reward"] for search in dump["training"]["searches"] for row in search["candidates"]] \
            + [row["label"] for row in dump["training"]["decisions"]]
        weights = [row["weight"] for search in dump["training"]["searches"] for row in search["candidates"]] \
            + [row["weight"] for row in dump["training"]["decisions"]]
        prior = np.array(dump["prior"])
        if previous is None:
            scale = linear_bayes.prior_scale(prior, np.array(features), np.array(rewards), np.array(weights))
            model = linear_bayes.LinearBayesModel(prior, dump["priorStrength"], dump["interceptPriorStrength"], scale)
        else:
            scale = previous["priorScale"]
            model = linear_bayes.LinearBayesModel(prior, dump["priorStrength"], dump["interceptPriorStrength"], scale)
            model.a, model.c = np.array(previous["statsA"]), np.array(previous["statsC"])
        if dump["forgetting"] < 1:
            model.forget(dump["forgetting"])
        for x, r, s in zip(features, rewards, weights):
            model.update(x, r, s)
        projected = linear_bayes.project(model.mean(), prior, np.array(dump["previous"]), MIN_RATIO, MAX_RATIO,
                                         MAX_STEP)
        out.append({"model": dump["model"], "version": dump["version"],
                    "priorScale": abs(scale - dump["priorScale"]),
                    "statsA": float(np.max(np.abs(model.a - np.array(dump["statsA"])))),
                    "statsC": float(np.max(np.abs(model.c - np.array(dump["statsC"])))),
                    "mean": float(np.max(np.abs(model.mean() - np.array(dump["posteriorMean"])))),
                    "weights": float(np.max(np.abs(projected - np.array(dump["weights"]))))})
        previous = dump
    return out


def serve() -> None:
    """The backend on the simulated database with the same properties, until Ctrl+C (demonstration)."""
    import run as runner
    if DATABASE != L1_DB:
        raise SystemExit(f"EVAL_DB must be {L1_DB}, not {DATABASE}")
    process = runner.start_backend(properties(), "l1-serve")
    try:
        wait_for_api()
        print("backend ready on http://localhost:8080 (ptutor_eval_l1); Ctrl+C to stop", flush=True)
        process.wait()
    except KeyboardInterrupt:
        pass
    finally:
        runner.stop_backend(process)


def analyze() -> None:
    import analyze_l1
    analyze_l1.main()


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "setup":
        setup()
    elif command == "run":
        run_days(int(sys.argv[2]) if len(sys.argv) > 2 else DAYS)
    elif command == "analyze":
        analyze()
    elif command == "serve":
        serve()
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
