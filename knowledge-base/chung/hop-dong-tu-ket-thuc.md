---
id: hop-dong-tu-ket-thuc
title: Hợp đồng tự kết thúc khi hết hạn
type: policy
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: ContractLifecycleService.processExpiredContracts (cron app.contract.lifecycle.expiration-cron, mặc định 0 0 0 * * *, múi giờ Asia/Ho_Chi_Minh)"
  - "backend: ContractRepository.completeActiveContractsEndingOnOrBefore, cancelPendingContractsEndingOnOrBefore"
  - "config: CONTRACT_EXPIRATION_CRON, CONTRACT_TIME_ZONE"
error_codes: [CONTRACT_EXPIRED]
keywords: [hợp đồng hết hạn, kết thúc hợp đồng, hợp đồng hoàn tất, ngày kết thúc, hop dong het han, ket thuc hop dong, hoan tat]
todo:
  - "Khi hợp đồng tự kết thúc mà vẫn còn kỳ học phí chưa đóng hoặc buổi học chờ xác nhận thì xử lý thế nào?"
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
