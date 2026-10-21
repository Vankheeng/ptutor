"""26a: puts the simulated market of out/<scenario>/world.json into the evaluation database, with history.

    python scripts/recommendation_eval/load.py             # insert (running it twice changes nothing)
    python scripts/recommendation_eval/load.py --print     # only print the SQL
    python scripts/recommendation_eval/load.py --disable   # close the eval postings, deactivate the accounts

Accounts, profiles and postings go in with SQL, like the demo data: a posting can only open through the
VNPay fee and an admin review, which a script cannot pass. Everything downstream runs the real way: start
the backend afterwards and its workers embed and enrich (A1) the new rows, and the nightly jobs compute the
aggregates and the score Q from the history written here (run.py starts it with those jobs every minute).

History (design 09, 9.2-9.3): over 28 days students apply to postings they like (by hidden relevance) and
tutors decide by the hidden acceptance model within their seats; less reliable tutors answer slowly, and
their finished contracts draw founded complaints. Some reliable tutors get an unfounded complaint.
Accounts use e-mails eval.*@ptutor.local; local use only.
"""

import argparse
import math
import random
import sys
from datetime import datetime, timedelta

from common import EMAIL_DOMAIN, OUT, PASSWORD, PASSWORD_HASH, SEED, psql, q, read_json, uid
from world import p_accept, p_student_accepts, rel

LAST_NAMES = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Ngô", "Dương", "Lý"]
MIDDLE = ["Văn", "Thị", "Minh", "Thu", "Quang", "Ngọc", "Đức", "Thanh", "Hải", "Bảo"]
FIRST = ["An", "Bình", "Chi", "Dũng", "Giang", "Hà", "Hùng", "Khánh", "Linh", "Long", "Mai", "Nam", "Nga",
         "Phong", "Quân", "Sơn", "Tâm", "Thảo", "Trang", "Tú", "Vy", "Yến"]
NOW = datetime(2026, 10, 20, 9, 0)
HISTORY_DAYS = 28
REVIEWER = "(SELECT e.id FROM employees e ORDER BY e.created_at, e.id LIMIT 1)"


def email(key: str) -> str:
    return f"eval.{key}@{EMAIL_DOMAIN}"


def ts(moment: datetime) -> str:
    return q(moment.strftime("%Y-%m-%d %H:%M:%S"))


def district(city: str, name: str) -> str:
    return (f"(SELECT d.id FROM districts d JOIN provinces p ON p.id = d.province_id "
            f"WHERE p.name = {q(city)} AND d.name = {q(name)} ORDER BY d.id LIMIT 1)")


def subject(name: str) -> str:
    return f"(SELECT id FROM subjects WHERE name = {q(name)} LIMIT 1)"


def grade(level: int) -> str:
    return f"(SELECT id FROM grades WHERE level = {level} ORDER BY name LIMIT 1)"


def user_sql(key: str, number: int, city: str, home: str, rng: random.Random) -> str:
    gender = rng.choice(["MALE", "FEMALE"])
    citizen_id = f"07{number:010d}"
    return (f"INSERT INTO users (id, district_id, citizen_id, email, password, first_name, last_name, gender, status, "
            f"suspension_count) VALUES ({q(uid('user', key))}, {district(city, home)}, {q(citizen_id)}, "
            f"{q(email(key))}, {q(PASSWORD_HASH)}, {q(rng.choice(FIRST))}, "
            f"{q(rng.choice(LAST_NAMES) + ' ' + rng.choice(MIDDLE))}, {q(gender)}, 'ACTIVE', 0) ON CONFLICT DO NOTHING;")


def slots_sql(table: str, column: str, owner: str, slots) -> list:
    return [f"INSERT INTO {table} (id, {column}, day_of_week, start_time, end_time, source) VALUES "
            f"({q(uid(table, owner, day, start))}, {q(owner)}, {day}, {q(start)}, {q(end)}, 'USER') "
            f"ON CONFLICT DO NOTHING;" for day, start, end in slots]


def entities_sql(world: dict, rng: random.Random) -> list:
    out = []
    for number, tutor in enumerate(world["tutors"], start=1):
        key = tutor["key"]
        profile = tutor["profile"]
        out.append(user_sql(key, number, tutor["city"], tutor["home"], rng))
        out.append(
            f"INSERT INTO tutors (id, user_id, introduction, experience_years, education, teaching_methodology, "
            f"target_student_type, profile_status, profile_reviewed_by, profile_reviewed_at) VALUES "
            f"({q(tutor['id'])}, {q(uid('user', key))}, {q(profile['introduction'])}, {tutor['experience']}, "
            f"{q(profile['education'] or None)}, {q(profile['methodology'] or None)}, {q(profile['target'] or None)}, "
            f"'VERIFIED', {REVIEWER}, {ts(NOW - timedelta(days=40))}) ON CONFLICT DO NOTHING;")
        for posting in tutor["postings"]:
            pid = uid("eval", posting["key"])
            posting["id"] = pid
            out.append(
                f"INSERT INTO teaching_requests (id, tutor_id, subject_id, title, description, quantity, expected_price, "
                f"teaching_mode, preferred_schedule, status, reviewed_by, reviewed_at, created_at) VALUES "
                f"({q(pid)}, {q(tutor['id'])}, {subject(posting['subject'])}, {q(posting['title'])}, "
                f"{q(posting['description'])}, {posting['quantity']}, {posting['price']}, {q(posting['mode'])}, "
                f"{q(posting['schedule_text'])}, 'OPEN', {REVIEWER}, {ts(NOW - timedelta(days=36))}, "
                f"{ts(NOW - timedelta(days=36, hours=rng.randint(0, 48)))}) ON CONFLICT DO NOTHING;")
            for level in posting["grades"]:
                out.append(f"INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES "
                           f"({q(uid('eval-grade', pid, level))}, {grade(level)}, {q(pid)}) ON CONFLICT DO NOTHING;")
            for name in posting["districts"]:
                out.append(f"INSERT INTO teaching_request_districts (id, teaching_request_id, district_id) VALUES "
                           f"({q(uid('eval-district', pid, name))}, {q(pid)}, {district(tutor['city'], name)}) "
                           f"ON CONFLICT DO NOTHING;")
            out += slots_sql("teaching_request_availabilities", "teaching_request_id", pid, posting["slots"])

    for number, student in enumerate(world["students"], start=1):
        key = student["key"]
        profile = student["profile"]
        out.append(user_sql(key, 500 + number, student["city"], student["home"], rng))
        out.append(
            f"INSERT INTO students (id, user_id, current_level, weak_points, goals_description) VALUES "
            f"({q(student['id'])}, {q(uid('user', key))}, {q(profile['current_level'])}, {q(profile['weak_points'])}, "
            f"{q(profile['goals'])}) ON CONFLICT DO NOTHING;")
        if student["has_posting"]:
            posting = student["posting"]
            pid = uid("eval", posting["key"])
            posting["id"] = pid
            mode = "ONLINE" if student["mode"] == "ONLINE" else "OFFLINE"
            home = district(student["city"], student["home"]) if mode == "OFFLINE" else "NULL"
            out.append(
                f"INSERT INTO studying_requests (id, district_id, student_id, subject_id, grade_id, title, description, "
                f"learning_goals, max_price, learning_mode, preferred_schedule, status, created_at) VALUES "
                f"({q(pid)}, {home}, {q(student['id'])}, {subject(student['subject'])}, {grade(student['grade'])}, "
                f"{q(posting['title'])}, {q(posting['description'])}, {q(posting['learning_goals'])}, "
                f"{student['budget']}, {q(mode)}, {q(posting['schedule_text'])}, 'OPEN', "
                f"{ts(NOW - timedelta(days=rng.randint(10, 30)))}) ON CONFLICT DO NOTHING;")
            out += slots_sql("studying_request_availabilities", "studying_request_id", pid, student["slots"])
    return out


def history_sql(world: dict, rng: random.Random) -> list:
    """Applications and decisions over the last 28 days, then contracts, reviews and complaints."""
    out = []
    tutors = {tutor["key"]: tutor for tutor in world["tutors"]}
    postings = [(posting, tutor) for tutor in world["tutors"] for posting in tutor["postings"]]
    seats = {posting["key"]: posting["quantity"] for posting, _ in postings}
    for student in world["students"]:
        scored = [(rel(student, posting, tutor), posting, tutor) for posting, tutor in postings
                  if posting["subject"] == student["subject"]]
        tried = set()
        for day in range(HISTORY_DAYS, 0, -1):
            if rng.random() > 0.18:
                continue
            options = [(value, posting, tutor) for value, posting, tutor in scored if posting["key"] not in tried]
            if not options:
                break
            weights = [math.exp(1.5 * value) for value, _, _ in options]
            value, posting, tutor = rng.choices(options, weights)[0]
            tried.add(posting["key"])
            sent = NOW - timedelta(days=day, hours=rng.randint(0, 12))
            hours = rng.uniform(2, 24) if tutor["reliable"] else rng.uniform(24, 96)
            answered = sent + timedelta(hours=hours)
            if answered > NOW or (not tutor["reliable"] and rng.random() < 0.3):
                status, answered = "PENDING", None
            elif seats[posting["key"]] > 0 and rng.random() < p_accept(student, posting, tutor):
                status = "ACCEPTED"
                seats[posting["key"]] -= 1
            else:
                status = "REJECTED"
            application = uid("eval-application", student["key"], posting["key"])
            out.append(
                f"INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, proposed_price, "
                f"learning_mode, message, status, created_at, updated_at, responded_at) VALUES ({q(application)}, "
                f"{q(student['id'])}, {grade(student['grade'])}, {q(posting['id'])}, {student['budget']}, "
                f"{q(posting['mode'])}, 'Em muốn đăng ký học.', {q(status)}, {ts(sent)}, "
                f"{ts(answered or sent)}, {ts(answered) if answered else 'NULL'}) ON CONFLICT DO NOTHING;")
            if status == "ACCEPTED":
                out += contract_sql(student, posting, tutor, application, answered, rng)

    for student in world["students"]:
        if not student["has_posting"]:
            continue
        offers = [(posting, tutor) for posting, tutor in postings if posting["subject"] == student["subject"]
                  and student["grade"] in posting["grades"]]
        for posting, tutor in rng.sample(offers, min(len(offers), rng.randint(0, 3))):
            sent = NOW - timedelta(days=rng.randint(2, HISTORY_DAYS), hours=rng.randint(0, 12))
            answered = sent + timedelta(hours=rng.uniform(3, 48))
            status = "ACCEPTED" if rng.random() < p_student_accepts(student, posting, tutor) else "REJECTED"
            out.append(
                f"INSERT INTO tutor_student_requests (id, tutor_id, grade_id, studying_request_id, proposed_price, "
                f"teaching_mode, message, status, created_at, updated_at, responded_at) VALUES "
                f"({q(uid('eval-offer', tutor['key'], student['key']))}, {q(tutor['id'])}, {grade(student['grade'])}, "
                f"{q(student['posting']['id'])}, {posting['price']}, {q(posting['mode'])}, 'Tôi có thể dạy em.', "
                f"{q(status)}, {ts(sent)}, {ts(answered)}, {ts(answered)}) ON CONFLICT DO NOTHING;")

    # Unfounded complaints need a finished contract of their own.
    for tutor in world["tutors"]:
        if tutor.get("unfounded_complaint"):
            student = rng.choice(world["students"])
            posting = tutor["postings"][0]
            application = uid("eval-application-old", student["key"], posting["key"])
            sent = NOW - timedelta(days=HISTORY_DAYS + 30)
            out.append(
                f"INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, proposed_price, "
                f"learning_mode, status, created_at, updated_at, responded_at) VALUES ({q(application)}, "
                f"{q(student['id'])}, {grade(posting['grades'][0])}, {q(posting['id'])}, {posting['price']}, "
                f"{q(posting['mode'])}, 'ACCEPTED', {ts(sent)}, {ts(sent)}, {ts(sent + timedelta(hours=5))}) "
                f"ON CONFLICT DO NOTHING;")
            out += contract_sql(student, posting, tutor, application, sent + timedelta(hours=5), rng,
                                force_complete=True, unfounded=True)
    return out


def contract_sql(student, posting, tutor, application, accepted: datetime, rng, force_complete=False,
                 unfounded=False) -> list:
    contract = uid("eval-contract", application)
    finished = force_complete or (NOW - accepted).days >= 14
    start = accepted.date() + timedelta(days=2)
    end = start + timedelta(days=12 if finished else 60)
    out = [
        f"INSERT INTO contracts (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period, "
        f"total_lession, start_date, end_date, status, signed_at, created_at, updated_at, created_by_user_id, "
        f"signed_by_user_id, student_tutor_request_id, preferred_schedule) VALUES ({q(contract)}, {q(student['id'])}, "
        f"{q(tutor['id'])}, {subject(posting['subject'])}, {grade(student['grade'])}, {q(posting['mode'])}, "
        f"{posting['price']}, 'PER_LESSON', 8, {q(str(start))}, {q(str(end))}, "
        f"{q('COMPLETED' if finished else 'ACTIVE')}, {ts(accepted + timedelta(hours=6))}, {ts(accepted)}, "
        f"{ts(accepted + timedelta(days=14) if finished else accepted)}, {q(uid('user', tutor['key']))}, "
        f"{q(uid('user', student['key']))}, {q(application)}, {q(posting['schedule_text'])}) ON CONFLICT DO NOTHING;"]
    if not finished:
        return out
    closed = min(NOW - timedelta(hours=1), datetime.combine(end, datetime.min.time()) + timedelta(hours=20))
    rating = rng.choice([4, 5, 5]) if tutor["reliable"] else rng.choice([2, 3, 3, 4])
    out.append(f"INSERT INTO reviews (id, student_id, tutor_id, rating, comment, contract_id, created_at) VALUES "
               f"({q(uid('eval-review', contract))}, {q(student['id'])}, {q(tutor['id'])}, {rating}, "
               f"'Nhận xét sau khoá học.', {q(contract)}, {ts(closed)}) ON CONFLICT DO NOTHING;")
    founded = not tutor["reliable"] and rng.random() < 0.7
    if founded or unfounded:
        category = rng.choice(["ABSENCE", "LATE_OR_SHORT", "TEACHING_QUALITY"]) if founded else "TEACHING_QUALITY"
        fault = "RESPONDENT" if founded else "NONE"
        out.append(
            f"INSERT INTO complaints (id, user_id, contract_id, employee_id, title, content, status, resolution, "
            f"resolved_at, created_at, updated_at, category, fault_party) VALUES ({q(uid('eval-complaint', contract))}, "
            f"{q(uid('user', student['key']))}, {q(contract)}, {REVIEWER}, 'Khiếu nại về buổi học', "
            f"'Nội dung khiếu nại mô phỏng.', 'RESOLVED', 'Đã xử lý (dữ liệu mô phỏng).', {ts(closed)}, "
            f"{ts(closed - timedelta(days=2))}, {ts(closed)}, {q(category)}, {q(fault)}) ON CONFLICT DO NOTHING;")
    return out


def build_sql(world: dict) -> str:
    rng = random.Random(SEED + 1)
    return "\n".join(["BEGIN;", *entities_sql(world, rng), *history_sql(world, rng), "COMMIT;"]) + "\n"


DISABLE_SQL = f"""BEGIN;
UPDATE teaching_requests SET status = 'CLOSED' WHERE status = 'OPEN' AND tutor_id IN (
    SELECT t.id FROM tutors t JOIN users u ON u.id = t.user_id WHERE u.email LIKE 'eval.%@{EMAIL_DOMAIN}');
UPDATE studying_requests SET status = 'CLOSED' WHERE status = 'OPEN' AND student_id IN (
    SELECT s.id FROM students s JOIN users u ON u.id = s.user_id WHERE u.email LIKE 'eval.%@{EMAIL_DOMAIN}');
UPDATE users SET status = 'INACTIVE' WHERE email LIKE 'eval.%@{EMAIL_DOMAIN}';
COMMIT;
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--print", action="store_true", help="print the SQL instead of running it")
    parser.add_argument("--disable", action="store_true", help="close the eval postings and deactivate the accounts")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    world = read_json(OUT / "world.json")
    sql = DISABLE_SQL if args.disable else build_sql(world)
    if args.print:
        print(sql)
        return 0
    psql(sql)
    if args.disable:
        print("Eval postings closed and eval accounts deactivated.")
        return 0
    print(f"Loaded. applications|contracts|complaints = {counts()}. Password of eval.* accounts: {PASSWORD}")
    return 0


def counts() -> str:
    return psql(f"""
        SELECT (SELECT count(*) FROM student_tutor_requests r JOIN students s ON s.id = r.student_id
                JOIN users u ON u.id = s.user_id WHERE u.email LIKE 'eval.%'),
               (SELECT count(*) FROM contracts c JOIN tutors t ON t.id = c.tutor_id JOIN users u ON u.id = t.user_id
                WHERE u.email LIKE 'eval.%'),
               (SELECT count(*) FROM complaints c JOIN contracts ct ON ct.id = c.contract_id
                JOIN tutors t ON t.id = ct.tutor_id JOIN users u ON u.id = t.user_id WHERE u.email LIKE 'eval.%');
        """).strip()


if __name__ == "__main__":
    raise SystemExit(main())
