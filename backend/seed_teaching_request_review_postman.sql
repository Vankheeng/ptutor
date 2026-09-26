BEGIN;

WITH seed_data(id, title, note, custom_subject_name, catalog_subject_name, expected_price) AS (
    VALUES
        ('11111111-1111-4111-8111-111111111101'::uuid,
         '[POSTMAN] Duyệt vì có note',
         'Nội dung ghi chú cần admin kiểm tra',
         NULL,
         'Toán',
         150000::decimal),
        ('11111111-1111-4111-8111-111111111102'::uuid,
         '[POSTMAN] Tạo môn mới',
         NULL,
         'Khoa học dữ liệu ứng dụng',
         NULL,
         250000::decimal),
        ('11111111-1111-4111-8111-111111111103'::uuid,
         '[POSTMAN] Liên kết môn có sẵn',
         NULL,
         'Toán nâng cao',
         NULL,
         180000::decimal),
        ('11111111-1111-4111-8111-111111111104'::uuid,
         '[POSTMAN] Từ chối và hoàn phí',
         'Nội dung ghi chú dùng để kiểm tra reject',
         NULL,
         'Toán',
         170000::decimal)
)
INSERT INTO teaching_requests (
    id, tutor_id, subject_id, custom_subject_name, title, note, quantity,
    expected_price, teaching_mode, description, status, created_at, updated_at
)
SELECT
    seed.id,
    tutor.id,
    subject.id,
    seed.custom_subject_name,
    seed.title,
    seed.note,
    2,
    seed.expected_price,
    'ONLINE',
    'Dữ liệu kiểm thử Postman cho luồng admin duyệt yêu cầu dạy',
    'PENDING_REVIEW',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM seed_data seed
JOIN users tutor_user ON lower(tutor_user.email) = 'tutor01@example.com'
JOIN tutors tutor ON tutor.user_id = tutor_user.id
LEFT JOIN subjects subject
       ON seed.catalog_subject_name IS NOT NULL
      AND lower(subject.name) = lower(seed.catalog_subject_name)
WHERE seed.catalog_subject_name IS NULL OR subject.id IS NOT NULL
ON CONFLICT (id) DO NOTHING;

WITH payment_data(id, request_id, transaction_code) AS (
    VALUES
        ('22222222-2222-4222-8222-222222222101'::uuid,
         '11111111-1111-4111-8111-111111111101'::uuid,
         'POSTMAN-TEACHING-REVIEW-101'),
        ('22222222-2222-4222-8222-222222222102'::uuid,
         '11111111-1111-4111-8111-111111111102'::uuid,
         'POSTMAN-TEACHING-REVIEW-102'),
        ('22222222-2222-4222-8222-222222222103'::uuid,
         '11111111-1111-4111-8111-111111111103'::uuid,
         'POSTMAN-TEACHING-REVIEW-103'),
        ('22222222-2222-4222-8222-222222222104'::uuid,
         '11111111-1111-4111-8111-111111111104'::uuid,
         'POSTMAN-TEACHING-REVIEW-104')
)
INSERT INTO payments (
    id, user_id, amount, payment_method, payment_type, status,
    transaction_code, reference_type, reference_id, note, paid_at,
    created_at, updated_at
)
SELECT
    payment.id,
    tutor_user.id,
    10000,
    'VNPAY',
    'TEACHING_REQUEST_FEE',
    'PAID',
    payment.transaction_code,
    'TEACHING_REQUEST',
    payment.request_id,
    'Postman seed payment',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM payment_data payment
JOIN users tutor_user ON lower(tutor_user.email) = 'tutor01@example.com'
JOIN teaching_requests request ON request.id = payment.request_id
ON CONFLICT (id) DO NOTHING;

COMMIT;
