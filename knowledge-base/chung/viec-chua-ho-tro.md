---
id: viec-chua-ho-tro
title: "Những việc nền tảng chưa hỗ trợ"
type: faq
audience: [STUDENT, TUTOR]
features: [contract, withdrawal, review, complaint]
routes: [/student/contracts, /student/wallet, /tutor/wallet, /, /public]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: ContractService.cancel (chỉ PENDING); CancelContractRequestStatus không có endpoint"
  - "backend: WithdrawalService (không có thao tác xử lý yêu cầu rút cho nhân viên)"
  - "backend: AdminRefundRequestService (TUTOR_EARNING chỉ cho buổi học viên nghỉ); LessonService (không cộng tiền khi xác nhận)"
  - "backend: PublicReviewController (chỉ đọc)"
  - "backend: AdminComplaintService (ghi bên có lỗi, không áp dụng hình phạt)"
  - "frontend: TutorComingSoonPage (Yêu cầu dạy của tôi, Hợp đồng, Lịch dạy); không có trang khiếu nại, rút tiền, đổi mật khẩu"
  - "backend: PublicReviewController GET /api/v1/reviews -> ReviewService.findLatestPublicReviews (chỉ đánh giá 5 sao, mặc định 3, tối đa 50)"
  - "backend: không có endpoint tạo, sửa, xoá đánh giá"
  - "frontend: components/home/HomeLanding.tsx (Học viên nói gì về Ptutor?), components/home/ReviewCard.tsx"
  - "backend: TutorRecommendationScoreService.ratingScore (đánh giá ảnh hưởng điểm đề xuất)"
error_codes: []
keywords: [chưa hỗ trợ, không làm được, chức năng chưa có, sắp ra mắt, chua ho tro, khong lam duoc, chuc nang chua co, sap ra mat, đánh giá gia sư, viết đánh giá, chấm sao, nhận xét gia sư, danh gia gia su, viet danh gia, cham sao, nhan xet, review]
todo:
  - "Với từng mục dưới đây, PTutor hướng dẫn người dùng liên hệ ai và bằng kênh nào trong thời gian chưa hỗ trợ?"
  - "Khi nào học viên viết được đánh giá gia sư, và đánh giá sau mỗi hợp đồng hay mỗi buổi học?"
  - "Gia sư có phản hồi hoặc yêu cầu gỡ đánh giá không đúng sự thật được không?"
---

# Những việc nền tảng chưa hỗ trợ

## Hủy hợp đồng đang hoạt động được không?

Chưa. PTutor chỉ cho người soạn hủy hợp đồng chưa ký. Hợp đồng đã ký (đang hoạt động) không hủy hay chấm dứt sớm được trên giao diện, và kết thúc khi tới ngày kết thúc.

## Nhân viên xử lý yêu cầu rút tiền thế nào?

Chưa có. Hệ thống ghi nhận và tạm giữ tiền khi bạn gửi yêu cầu rút, nhưng chưa có quy trình để nhân viên chuyển tiền hay từ chối yêu cầu. Giao diện rút tiền và khai báo tài khoản ngân hàng cũng chưa có.

## Gia sư nhận tiền dạy thế nào?

Chưa tự động. Học phí của buổi đã dạy chưa được tự cộng vào ví gia sư. Gia sư chỉ được cộng phần 20% của buổi học viên nghỉ khi kế toán kết luận yêu cầu hoàn tiền.

## Học viên viết đánh giá gia sư được không?

Chưa. Học viên chưa viết, sửa hay xóa được đánh giá. Trang chủ chỉ hiện các đánh giá 5 sao mới nhất.

## Vi phạm thì bị phạt thế nào?

Hệ thống chưa có hình phạt tự động. Khi khiếu nại được chấp nhận, nhân viên ghi bên có lỗi nhưng hệ thống không tự trừ tiền hay khóa tài khoản. PTutor có thể khóa tài khoản vi phạm; việc này do nhân viên quyết định.

## Gửi khiếu nại trên giao diện được không?

Chưa. Hệ thống đã có quy trình khiếu nại, nhưng chưa có trang để học viên và gia sư gửi, theo dõi và phản hồi khiếu nại.

## Gia sư quản lý tin nhận dạy, hợp đồng, lịch dạy ở đâu?

Các mục **Yêu cầu dạy của tôi**, **Hợp đồng** và **Lịch dạy** trong menu gia sư đang hiện "Sắp ra mắt".

## Đổi mật khẩu khi đang đăng nhập được không?

Chưa có nút đổi mật khẩu trong trang hồ sơ. Bạn dùng trang **Quên mật khẩu?** để đặt mật khẩu mới qua mã OTP.

## Đóng học phí, trả phí đăng tin bằng số dư ví được không?

Hệ thống hỗ trợ, nhưng giao diện chưa có nút trả bằng ví. Hiện bạn trả qua VNPay.

## Học viên có viết đánh giá gia sư được không?

Hiện chưa. PTutor chưa hỗ trợ học viên viết, sửa hay xóa đánh giá gia sư trên giao diện.

## Đánh giá hiện hiển thị ở đâu?

Trang chủ PTutor có mục "Học viên nói gì về Ptutor?", hiện các đánh giá 5 sao mới nhất của học viên, kèm tên gia sư được đánh giá. Đánh giá không có bình luận hiện câu "Học viên chưa để lại bình luận."

## Đánh giá có ảnh hưởng gì tới gia sư?

Điểm đánh giá trung bình và số lượt đánh giá là một phần của điểm đề xuất gia sư. Gia sư có nhiều đánh giá tốt được xếp hạng cao hơn khi PTutor gợi ý gia sư cho học viên.
