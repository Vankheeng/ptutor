-- Yêu cầu hỗ trợ: người dùng tự gửi câu hỏi cho nhân viên CUSTOMER_SERVICE khi trợ lý không trả lời được
-- (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.4). Dữ liệu nghiệp vụ nên nằm ở schema public của backend.

CREATE SEQUENCE IF NOT EXISTS support_request_code_seq START 1;

CREATE TABLE support_requests (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    user_role VARCHAR(20) NOT NULL,
    question TEXT NOT NULL,
    source VARCHAR(20) NOT NULL,
    -- Tham chiếu sang schema assistant; cố ý không đặt khoá ngoại (hai service tách biệt).
    conversation_id UUID,
    message_id UUID,
    -- Chỉ có khi người dùng đồng ý đính kèm; tối đa 6 lượt, đã che thông tin cá nhân. Xoá sau 365 ngày kể từ khi đóng.
    conversation_excerpt JSONB,
    attach_conversation BOOLEAN NOT NULL DEFAULT FALSE,
    page_path VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    assigned_employee_id UUID REFERENCES employees(id),
    answer TEXT,
    previous_request_id UUID REFERENCES support_requests(id),
    assigned_at TIMESTAMP,
    answered_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT uq_support_requests_code UNIQUE (code),
    CONSTRAINT ck_support_requests_role CHECK (user_role IN ('STUDENT', 'TUTOR')),
    CONSTRAINT ck_support_requests_source CHECK (
        source IN ('ASSISTANT_ABSTAIN', 'ASSISTANT_PARTIAL', 'ASSISTANT_FEEDBACK', 'MENU')),
    CONSTRAINT ck_support_requests_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'ANSWERED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_support_requests_question_length CHECK (char_length(question) BETWEEN 1 AND 2000),
    -- Chỉ đóng được sau khi đã có câu trả lời (OPEN → … → ANSWERED → CLOSED), nên hai trạng thái này luôn có answer.
    CONSTRAINT ck_support_requests_answer CHECK (status NOT IN ('ANSWERED', 'CLOSED') OR answer IS NOT NULL)
);

CREATE INDEX ix_support_requests_user_created ON support_requests (user_id, created_at DESC);
CREATE INDEX ix_support_requests_status_created ON support_requests (status, created_at);
CREATE INDEX ix_support_requests_assignee_status ON support_requests (assigned_employee_id, status);

-- Thông báo mới: SUPPORT_REQUEST_ANSWERED (gửi người dùng), SUPPORT_REQUEST_RECEIVED (gửi nhân viên CUSTOMER_SERVICE).
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT', 'USER', 'LESSON', 'WITHDRAWAL_REQUEST', 'REFUND_REQUEST', 'SUPPORT_REQUEST'
        )
    );

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
            'TUTOR_PROFILE_APPROVED', 'TUTOR_PROFILE_REJECTED',
            'TEACHING_REQUEST_REVIEW_REQUIRED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'STUDYING_REQUEST_REVIEW_REQUIRED',
            'STUDYING_REQUEST_APPROVED', 'STUDYING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'COMPLAINT_RECEIVED_AGAINST_YOU', 'COMPLAINT_AGAINST_YOU_RESOLVED',
            'COMPLAINT_AGAINST_YOU_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
            'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
            'WITHDRAWAL_REVIEW_REQUIRED',
            'REFUND_REQUEST_REVIEW_REQUIRED', 'REFUND_REQUEST_SUBMITTED', 'REFUND_REQUEST_TUTOR_RESPONDED',
            'REFUND_REQUEST_APPROVED', 'REFUND_REQUEST_REJECTED',
            'SUPPORT_REQUEST_ANSWERED', 'SUPPORT_REQUEST_RECEIVED'
        )
    );
