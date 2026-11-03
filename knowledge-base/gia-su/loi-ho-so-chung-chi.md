---
id: loi-ho-so-chung-chi
title: Lỗi thường gặp với hồ sơ và chứng chỉ
type: error
audience: [TUTOR]
features: [tutor-profile, certificate]
routes: [/tutor/profile, /tutor/certificates]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: CertificateFileStorageService (CERTIFICATE_FILE_TOO_LARGE, UNSUPPORTED_CERTIFICATE_FILE_TYPE, CERTIFICATE_STORAGE_*)"
  - "backend: CertificateService (VERIFIED_CERTIFICATE_IMMUTABLE); TutorProfileService (ADDRESS_IDS_REQUIRED_TOGETHER, DISTRICT_PROVINCE_MISMATCH)"
  - "frontend: pages/tutor/TutorCertificatesPage.tsx, TutorProfilePage.tsx (câu tiếng Việt trên trang; còn lại hiển thị câu của máy chủ)"
error_codes: [CERTIFICATE_FILE_TOO_LARGE, UNSUPPORTED_CERTIFICATE_FILE_TYPE, CERTIFICATE_FILE_REQUIRED, VERIFIED_CERTIFICATE_IMMUTABLE, CERTIFICATE_STORAGE_UNAVAILABLE, CERTIFICATE_STORAGE_UNREACHABLE, CERTIFICATE_STORAGE_REJECTED, ADDRESS_IDS_REQUIRED_TOGETHER]
keywords: [lỗi chứng chỉ, không tải được chứng chỉ, tệp quá lớn, lỗi hồ sơ, loi chung chi, tep qua lon, loi ho so]
todo: []
---

# Lỗi thường gặp với hồ sơ và chứng chỉ

## "Certificate files must be 10 MB or smaller"

Câu này nghĩa là tệp chứng chỉ lớn hơn 10 MB. Cách khắc phục: nén hoặc chụp lại tệp cho nhỏ hơn 10 MB, hoặc dán đường dẫn vào ô **Hoặc đường dẫn chứng chỉ**.

## "Certificate files must be PDF, JPEG or PNG"

Câu này nghĩa là định dạng tệp không được hỗ trợ. Cách khắc phục: chuyển tệp sang PDF, JPEG hoặc PNG.

## "Vui lòng tải tài liệu lên hoặc nhập đường dẫn chứng chỉ."

Câu này nghĩa là bạn chưa chọn tệp và chưa nhập đường dẫn. Cách khắc phục: chọn tệp hoặc dán đường dẫn rồi bấm lại.

## Chứng chỉ đã xác minh không sửa được

Khi sửa chứng chỉ đã xác minh, hệ thống từ chối. Cách khắc phục: chứng chỉ đã duyệt giữ nguyên; nếu cần thay, xóa chứng chỉ cũ rồi thêm chứng chỉ mới.

## Lỗi kho lưu trữ tệp chứng chỉ

Các câu tiếng Anh như "Certificate file storage is not configured", "Certificate storage could not be reached…" hay "Object storage rejected the upload…" nghĩa là PTutor đang gặp sự cố lưu tệp, không phải lỗi của bạn. Cách khắc phục: thử lại sau, hoặc dùng đường dẫn chứng chỉ; nếu vẫn lỗi, báo PTutor.

## "Không tìm thấy đường dẫn chứng chỉ có thể xem…"

Câu này nghĩa là tệp chứng chỉ hiện không mở được. Cách khắc phục: thử lại sau; nếu vẫn lỗi, báo PTutor.

## "Vui lòng chọn cả tỉnh/thành phố và quận/huyện, hoặc để trống cả hai."

Câu này hiện ở trang hồ sơ, nghĩa là bạn chỉ chọn một trong hai ô địa chỉ. Cách khắc phục: chọn đủ cả hai hoặc bỏ trống cả hai.

## "Vui lòng chọn ảnh JPG hoặc PNG."

Câu này nghĩa là ảnh đại diện không đúng định dạng. Cách khắc phục: chọn ảnh JPG hoặc PNG, tối đa 10 MB.

## "Không thể lưu hồ sơ. Vui lòng thử lại."

Câu này nghĩa là hồ sơ chưa lưu được. Cách khắc phục: kiểm tra các ô bắt buộc không để toàn khoảng trắng, số năm kinh nghiệm không âm, rồi thử lại.
