---
id: hop-dong-soan-va-sua
title: Ai soạn hợp đồng và được sửa những gì
type: policy
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId, /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: StudentContractController POST (học viên tạo từ đề nghị dạy đã chấp nhận); TutorContractController POST (gia sư tạo từ đơn đăng ký học đã chấp nhận)"
  - "backend: ContractService.createFromTutorStudentRequest, createFromStudentTutorRequest, update, updateStudentCreatedContract, updateTutorCreatedContract, updateRenewalTerms, normalizeTerms, validatePrice, validateDateRange, ensureContractableRequest"
  - "frontend: components/student/StudentContractTermsForm.tsx, StudentContractEditForm.tsx"
error_codes: [DUPLICATE_CONTRACT, INVALID_CONTRACT_SOURCE_STATUS, INVALID_CONTRACT_APPLICATION_STATUS, CONTRACT_CREATOR_REQUIRED, CONTRACT_FIELD_NOT_EDITABLE, INVALID_CONTRACT_PRICE, INVALID_CONTRACT_DATE_RANGE, INVALID_CONTRACT_SCHEDULE, INVALID_TOTAL_LESSONS]
keywords: [soạn hợp đồng, tạo hợp đồng, sửa hợp đồng, điều khoản hợp đồng, chu kỳ thanh toán, soan hop dong, tao hop dong, sua hop dong]
todo:
  - "Giá trong hợp đồng là giá cho mỗi kỳ thanh toán (mỗi buổi, mỗi tuần, mỗi tháng hoặc cả gói). Admin xác nhận cách diễn giải này với người dùng."
---

# Ai soạn hợp đồng và được sửa những gì

## Ai soạn hợp đồng

Người soạn hợp đồng là bên đã chấp nhận lời đề nghị:

- Học viên chấp nhận đề nghị dạy của gia sư thì **học viên soạn hợp đồng** và gửi gia sư ký.
- Gia sư chấp nhận đơn đăng ký học của học viên thì **gia sư soạn hợp đồng** và gửi học viên ký.

Chỉ soạn được hợp đồng khi đề nghị hoặc đơn đã được chấp nhận và tin còn đang mở hoặc đã ghép. Mỗi đề nghị hay đơn chỉ có một hợp đồng còn hiệu lực; nếu hợp đồng trước bị hủy, bạn soạn lại được.

## Hợp đồng gồm những điều khoản nào

Hợp đồng có các thông tin lấy sẵn từ đề nghị hoặc đơn: môn học, lớp, hình thức dạy. Người soạn điền thêm:

- **Giá**: không được âm.
- **Chu kỳ thanh toán**: **Mỗi buổi**, **Hàng tuần**, **Hàng tháng** hoặc **Theo gói**.
- **Lịch học**: một hoặc nhiều khung giờ trong tuần, giờ kết thúc phải sau giờ bắt đầu.
- **Ngày bắt đầu** và **Ngày kết thúc**: ngày kết thúc phải sau ngày bắt đầu.

Tổng số buổi được tính tự động từ lịch học trong khoảng ngày bắt đầu đến ngày kết thúc. Lịch không có buổi nào trong khoảng đó thì không tạo được hợp đồng.

## Người soạn sửa được gì khi hợp đồng đang chờ

Chỉ người soạn mới sửa được hợp đồng, và chỉ khi hợp đồng đang chờ ký:

- Hợp đồng do học viên soạn: sửa được **Giá** và **Lịch học**.
- Hợp đồng do gia sư soạn: sửa được giá, lịch học và lớp (lớp phải thuộc tin nhận dạy).
- Hợp đồng gia hạn: sửa được giá, chu kỳ thanh toán, số buổi, ngày bắt đầu, ngày kết thúc và lịch học, nhưng không đổi được lớp.

Chu kỳ thanh toán, ngày bắt đầu và ngày kết thúc của hợp đồng mới (không phải gia hạn) không sửa được sau khi gửi. Muốn đổi, người soạn hủy hợp đồng rồi soạn lại.

## Bên nhận hợp đồng làm gì

Bên nhận không sửa được hợp đồng. Bên nhận chỉ ký hoặc từ chối. Sau khi hợp đồng đã ký, không bên nào sửa được nữa.
