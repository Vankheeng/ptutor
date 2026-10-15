# 1. Phạm vi, quyết định và điều kiện tiên quyết

## 1.1. Mục tiêu

1. **Học sinh** nhập tiêu chí (bộ lọc, có thể kèm câu tự nhiên) → nhận danh sách **tin dạy** phù hợp
   nhất mà gia sư **có khả năng cao sẽ nhận** em. Học sinh **không cần có tin đăng**.
2. **Gia sư** nhập tiêu chí → nhận danh sách **tin học** phù hợp nhất mà học sinh **có khả năng cao
   sẽ đồng ý**. Gia sư **không cần có tin dạy**.
3. Mỗi kết quả có **2–3 lý do khớp** dễ hiểu.
4. Khi ít kết quả, chỉ ra **cách nới tiêu chí** cụ thể và lợi ích của từng cách.
5. Kết quả **cá nhân hoá dần** theo hành vi của người dùng (trong phiên và qua nhiều phiên).
6. Hệ thống **chủ động** báo khi xuất hiện ghép đôi tốt (cho tin đăng của người dùng hoặc tìm kiếm
   đã lưu).
7. Có **số liệu đánh giá** so sánh được giữa các phiên bản thuật toán.

Không phải chatbot: LLM chỉ được gọi một lượt cho mỗi tác vụ (trích xuất có schema).

## 1.2. Quyết định phạm vi

| Hướng | Quyết định | Lý do |
|---|---|---|
| R1 Reciprocal | Chọn — lõi | Thị trường hai phía; dữ liệu chấp nhận/từ chối đã có trong bảng đơn |
| R2 Nhân / gate | Chọn | Tránh lỗi "bù trừ" của tổng tuyến tính |
| R3 Cross-encoder rerank | Chọn | Tăng chất lượng phần ngữ nghĩa; chạy được trên CPU nhờ cache + int8 |
| R6 Công bằng hiển thị | Chọn | Mọi ứng viên phía gia sư đều trả phí đăng tin |
| A1 LLM chuẩn hoá | Chọn — nền | Gỡ vấn đề văn bản tự do |
| A2 Câu tự nhiên | Chọn — **đầu vào phụ** | Tự điền bộ lọc; bộ lọc mới là đầu vào chính |
| B4 Chống dồn đơn | Chọn mức đơn giản | Gộp vào b |
| B5 Rocchio | Chọn | Thích ứng trong phiên |
| **H1 Lịch sử hành vi** | **Chọn** | Thích ứng dài hạn; không cần huấn luyện, người mới không bị ảnh hưởng |
| C6 Nới tiêu chí | Chọn | |
| C7 Chủ động thông báo | Chọn, **kèm "lưu tìm kiếm"** | Người không có tin đăng vẫn nhận được thông báo |
| D9 Đánh giá mô phỏng | Chọn | Thiếu dữ liệu thật |
| C8 Lý do do LLM viết | Tuỳ chọn | |
| **L1 Học trọng số trực tuyến** | **Chọn** (quyết định 13/10/2026) | Trọng số tự đặt thành prior; contextual bandit (phía a) và hồi quy Bayes (phía b) tự tinh chỉnh khi có dữ liệu; mặc định chế độ `SHADOW`; chứng minh cơ chế trên mô phỏng (12; 09, mục 9.9) |
| Learning-to-rank đầy đủ (LightGBM) | **Hoãn — hướng phát triển** | Cần nhiều dữ liệu thật; dùng lại log của L1 (10, mục 10.5) |
| Ghép đôi thành công lâu dài | Hoãn | Cần nhiều hợp đồng đã kết thúc |
| Gale–Shapley toàn cục | Hoãn | Nặng; bản chống dồn đơn giản đủ ở quy mô đồ án |
| Collaborative filtering | Loại | Dữ liệu tương tác quá thưa |

### Các quyết định của nhóm (theo thời gian)

| Ngày | Quyết định |
|---|---|
| 25/08/2026 | Ứng viên phía gia sư chỉ gồm **tin dạy đã trả phí và được duyệt** (`OPEN`). Không dùng toạ độ địa lý. |
| 26/08/2026 | Không làm tính năng gia sư đánh giá học sinh (không có điểm chất lượng cho học sinh). |
| 09/10/2026 | Chọn bộ hướng R1, R2, R3, R6, A1, A2, B4, B5, C6, C7, D9. |
| 12/10/2026 | **Người tìm không cần có tin đăng**; học sinh tìm → ứng viên là tin dạy `OPEN`; gia sư tìm → ứng viên là tin học `OPEN`. **Bộ lọc là đầu vào chính**. **Khu vực do người tìm chọn** (nhiều quận / cả tỉnh / không giới hạn). Thêm **H1** và **lưu tìm kiếm**. Dùng **trọng số khởi đầu lạnh**; mô hình học từ dữ liệu là hướng phát triển. |
| 12/10/2026 | (1) **Đổi số migration**: điều kiện tiên quyết P2, P5 là `V27` (làm trước), dữ liệu đề xuất là `V28`, vì Flyway không bật `out-of-order`. (2) Hợp đồng `COMPLETED` (bulk update): lấy danh sách gia sư bị ảnh hưởng của lần cập nhật rồi phát sự kiện tính lại điểm sau commit, không chỉ dựa vào job hằng đêm. (3) Vị trí code **theo cấu trúc tầng sẵn có** của repo (02, mục 2.4). (4) P7 (`day_of_week` ISO) làm cùng bước tìm gia sư V2-a. |
| 12/10/2026 | Khi lập trình: (1) `REQUIRES_NEW` đặt ở listener tính lại điểm; (2) 6 cột tổng hợp làm mới cả hằng đêm và theo sự kiện; (3) API đánh giá `POST /students/me/contracts/{id}/review`, không gửi thông báo; (4) model embedding lấy từ `Xenova/bge-m3` (int8); (5) phân trang kết quả tìm kiếm bằng `logId`; (6) **đặc trưng thiếu tính là trung tính 0,5** khi gộp a (06, mục 6.5) thay vì chỉ chuẩn hoá lại; (7) chỉ gia sư `VERIFIED` được tìm học viên; (8) LLM: **OpenAI `gpt-6-luna`** (04, mục 4.9). |

## 1.3. Giả định

- Quy mô đồ án: vài trăm tin đăng mỗi loại; truy vấn vector không cần index ANN.
- Máy chạy AI service: CPU, không GPU, RAM ≥ 8 GB. LLM dùng qua API; embedding và rerank chạy cục bộ.
- Tin đăng chỉ có hình thức `OFFLINE`/`ONLINE` (theo `CHECK` hiện tại). Ở **tiêu chí tìm kiếm**,
  người dùng chọn thêm được "Cả hai" (`ANY`).
- `day_of_week` theo **ISO: 1 = Thứ Hai … 7 = Chủ nhật** (thống kê admin dùng `isodow`). Đã ghi vào
  OpenAPI cho mọi schema có `dayOfWeek`; các API đều kiểm tra 1–7 (P7, xong 12/10/2026).

## 1.4. Hiện trạng code liên quan (đã rà soát, cập nhật 09/10/2026)

| Thành phần | Hiện trạng |
|---|---|
| Cột embedding + metadata trên `students`, `tutors` | Có, `vector(1536)`, **chưa có code ghi** |
| Cột tổng hợp trên `tutors` (`average_rating`, `total_reviews`, `completed_contracts_count`, `total_students_taught`, `acceptance_rate`, `avg_response_time_hours`) | Có, **không có code cập nhật** |
| `tutors.recommendation_score` (v1, thang 100) | Có. Công thức tốt nhưng đầu vào không đổi → thực tế chỉ 3 mức: 37 / 42 / 47 |
| Bảng lịch rảnh `*_request_availabilities` | Có, API nhận `dayOfWeek` 1–7 |
| `recommendation_logs`, `recommendation_click_logs`, `favorite_tutors` | Có bảng + entity, **chưa có code ghi** |
| API đánh giá gia sư | **Chưa có** |
| Tìm tin học `GET /api/v1/studying-requests`, tin dạy `GET /api/v1/teaching-requests` | Có: lọc + sắp theo thời gian, **không cá nhân hoá**. Endpoint đề xuất bổ sung, không thay thế |
| Endpoint đề xuất, pipeline embedding | **Chưa có** |
| Listener tính lại điểm | `AFTER_COMMIT` gọi hàm `@Transactional` mặc định → **nguy cơ không lưu** |

## 1.5. Điều kiện tiên quyết (làm trước khi xây tính năng)

| # | Việc | Ghi chú |
|---|---|---|
| P1 | Job làm mới 6 cột tổng hợp trên `tutors` | SQL ở 03, mục 3.5 |
| P2 | API học sinh đánh giá gia sư | `POST /api/v1/students/me/contracts/{contractId}/review`. Khi hợp đồng `COMPLETED`; mỗi hợp đồng 1 đánh giá; không gửi thông báo cho gia sư, chỉ kích hoạt tính lại điểm |
| P3 | Sửa propagation của listener | Listener chạy mỗi lần tính lại trong transaction riêng (`REQUIRES_NEW`). Không đặt trên `recalculateByUserId` / `recalculateByTutorId` vì `AdminTutorService` gọi hai hàm này ngay trong transaction duyệt hồ sơ |
| P4 | Bổ sung sự kiện kích hoạt tính lại điểm | Hợp đồng `COMPLETED` (bulk update), đơn được chấp nhận/từ chối, có đánh giá mới, sửa hồ sơ |
| P5 | Thêm `responded_at` cho hai bảng đơn | Tính thời gian phản hồi chính xác |
| P6 | Đổi số chiều vector sang 1024 | Do dùng `bge-m3`; an toàn vì chưa có dữ liệu |
| P7 | Chốt quy ước `day_of_week` ISO | Cập nhật OpenAPI + validation |
| P8 | Kết luận khiếu nại có cấu trúc | `complaints.fault_party`, `category` (nhánh `feat/complaint-structured-resolution`). Phải vào `dev` và được merge vào nhánh đề xuất trước khi code phạt khiếu nại trong Q (06, mục 6.8) |
