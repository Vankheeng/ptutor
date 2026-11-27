---
id: tai-khoan-bi-khoa
title: "Tài khoản bị tạm khóa hoặc khóa vĩnh viễn"
type: policy
audience: [STUDENT, TUTOR]
features: [account]
routes: [/login, /student/notifications, /tutor/notifications]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: AccountSuspensionService.suspend/reactivate (lý do 10–1000 ký tự; thu hồi mọi phiên đăng nhập)"
  - "backend: UserAccessService.refreshAccessState, reactivateExpiredAccounts (tự mở khóa khi hết hạn)"
  - "backend: AccountSuspensionScheduler (app.account-suspension.expiration-interval-ms, mặc định 60000 ms)"
  - "backend: AuthService.accountUnavailable; security/AccountStatusFilter"
  - "backend: TutorRecommendationScoreService (trừ điểm theo số lần bị khóa)"
  - "enum: UserStatus, SuspensionType; NotificationEventType ACCOUNT_SUSPENDED, ACCOUNT_REACTIVATED"
  - "enum: UserStatus (ACTIVE, INACTIVE, BLOCKED); SuspensionType (TEMPORARY, PERMANENT)"
  - "frontend: utils/tutorDisplay.ts statusLabels (Đang hoạt động, Ngừng hoạt động, Đã khóa)"
  - "backend: AuthService.accountUnavailable; AccountStatusFilter"
error_codes: [ACCOUNT_TEMPORARILY_SUSPENDED, ACCOUNT_PERMANENTLY_SUSPENDED, ACCOUNT_SUSPENDED, ACCOUNT_INACTIVE, USER_INACTIVE]
keywords: [tài khoản bị khóa, tạm khóa, khóa vĩnh viễn, mở khóa tài khoản, bị chặn, tai khoan bi khoa, tam khoa, khoa vinh vien, mo khoa, account suspended, trạng thái tài khoản, đang hoạt động, ngừng hoạt động, đã khóa, trang thai tai khoan, dang hoat dong, ngung hoat dong, da khoa]
todo: []
---

# Tài khoản bị tạm khóa hoặc khóa vĩnh viễn

## Ai khóa tài khoản và có những kiểu khóa nào

Nhân viên chăm sóc khách hàng hoặc quản trị viên PTutor có thể khóa tài khoản học viên hoặc gia sư. Mỗi lần khóa phải ghi lý do. Có hai kiểu khóa:

- **Tạm khóa**: có thời điểm kết thúc. Hết thời điểm đó, tài khoản tự mở lại.
- **Khóa vĩnh viễn**: không có thời điểm kết thúc. Chỉ nhân viên hoặc quản trị viên mở lại được.

## Điều gì xảy ra khi tài khoản bị khóa

Khi tài khoản bị khóa:

- PTutor đăng xuất tài khoản khỏi mọi thiết bị ngay lập tức.
- Bạn không đăng nhập được. Trang đăng nhập hiện câu tiếng Anh cho biết tài khoản bị khóa, thời điểm hết khóa (với tạm khóa) và lý do.
- Bạn nhận một thông báo "Account suspended" trong mục thông báo, ghi kiểu khóa và lý do.
- Trên giao diện, trạng thái tài khoản hiện là "Đã khóa".
- Mọi thao tác khác đều bị từ chối, kể cả khai báo tài khoản ngân hàng và rút tiền.

## Khi nào tài khoản được mở lại

Với tạm khóa, PTutor kiểm tra mỗi phút và tự mở tài khoản khi đã qua thời điểm kết thúc. Bạn cũng được mở ngay nếu đăng nhập sau thời điểm đó. Khi tài khoản được mở lại, bạn nhận thông báo "Account reactivated".

Với khóa vĩnh viễn, tài khoản chỉ được mở lại khi nhân viên hoặc quản trị viên xem xét và mở khóa, kèm lý do.

## Ảnh hưởng tới gia sư

Với gia sư, mỗi lần bị khóa làm giảm điểm đề xuất của gia sư, tối đa trừ 15 điểm. Phần trừ này vẫn tính sau khi tài khoản đã được mở lại.

## Thời điểm hết khóa hiển thị theo giờ quốc tế

Câu báo lỗi khi đăng nhập ghi thời điểm hết tạm khóa theo giờ quốc tế (có chữ UTC). Giờ Việt Nam nhanh hơn 7 tiếng. Ví dụ "until 2026-11-05T03:00 UTC" nghĩa là tài khoản mở lại lúc 10 giờ sáng ngày 05/11/2026 giờ Việt Nam.

## Đang hoạt động

Tài khoản "Đang hoạt động" dùng được đầy đủ các chức năng của vai trò. Đây là trạng thái của mọi tài khoản ngay sau khi đăng ký. Bạn không cần làm gì thêm.

## Ngừng hoạt động

Tài khoản "Ngừng hoạt động" không đăng nhập được và mọi thao tác bị từ chối. Khi đăng nhập, bạn thấy câu tiếng Anh "Account is inactive". Bạn cần liên hệ PTutor để được hỗ trợ.

## Đã khóa

Tài khoản "Đã khóa" bị nhân viên hoặc quản trị viên PTutor khóa, kèm lý do. Tài khoản không đăng nhập được. Trạng thái này có hai kiểu:

### Tạm khóa

Tài khoản tạm khóa có thời điểm kết thúc. Khi qua thời điểm đó, tài khoản tự trở lại "Đang hoạt động" và bạn nhận thông báo. Bạn chỉ cần chờ.

### Khóa vĩnh viễn

Tài khoản khóa vĩnh viễn không tự mở lại. Chỉ nhân viên hoặc quản trị viên mở khóa được sau khi xem xét. Chi tiết xem mục "Khi nào tài khoản được mở lại" trong tài liệu này.

## Căn cứ khóa và đề nghị xem xét

Mã nguồn yêu cầu nhân viên ghi lý do nhưng không tự phân loại hành vi thành tạm khóa hay khóa không thời hạn. Dự thảo đề xuất tạm khóa với spam, quấy rối hoặc thông tin sai có thể khắc phục; khóa không thời hạn với giả mạo danh tính, gian lận thanh toán hoặc vi phạm nghiêm trọng có chứng cứ. Người dùng nên được quyền yêu cầu xem xét trong 7 ngày, nhưng PTutor cần công bố một kênh liên hệ cho người không đăng nhập được trước khi áp dụng mốc này. Chatbot không được hứa tự mở khóa theo khiếu nại.

## Hợp đồng và tiền khi tài khoản bị khóa

Việc khóa tài khoản không tự xóa hợp đồng, buổi học hoặc số dư ví. Người dùng bị chặn đăng nhập và thao tác; nhân viên phải đối soát các nghĩa vụ còn mở theo hợp đồng. Trạng thái **Ngừng hoạt động** có trong mô hình dữ liệu nhưng mã nguồn hiện chưa có thao tác nghiệp vụ đưa tài khoản vào trạng thái này. Nhãn **Tạm khóa** và **Khóa vĩnh viễn** ở đây giải thích hai cách đặt thời hạn khóa; giao diện người dùng chủ yếu hiển thị **Đã khóa**.
