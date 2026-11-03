# Danh mục màn hình (học viên, gia sư, công khai)

Nguồn: `frontend/src/App.tsx` và `frontend/src/pages/` trên nhánh `dev`, commit `27f6eeb09815385bf92d3d552b0f8f705070a23c`.
Tên trang, nút, nhãn ghi đúng như trong code, kể cả chỗ đang là tiếng Anh. Khu vực `/admin/*` (nhân viên, quản trị) cố ý không liệt kê vì trợ lý không phục vụ vai trò này.

## Menu

| Vai trò | Mục menu (nhãn) | Route |
|---|---|---|
| Học viên | Dashboard | /student/dashboard |
| Học viên | My Studying Requests | /student/studying-requests |
| Học viên | Browse Teaching Requests | /student/teaching-requests |
| Học viên | My requests to studying | /student/applications |
| Học viên | Hợp đồng | /student/contracts |
| Học viên | Yêu cầu hoàn tiền | /student/refund-requests |
| Học viên | Notifications | /student/notifications |
| Học viên | Wallet & Payments | /student/wallet |
| Học viên | My Profile | /student/profile |
| Gia sư | Tổng quan | /tutor/dashboard |
| Gia sư | Yêu cầu dạy của tôi | /tutor/sections/teaching-requests (Sắp ra mắt) |
| Gia sư | Đề xuất giảng dạy | /tutor/proposals |
| Gia sư | Hợp đồng | /tutor/sections/contracts (Sắp ra mắt) |
| Gia sư | Yêu cầu hoàn tiền | /tutor/refund-requests |
| Gia sư | Lịch dạy | /tutor/sections/calendar (Sắp ra mắt) |
| Gia sư | Thông báo | /tutor/notifications |
| Gia sư | Ví tiền | /tutor/wallet |
| Gia sư | Chứng chỉ | /tutor/certificates |
| Gia sư | Hồ sơ của tôi | /tutor/profile |

## Màn hình công khai

| Route | Tên trang | Vai trò | Nút, ô nhập chính | Điều kiện ẩn / khóa |
|---|---|---|---|---|
| / , /public | Trang chủ Ptutor | Mọi người | Ô "Bạn muốn học môn gì?", "Bạn đang học lớp nào?"; mục BÀI ĐĂNG GIA SƯ; "Học viên nói gì về Ptutor?"; liên kết Hỗ trợ: "Liên hệ Ptutor", "Trung tâm trợ giúp", "Chatbot hỗ trợ" | Liên kết Hỗ trợ trỏ về đầu trang (#top) |
| /login | Đăng nhập Ptutor | Khách | Email, Mật khẩu, **Đăng nhập**, liên kết **Đăng ký miễn phí**, trang **Quên mật khẩu?** | Chỉ khách chưa đăng nhập vào được |
| /register | Tạo tài khoản | Khách | **Tôi là học viên** / **Tôi là gia sư**; Họ, Tên, Email, Số CCCD, Số điện thoại, Mật khẩu, Xác nhận mật khẩu, Ngày sinh, Giới tính (Nam/Nữ/Khác), Tỉnh/thành phố, Quận/huyện; **Đăng ký tài khoản** | Quận/huyện khóa tới khi chọn tỉnh |
| /forgot-password | Quên mật khẩu? | Khách | Bước 1: Email, **Gửi mã OTP**. Bước 2: Mã OTP, **Xác thực OTP**, **Gửi lại OTP**, **Đổi email**. Bước 3: Mật khẩu mới, Xác nhận mật khẩu mới, **Đặt lại mật khẩu** | **Xác thực OTP** khóa khi hết 5 phút ("Mã OTP đã hết hạn") |

## Màn hình học viên

| Route | Tên trang | Nút, ô nhập chính | Điều kiện ẩn / khóa |
|---|---|---|---|
| /student, /student/dashboard | YOUR LEARNING HOME | Active contracts, My studying requests, Pending applications, Unread notifications | — |
| /student/studying-requests | My Studying Requests | **Create request**; lọc All statuses / trạng thái (hiện mã tiếng Anh viết thường); Newest first / Oldest first; menu thao tác: **Edit information**, **Pay to publish**, **Close request**, **Reopen request**, **Cancel request** | Edit information: tin draft/open. Pay to publish: draft. Close request: open. Reopen request: closed. Cancel request: draft/open/matched/closed |
| /student/studying-requests/new | Create a studying request | Title, Description, Subject (Other / custom subject), Custom subject / note, Grade, Learning mode (Online/In person), Province, District, Detail address, Minimum price (VND), Maximum price (VND), Learning goals, Preferred schedule, Availability (optional); **Cancel**, **Create draft**, **Create & pay**; khi lỗi thanh toán: **Retry payment** | — |
| /student/studying-requests/:requestId | Chi tiết tin | Title, Status, Description, Subject, Grade, Mode, Budget, Preferred schedule, Address, Learning goals, Availability; mục **Đề nghị từ gia sư**: **Chấp nhận**, **Từ chối**, **Tạo hợp đồng**, **Xem** | Chấp nhận/Từ chối: đề nghị đang chờ và tin open. Tạo hợp đồng: đề nghị đã chấp nhận và tin open/matched |
| /student/studying-requests/:requestId/edit | Edit studying request | Như form tạo; **Save changes** | Chỉ khi tin draft/open |
| /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId | Chi tiết đề nghị từ gia sư | Giá đề nghị, Hình thức dạy, Lịch học mong muốn, Lời nhắn, Gửi lúc, Cập nhật lúc | — |
| /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new | Tạo hợp đồng | Thông tin học viên, Thông tin gia sư, Môn học, Lớp, Hình thức dạy (cố định); Giá, Chu kỳ thanh toán (Mỗi buổi/Hàng tuần/Hàng tháng/Theo gói), Lịch học (Ngày, Từ, Đến, **Thêm khung giờ**, **Xóa**), Ngày bắt đầu, Ngày kết thúc; **Hủy**, **Tạo hợp đồng** | Nút tạo khóa khi thiếu ngày hoặc khung giờ |
| /student/teaching-requests | Browse Teaching Requests | **Apply to study**; form: Grade ID, Proposed price, Learning mode, Preferred schedule, Message; **Cancel**, **Send application** | Chỉ hiện tin nhận dạy open |
| /student/applications | My requests to studying | Thẻ lọc All/…; **Cancel**; **Previous**, **Next** | Cancel: đơn pending |
| /student/contracts | Hợp đồng | Lọc Tất cả, Đang chờ, Đang hoạt động, Đã hoàn tất, Đã hủy; **Xem hợp đồng**; **Trang trước**, **Trang sau** | — |
| /student/contracts/:contractId | Chi tiết hợp đồng | **Ký hợp đồng**, **Từ chối hợp đồng**, **Hủy hợp đồng**; form sửa (Giá, Lịch học, **Thêm khung giờ**, **Lưu thay đổi**); **Đề xuất gia hạn** → **Gửi đề xuất gia hạn**; **Các kỳ thanh toán** (Ngày đến hạn, Số tiền, Trạng thái, **Thanh toán**); liên kết **Buổi học và hoàn tiền** | Ký/Từ chối: hợp đồng Đang chờ và là bên nhận. Hủy, sửa: Đang chờ và là người tạo. Gia hạn: Đang hoạt động. Thanh toán: kỳ Đang chờ |
| /student/contracts/:contractId/lessons | Buổi học | **Yêu cầu hoàn tiền**, **Xem yêu cầu hoàn tiền**; mỗi buổi: Hạn xác nhận, **Xác nhận đã học**; nhãn Sắp diễn ra, Chờ xác nhận, Đã học, Đã huỷ, Đang yêu cầu hoàn tiền | Xác nhận đã học: buổi Chờ xác nhận và không bị giữ |
| /student/contracts/:contractId/refund-request | Yêu cầu hoàn tiền (tạo) | Chọn buổi; nguyên nhân **Gia sư không dạy** / **Tôi nghỉ buổi này**; mô tả; **Minh chứng** (đường dẫn http/https); **Huỷ**, **Gửi yêu cầu** | Chỉ liệt kê buổi Chờ xác nhận |
| /student/refund-requests | Yêu cầu hoàn tiền | Danh sách; **Trước**, **Sau** | — |
| /student/refund-requests/:refundRequestId | Yêu cầu hoàn tiền (chi tiết) | Tiến trình: Đã gửi yêu cầu, Kế toán xem xét, Hoàn vào ví hoặc từ chối; Buổi học bị phản đối; Phản hồi của gia sư; **Huỷ yêu cầu** | Huỷ yêu cầu: trạng thái Chờ tiếp nhận |
| /student/notifications | Notifications | Lọc All / unread / read; số unread; **Mark all as read**; bấm thông báo để đánh dấu đã đọc | — |
| /student/wallet | Wallet & Payments | Available balance; Top up wallet: Amount (VND), **Open VNPay**; Transactions (20 giao dịch gần nhất) | Open VNPay khóa khi ô số tiền trống |
| /student/profile | My Profile | First name, Last name, Email (chỉ xem), Phone, Date of birth, Gender, Province, District, Avatar URL, Introduction, Learning style, Current level, Goals, Personality tags, Weak points; **Save profile** | — |

## Màn hình gia sư

| Route | Tên trang | Nút, ô nhập chính | Điều kiện ẩn / khóa |
|---|---|---|---|
| /tutor, /tutor/dashboard | Tổng quan (GÓC GIẢNG DẠY CỦA BẠN) | Hợp đồng đang dạy, Hợp đồng chờ xử lý; Buổi học sắp tới (7 ngày tới); Yêu cầu tìm gia sư: ô "Tìm môn học, lớp, khu vực...", lọc Tất cả/Trực tuyến/Trực tiếp, sắp xếp Mới nhất/Ngân sách cao nhất | Chỉ hiện tin tìm gia sư đang mở |
| /tutor/studying-requests/:requestId | Chi tiết yêu cầu học | **Ứng tuyển yêu cầu này**, **Sửa đề xuất**, **Gửi lại đề xuất**, **Xem đề xuất của tôi**; form: Học phí đề xuất (VNĐ), Hình thức dạy, Thời gian có thể dạy (**+ Thêm ngày**, Thứ, Từ, Đến), Địa điểm dạy (Tỉnh/thành phố, Quận/huyện), Lời nhắn cho học viên; **Hủy**, **Gửi đề xuất** / **Lưu thay đổi** | Sửa đề xuất: có đề xuất đang chờ. Gửi lại đề xuất: đề xuất trước không còn chờ. Tỉnh/quận bắt buộc khi dạy trực tiếp |
| /tutor/proposals | Đề xuất giảng dạy của tôi | Ô "Tìm môn học, lịch dạy, giá hoặc địa chỉ"; lọc Tất cả/Đang chờ/Đã chấp nhận/Bị từ chối/Đã hủy; **Hủy đề xuất** | Hủy đề xuất: đề xuất đang chờ |
| /tutor/refund-requests | Yêu cầu hoàn tiền | Danh sách; **Trước**, **Sau** | — |
| /tutor/refund-requests/:refundRequestId | Yêu cầu hoàn tiền (chi tiết) | Phản hồi của bạn: **Tôi đã dạy** / **Tôi không dạy** / **Học viên nghỉ** cho từng buổi; lời nhắn; minh chứng; **Gửi phản hồi** | Chỉ hiện form khi chưa phản hồi và còn trong hạn |
| /tutor/certificates | Chứng chỉ | Thêm chứng chỉ: Tên chứng chỉ, Đơn vị cấp, Ngày cấp, Ngày hết hạn, Mô tả, tệp, Hoặc đường dẫn chứng chỉ, **Tải lên và gửi**; **Chỉnh sửa**, **Thay tệp chứng chỉ**, **Lưu thay đổi**, **Hủy chỉnh sửa**; **Xem tài liệu ↗**; xóa (có hộp thoại xác nhận); Lọc theo trạng thái | — |
| /tutor/profile | Chỉnh sửa hồ sơ | Thông tin cá nhân (Tên, Họ, Địa chỉ email, Số điện thoại, Ngày sinh, Giới tính, Tỉnh/thành phố, Quận/huyện, Địa chỉ cụ thể); Ảnh hồ sơ (**Chọn ảnh**, JPG/PNG tối đa 10 MB); Hồ sơ giảng dạy (Giới thiệu bản thân, Số năm kinh nghiệm, Trình độ học vấn, Môn học giảng dạy, Từ khóa phong cách giảng dạy, Phương pháp giảng dạy, Đối tượng học viên phù hợp); **Lưu hồ sơ**; trạng thái Waiting for review / Verified profile / Not approved | — |
| /tutor/notifications | Notifications | Như trang của học viên | — |
| /tutor/wallet | Wallet & Payments | Như trang của học viên | — |
| /tutor/sections/:section | Sắp ra mắt | Tiêu đề theo mục: Yêu cầu dạy của tôi, Hợp đồng, Lịch dạy; "Mục này sẽ sớm được đưa vào hoạt động." | Trang giữ chỗ, chưa có chức năng |
