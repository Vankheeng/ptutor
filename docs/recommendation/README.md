# Thiết kế tính năng Đề xuất Gia sư ↔ Học viên — phiên bản 2

> Bộ tài liệu này **thay thế** bộ `docs/01–08` cũ (nằm ngoài repo, tại `D:\DATN\AI_Tutor_Recommendation\docs`). Bộ cũ phân tích file `Đồ án .sql`, trong khi
> schema thật của dự án (migration Flyway V1–V24 trên nhánh `dev`, thư mục `backend/src/main/resources/db/migration`) đã khác khá nhiều.
> Mọi tên bảng, cột, enum dưới đây đều lấy theo **code thật** tại thời điểm viết (09/10/2026).
>
> Lịch sử thay đổi và các việc còn mở: xem `NHAT-KY.md` cùng thư mục (chỉ có trên máy, không đưa lên Git).
>
> Trạng thái: **bản thiết kế**, chưa triển khai. Các tham số (trọng số, ngưỡng) là giá trị khởi
> điểm, sẽ được hiệu chỉnh ở bước đánh giá (tài liệu 09).

## Tóm tắt một đoạn

Hệ thống đề xuất **hai chiều**: học sinh tìm gia sư (S→T) và gia sư tìm học viên (T→S). Đơn vị
ghép đôi là **cặp tin đăng** (`studying_requests` ↔ `teaching_requests`). Điểm cuối của mỗi cặp
kết hợp *độ phù hợp với người tìm* (a) và *khả năng phía bên kia đồng ý* (b) bằng trung bình
điều hoà (reciprocal recommendation), sau đó nhân với hệ số chất lượng và hệ số chặn mềm.
Văn bản tự do được LLM chuẩn hoá thành dữ liệu có cấu trúc ở tiến trình nền; người dùng có thể
tìm bằng một câu tiếng Việt tự nhiên; kết quả được xếp lại bằng cross-encoder, dành chỗ cho gia
sư ít được hiển thị, tự điều chỉnh theo hành vi trong phiên, gợi ý cách nới tiêu chí khi ít kết
quả, và chủ động thông báo khi xuất hiện ghép đôi tốt.

## Các hướng được chọn

| Mã | Hướng | Tài liệu |
|---|---|---|
| R1 | Đề xuất hai chiều (reciprocal) | [06](./06-thuat-toan-xep-hang.md) |
| R2 | Cấu trúc nhân / hệ số chặn mềm (gate) | [06](./06-thuat-toan-xep-hang.md) |
| R3 | Cross-encoder rerank | [06](./06-thuat-toan-xep-hang.md) |
| R6 | Công bằng hiển thị (exploration slot) | [06](./06-thuat-toan-xep-hang.md) |
| A1 | LLM chuẩn hoá văn bản tự do (chạy nền) | [04](./04-chuan-hoa-du-lieu-llm.md) |
| A2 | Tìm kiếm bằng ngôn ngữ tự nhiên | [05](./05-tim-kiem-ngon-ngu-tu-nhien.md) |
| B4 | Chống dồn đơn (congestion-aware, mức đơn giản) | [06](./06-thuat-toan-xep-hang.md) |
| B5 | Xếp lại trong phiên (Rocchio) | [07](./07-tuong-tac-va-chu-dong.md) |
| C6 | Gợi ý nới tiêu chí | [07](./07-tuong-tac-va-chu-dong.md) |
| C7 | Chủ động thông báo ghép đôi tốt | [07](./07-tuong-tac-va-chu-dong.md) |
| D9 | Đánh giá bằng persona LLM + bộ mô phỏng | [09](./09-danh-gia.md) |

Tuỳ chọn: C8 (lý do khớp do LLM viết). Hoãn: learning-to-rank, xếp hạng theo ghép đôi thành công
lâu dài, Gale–Shapley toàn cục. Loại: collaborative filtering. Lý do ở [01](./01-pham-vi-va-quyet-dinh.md).

## Mục lục

1. [Phạm vi, quyết định và điều kiện tiên quyết](./01-pham-vi-va-quyet-dinh.md)
2. [Kiến trúc tổng thể](./02-kien-truc.md)
3. [Dữ liệu và schema bổ sung](./03-du-lieu-va-schema.md)
4. [A1 — Chuẩn hoá dữ liệu bằng LLM](./04-chuan-hoa-du-lieu-llm.md)
5. [A2 — Tìm kiếm bằng ngôn ngữ tự nhiên](./05-tim-kiem-ngon-ngu-tu-nhien.md)
6. [Thuật toán xếp hạng (R1, R2, R3, R6, B4)](./06-thuat-toan-xep-hang.md)
7. [Tương tác và chủ động (B5, C6, C7, lý do khớp)](./07-tuong-tac-va-chu-dong.md)
8. [API](./08-api.md)
9. [Đánh giá (D9)](./09-danh-gia.md)
10. [Vận hành, tham số và hướng phát triển](./10-van-hanh-va-huong-phat-trien.md)

## Thuật ngữ

| Thuật ngữ | Nghĩa |
|---|---|
| Người tìm (searcher) | Bên đang thực hiện tìm kiếm: học sinh (S→T) hoặc gia sư (T→S) |
| Ứng viên (candidate) | Tin đăng của phía bên kia được xét đề xuất |
| Tin học / SR | `studying_requests` — tin tìm gia sư của học sinh |
| Tin dạy / TR | `teaching_requests` — tin tìm học viên của gia sư (trả phí, qua duyệt) |
| a | Độ phù hợp của ứng viên **theo góc nhìn người tìm** |
| b | Khả năng **ứng viên đồng ý** ghép với người tìm |
| R | Điểm reciprocal = trung bình điều hoà của a và b |
| Q | Điểm chất lượng tĩnh của gia sư = `tutors.recommendation_score / 100` |
| G | Hệ số chặn mềm (gate) |
