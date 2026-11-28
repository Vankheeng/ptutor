"""Queue EMBED jobs when a local seed refresh changed text outside JPA events.

This only compares authored fields with the saved embedding source text. It never
deletes a vector, and it does not create another open job for the same entity.
"""

import argparse
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", default="ptutor_app_seeded")
    parser.add_argument("--container", default="ptutor-postgres")
    args = parser.parse_args()
    if args.database in ("postgres", "ptutor"):
        parser.error("choose a dedicated local fixture database")
    targets = (
        ("TUTOR", "tutors", "profile_embedding", ("introduction", "teaching_methodology"), None),
        ("STUDENT", "students", "profile_embedding", ("goals_description", "weak_points"), None),
        ("TEACHING_REQUEST", "teaching_requests", "embedding", ("title", "description"), "OPEN"),
        ("STUDYING_REQUEST", "studying_requests", "embedding", ("title", "description", "learning_goals"), "OPEN"),
    )
    sql = ["BEGIN;"]
    for entity_type, table, vector, fields, status in targets:
        state_filter = f"AND e.status='{status}'" if status else ""
        stale = " OR ".join(
            f"(e.{field} IS NOT NULL AND btrim(e.{field})<>'' "
            f"AND COALESCE(position(rtrim(btrim(e.{field}), ' .') in e.embedding_source_text),0)=0)"
            for field in fields
        )
        sql.append(f"""
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts,
                 next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'EMBED', '{entity_type}', e.id, 'PENDING', 0,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM {table} e
            WHERE e.deleted_at IS NULL AND e.{vector} IS NOT NULL
              AND ({stale})
              {state_filter}
              AND NOT EXISTS (
                  SELECT 1 FROM ai_enrichment_jobs j
                  WHERE j.job_type='EMBED' AND j.entity_type='{entity_type}'
                    AND j.entity_id=e.id AND j.status IN ('PENDING','PROCESSING'))
            ON CONFLICT DO NOTHING;
        """)
    sql.append("COMMIT;")
    process = subprocess.run(
        ["docker", "exec", "-i", args.container, "psql", "-X", "-v", "ON_ERROR_STOP=1",
         "-U", "postgres", "-d", args.database],
        input="\n".join(sql).encode("utf-8"), capture_output=True,
    )
    if process.returncode:
        raise SystemExit(process.stderr.decode("utf-8", errors="replace"))
    for line in process.stdout.decode("utf-8", errors="replace").splitlines():
        if line.startswith("INSERT"):
            print(line)


if __name__ == "__main__":
    main()
