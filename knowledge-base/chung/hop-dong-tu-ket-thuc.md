---
id: hop-dong-tu-ket-thuc
title: Hợp đồng tự kết thúc khi hết hạn
type: policy
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: ContractLifecycleService.processExpiredContracts (cron app.contract.lifecycle.expiration-cron, mặc định 0 0 0 * * *, múi giờ Asia/Ho_Chi_Minh)"
  - "backend: ContractRepository.completeActiveContractsEndingOnOrBefore, cancelPendingContractsEndingOnOrBefore"
  - "config: CONTRACT_EXPIRATION_CRON, CONTRACT_TIME_ZONE"
error_codes: [CONTRACT_EXPIRED]
keywords: [hợp đồng hết hạn, kết thúc hợp đồng, hợp đồng hoàn tất, ngày kết thúc, hop dong het han, ket thuc hop dong, hoan tat]
todo: []
---

# Hợp đồng tự kết thúc khi hết hạn

## Hệ thống tự kết thúc hợp đồng lúc nào

Mỗi ngày lúc 0 giờ (giờ Việt Nam), PTutor tự kiểm tra các hợp đồng đã tới ngày kết thúc:

- Hợp đồng "Đang hoạt động" tới ngày kết thúc chuyển sang "Đã hoàn tất".
- Hợp đồng "Đang chờ" tới ngày kết thúc mà chưa được ký chuyển sang "Đã hủy".

Ví dụ: hợp đồng có ngày kết thúc 30/11/2026 sẽ chuyển trạng thái lúc 0 giờ ngày 30/11/2026. Ngày kết thúc không còn là ngày hợp đồng có hiệu lực.

## Sau khi hợp đồng hoàn tất

Hợp đồng "Đã hoàn tất" không ký, sửa hay gia hạn được nữa. Muốn học tiếp với cùng gia sư, bạn nên đề xuất gia hạn trước ngày kết thúc. Nếu đã quá ngày kết thúc, bạn cần đăng tin hoặc đăng ký học mới.

## Hợp đồng chờ ký bị hủy vì hết hạn

Hợp đồng chưa ký không ký được từ ngày kết thúc trở đi, và tự chuyển sang "Đã hủy" lúc 0 giờ ngày kết thúc. Người soạn có thể soạn hợp đồng mới với ngày kết thúc sau hôm nay.

## Kỳ chưa đóng và buổi chưa xác nhận lúc hết hạn

Tác vụ kết thúc hợp đồng dựa trên ngày kết thúc và trạng thái hợp đồng; nó không kiểm tra trước toàn bộ kỳ học phí hoặc buổi học còn chờ xác nhận. Vì vậy hợp đồng có thể chuyển sang **Đã hoàn tất** khi vẫn còn dữ liệu thanh toán hoặc buổi học cần xử lý. Hai bên nên kiểm tra, giải quyết các khoản và buổi còn mở trước ngày kết thúc; nếu có tranh chấp, gửi khiếu nại trong thời hạn cho phép.
