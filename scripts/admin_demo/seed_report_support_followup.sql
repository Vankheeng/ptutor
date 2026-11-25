-- Sample escalations linked to assistant conversations in this report database.
BEGIN;
INSERT INTO support_requests
  (id, code, user_id, user_role, question, source, conversation_id, message_id,
   attach_conversation, page_path, status, created_at, updated_at)
VALUES
  ('a0000000-0000-4000-8000-000000000221', 'HT-900004',
   '30fc2221-8718-5142-9ecc-17c40c939211', 'STUDENT',
   'Trợ lý chưa giải thích được quy định đổi lịch sát giờ. Nhờ nhân viên hướng dẫn trường hợp này.',
   'ASSISTANT_ABSTAIN', 'a0000000-0000-4000-8000-000000000301',
   'a0000000-0000-4000-8000-000000000321', false, '/student/contracts', 'OPEN',
   now() - interval '4 hours', now() - interval '4 hours'),
  ('a0000000-0000-4000-8000-000000000222', 'HT-900005',
   '9da38ede-06b3-5316-912b-56d5ee09ff40', 'TUTOR',
   'Trợ lý mới hướng dẫn xem trạng thái hồ sơ. Tôi cần biết giấy tờ nào phải bổ sung để gửi lại.',
   'ASSISTANT_PARTIAL', 'a0000000-0000-4000-8000-000000000302',
   'a0000000-0000-4000-8000-000000000322', false, '/tutor/profile', 'OPEN',
   now() - interval '2 hours', now() - interval '2 hours')
ON CONFLICT (id) DO NOTHING;
COMMIT;
