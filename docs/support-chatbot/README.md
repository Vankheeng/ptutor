# Thiết kế tính năng Trợ lý hỗ trợ người dùng (Support Assistant) — phiên bản 0.6

> Nhánh: `feat/support-chatbot` (tạo từ `dev`). Mọi tên endpoint, enum, bảng nhắc tới dưới đây lấy theo
> code trên `dev` (migration Flyway V1–V24, các controller trong `backend/.../controller`).
>
> Trạng thái: **bản thiết kế**, chưa triển khai. Các con số (ngưỡng, top-k, giới hạn) là giá trị khởi đầu,
> hiệu chỉnh ở bước đánh giá ([10](./10-danh-gia.md)).
>
> 0.2 (13/10/2026): chốt câu hỏi mở Q1–Q6; tổ chức `assistant-service` theo 6 hook middleware; thêm bộ kiểm
> chứng trích dẫn; kho tri thức chuyển sang lưu trong DB, admin quản lý; embedding bge-m3 qua `ai-service`;
> thêm cơ sở lý thuyết ([12](./12-co-so-ly-thuyet.md)) và thiết kế kiểm thử / eval
> ([13](./13-thiet-ke-eval.md)).
>
> 0.3 (13/10/2026): bộ nhớ hội thoại — trạng thái phiên, ngân sách token theo lớp ngữ cảnh, quyết định không lưu bộ
> nhớ dài hạn / xuyên phiên, xóa có kiểm chứng trên mọi bảng ([04](./04-luong-hoi-dap.md) mục 4.10); cơ sở lý thuyết
> về bộ nhớ ([12](./12-co-so-ly-thuyet.md) mục 12.8).
>
> 0.4 (13/10/2026): thu gọn phạm vi — bỏ tóm tắt hội thoại bằng LLM, `topics` / `unresolved`, thử hỏi trên bản nháp,
> kiểm tra hồi quy khi công bố, LLM chấm điểm, chạy 5 lần, thí nghiệm E3 / E5 / E6, workflow CI; giữ E1, E2, E4; thêm
> khôi phục phiên bản; thêm benchmark B2 (phân rã độ trễ, token, chi phí — [10](./10-danh-gia.md) mục 10.5). Các phần
> bỏ ghi ở [11](./11-lo-trinh-va-van-hanh.md) mục 11.5.
>
> 0.5 (18/10/2026): vòng đời phiên (kết thúc khi đóng tab / đăng xuất); lớp chặn câu hỏi ngoài phạm vi
> ([04](./04-luong-hoi-dap.md) mục 4.11); chuyển câu hỏi cho nhân viên bằng yêu cầu hỗ trợ, gồm trang xử lý của nhân
> viên ([14](./14-yeu-cau-ho-tro.md)); lý do không dùng Graph RAG.
>
> 0.6 (18/10/2026): tách đoạn để tìm (≤ 400 token) và mục để đọc (≤ 1.200 token, small-to-big); làm rõ cửa sổ lịch
> sử 6 lượt và mốc 20 lượt; chuẩn hóa khi kiểm bằng chứng của verifier; phương án hai pha khi model không gọi tool
> và trả JSON cùng lúc; sửa `unaccent` (hàm IMMUTABLE); đánh đổi kiến trúc ([02](./02-kien-truc.md) mục 2.7); bản
> thử nhỏ ở giai đoạn 0; bỏ việc chuyển câu trả lời của nhân viên thành tài liệu.

## Tóm tắt

Một **trợ lý hội thoại nằm ngay trên nền tảng** giúp học viên và gia sư:

1. **Hỏi đáp quy định** — trả lời dựa trên **kho tri thức nội bộ**, có trích dẫn nguồn, **mỗi khẳng định được
   kiểm chứng** với đoạn được trích; không đủ căn cứ thì từ chối trả lời ("chưa có thông tin").
2. **Hướng dẫn thao tác** — các bước làm theo đúng vai trò, kèm nút dẫn tới đúng trang của frontend.
3. **Tra cứu nhanh** — đọc dữ liệu của **chính người hỏi** (hợp đồng, kỳ thanh toán, ví, rút tiền, khiếu
   nại, tin đăng…) qua các API **chỉ-đọc** sẵn có của backend.

Trợ lý chạy trong **service riêng `assistant-service`** (Python/FastAPI). Bên trong là **một agent đơn** (vòng
lặp ReAct ngắn: truy xuất → gọi model → tool → trả lời) được bao bởi một **harness gồm 6 hook middleware**;
mọi quy tắc an toàn nằm ở hook (code), không chỉ trong prompt. Embedding dùng **bge-m3** của `ai-service`.
Kho tri thức là **dữ liệu trong DB**, **admin** soạn, duyệt và công bố qua trang quản trị; bản khởi tạo được
sinh từ code và nạp vào dưới dạng nháp.

## Các quyết định chính

| Mã | Quyết định | Tài liệu |
|---|---|---|
| QĐ1 | Service riêng `assistant-service`, không gộp vào `ai-service` | [01](./01-pham-vi-va-quyet-dinh.md), [02](./02-kien-truc.md) |
| QĐ2 | Frontend chỉ gọi backend; backend chuyển tiếp sang `assistant-service` (mạng nội bộ) | [02](./02-kien-truc.md), [07](./07-api.md) |
| QĐ3 | RAG truy xuất lai (vector + full-text, rerank), lọc theo vai trò, bắt buộc trích dẫn | [04](./04-luong-hoi-dap.md) |
| QĐ4 | Kho tri thức lưu trong DB, **admin** soạn / duyệt / công bố; bản khởi tạo sinh từ code, nạp dạng nháp | [03](./03-kho-tri-thuc.md) |
| QĐ5 | Tra cứu bằng tool calling, **chỉ GET**, token của chính người hỏi do harness gắn, lọc trường trả về | [05](./05-cong-cu-tra-cuu.md) |
| QĐ6 | Schema DB riêng `assistant`, role DB riêng, **không đọc bảng nghiệp vụ** | [06](./06-du-lieu-va-schema.md), [09](./09-bao-mat-va-quyen-rieng-tu.md) |
| QĐ7 | Không thao tác ghi (hủy, thanh toán, rút tiền…) — chỉ hướng dẫn | [01](./01-pham-vi-va-quyet-dinh.md) |
| QĐ8 | Embedding **bge-m3** qua `ai-service /embed` (1024 chiều), rerank `ai-service /rerank`; LLM qua interface | [02](./02-kien-truc.md), [04](./04-luong-hoi-dap.md) |
| QĐ9 | Chỉ người đã đăng nhập (`STUDENT`, `TUTOR`); không có tool tra cứu cho nhân viên | [01](./01-pham-vi-va-quyet-dinh.md) |
| QĐ10 | **Agent đơn + harness 6 hook**; không multi-agent, không tree search | [02](./02-kien-truc.md), [12](./12-co-so-ly-thuyet.md) |
| QĐ11 | **Kiểm chứng trích dẫn** ở `after_model`: verifier tách ngữ cảnh, thử lại tối đa 1 lần, sau đó từ chối | [04](./04-luong-hoi-dap.md) |
| QĐ12 | Mọi thay đổi prompt / model / cấu hình phải qua **bộ hồi quy** trước khi áp dụng | [10](./10-danh-gia.md), [13](./13-thiet-ke-eval.md) |
| QĐ13 | Bộ nhớ chỉ **trong một hội thoại** (phiên kết thúc khi đóng tab / đăng xuất) (cửa sổ 6 lượt + trạng thái phiên do code cập nhật); **không** bộ nhớ dài hạn / xuyên phiên — dữ liệu người dùng lấy mới qua tool; agent không tự ghi bộ nhớ bền vững | [04](./04-luong-hoi-dap.md), [12](./12-co-so-ly-thuyet.md) |
| QĐ14 | Lớp chặn câu hỏi ngoài phạm vi trước khi gọi model | [04](./04-luong-hoi-dap.md) |
| QĐ15 | Chuyển câu hỏi cho nhân viên bằng yêu cầu hỗ trợ (người dùng tự gửi; nhân viên `USER_SUPPORT` xử lý) | [14](./14-yeu-cau-ho-tro.md) |
| QĐ16 | Không dùng Graph RAG | [12](./12-co-so-ly-thuyet.md) |

## Mục lục

1. [Phạm vi và quyết định](./01-pham-vi-va-quyet-dinh.md)
2. [Kiến trúc tổng thể và harness](./02-kien-truc.md)
3. [Kho tri thức — soạn, duyệt, công bố](./03-kho-tri-thuc.md)
4. [Luồng hỏi đáp (RAG) và kiểm chứng trích dẫn](./04-luong-hoi-dap.md)
5. [Công cụ tra cứu dữ liệu người dùng](./05-cong-cu-tra-cuu.md)
6. [Dữ liệu và schema](./06-du-lieu-va-schema.md)
7. [API](./07-api.md)
8. [Giao diện](./08-giao-dien.md)
9. [Bảo mật và quyền riêng tư](./09-bao-mat-va-quyen-rieng-tu.md)
10. [Đánh giá](./10-danh-gia.md)
11. [Lộ trình, vận hành, rủi ro và hướng phát triển](./11-lo-trinh-va-van-hanh.md)
12. [Cơ sở lý thuyết](./12-co-so-ly-thuyet.md)
13. [Thiết kế kiểm thử và eval](./13-thiet-ke-eval.md)
14. [Chuyển câu hỏi cho nhân viên (yêu cầu hỗ trợ)](./14-yeu-cau-ho-tro.md)

## Thuật ngữ

| Thuật ngữ | Nghĩa |
|---|---|
| Trợ lý / assistant | Chatbot hỗ trợ người dùng mô tả trong bộ tài liệu này |
| Kho tri thức (KB) | Tập tài liệu quy định, hướng dẫn, FAQ mà trợ lý được phép dùng để trả lời |
| Tài liệu / document | Một bài trong kho tri thức (nội dung Markdown + metadata), có nhiều phiên bản |
| Đoạn / chunk | Phần nhỏ của tài liệu (theo heading) — đơn vị được truy xuất và trích dẫn |
| Đối tượng / audience | Vai trò được phép thấy một tài liệu: `PUBLIC`, `STUDENT`, `TUTOR` |
| Ý định / intent | Loại câu hỏi: `POLICY`, `HOWTO`, `LOOKUP`, `CHITCHAT`, `OUT_OF_SCOPE` |
| Tool | Hàm tra cứu mà LLM được phép **đề nghị** gọi; mỗi tool ánh xạ tới một endpoint GET của backend |
| Lượt / turn | Một câu hỏi của người dùng và câu trả lời của trợ lý; cũng là một lần chạy agent |
| Harness | Toàn bộ code bao quanh model: prompt, tool, middleware |
| Hook / middleware | Đoạn code chạy tại một điểm cố định của vòng lặp agent (6 hook, [02](./02-kien-truc.md)) |
| Actor | Lượt gọi model sinh câu trả lời |
| Verifier | Lượt gọi model riêng kiểm chứng từng khẳng định với đoạn được trích |
| Grounded | Câu trả lời mà mọi khẳng định đều có căn cứ trong đoạn tài liệu hoặc kết quả tool |
| Abstain | Từ chối trả lời khi không đủ căn cứ |
| Phiên | Một `conversation`: bắt đầu khi widget chưa có `conversationId`, kết thúc khi đóng tab hoặc đăng xuất |
| Yêu cầu hỗ trợ | Câu hỏi người dùng tự gửi cho nhân viên khi trợ lý không trả lời được |
| Trace / span | Dấu vết một lượt (root span) và từng lời gọi model / tool bên trong (span con) |
| Trạng thái phiên / `session_state` | Bộ nhớ ngắn hạn có cấu trúc của một hội thoại: các đối tượng đang bàn (`focus`) |
| Tombstone | Đánh dấu đã xóa để dữ liệu không bao giờ được dùng lại, trước khi xóa vật lý |
