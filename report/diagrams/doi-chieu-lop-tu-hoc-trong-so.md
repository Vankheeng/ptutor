# Đối chiếu biểu đồ tuần tự tự học trọng số với code

Biểu đồ `tuan-tu-tu-hoc-trong-so` mô tả lần chạy định kỳ của `WeightLearningJob`. API để quản trị viên chạy thủ công dùng cùng `WeightLearningService` nhưng không thuộc kịch bản này.

| Lifeline | Vai trò trong code |
| --- | --- |
| `WeightLearningJob` | Kích hoạt tác vụ theo lịch. |
| `WeightLearningService` | Khóa lần chạy, lấy dữ liệu, học, đánh giá, lưu phiên bản và phát thông báo nội bộ khi có phiên bản được kích hoạt. |
| `WeightProvider` | Cung cấp chế độ/trọng số ban đầu và nạp lại phiên bản đang hoạt động. |
| `RecommendationLearnedWeightsRepository` | Đọc và lưu các phiên bản trọng số. |
| `RecommendationLearnedWeights` | Entity được JPA ánh xạ khi đọc và được service tạo trước khi lưu phiên bản mới. |
| `LearningDataRepository` | Truy vấn dữ liệu học bằng SQL. |
| `LinearBayesModel` | Cập nhật thống kê và tính trọng số dự kiến. |

`LearningDataRepository` truy vấn các bảng `recommendation_logs`, `recommendation_click_logs`, `recommendation_decision_snapshots`, `student_tutor_requests` và `tutor_student_requests`. Các bảng này là nguồn dữ liệu, không phải đối tượng được gọi phương thức trong luồng; vì vậy chúng được nêu ở ghi chú thay vì thành lifeline.

Khi có phiên bản mới được kích hoạt, `WeightLearningService` phát `LearnedWeightsUpdatedEvent` qua `ApplicationEventPublisher` của Spring. `WeightProvider` nhận sự kiện và nạp lại trọng số. Biểu đồ gộp việc chuyển thông báo này thành một bước vì `ApplicationEventPublisher` không phải dịch vụ độc lập.
