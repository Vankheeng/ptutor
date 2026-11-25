-- Local report examples for the assistant admin pages. Run after Alembic and KB import.
BEGIN;
INSERT INTO assistant.conversation (id, user_id, user_role, title, created_at, last_message_at)
VALUES
 ('a0000000-0000-4000-8000-000000000301','30fc2221-8718-5142-9ecc-17c40c939211','STUDENT','Thay đổi lịch học',now()-interval '4 days',now()-interval '4 days'),
 ('a0000000-0000-4000-8000-000000000302','9da38ede-06b3-5316-912b-56d5ee09ff40','TUTOR','Xét duyệt hồ sơ gia sư',now()-interval '3 days',now()-interval '3 days'),
 ('a0000000-0000-4000-8000-000000000303','aa0c6d3e-76ec-5482-8e5d-4cc2270de36a','STUDENT','Lịch sử thanh toán',now()-interval '2 days',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO assistant.message (id, conversation_id, sender, content, created_at)
VALUES
 ('a0000000-0000-4000-8000-000000000311','a0000000-0000-4000-8000-000000000301','USER','Nếu gia sư xin đổi lịch sát giờ học thì học viên xử lý thế nào?',now()-interval '4 days'),
 ('a0000000-0000-4000-8000-000000000312','a0000000-0000-4000-8000-000000000302','USER','Hồ sơ của tôi bị từ chối vì lý do gì và cần nộp lại giấy tờ nào?',now()-interval '3 days'),
 ('a0000000-0000-4000-8000-000000000313','a0000000-0000-4000-8000-000000000303','USER','Tôi xem các kỳ thanh toán của hợp đồng ở đâu?',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO assistant.message (id, conversation_id, sender, reply_to_message_id, content, intent, grounded, fallback_reason, created_at)
VALUES
 ('a0000000-0000-4000-8000-000000000321','a0000000-0000-4000-8000-000000000301','ASSISTANT','a0000000-0000-4000-8000-000000000311','Tôi chưa có đủ thông tin về quy định đổi lịch sát giờ. Bạn có thể gửi yêu cầu hỗ trợ để nhân viên kiểm tra.', 'POLICY','NONE','NO_CONTEXT',now()-interval '4 days'),
 ('a0000000-0000-4000-8000-000000000322','a0000000-0000-4000-8000-000000000302','ASSISTANT','a0000000-0000-4000-8000-000000000312','Bạn có thể mở hồ sơ gia sư để xem trạng thái xét duyệt. Với giấy tờ cần bổ sung, nhân viên hỗ trợ có thể kiểm tra cụ thể.', 'ACCOUNT','PARTIAL','NO_CONTEXT',now()-interval '3 days'),
 ('a0000000-0000-4000-8000-000000000323','a0000000-0000-4000-8000-000000000303','ASSISTANT','a0000000-0000-4000-8000-000000000313','Bạn mở mục Hợp đồng, chọn hợp đồng cần xem rồi vào phần Thanh toán.', 'NAVIGATION','FULL',NULL,now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO assistant.message_feedback (message_id,user_id,rating,reason,comment,created_at)
VALUES ('a0000000-0000-4000-8000-000000000322','9da38ede-06b3-5316-912b-56d5ee09ff40',-1,'NOT_HELPFUL','Chưa nêu rõ loại giấy tờ cần bổ sung.',now()-interval '3 days')
ON CONFLICT (message_id) DO NOTHING;
UPDATE assistant.message_feedback SET reason='NOT_HELPFUL' WHERE message_id='a0000000-0000-4000-8000-000000000322';
COMMIT;
