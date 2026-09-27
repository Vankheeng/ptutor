ALTER TABLE notifications
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN record_type varchar(30) NOT NULL DEFAULT 'SYSTEM_EVENT',
    ADD COLUMN parent_notification_id uuid REFERENCES notifications(id),
    ADD COLUMN created_by_user_id uuid REFERENCES users(id),
    ADD COLUMN updated_by_user_id uuid REFERENCES users(id),
    ADD COLUMN category varchar(30),
    ADD COLUMN audience varchar(20),
    ADD COLUMN campaign_status varchar(30),
    ADD COLUMN scheduled_at timestamp,
    ADD COLUMN processing_started_at timestamp,
    ADD COLUMN sent_at timestamp,
    ADD COLUMN recipient_count integer NOT NULL DEFAULT 0,
    ADD COLUMN failure_reason text;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_record_type CHECK (
        record_type IN ('SYSTEM_EVENT', 'BROADCAST_MASTER', 'BROADCAST_DELIVERY')
    ),
    ADD CONSTRAINT ck_notifications_category CHECK (
        category IS NULL OR category IN ('MAINTENANCE', 'PROMOTION', 'SERVICE_UPDATE', 'GENERAL')
    ),
    ADD CONSTRAINT ck_notifications_audience CHECK (
        audience IS NULL OR audience IN ('ALL', 'STUDENT', 'TUTOR')
    ),
    ADD CONSTRAINT ck_notifications_campaign_status CHECK (
        campaign_status IS NULL OR campaign_status IN (
            'DRAFT', 'SCHEDULED', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED', 'RETRACTED'
        )
    ),
    ADD CONSTRAINT ck_notifications_record_shape CHECK (
        (record_type = 'SYSTEM_EVENT' AND user_id IS NOT NULL AND parent_notification_id IS NULL)
        OR (record_type = 'BROADCAST_MASTER' AND user_id IS NULL AND parent_notification_id IS NULL
            AND created_by_user_id IS NOT NULL AND category IS NOT NULL
            AND audience IS NOT NULL AND campaign_status IS NOT NULL)
        OR (record_type = 'BROADCAST_DELIVERY' AND user_id IS NOT NULL AND parent_notification_id IS NOT NULL)
    );

CREATE INDEX idx_notifications_campaign_schedule
    ON notifications(campaign_status, scheduled_at)
    WHERE record_type = 'BROADCAST_MASTER' AND deleted_at IS NULL;

CREATE INDEX idx_notifications_parent
    ON notifications(parent_notification_id)
    WHERE parent_notification_id IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_notification_delivery_recipient
    ON notifications(parent_notification_id, user_id)
    WHERE record_type = 'BROADCAST_DELIVERY' AND deleted_at IS NULL;
