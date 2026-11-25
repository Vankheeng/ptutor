"""Add a small set of Vietnamese admin report examples to a local PostgreSQL DB.

Run after scripts/admin_demo/seed.py. The records are idempotent and use the
backend's local bank-account encryption key for the withdrawal snapshot.
"""

import argparse
import base64
import hashlib
import os
import re
import secrets
import subprocess
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def bank_account_ciphertext(account_number: str) -> str:
    configured = os.environ.get("BANK_ACCOUNT_ENCRYPTION_KEY") or os.environ.get("CITIZEN_ID_ENCRYPTION_KEY")
    if not configured:
        config = Path(__file__).resolve().parents[2] / "backend/src/main/resources/application.yml"
        match = re.search(r"^\s*citizen-id-encryption-key:\s*(\S+)", config.read_text(encoding="utf-8"), re.M)
        if match is None:
            raise RuntimeError("Missing local encryption key")
        configured = match.group(1)
    master = base64.b64decode(configured)
    if len(master) != 32:
        raise ValueError("Bank account encryption key must decode to 32 bytes")
    key = hashlib.sha256(b"bank-account-number\0" + master).digest()
    iv = secrets.token_bytes(12)
    payload = iv + AESGCM(key).encrypt(iv, account_number.encode(), None)
    return base64.urlsafe_b64encode(payload).decode().rstrip("=")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", default="ptutor_report_admin")
    args = parser.parse_args()
    ciphertext = bank_account_ciphertext("9704000000123456")
    sql = f"""
BEGIN;
UPDATE users SET first_name='Nguyễn', last_name='Thu Hà' WHERE id='a0000000-0000-4000-8000-000000000001';
UPDATE users SET first_name='Trần', last_name='Minh Quân' WHERE id='a0000000-0000-4000-8000-000000000002';
UPDATE users SET first_name='Lê', last_name='Phương Anh' WHERE id='a0000000-0000-4000-8000-000000000003';
UPDATE users SET first_name='Phạm', last_name='Hữu Đạt' WHERE id='a0000000-0000-4000-8000-000000000004';
UPDATE users SET first_name='Võ', last_name='Gia Hân' WHERE id='a0000000-0000-4000-8000-000000000005';
UPDATE users SET first_name='Đặng', last_name='Minh Khang' WHERE id='a0000000-0000-4000-8000-000000000006';
UPDATE users SET suspension_reason='Tài khoản tạm khóa để xác minh thông tin cá nhân' WHERE id='a0000000-0000-4000-8000-000000000006';
UPDATE users SET detail_address='Văn phòng hỗ trợ PTutor' WHERE id IN ('a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002');
UPDATE tutors SET introduction='Gia sư Toán THCS, có kinh nghiệm kèm học sinh lớp 8 và lớp 9.' WHERE id='a0000000-0000-4000-8000-000000000021';
UPDATE tutors SET introduction='Gia sư tiếng Anh giao tiếp và luyện thi THPT.', profile_rejection_reason='Cần bổ sung thông tin kinh nghiệm giảng dạy.' WHERE id='a0000000-0000-4000-8000-000000000022';
UPDATE certificates SET name='Chứng chỉ tiếng Anh B2', issuing_organization='Đại học Ngoại ngữ - ĐHQGHN' WHERE id='a0000000-0000-4000-8000-000000000031';
UPDATE certificates SET name='Chứng chỉ IELTS 7.0', issuing_organization='British Council' WHERE id='a0000000-0000-4000-8000-000000000032';
UPDATE certificates SET name='Chứng chỉ nghiệp vụ sư phạm', issuing_organization='Trường Đại học Sư phạm Hà Nội' WHERE id='a0000000-0000-4000-8000-000000000033';
UPDATE certificates SET name='Chứng chỉ tiếng Anh B2', issuing_organization='Đại học Ngoại ngữ - ĐHQGHN', rejection_reason='Ảnh chứng chỉ chưa rõ thông tin.' WHERE id='a0000000-0000-4000-8000-000000000034';
UPDATE teaching_requests SET title='Dạy kèm Toán lớp 9 trực tuyến', description='Ôn tập kiến thức Toán lớp 9 và luyện đề vào lớp 10.', rejection_reason=NULL WHERE id='a0000000-0000-4000-8000-000000000041';
UPDATE teaching_requests SET title='Dạy tiếng Anh giao tiếp trực tuyến', description='Luyện nghe nói tiếng Anh theo chủ đề hằng tuần.', rejection_reason='Cần bổ sung mục tiêu và lịch học dự kiến.' WHERE id='a0000000-0000-4000-8000-000000000042';
UPDATE studying_requests SET title='Tìm gia sư Toán lớp 9', description='Cần gia sư hỗ trợ ôn tập kiến thức và luyện đề vào lớp 10.', rejection_reason=NULL WHERE id='a0000000-0000-4000-8000-000000000051';
UPDATE studying_requests SET title='Tìm gia sư tiếng Anh lớp 8', description='Mong muốn cải thiện kỹ năng nghe nói và ngữ pháp.', rejection_reason='Cần bổ sung thời gian học dự kiến.' WHERE id='a0000000-0000-4000-8000-000000000052';
UPDATE complaints SET title='Buổi học chưa đúng thời lượng', content='Buổi học ngày gần nhất kết thúc sớm hơn thời lượng đã thống nhất.' WHERE id='a0000000-0000-4000-8000-000000000061';
UPDATE complaints SET title='Cần đối chiếu nội dung buổi học', content='Nội dung buổi học chưa đúng với kế hoạch ban đầu, mong được hỗ trợ kiểm tra.' WHERE id='a0000000-0000-4000-8000-000000000062';
UPDATE notifications SET title='Cập nhật quy trình xác minh gia sư', content='Gia sư vui lòng kiểm tra hồ sơ và bổ sung chứng chỉ còn thiếu.' WHERE id='a0000000-0000-4000-8000-000000000071';
UPDATE notifications SET title='Lịch bảo trì hệ thống', content='Một số chức năng có thể tạm gián đoạn trong thời gian bảo trì.' WHERE id='a0000000-0000-4000-8000-000000000072';
UPDATE notifications SET title='Biến động số dư ví', content='Số dư ví đã được cập nhật sau giao dịch.' WHERE id='a0000000-0000-4000-8000-000000000073';
UPDATE lessons SET title='Buổi học cần xác minh' WHERE title='ADMIN DEMO: disputed lesson';
UPDATE refund_requests SET description='Đề nghị kiểm tra buổi học được đánh dấu hoàn thành nhưng chưa diễn ra đầy đủ.', tutor_response='Tôi có thể cung cấp lịch sử trao đổi và nội dung buổi học.', rejection_reason='Chưa đủ căn cứ xác nhận yêu cầu hoàn tiền.' WHERE id='a0000000-0000-4000-8000-000000000103';
UPDATE refund_requests SET description='Đề nghị kiểm tra buổi học được đánh dấu hoàn thành nhưng chưa diễn ra đầy đủ.' WHERE id IN ('a0000000-0000-4000-8000-000000000101','a0000000-0000-4000-8000-000000000102');
UPDATE refund_requests SET tutor_response='Tôi có thể cung cấp lịch sử trao đổi và nội dung buổi học.' WHERE id='a0000000-0000-4000-8000-000000000102';
UPDATE wallet_transactions SET description='Nạp tiền vào ví để thanh toán học phí' WHERE id='a0000000-0000-4000-8000-000000000122';
UPDATE payments SET note='Dữ liệu mẫu phục vụ báo cáo: học phí của hợp đồng' WHERE transaction_code LIKE 'ADMIN-DEMO-TUITION-%';
UPDATE payments SET note='Dữ liệu mẫu phục vụ báo cáo: nạp ví' WHERE id='a0000000-0000-4000-8000-000000000123';
UPDATE payments SET note='Dữ liệu mẫu phục vụ báo cáo: phí đăng bài' WHERE id IN ('a0000000-0000-4000-8000-000000000131','a0000000-0000-4000-8000-000000000132');
INSERT INTO support_requests (id, code, user_id, user_role, question, source, page_path, status, assigned_employee_id, answer, assigned_at, answered_at, created_at, updated_at)
VALUES
 ('a0000000-0000-4000-8000-000000000201','HT-900001','30fc2221-8718-5142-9ecc-17c40c939211','STUDENT','Tôi muốn thay đổi lịch học đã thống nhất với gia sư thì cần làm thế nào?','MENU','/student/contracts','OPEN',NULL,NULL,NULL,NULL,now()-interval '2 days',now()-interval '2 days'),
 ('a0000000-0000-4000-8000-000000000202','HT-900002','9da38ede-06b3-5316-912b-56d5ee09ff40','TUTOR','Hồ sơ gia sư của tôi cần bổ sung thông tin gì để được duyệt?','MENU','/tutor/profile','IN_PROGRESS','a0000000-0000-4000-8000-000000000011',NULL,now()-interval '1 day',NULL,now()-interval '1 day',now()-interval '1 day'),
 ('a0000000-0000-4000-8000-000000000203','HT-900003','aa0c6d3e-76ec-5482-8e5d-4cc2270de36a','STUDENT','Tôi có thể xem lại lịch sử thanh toán của hợp đồng ở đâu?','MENU','/student/payments','ANSWERED','a0000000-0000-4000-8000-000000000011','Bạn mở mục Hợp đồng, chọn hợp đồng cần xem rồi vào phần Thanh toán để xem các khoản đã ghi nhận.',now()-interval '3 days',now()-interval '2 days',now()-interval '3 days',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;
UPDATE wallets SET balance=700000, pending_balance=300000 WHERE id='a0000000-0000-4000-8000-000000000124';
INSERT INTO withdrawal_requests (id,wallet_id,amount,bank_code,bank_name,encrypted_account_number,account_number_last_four,account_holder_name,note,idempotency_key,status,created_at,updated_at)
VALUES ('a0000000-0000-4000-8000-000000000211','a0000000-0000-4000-8000-000000000124',300000,'VCB','Vietcombank','{ciphertext}','3456','NGUYEN THI LAN','Rút thu nhập giảng dạy','report-admin-withdrawal-1','PENDING',now()-interval '1 day',now()-interval '1 day')
ON CONFLICT (id) DO NOTHING;
UPDATE withdrawal_requests SET account_holder_name='NGUYEN THI LAN' WHERE id='a0000000-0000-4000-8000-000000000211';
COMMIT;
"""
    result = subprocess.run(
        ["docker", "exec", "-i", "ptutor-postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", args.database],
        input=sql.encode("utf-8"), capture_output=True,
    )
    if result.returncode:
        raise SystemExit(result.stderr.decode("utf-8", errors="replace"))
    print(result.stdout.decode("utf-8", errors="replace"))


if __name__ == "__main__":
    main()
