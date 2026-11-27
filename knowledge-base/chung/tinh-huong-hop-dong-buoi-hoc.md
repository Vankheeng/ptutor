---
id: tinh-huong-hop-dong-buoi-hoc
title: Xử lý buổi học và hợp đồng trong các trường hợp ngoại lệ
type: faq
audience: [STUDENT, TUTOR]
features: [contract, lesson, refund, complaint]
routes: [/student/contracts/:contractId, /student/contracts/:contractId/lessons, /student/complaints, /tutor/sections/contracts/:contractId]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: ContractLifecycleService, LessonService, ContractLessonProvisioningService, RefundRequestService"
  - "frontend: StudentContractLessonsPage, TutorContractDetailPage, ComplaintsPage"
error_codes: [CONTRACT_LESSON_LIMIT_REACHED, LESSON_COMPLETION_ON_HOLD]
keywords: [xác nhận nhầm, buổi học bù, hết số buổi, hợp đồng hết hạn, kỳ học phí chưa đóng, xac nhan nham, buoi bu, het han]
todo: []
---

# Xử lý buổi học và hợp đồng trong các trường hợp ngoại lệ

## Đã xác nhận nhầm một buổi học

Sau khi buổi chuyển sang **Đã học**, học viên không có nút hủy xác nhận hay tạo yêu cầu hoàn tiền tự phục vụ cho buổi đó. Hãy lưu thông tin buổi, trao đổi với gia sư và gửi **Khiếu nại** nếu còn trong thời hạn của hợp đồng, hoặc **Yêu cầu hỗ trợ** để nhân viên xem xét. Hệ thống hiện không tự đảo khoản tiền dạy đã cộng vào ví gia sư.

## Cần dạy buổi bù nhưng hợp đồng đã đủ số buổi

Hợp đồng **Mỗi buổi** không cho tạo buổi vượt tổng số buổi đã thỏa thuận. Hai bên cần thống nhất gia hạn hoặc tạo hợp đồng mới trước khi ghi thêm buổi và học phí trên PTutor. Buổi bù không có trạng thái riêng trong hệ thống; đừng ghi buổi ngoài hợp đồng rồi kỳ vọng PTutor tự tạo kỳ học phí.

## Hợp đồng tới ngày kết thúc khi còn việc chưa xong

Tác vụ tự kết thúc hợp đồng dựa trên ngày và trạng thái hợp đồng; nó không chờ mọi kỳ học phí được đóng hoặc mọi buổi được xác nhận. Hai bên nên kiểm tra các khoản còn mở trước ngày kết thúc. Nếu có bất đồng về buổi học hay tiền, gửi khiếu nại khi hợp đồng còn hiệu lực hoặc trong **7 ngày sau ngày kết thúc**. Trạng thái **Đã hoàn tất** của hợp đồng không tự xác nhận rằng mọi khoản đã được đối soát.

## Muốn dừng hợp đồng đang hoạt động sớm

Hiện không có nút chấm dứt sớm cho hợp đồng đã ký. Hai bên cần trao đổi và gửi yêu cầu hỗ trợ hoặc khiếu nại để nhân viên kiểm tra buổi đã học, học phí đã đóng và tiền đã giải ngân. PTutor chưa có phép tính tự động hoàn phần học phí chưa học trong trường hợp này.
