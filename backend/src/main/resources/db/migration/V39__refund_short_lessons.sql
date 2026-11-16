ALTER TABLE refund_request_lessons
    ADD COLUMN missed_minutes integer;

ALTER TABLE refund_request_lessons
    DROP CONSTRAINT ck_refund_request_lessons_claim,
    ADD CONSTRAINT ck_refund_request_lessons_claim CHECK (
        claimed_outcome IN ('TUTOR_ABSENT', 'STUDENT_ABSENT', 'TUTOR_LATE_OR_SHORT')
    ),
    DROP CONSTRAINT ck_refund_request_lessons_position,
    ADD CONSTRAINT ck_refund_request_lessons_position CHECK (
        tutor_position IS NULL OR tutor_position IN ('TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT', 'TUTOR_LATE_OR_SHORT')
    ),
    DROP CONSTRAINT ck_refund_request_lessons_decision,
    ADD CONSTRAINT ck_refund_request_lessons_decision CHECK (
        decision IS NULL OR decision IN ('TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT', 'TUTOR_LATE_OR_SHORT')
    ),
    ADD CONSTRAINT ck_refund_request_lessons_missed_minutes CHECK (
        (claimed_outcome = 'TUTOR_LATE_OR_SHORT' AND missed_minutes > 0)
        OR (claimed_outcome <> 'TUTOR_LATE_OR_SHORT' AND missed_minutes IS NULL)
    );
