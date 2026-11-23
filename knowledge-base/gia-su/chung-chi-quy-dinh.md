---
id: chung-chi-quy-dinh
title: "Quy định và trạng thái chứng chỉ: duyệt, hết hạn, sửa, xóa"
type: policy
audience: [TUTOR]
features: [certificate]
routes: [/tutor/certificates, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: CertificateController (upload, create, update, delete) -> CertificateService (tạo PENDING; sửa -> PENDING; không sửa được VERIFIED; xoá ở mọi trạng thái)"
  - "backend: AdminCertificateService.approve (không duyệt chứng chỉ đã quá ngày hết hạn), reject (lý do tối đa 500 ký tự)"
  - "backend: CertificateController GET /{tutorId}/certificates (chỉ trả chứng chỉ VERIFIED cho người khác xem)"
  - "backend: TutorRecommendationScoreService.verificationScore (có chứng chỉ đã duyệt được cộng điểm)"
  - "enum: CertificateStatus; NotificationEventType CERTIFICATE_APPROVED, CERTIFICATE_REJECTED"
  - "enum: CertificateStatus (PENDING, VERIFIED, REJECTED, EXPIRED)"
  - "frontend: utils/tutorDisplay.ts tutorStatusLabel (Đang chờ, Đã xác minh, Bị từ chối, Đã hết hạn)"
  - "backend: CertificateService; AdminCertificateService"
error_codes: [VERIFIED_CERTIFICATE_IMMUTABLE, CERTIFICATE_EXPIRED, CERTIFICATE_NOT_FOUND]
keywords: [chứng chỉ, duyệt chứng chỉ, chứng chỉ hết hạn, sửa chứng chỉ, xóa chứng chỉ, chung chi, duyet chung chi, het han, xoa chung chi, certificate, trạng thái chứng chỉ, đang chờ, đã xác minh, bị từ chối, đã hết hạn, trang thai chung chi, da xac minh, bi tu choi, da het han]
todo:
  - "Trạng thái Đã hết hạn chưa được hệ thống tự đặt khi chứng chỉ qua ngày hết hạn. Chứng chỉ đã duyệt mà hết hạn thì xử lý thế nào?"
  - "Gia sư muốn cập nhật chứng chỉ đã duyệt (ví dụ điểm mới) thì làm thế nào ngoài việc xóa và thêm lại?"
  - "Tiêu chí duyệt chứng chỉ và thời gian duyệt là bao lâu?"
  - "Hệ thống chưa tự đặt trạng thái Đã hết hạn; admin xác nhận chứng chỉ chuyển sang trạng thái này khi nào."
---

# Quy định và trạng thái chứng chỉ: duyệt, hết hạn, sửa, xóa

## Chứng chỉ dùng để làm gì

Chứng chỉ giúp học viên tin tưởng hơn khi chọn bạn. Chỉ chứng chỉ đã được duyệt mới hiện với người khác. Gia sư có ít nhất một chứng chỉ đã duyệt được cộng điểm trong điểm đề xuất.

## Xét duyệt chứng chỉ

Chứng chỉ mới thêm ở trạng thái chờ duyệt. Nhân viên chăm sóc khách hàng xem tài liệu và:

- Duyệt: chứng chỉ chuyển sang "Đã xác minh". Bạn nhận thông báo "Certificate approved".
- Từ chối: chứng chỉ chuyển sang "Bị từ chối". Bạn nhận thông báo "Certificate rejected" kèm lý do.

Chứng chỉ có ngày hết hạn đã qua thì không được duyệt.

## Sửa chứng chỉ

Bạn sửa được chứng chỉ đang chờ duyệt hoặc bị từ chối. Sau khi sửa, chứng chỉ quay về chờ duyệt. Chứng chỉ đã xác minh thì không sửa được.

## Xóa chứng chỉ

Bạn xóa được chứng chỉ ở mọi trạng thái, kể cả đã xác minh. Chứng chỉ đã xóa không khôi phục được. Nếu xóa chứng chỉ đã xác minh cuối cùng, bạn mất phần điểm cộng cho chứng chỉ trong điểm đề xuất.

## Ngày hết hạn của chứng chỉ

Khi thêm chứng chỉ, bạn nhập ngày hết hạn nếu chứng chỉ có thời hạn. Chứng chỉ đã quá ngày hết hạn không được duyệt. Khi chứng chỉ hết hạn, bạn nên thêm chứng chỉ mới còn hiệu lực.

## Đang chờ

Chứng chỉ "Đang chờ" vừa được thêm hoặc vừa sửa, đang chờ nhân viên chăm sóc khách hàng xét duyệt. Bạn vẫn sửa hoặc xóa được. Bước tiếp theo: chờ thông báo kết quả.

## Đã xác minh

Chứng chỉ "Đã xác minh" đã được nhân viên chăm sóc khách hàng duyệt và hiện với học viên. Chứng chỉ không sửa được nữa, chỉ xóa được.

## Bị từ chối

Chứng chỉ "Bị từ chối" không được duyệt, kèm lý do trong thông báo. Bước tiếp theo: bấm **Chỉnh sửa**, sửa theo lý do rồi **Lưu thay đổi** để gửi duyệt lại; hoặc xóa chứng chỉ.

## Đã hết hạn

Chứng chỉ "Đã hết hạn" đã qua ngày hết hạn. Chứng chỉ quá hạn không được duyệt. Bước tiếp theo: thêm chứng chỉ mới còn hiệu lực.
