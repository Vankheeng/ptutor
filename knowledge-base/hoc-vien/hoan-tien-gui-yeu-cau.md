---
id: hoan-tien-gui-yeu-cau
title: Cách gửi, theo dõi và hủy yêu cầu hoàn tiền
type: guide
audience: [STUDENT]
features: [refund]
routes: [/student/contracts/:contractId/lessons, /student/contracts/:contractId/refund-request, /student/refund-requests, /student/refund-requests/:refundRequestId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/student/StudentContractLessonsPage.tsx (Yêu cầu hoàn tiền, Xem yêu cầu hoàn tiền)"
  - "frontend: pages/student/StudentRefundRequestCreatePage.tsx (Gia sư không dạy, Tôi nghỉ buổi này, Minh chứng, Huỷ, Gửi yêu cầu)"
  - "frontend: components/refund/RefundEvidenceField.tsx (đường dẫn http:// hoặc https://)"
  - "frontend: pages/student/StudentRefundRequestsPage.tsx, StudentRefundRequestDetailPage.tsx (Tiến trình, Huỷ yêu cầu)"
  - "backend: StudentRefundRequestController POST, GET, PATCH /{id}/cancel (chỉ PENDING)"
error_codes: [REFUND_REQUEST_ALREADY_OPEN, CONTRACT_NOT_REFUNDABLE, LESSON_NOT_DISPUTABLE, INVALID_REFUND_REQUEST_STATUS_TRANSITION]
keywords: [gửi yêu cầu hoàn tiền, hủy yêu cầu hoàn tiền, theo dõi hoàn tiền, gui yeu cau hoan tien, huy yeu cau hoan tien, theo doi hoan tien]
todo:
  - "Minh chứng hiện chỉ nhập bằng đường dẫn, chưa tải tệp lên được; người dùng nên tải ảnh, video lên đâu để lấy đường dẫn?"
---

# Cách gửi, theo dõi và hủy yêu cầu hoàn tiền

## Gửi yêu cầu hoàn tiền

1. Vào **Hợp đồng**, mở hợp đồng, bấm **Buổi học và hoàn tiền**.
2. Ở trang **Buổi học**, bấm **Yêu cầu hoàn tiền**.
3. Trang **Yêu cầu hoàn tiền** liệt kê các buổi đang chờ xác nhận. Chọn buổi cần hoàn.
4. Với mỗi buổi đã chọn, chọn nguyên nhân: **Gia sư không dạy** hoặc **Tôi nghỉ buổi này**.
5. Mô tả sự việc trong ô mô tả.
6. Nếu có, thêm đường dẫn ảnh, video hoặc tài liệu ở mục **Minh chứng**. Đường dẫn phải bắt đầu bằng http:// hoặc https://.
7. Bấm **Gửi yêu cầu**.

Trang báo "Đã gửi yêu cầu hoàn tiền." Nếu trang hiện "Không có buổi nào đang chờ xác nhận.", hợp đồng không còn buổi nào yêu cầu hoàn tiền được.

## Theo dõi yêu cầu hoàn tiền

1. Trong menu, chọn **Yêu cầu hoàn tiền**.
2. Mở yêu cầu cần xem.

Trang chi tiết có mục **Tiến trình** gồm ba bước: **Đã gửi yêu cầu**, **Kế toán xem xét**, **Hoàn vào ví hoặc từ chối**. Trang còn ghi **Buổi học bị phản đối** với nguyên nhân bạn chọn, phản hồi của gia sư, hạn phản hồi của gia sư và kết luận cho từng buổi.

## Hủy yêu cầu hoàn tiền

1. Mở yêu cầu hoàn tiền đang ở trạng thái "Chờ tiếp nhận".
2. Bấm **Huỷ yêu cầu**.

Trang báo "Đã huỷ yêu cầu hoàn tiền." Khi kế toán đã tiếp nhận (trạng thái "Đang xử lý"), bạn không hủy được nữa. Sau khi hủy, các buổi học được xử lý bình thường trở lại: bạn xác nhận được, hoặc buổi tự hoàn tất khi đủ 3 ngày.
