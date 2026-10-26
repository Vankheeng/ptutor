# Thiết kế tính năng Đề xuất Gia sư ↔ Học viên — phiên bản 2.1

> 📖 **Mới đọc lần đầu? Bắt đầu từ [00 — Đọc trước](./00-doc-truoc.md)**: giải thích toàn bộ tính năng bằng lời thường,
> khoảng 15 phút đọc, kèm bảng giải nghĩa thuật ngữ và bảng "muốn biết gì thì đọc file nào". Mỗi file 01–12 cũng có
> khung 💡 giải thích ngắn ngay dưới tiêu đề và dưới từng mục.
>
> Ghi chú phiên bản (18/10/2026): thêm file 00 và các khung 💡 giải thích bằng lời thường; nội dung kỹ thuật không đổi.

> Bộ tài liệu này thay thế bộ `docs/01–08` cũ (nằm ngoài repo, tại `D:\DATN\AI_Tutor_Recommendation\docs`),
> vốn phân tích file `Đồ án .sql` và không còn khớp schema thật. Mọi tên bảng, cột, enum dưới đây lấy theo
> migration Flyway trên nhánh `dev` (V1–V30) và của nhánh tính năng (V31–V35).
>
> Lịch sử thay đổi và việc còn mở: `NHAT-KY.md` cùng thư mục (chỉ có trên máy, không đưa lên Git).
>
> Trạng thái (20/10/2026): **đã lập trình** trên nhánh `feat/tutor-recommendation` — hai chiều tìm kiếm, A1/A2,
> rerank, lịch sử, khám phá, tìm kiếm đã lưu và thông báo, phạt khiếu nại trong Q, học trọng số L1 (mặc định
> `SHADOW`), giao diện học sinh và gia sư; bộ đánh giá mô phỏng ở `scripts/recommendation_eval/`, kết quả ở
> `ket-qua/`. Trọng số và ngưỡng là giá trị khởi đầu, xem kết quả đánh giá (09).

## Tóm tắt

**Học sinh hoặc gia sư nhập tiêu chí tìm kiếm** — chủ yếu bằng bộ lọc (danh sách sổ, ô nhập, lưới lịch,
chip), có thể gõ một câu tự nhiên để hệ thống tự điền bộ lọc. Người tìm **không cần có tin đăng**. Hệ
thống ghép tiêu chí với hồ sơ của người tìm thành một **nhu cầu**, rồi xếp hạng **tin đăng `OPEN` của phía
bên kia**: học sinh nhận danh sách tin dạy (gia sư đã trả phí, đã được duyệt), gia sư nhận danh sách tin
học.

Điểm của mỗi ứng viên kết hợp **độ phù hợp với người tìm** (a) và **khả năng phía bên kia đồng ý** (b)
bằng trung bình điều hoà — mô hình gợi ý hai chiều theo hướng RECON — rồi nhân với hệ số chất lượng và
hệ số chặn mềm. Khả năng đồng ý tính đến **mức dồn đơn** của từng tin. Kết quả được xếp lại bằng
cross-encoder, dành chỗ cho người ít được hiển thị, **cá nhân hoá dần theo hành vi** (trong phiên và qua
nhiều phiên), gợi ý cách nới tiêu chí khi ít kết quả, và **chủ động thông báo** khi xuất hiện ghép đôi tốt
cho tin đăng hoặc tìm kiếm đã lưu của người dùng.

Trọng số gộp do nhóm đặt là **điểm xuất phát (prior)**: khi vận hành, hệ thống **tự tinh chỉnh trọng số**
từ phản hồi — contextual bandit (Linear Thompson Sampling) cho phía người tìm, hồi quy Bayes trực tuyến cho
phía đồng ý — có giới hạn an toàn và chế độ chạy ngầm; cơ chế học được kiểm chứng trên dữ liệu mô phỏng (L1).

## Các hướng được chọn

| Mã | Hướng | Tài liệu |
|---|---|---|
| R1 | Gợi ý hai chiều (reciprocal) | [06](./06-thuat-toan-xep-hang.md) |
| R2 | Cấu trúc nhân / hệ số chặn mềm | [06](./06-thuat-toan-xep-hang.md) |
| R3 | Cross-encoder rerank | [06](./06-thuat-toan-xep-hang.md) |
| R6 | Công bằng hiển thị (slot khám phá) | [06](./06-thuat-toan-xep-hang.md) |
| B4 | Chống dồn đơn (mức đơn giản) | [06](./06-thuat-toan-xep-hang.md) |
| H1 | Gợi ý theo lịch sử hành vi (hồ sơ sở thích dài hạn) | [06](./06-thuat-toan-xep-hang.md), [07](./07-tuong-tac-va-chu-dong.md) |
| A1 | LLM chuẩn hoá văn bản tự do (chạy nền) | [04](./04-chuan-hoa-du-lieu-llm.md) |
| A2 | Câu tự nhiên tự điền bộ lọc | [05](./05-dau-vao-tim-kiem.md) |
| B5 | Xếp lại trong phiên (Rocchio) | [07](./07-tuong-tac-va-chu-dong.md) |
| C6 | Gợi ý nới tiêu chí | [07](./07-tuong-tac-va-chu-dong.md) |
| C7 | Chủ động thông báo + lưu tìm kiếm | [07](./07-tuong-tac-va-chu-dong.md), [05](./05-dau-vao-tim-kiem.md) |
| D9 | Đánh giá bằng dữ liệu mô phỏng có đáp án ẩn | [09](./09-danh-gia.md) |
| L1 | Học trọng số trực tuyến (contextual bandit, trọng số tự đặt làm prior) | [12](./12-hoc-trong-so.md), [09](./09-danh-gia.md) |

Tuỳ chọn: C8 (lý do khớp do LLM viết). Hướng phát triển: learning-to-rank đầy đủ (LightGBM), xếp
hạng theo ghép đôi thành công lâu dài, tối ưu toàn cục. Loại: collaborative filtering. Lý do ở
[01](./01-pham-vi-va-quyet-dinh.md).

## Mục lục

0. [**Đọc trước — giải thích bằng lời thường**](./00-doc-truoc.md)
1. [Phạm vi, quyết định và điều kiện tiên quyết](./01-pham-vi-va-quyet-dinh.md)
2. [Kiến trúc tổng thể](./02-kien-truc.md)
3. [Dữ liệu và schema bổ sung](./03-du-lieu-va-schema.md)
4. [A1 — Chuẩn hoá dữ liệu bằng LLM](./04-chuan-hoa-du-lieu-llm.md)
5. [Đầu vào tìm kiếm — bộ lọc và câu tự nhiên (A2)](./05-dau-vao-tim-kiem.md)
6. [Thuật toán xếp hạng](./06-thuat-toan-xep-hang.md)
7. [Tương tác, chủ động và lịch sử hành vi](./07-tuong-tac-va-chu-dong.md)
8. [API](./08-api.md)
9. [Đánh giá](./09-danh-gia.md)
10. [Vận hành, tham số và hướng phát triển](./10-van-hanh-va-huong-phat-trien.md)
11. [Cơ sở lý thuyết và công trình liên quan](./11-co-so-ly-thuyet.md)
12. [L1 — Học trọng số trực tuyến](./12-hoc-trong-so.md)
13. [Tinh chỉnh kiến trúc sau các lượt chạy đánh giá (26/10/2026)](./13-tinh-chinh-sau-danh-gia.md)

## Thuật ngữ

| Thuật ngữ | Nghĩa |
|---|---|
| Người tìm | Bên đang tìm: học sinh (S→T) hoặc gia sư (T→S) |
| Ứng viên | Tin đăng `OPEN` của phía bên kia |
| Tin học / SR | `studying_requests` — tin tìm gia sư của học sinh |
| Tin dạy / TR | `teaching_requests` — tin tìm học viên của gia sư (trả phí, qua duyệt) |
| Nhu cầu / `QueryContext` | Tiêu chí tìm kiếm sau khi gộp bộ lọc, câu tự nhiên, hồ sơ (và tin đăng của người tìm, nếu chọn) |
| a | Độ phù hợp của ứng viên theo góc nhìn người tìm |
| b | Khả năng ứng viên đồng ý ghép với người tìm |
| R | Điểm reciprocal = trung bình điều hoà của a và b |
| Q | Điểm chất lượng tĩnh của gia sư = `tutors.recommendation_score / 100` |
| G | Hệ số chặn mềm |
| `f_hist`, `w_hist` | Đặc trưng lịch sử hành vi và trọng số tăng dần của nó |
| Prior | Trọng số nhóm tự đặt — điểm xuất phát và mốc giới hạn của L1 |
| `WeightProvider` | Thành phần trả trọng số đang dùng: bản học được nếu có, nếu không thì prior (12) |
