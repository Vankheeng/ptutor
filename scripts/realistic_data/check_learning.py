"""Check that a local recommendation seed reaches the weight-learning gates."""

import argparse
import subprocess
import sys

SQL = """
WITH searches AS (
  SELECT l.direction, l.created_at, jsonb_array_length(l.returned_candidate_ids) AS shown,
         max(c.rank) AS deepest
  FROM recommendation_logs l
  JOIN recommendation_click_logs c ON c.recommendation_log_id=l.id
       AND c.created_at<=l.created_at+interval '7 days'
  WHERE l.algorithm_version='local-seed-v1'
  GROUP BY l.id,l.direction,l.created_at,l.returned_candidate_ids
), a AS (
  SELECT CASE direction WHEN 'STUDENT_TO_TUTOR' THEN 'A_S2T' ELSE 'A_T2S' END AS model,
         count(*) FILTER (WHERE created_at<=now()-interval '14 days') AS train_searches,
         coalesce(sum(least(shown,deepest+2)) FILTER
           (WHERE created_at<=now()-interval '14 days'),0) AS train_samples,
         coalesce(sum(least(shown,deepest+2)) FILTER
           (WHERE created_at>now()-interval '14 days' AND created_at<=now()-interval '7 days'),0) AS heldout
  FROM searches GROUP BY direction
), decisions AS (
  SELECT s.model_key AS model,
         CASE WHEN s.sample_kind='INITIATIVE' THEN s.created_at
              WHEN coalesce(a.status,p.status) IN ('ACCEPTED','REJECTED')
                THEN coalesce(a.responded_at,p.responded_at)
              WHEN coalesce(a.status,p.status)='PENDING'
                THEN coalesce(a.created_at,p.created_at)+interval '14 days'
         END AS labelled_at
  FROM recommendation_decision_snapshots s
  LEFT JOIN student_tutor_requests a
    ON s.request_type='STUDENT_TUTOR_REQUEST' AND a.id=s.request_id
  LEFT JOIN tutor_student_requests p
    ON s.request_type='TUTOR_STUDENT_REQUEST' AND p.id=s.request_id
), b AS (
  SELECT model,0::bigint AS train_searches,
         count(*) FILTER (WHERE labelled_at<=now()-interval '7 days') AS train_samples,
         count(*) FILTER (WHERE labelled_at>now()-interval '7 days' AND labelled_at<=now()) AS heldout
  FROM decisions GROUP BY model
)
SELECT model,train_searches,train_samples,heldout FROM a
UNION ALL SELECT model,train_searches,train_samples,heldout FROM b
ORDER BY model
"""


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database",default="ptutor_learning_final")
    parser.add_argument("--container",default="ptutor-postgres")
    args=parser.parse_args()
    result=subprocess.run(["docker","exec","-u","postgres",args.container,"psql","-X","-At",
                           "-F","|","-d",args.database,"-c",SQL],capture_output=True)
    if result.returncode:
        sys.stderr.write(result.stderr.decode("utf-8","replace"))
        raise SystemExit(result.returncode)
    expected={"A_S2T","A_T2S","B_S2T","B_T2S"}
    seen=set()
    bad=[]
    for line in result.stdout.decode("utf-8").splitlines():
        model,searches,samples,heldout=line.split("|")
        searches,samples,heldout=map(int,(searches,samples,heldout))
        seen.add(model)
        enough=samples>= (2000 if model.startswith("A_") else 300)
        if model.startswith("A_"):
            enough=enough and searches>=300
        enough=enough and heldout>0
        print(f"{model}: searches={searches}, train={samples}, heldout={heldout}, ready={enough}")
        if not enough:
            bad.append(model)
    if seen!=expected or bad:
        raise SystemExit(f"learning data below threshold: {sorted(expected-seen|set(bad))}")


if __name__=="__main__":
    main()
