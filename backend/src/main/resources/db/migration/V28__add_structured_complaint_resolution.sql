ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS category varchar(30),
    ADD COLUMN IF NOT EXISTS fault_party varchar(20),
    ADD COLUMN IF NOT EXISTS respondent_response text,
    ADD COLUMN IF NOT EXISTS respondent_responded_at timestamp;

ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS ck_complaints_category;

ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS ck_complaints_fault_party;

-- Complaints created before this migration keep a NULL category and fault party.
ALTER TABLE complaints
    ADD CONSTRAINT ck_complaints_category CHECK (
        category IS NULL OR category IN (
            'ABSENCE', 'LATE_OR_SHORT', 'TEACHING_QUALITY',
            'MISCONDUCT', 'SERIOUS_MISCONDUCT', 'OTHER'
        )
    ),
    ADD CONSTRAINT ck_complaints_fault_party CHECK (
        fault_party IS NULL
        OR (status = 'RESOLVED' AND fault_party IN ('RESPONDENT', 'COMPLAINANT', 'BOTH', 'NONE'))
    );

ALTER TABLE evidences
    ADD COLUMN IF NOT EXISTS uploaded_by_user_id uuid REFERENCES users (id);

-- Only the complainant could attach evidence before respondents were able to reply.
UPDATE evidences evidence
SET uploaded_by_user_id = complaint.user_id
FROM complaints complaint
WHERE evidence.complaint_id = complaint.id
  AND evidence.uploaded_by_user_id IS NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_event_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_event_type CHECK (
        event_type IS NULL OR event_type IN (
            'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
            'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
            'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
            'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
            'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
            'CERTIFICATE_REVIEW_REQUIRED', 'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
            'TEACHING_REQUEST_REVIEW_REQUIRED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'STUDYING_REQUEST_REVIEW_REQUIRED',
            'STUDYING_REQUEST_APPROVED', 'STUDYING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'COMPLAINT_RECEIVED_AGAINST_YOU', 'COMPLAINT_AGAINST_YOU_RESOLVED',
            'COMPLAINT_AGAINST_YOU_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
            'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
            'WITHDRAWAL_REVIEW_REQUIRED'
        )
    );
