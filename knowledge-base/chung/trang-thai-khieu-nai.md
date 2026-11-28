---
id: trang-thai-khieu-nai
title: Giải nghĩa trạng thái khiếu nại
type: glossary
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: ComplaintStatus (PENDING, IN_REVIEW, AWAITING_EVIDENCE, RESOLVED, REJECTED, CANCELLED)"
  - "frontend: pages/admin/complaintFormat.ts complaintStatusMeta (Chờ tiếp nhận, Đang xử lý, Chờ bằng chứng, Đã giải quyết, Đã từ chối, Đã huỷ) — chưa có trang khiếu nại cho người dùng"
  - "backend: ComplaintService; AdminComplaintService"
error_codes: [INVALID_COMPLAINT_STATUS_TRANSITION]
keywords: [trạng thái khiếu nại, chờ tiếp nhận, đang xử lý, chờ bằng chứng, đã giải quyết, trang thai khieu nai, cho tiep nhan, cho bang chung, da giai quyet]
todo:
  - "Nhãn lấy từ trang quản trị vì trang người dùng chưa có; admin xác nhận dùng cùng nhãn cho học viên và gia sư."
  - "Khiếu nại được tiếp nhận và kết luận trong bao lâu?"
---

# Giải nghĩa trạng thái khiếu nại

## Chờ tiếp nhận

Khiếu nại "Chờ tiếp nhận" vừa được gửi, chưa có nhân viên nhận xử lý. Bên bị khiếu nại chưa thấy khiếu nại này. Người gửi còn sửa hoặc hủy được. Bước tiếp theo: nhân viên chăm sóc khách hàng tiếp nhận.

## Đang xử lý

Khiếu nại "Đang xử lý" đã có nhân viên chăm sóc khách hàng phụ trách. Bên bị khiếu nại nhận thông báo và được gửi một phản hồi. Người gửi không sửa hay hủy được nữa. Bước tiếp theo: nhân viên kết luận hoặc yêu cầu bổ sung bằng chứng.

## Chờ bằng chứng

Khiếu nại "Chờ bằng chứng" đang chờ người gửi khiếu nại bổ sung bằng chứng theo yêu cầu của nhân viên. Bước tiếp theo: người gửi gửi từ 1 đến 5 bằng chứng; khiếu nại quay lại "Đang xử lý". Bên bị khiếu nại vẫn phản hồi được nếu chưa phản hồi.

## Đã giải quyết

Khiếu nại "Đã giải quyết" được nhân viên chấp nhận, có ghi bên có lỗi và cách giải quyết. Đây là trạng thái cuối.

## Đã từ chối

Khiếu nại "Đã từ chối" không được chấp nhận, có ghi lý do. Đây là trạng thái cuối.

## Đã huỷ

Khiếu nại "Đã huỷ" do người gửi hủy khi còn chờ tiếp nhận. Bên bị khiếu nại không thấy khiếu nại này.

## Khiếu nại chưa có kết luận

Ba trạng thái "Chờ tiếp nhận", "Đang xử lý" và "Chờ bằng chứng" là khiếu nại chưa có kết luận. Trong thời gian này, buổi học chờ xác nhận của hợp đồng bị giữ lại và yêu cầu hoàn tiền của hợp đồng chưa được kết luận.
