---
id: chung-chi-nop
title: Cách nộp chứng chỉ
type: guide
audience: [TUTOR]
features: [certificate]
routes: [/tutor/certificates]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/tutor/TutorCertificatesPage.tsx (Thêm chứng chỉ, Tên chứng chỉ, Đơn vị cấp, Ngày cấp, Ngày hết hạn, Mô tả, Hoặc đường dẫn chứng chỉ, Tải lên và gửi, Chỉnh sửa, Lưu thay đổi, Hủy chỉnh sửa, Xem tài liệu, Lọc theo trạng thái)"
  - "backend: CertificateController POST /me/certificates/upload (multipart), POST/PUT/DELETE /me/certificates"
  - "backend: CertificateFileStorageService (PDF, JPEG, PNG; tối đa 10 MB)"
  - "backend: dto/request/CertificateRequest (tên bắt buộc, tối đa 255 ký tự; đơn vị cấp, mô tả tối đa 255 ký tự)"
error_codes: [CERTIFICATE_FILE_REQUIRED, CERTIFICATE_FILE_TOO_LARGE, UNSUPPORTED_CERTIFICATE_FILE_TYPE, VERIFIED_CERTIFICATE_IMMUTABLE]
keywords: [nộp chứng chỉ, tải chứng chỉ, thêm chứng chỉ, upload chứng chỉ, nop chung chi, tai chung chi, them chung chi]
todo: []
---

# Cách nộp chứng chỉ

## Các bước thêm chứng chỉ

1. Trong menu, chọn **Chứng chỉ**.
2. Ở khung **Thêm chứng chỉ**, nhập **Tên chứng chỉ**, ví dụ "IELTS 8.0".
3. Nếu có, nhập **Đơn vị cấp**, **Ngày cấp**, **Ngày hết hạn**, **Mô tả**.
4. Chọn tệp chứng chỉ trên máy, hoặc dán đường dẫn vào ô **Hoặc đường dẫn chứng chỉ**.
5. Bấm **Tải lên và gửi**.

Thành công, trang báo "Đã tải chứng chỉ lên và gửi để xét duyệt."

## Yêu cầu với tệp chứng chỉ

- Tệp phải là PDF, JPEG hoặc PNG.
- Dung lượng tối đa 10 MB.
- Phải có tệp hoặc đường dẫn; thiếu cả hai, trang báo "Vui lòng tải tài liệu lên hoặc nhập đường dẫn chứng chỉ."
- **Tên chứng chỉ** bắt buộc, tối đa 255 ký tự.

## Xem và lọc chứng chỉ đã nộp

Mục **Chứng chỉ của bạn** liệt kê các chứng chỉ đã gửi kèm trạng thái. Dùng **Lọc theo trạng thái** để xem theo từng trạng thái. Bấm **Xem tài liệu ↗** để mở tệp; nếu không mở được, hãy cho phép cửa sổ bật lên.

## Sửa chứng chỉ

1. Bấm **Chỉnh sửa** ở chứng chỉ cần sửa.
2. Sửa thông tin, hoặc chọn **Thay tệp chứng chỉ**.
3. Bấm **Lưu thay đổi**. Muốn bỏ, bấm **Hủy chỉnh sửa**.

Trang báo "Đã cập nhật chứng chỉ và gửi để xét duyệt." Chứng chỉ đã xác minh không sửa được.

## Xóa chứng chỉ

1. Bấm biểu tượng xóa ở chứng chỉ.
2. Xác nhận ở hộp thoại "Bạn có chắc muốn xóa chứng chỉ … không?".

Trang báo "Đã xóa chứng chỉ."
