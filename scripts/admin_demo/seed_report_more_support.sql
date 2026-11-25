-- A few additional cases so the admin queues show a useful mix of questions.
BEGIN;
INSERT INTO assistant.conversation (id,user_id,user_role,title,created_at,last_message_at)
VALUES
 ('a0000000-0000-4000-8000-000000000304','78e0b0c8-c96e-5319-ba0f-879f802dd6e0','STUDENT','Điều kiện hoàn tiền',now()-interval '1 day',now()-interval '1 day'),
 ('a0000000-0000-4000-8000-000000000305','2c36394b-b7c6-5759-afc4-ae68c3c7db99','TUTOR','Cập nhật chứng chỉ',now()-interval '6 hours',now()-interval '6 hours')
ON CONFLICT (id) DO NOTHING;
INSERT INTO assistant.message (id,conversation_id,sender,content,created_at)
VALUES
 ('a0000000-0000-4000-8000-000000000314','a0000000-0000-4000-8000-000000000304','USER','Buổi học bị hủy sát giờ thì trường hợp nào được hoàn tiền?',now()-interval '1 day'),
 ('a0000000-0000-4000-8000-000000000315','a0000000-0000-4000-8000-000000000305','USER','Tôi có thể thay chứng chỉ đã nộp bằng bản mới trong lúc chờ duyệt không?',now()-interval '6 hours')
ON CONFLICT (id) DO NOTHING;
INSERT INTO assistant.message
 (id,conversation_id,sender,reply_to_message_id,content,intent,grounded,fallback_reason,created_at)
VALUES
 ('a0000000-0000-4000-8000-000000000324','a0000000-0000-4000-8000-000000000304','ASSISTANT',
  'a0000000-0000-4000-8000-000000000314','Tôi chưa có đủ thông tin về điều kiện hoàn tiền cho trường hợp này.','POLICY','NONE','NO_CONTEXT',now()-interval '1 day'),
 ('a0000000-0000-4000-8000-000000000325','a0000000-0000-4000-8000-000000000305','ASSISTANT',
  'a0000000-0000-4000-8000-000000000315','Bạn có thể theo dõi trạng thái chứng chỉ trong hồ sơ gia sư.','ACCOUNT','PARTIAL','NO_CONTEXT',now()-interval '6 hours')
ON CONFLICT (id) DO NOTHING;

INSERT INTO support_requests
 (id,code,user_id,user_role,question,source,conversation_id,message_id,
  attach_conversation,page_path,status,created_at,updated_at)
VALUES
 ('a0000000-0000-4000-8000-000000000223','HT-900006',
  'fd2c875c-25c4-5f9b-82b9-ba9c42f3b9ee','STUDENT',
  'Tôi đã thanh toán học phí nhưng hợp đồng vẫn hiển thị chờ thanh toán, nhờ kiểm tra giúp.',
  'MENU',NULL,NULL,false,'/student/contracts','OPEN',now()-interval '8 hours',now()-interval '8 hours'),
 ('a0000000-0000-4000-8000-000000000224','HT-900007',
  '2c36394b-b7c6-5759-afc4-ae68c3c7db99','TUTOR',
  'Chứng chỉ mới của tôi đã được tải lên. Bao lâu nữa hồ sơ được xem xét?',
  'MENU',NULL,NULL,false,'/tutor/profile','OPEN',now()-interval '5 hours',now()-interval '5 hours'),
 ('a0000000-0000-4000-8000-000000000225','HT-900008',
  '78e0b0c8-c96e-5319-ba0f-879f802dd6e0','STUDENT',
  'Trợ lý chưa giải thích được điều kiện hoàn tiền khi buổi học bị hủy sát giờ.',
  'ASSISTANT_ABSTAIN','a0000000-0000-4000-8000-000000000304',
  'a0000000-0000-4000-8000-000000000324',false,'/student/contracts','OPEN',
  now()-interval '1 day',now()-interval '1 day')
ON CONFLICT (id) DO NOTHING;
COMMIT;
