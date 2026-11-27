---
id: viec-chua-ho-tro
title: "Chức năng còn giới hạn và cách nhận hỗ trợ"
type: faq
audience: [STUDENT, TUTOR]
features: [contract, withdrawal, review, complaint]
routes: [/student/contracts, /student/wallet, /tutor/wallet, /, /public]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: StudentReviewController/ReviewService; frontend: chưa có form đánh giá trong khu vực học viên"
  - "frontend: App.tsx các route ví, khiếu nại, hợp đồng và lịch dạy; backend: LessonPayoutService/AdminWithdrawalService"
  - "backend: ContractService.cancel (chỉ PENDING); CancelContractRequestStatus không có endpoint"
  - "backend: WithdrawalService và AdminWithdrawalService (duyệt, từ chối, hoàn tất yêu cầu rút)"
  - "backend: LessonPayoutService.release và AdminRefundRequestService.approve"
  - "backend: PublicReviewController (chỉ đọc)"
  - "backend: AdminComplaintService (ghi bên có lỗi, không áp dụng hình phạt)"
  - "frontend: App.tsx, MyTeachingRequestsPage, TutorContractsPage, TutorCalendarPage, ComplaintsPage, WalletPage"
  - "backend: PublicReviewController GET /api/v1/reviews -> ReviewService.findLatestPublicReviews (chỉ đánh giá 5 sao, mặc định 3, tối đa 50)"
  - "backend: StudentReviewController có endpoint tạo đánh giá; chưa có sửa hoặc xóa"
  - "frontend: components/home/HomeLanding.tsx (Học viên nói gì về Ptutor?), components/home/ReviewCard.tsx"
  - "backend: TutorRecommendationScoreService.ratingScore (đánh giá ảnh hưởng điểm đề xuất)"
error_codes: []
keywords: [chưa hỗ trợ, không làm được, chức năng chưa có, sắp ra mắt, chua ho tro, khong lam duoc, chuc nang chua co, sap ra mat, đánh giá gia sư, viết đánh giá, chấm sao, nhận xét gia sư, danh gia gia su, viet danh gia, cham sao, nhan xet, review]
todo: []
---

# Chức năng còn giới hạn và cách nhận hỗ trợ

## Hủy hợp đồng đang hoạt động được không?

Chưa. PTutor chỉ cho người soạn hủy hợp đồng chưa ký. Hợp đồng đã ký (đang hoạt động) không hủy hay chấm dứt sớm được trên giao diện, và kết thúc khi tới ngày kết thúc.

## Nhân viên xử lý yêu cầu rút tiền thế nào?

Nhân viên PTutor có trang duyệt, từ chối và xác nhận chuyển khoản. Người dùng gửi yêu cầu rút và liên kết ngân hàng tại trang **Ví tiền**; yêu cầu cần xác thực OTP.

## Gia sư nhận tiền dạy thế nào?

Buổi đã được xác nhận hoặc tự hoàn tất được cộng tiền dạy vào ví gia sư nếu học phí đã đóng và không còn yêu cầu hoàn tiền đang xử lý. Gia sư có thể rút số dư ví về ngân hàng.

## Học viên viết đánh giá gia sư được không?

Giao diện học viên hiện chưa có biểu mẫu đánh giá. Backend cho phép học viên tạo **một đánh giá cho mỗi hợp đồng đã hoàn tất**, chấm 1–5 sao và ghi nhận xét tối đa 2.000 ký tự; chưa có API sửa hoặc xóa đánh giá.

## Vi phạm thì bị phạt thế nào?

Hệ thống chưa có hình phạt tự động. Khi khiếu nại được chấp nhận, nhân viên ghi bên có lỗi nhưng hệ thống không tự trừ tiền hay khóa tài khoản. PTutor có thể khóa tài khoản vi phạm; việc này do nhân viên quyết định.

## Gửi khiếu nại trên giao diện được không?

Có. Học viên và gia sư mở **Khiếu nại** để gửi, theo dõi và phản hồi khiếu nại.

## Gia sư quản lý tin nhận dạy, hợp đồng, lịch dạy ở đâu?

Các mục **Yêu cầu dạy của tôi**, **Hợp đồng** và **Lịch dạy** trong menu gia sư đã có trang chức năng.

## Đổi mật khẩu khi đang đăng nhập được không?

Chưa có nút đổi mật khẩu trong trang hồ sơ. Bạn dùng trang **Quên mật khẩu?** để đặt mật khẩu mới qua mã OTP.

## Đóng học phí, trả phí đăng tin bằng số dư ví được không?

Có. Khi đủ điều kiện thanh toán, bạn có thể chọn VNPay hoặc Ví PTutor trên giao diện.

## Đánh giá hiện hiển thị ở đâu?

Trang chủ PTutor có mục "Học viên nói gì về Ptutor?", hiện các đánh giá 5 sao mới nhất của học viên, kèm tên gia sư được đánh giá. Đánh giá không có bình luận hiện câu "Học viên chưa để lại bình luận."

## Đánh giá có ảnh hưởng gì tới gia sư?

Điểm đánh giá trung bình và số lượt đánh giá là một phần của điểm đề xuất gia sư. Gia sư có nhiều đánh giá tốt được xếp hạng cao hơn khi PTutor gợi ý gia sư cho học viên.

## Khi cần nhân viên hỗ trợ

Học viên và gia sư đã đăng nhập chọn **Yêu cầu hỗ trợ của tôi** hoặc **Hỏi nhân viên hỗ trợ** trong trợ lý. Nếu tài khoản không đăng nhập được, hiện chưa có kênh ngoài ứng dụng được PTutor công bố; không gửi CCCD, mật khẩu hoặc OTP cho tài khoản tự xưng hỗ trợ.

## Tranh chấp đánh giá

Gia sư hiện chưa có nút phản hồi công khai hoặc yêu cầu gỡ đánh giá. Nếu cho rằng đánh giá sai sự thật, gia sư có thể gửi **Yêu cầu hỗ trợ**, kèm mã hợp đồng và nội dung cần xem xét. Mã nguồn chưa có quy trình tự gỡ hay sửa đánh giá; đề xuất nhân viên đối chiếu hợp đồng và chứng cứ trước khi quyết định.
