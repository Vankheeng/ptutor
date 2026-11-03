---
id: loi-hop-dong
title: Lỗi thường gặp với hợp đồng
type: error
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId, /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: ContractService (create*, update, sign, reject, cancel, renew, validate*, ensure*)"
  - "frontend: components/student/StudentContractTermsForm.tsx (kiểm tra trên trang bằng tiếng Việt); StudentContractDetailPage.tsx (hiển thị câu của máy chủ)"
error_codes: [DUPLICATE_CONTRACT, CONTRACT_CREATOR_REQUIRED, CONTRACT_COUNTERPARTY_REQUIRED, CONTRACT_FIELD_NOT_EDITABLE, CONTRACT_EXPIRED, DUPLICATE_CONTRACT_RENEWAL, INVALID_CONTRACT_DATE_RANGE, INVALID_CONTRACT_SCHEDULE, INVALID_CONTRACT_PRICE, INVALID_CONTRACT_STATUS_TRANSITION, INVALID_CONTRACT_SOURCE_STATUS, CONTRACT_SUBJECT_REQUIRED]
keywords: [lỗi hợp đồng, không ký được hợp đồng, không tạo được hợp đồng, loi hop dong, khong ky duoc, khong tao duoc hop dong]
todo: []
---

# Lỗi thường gặp với hợp đồng

## "Giá không được là số âm" / "Ngày kết thúc phải sau ngày bắt đầu"

Đây là lỗi khi điền điều khoản hợp đồng. Cách khắc phục: nhập **Giá** từ 0 trở lên, chọn **Ngày kết thúc** sau **Ngày bắt đầu**.

## "Vui lòng thêm ít nhất một khung giờ đầy đủ"

Câu này nghĩa là **Lịch học** chưa có khung giờ nào đủ ngày, giờ bắt đầu và giờ kết thúc. Cách khắc phục: bấm **Thêm khung giờ** và chọn đủ thông tin.

## "Lịch đã chọn không có buổi học nào trong khoảng thời gian này"

Câu này nghĩa là các thứ trong lịch học không rơi vào ngày nào giữa ngày bắt đầu và ngày kết thúc. Cách khắc phục: kéo dài khoảng ngày hoặc đổi thứ trong lịch học.

## "A non-cancelled contract already exists for this source"

Câu này nghĩa là đề nghị hoặc đơn này đã có một hợp đồng chưa bị hủy. Cách khắc phục: mở trang **Hợp đồng** để xem hợp đồng đã có.

## "Only the contract counterparty can perform this action"

Câu này nghĩa là chỉ bên nhận mới ký hoặc từ chối được. Người soạn không tự ký hợp đồng của mình.

## "Only the contract creator can update a pending contract" hoặc "… cancel a pending contract"

Câu này nghĩa là chỉ người soạn mới sửa hoặc hủy được hợp đồng đang chờ. Nếu bạn là bên nhận và không đồng ý, hãy dùng **Từ chối hợp đồng**.

## "One or more contract fields cannot be changed by this creator"

Câu này nghĩa là bạn đang sửa điều khoản không được phép sửa, ví dụ chu kỳ thanh toán hay ngày bắt đầu. Cách khắc phục: hủy hợp đồng rồi soạn lại với điều khoản mới.

## "Contract has reached its end date"

Câu này nghĩa là hợp đồng đã tới ngày kết thúc nên không ký hay gia hạn được. Cách khắc phục: soạn hợp đồng mới với ngày kết thúc sau hôm nay.

## "A pending or effective renewal already exists for this contract"

Câu này nghĩa là hợp đồng đã có một bản gia hạn đang chờ ký hoặc đã có hiệu lực. Cách khắc phục: xử lý bản gia hạn đó trước.

## "Renewal start date must not be before the previous contract end date"

Câu này nghĩa là ngày bắt đầu gia hạn sớm hơn ngày kết thúc hợp đồng cũ. Cách khắc phục: chọn ngày bắt đầu từ ngày kết thúc hợp đồng cũ trở đi.

## "Only PENDING contracts can be processed"

Câu này nghĩa là hợp đồng không còn ở trạng thái "Đang chờ", nên không ký, từ chối hay hủy được nữa. Cách khắc phục: tải lại trang để xem trạng thái mới.
