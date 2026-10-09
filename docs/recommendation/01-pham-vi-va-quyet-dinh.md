# 1. Phạm vi, quyết định và điều kiện tiên quyết

## 1.1. Mục tiêu

1. Với một **học sinh**, trả về danh sách tin dạy (`teaching_requests`) phù hợp nhất, mà gia sư
   của tin đó **có khả năng cao sẽ nhận** học sinh.
2. Với một **gia sư** (dựa trên một tin dạy đang `OPEN`), trả về danh sách tin học
   (`studying_requests`) phù hợp nhất, mà học sinh **có khả năng cao sẽ đồng ý**.
3. Mỗi kết quả có **2–3 lý do khớp** dễ hiểu.
4. Khi ít kết quả, chỉ ra **cách nới tiêu chí** cụ thể và lợi ích của từng cách.
5. Hệ thống **chủ động** báo cho người dùng khi xuất hiện ghép đôi tốt.
6. Có **số liệu đánh giá** so sánh được giữa các phiên bản thuật toán.

Không phải chatbot: không có hội thoại nhiều lượt. LLM chỉ được gọi một lượt cho mỗi tác vụ
(trích xuất có schema), chủ yếu ở tiến trình nền.

## 1.2. Quyết định phạm vi

| Hướng | Quyết định | Lý do |
|---|---|---|
| R1 Reciprocal | Chọn — lõi | Thị trường hai phía; dữ liệu chấp nhận/từ chối đã có sẵn ở `student_tutor_requests`, `tutor_student_requests` |
| R2 Nhân / gate | Chọn | Tránh lỗi "bù trừ" của tổng tuyến tính |
| R3 Cross-encoder rerank | Chọn | Tăng chất lượng phần ngữ nghĩa; chạy được trên CPU nếu tính trước + lượng tử hoá |
| R6 Công bằng hiển thị | Chọn | Mọi ứng viên đều trả phí đăng tin |
| A1 LLM chuẩn hoá | Chọn — nền | Gỡ gốc rễ vấn đề văn bản tự do; các thành phần khác phụ thuộc |
| A2 Câu tự nhiên | Chọn | Trải nghiệm tìm kiếm tốt, chỉ một lượt gọi LLM |
| B4 Chống dồn đơn | Chọn mức đơn giản | Gộp vào b; Gale–Shapley toàn cục để hướng phát triển |
| B5 Rocchio | Chọn | Rẻ, dùng dữ liệu click/tym đã có bảng |
| C6 Nới tiêu chí | Chọn | Rẻ, tận dụng lại pipeline |
| C7 Chủ động thông báo | Chọn | Hệ thống notification đã có sẵn |
| D9 Đánh giá persona | Chọn | Thiếu dữ liệu thật; cần số liệu cho chương đánh giá |
| C8 Lý do do LLM viết | Tuỳ chọn | Mặc định dùng câu mẫu, để sẵn điểm mở rộng |
| Learning-to-rank | Hoãn | Cần nhiều log thật. Log được thiết kế sẵn để gắn vào sau |
| Ghép đôi thành công lâu dài | Hoãn | Cần nhiều hợp đồng đã kết thúc |
| Gale–Shapley toàn cục | Hoãn | Nặng, chưa cần ở quy mô đồ án |
| Collaborative filtering | Loại | Dữ liệu tương tác quá thưa |

Các quyết định đã chốt từ trước, giữ nguyên:
- Ứng viên phía gia sư **chỉ gồm tin dạy đã trả phí và được duyệt** (`status = 'OPEN'`).
- **Không dùng toạ độ địa lý.** Khu vực chỉ so ở mức quận/huyện (`districts`) và tỉnh/thành
  (`provinces`).
- Không làm tính năng gia sư đánh giá học sinh, nên **không có điểm chất lượng cho học sinh**.

## 1.3. Giả định

- Quy mô đồ án: khoảng vài trăm tin đăng mỗi loại. Truy vấn vector **không cần index ANN**.
- Máy chạy AI service: CPU, không GPU, RAM ≥ 8 GB.
- LLM dùng qua API bên ngoài (có chế độ trả JSON). Embedding và rerank chạy cục bộ.
- Hình thức học chỉ có `OFFLINE` / `ONLINE` (theo `CHECK` constraint hiện tại), không có `BOTH`.
- `day_of_week` theo **chuẩn ISO: 1 = Thứ Hai … 7 = Chủ nhật**. Lý do: API hiện nhận 1–7 nhưng
  chưa định nghĩa ý nghĩa; thống kê admin đang dùng `extract(isodow …)` nên chọn ISO cho nhất
  quán. **Cần ghi rõ quy ước này vào OpenAPI và validation.**

## 1.4. Hiện trạng code liên quan (đã rà soát)

| Thành phần | Hiện trạng |
|---|---|
| Cột embedding + metadata trên `students`, `tutors` | Có, kiểu `vector(1536)`, **chưa có code ghi** |
| Cột tổng hợp trên `tutors` (`average_rating`, `total_reviews`, `completed_contracts_count`, `total_students_taught`, `acceptance_rate`, `avg_response_time_hours`) | Có, **không có code nào cập nhật** (chỉ gán 0 khi đăng ký) |
| `tutors.recommendation_score` (v1, thang 100, có `score_breakdown`) | Có. Công thức tốt nhưng đầu vào không đổi nên điểm thực tế chỉ có 3 mức: 37 / 42 / 47 |
| Bảng lịch rảnh `*_request_availabilities` | Có, API nhận `dayOfWeek` 1–7 |
| `recommendation_logs`, `recommendation_click_logs`, `favorite_tutors` | Có bảng + entity, **chưa có code ghi** |
| API đánh giá gia sư | **Chưa có** (chỉ có API đọc) |
| Endpoint đề xuất, pipeline embedding | **Chưa có** |
| Tìm tin học `GET /api/v1/studying-requests` (có từ 04/10) | Có: lọc theo trạng thái, môn, lớp, quận, hình thức học; sắp xếp theo thời gian tạo, **không cá nhân hoá**. Endpoint đề xuất (08) bổ sung cho API này, không thay thế |
| Listener tính lại điểm | Chạy ở `AFTER_COMMIT` nhưng gọi hàm `@Transactional` mặc định (`REQUIRED`) → **có nguy cơ không lưu** |

## 1.5. Điều kiện tiên quyết (làm trước khi xây tính năng)

| # | Việc | Ghi chú |
|---|---|---|
| P1 | Job làm mới 6 cột tổng hợp trên `tutors` | Chạy ngay trước bước tính `recommendation_score` trong scheduler hằng ngày, và theo sự kiện. Công thức ở [03](./03-du-lieu-va-schema.md#35-cột-tổng-hợp-trên-tutors) |
| P2 | API học sinh đánh giá gia sư | Chỉ cho phép khi hợp đồng `COMPLETED`; mỗi hợp đồng 1 đánh giá (thêm `reviews.contract_id` UNIQUE) |
| P3 | Sửa propagation của listener | `@Transactional(propagation = REQUIRES_NEW)` cho `recalculateByUserId` / `recalculateByTutorId` |
| P4 | Bổ sung sự kiện kích hoạt tính lại điểm | Hợp đồng `COMPLETED` (bulk update hiện không phát sự kiện), đơn được chấp nhận/từ chối, có đánh giá mới, gia sư sửa hồ sơ |
| P5 | Thêm `responded_at` cho hai bảng đơn | Để tính `avg_response_time_hours` chính xác |
| P6 | Đổi số chiều vector sang 1024 | Do dùng `bge-m3`. An toàn vì chưa có dữ liệu embedding |
| P7 | Chốt quy ước `day_of_week` ISO | Cập nhật OpenAPI + validation |
