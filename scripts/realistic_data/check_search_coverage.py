"""Audit the loaded local catalog against every supported recommendation filter pair."""

import argparse
import subprocess

import seed


def sql_rows(container, database, query):
    process = subprocess.run(
        ["docker", "exec", container, "psql", "-X", "-A", "-t", "-F", "|",
         "-U", "postgres", "-d", database, "-c", query],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return [row.split("|") for row in process.stdout.splitlines() if row]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", default="ptutor_app_seeded")
    parser.add_argument("--container", default="ptutor-postgres")
    args = parser.parse_args()
    values = ",".join(
        f"({seed.q(subject)}, {grade}, {seed.q(mode)})"
        for subject, grades in seed.SUBJECT_GRADES.items()
        for grade in grades for mode in ("ONLINE", "OFFLINE")
    )
    query = f"""
        WITH wanted(subject, grade, mode) AS (VALUES {values}),
        supply AS (
            SELECT s.name subject, g.level grade, tr.teaching_mode::text mode, count(*) n
            FROM teaching_requests tr
            JOIN subjects s ON s.id=tr.subject_id
            JOIN grade_teaching_requests gt ON gt.teaching_request_id=tr.id AND gt.deleted_at IS NULL
            JOIN grades g ON g.id=gt.grade_id
            JOIN tutors t ON t.id=tr.tutor_id AND t.profile_status='VERIFIED' AND t.deleted_at IS NULL
            JOIN users u ON u.id=t.user_id AND u.status='ACTIVE' AND u.deleted_at IS NULL
            WHERE tr.status='OPEN' AND tr.deleted_at IS NULL
            GROUP BY s.name, g.level, tr.teaching_mode
        ),
        demand AS (
            SELECT s.name subject, g.level grade, sr.learning_mode::text mode, count(*) n
            FROM studying_requests sr
            JOIN subjects s ON s.id=sr.subject_id
            JOIN grades g ON g.id=sr.grade_id
            JOIN students st ON st.id=sr.student_id AND st.deleted_at IS NULL
            JOIN users u ON u.id=st.user_id AND u.status='ACTIVE' AND u.deleted_at IS NULL
            WHERE sr.status='OPEN' AND sr.deleted_at IS NULL
            GROUP BY s.name, g.level, sr.learning_mode
        )
        SELECT w.subject, w.grade, w.mode, coalesce(s.n,0), coalesce(d.n,0)
        FROM wanted w
        LEFT JOIN supply s USING(subject,grade,mode)
        LEFT JOIN demand d USING(subject,grade,mode)
        ORDER BY w.subject,w.grade,w.mode
    """
    rows = sql_rows(args.container, args.database, query)
    missing_tutors = [row[:3] for row in rows if int(row[3]) == 0]
    missing_students = [row[:3] for row in rows if int(row[4]) == 0]
    print(f"filter combinations: {len(rows)}")
    print(f"tutor postings: min={min(int(row[3]) for row in rows)} missing={len(missing_tutors)}")
    print(f"student postings: min={min(int(row[4]) for row in rows)} missing={len(missing_students)}")
    if missing_tutors or missing_students:
        print("missing tutor examples:", ascii(missing_tutors[:8]))
        print("missing student examples:", ascii(missing_students[:8]))
        raise SystemExit(1)
    stats = sql_rows(args.container, args.database, """
        SELECT 'tutors',count(*),count(profile_embedding) FROM tutors
        UNION ALL SELECT 'students',count(*),count(profile_embedding) FROM students
        UNION ALL SELECT 'teaching',count(*),count(embedding) FROM teaching_requests
        UNION ALL SELECT 'studying',count(*),count(embedding) FROM studying_requests
    """)
    for row in stats:
        print(f"{row[0]}: {row[1]} rows, {row[2]} embeddings")
    variety = sql_rows(args.container, args.database, """
        SELECT 'teaching',count(DISTINCT p.id),count(DISTINCT d.id),
               count(DISTINCT tr.expected_price),count(DISTINCT a.day_of_week),count(DISTINCT a.start_time)
        FROM teaching_requests tr
        LEFT JOIN teaching_request_districts td ON td.teaching_request_id=tr.id AND td.deleted_at IS NULL
        LEFT JOIN districts d ON d.id=td.district_id
        LEFT JOIN provinces p ON p.id=d.province_id
        LEFT JOIN teaching_request_availabilities a ON a.teaching_request_id=tr.id AND a.deleted_at IS NULL
        WHERE tr.status='OPEN' AND tr.deleted_at IS NULL
        UNION ALL
        SELECT 'studying',count(DISTINCT p.id),count(DISTINCT d.id),
               count(DISTINCT sr.max_price),count(DISTINCT a.day_of_week),count(DISTINCT a.start_time)
        FROM studying_requests sr
        LEFT JOIN districts d ON d.id=sr.district_id
        LEFT JOIN provinces p ON p.id=d.province_id
        LEFT JOIN studying_request_availabilities a ON a.studying_request_id=sr.id AND a.deleted_at IS NULL
        WHERE sr.status='OPEN' AND sr.deleted_at IS NULL
    """)
    for kind, provinces, wards, prices, weekdays, start_times in variety:
        print(f"{kind} filters: {provinces} provinces, {wards} wards, {prices} prices, "
              f"{weekdays} weekdays, {start_times} start times")


if __name__ == "__main__":
    main()
