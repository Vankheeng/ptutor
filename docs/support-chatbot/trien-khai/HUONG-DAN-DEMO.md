# Hướng dẫn chạy demo trợ lý hỗ trợ

Chạy cả hệ thống (Postgres, backend, assistant-service, frontend) bằng Docker. Đã chạy thử đúng các bước dưới đây ngày
2026-11-04 trên Windows 11 + Docker Desktop. Ảnh màn hình với model thật `gpt-4.1-mini`: [`demo/`](./demo/); bộ ảnh cũ chạy Actor giả lập:
[`demo/scripted/`](./demo/scripted/).

## 1. Chuẩn bị `.env`

Ở thư mục gốc repo, chép `.env.example` thành `.env` (không commit `.env`) rồi điền tối thiểu:

| Biến | Gợi ý tạo giá trị |
|---|---|
| `POSTGRES_PASSWORD` | chuỗi ngẫu nhiên |
| `JWT_SECRET` | `python -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(48)).decode())"` |
| `CITIZEN_ID_ENCRYPTION_KEY`, `BANK_ACCOUNT_ENCRYPTION_KEY` | như trên nhưng `token_bytes(32)` (đúng 32 byte) |
| `VNPAY_HASH_SECRET` | chuỗi ngẫu nhiên (demo không thanh toán thật) |
| `PAYMENT_*_FEE`, `VNPAY_TMN_CODE`, `VNPAY_*_URL` | để trống → compose dùng mặc định **chỉ cho demo** (phí 50.000 đ, mã `DEMO`, VNPay sandbox, `localhost`); triển khai thật phải đặt giá trị thật |
| `ASSISTANT_INTERNAL_TOKEN` | `python -c "import secrets;print(secrets.token_urlsafe(32))"` |
| `SPRING_PROFILES_ACTIVE` | **`prod,demo`** — bật seeder tài khoản demo. Compose mặc định `prod` (không có tài khoản demo) |
| `DEMO_USER_PASSWORD` | mật khẩu chung cho 4 tài khoản demo (≥ 8 ký tự) |
| `ASSISTANT_LLM_MODEL`, `OPENAI_API_KEY` | model thật (`gpt-4.1-mini` cho cả trả lời / viết lại / kiểm chứng). `OPENAI_BASE_URL` để trống = endpoint mặc định. **Không có khoá** → đặt `ASSISTANT_LLM_PROVIDER=scripted` (câu trả lời chép từ tài liệu, chỉ để xem luồng) |

Đã có Postgres khác chạy cổng 5432 / cùng tên container → đổi `POSTGRES_PORT`, `POSTGRES_CONTAINER_NAME`,
`POSTGRES_VOLUME_NAME` (ví dụ `5434`, `ptutor-demo-postgres`, `ptutor_demo_data`) để không đụng dữ liệu đang có.

## 2. Chạy

```bash
docker compose -p ptutor-demo --profile assistant up -d --build     # lần đầu build ~5–10 phút
# Nạp kho tri thức vào DB (một lần). --allow-todo CHỈ cho demo: công bố cả tài liệu còn câu hỏi todo.
docker compose -p ptutor-demo --profile assistant run --rm assistant-service kb-import --publish --allow-todo
docker restart ptutor-assistant-service                              # nạp lại cài đặt route / gợi ý vừa ghi
```

- Frontend: <http://127.0.0.1:8088> (dùng `127.0.0.1`; trên một số máy Docker Desktop không chuyển `localhost` qua
  IPv6). Backend: cổng `BACKEND_PORT` (mặc định 8080). assistant-service không mở cổng ra ngoài — chỉ backend gọi được.
- Backend chạy profile `prod,demo` (từ `.env`; thiếu dòng này thì chạy `prod` và không có tài khoản demo) với
  `TZ=Asia/Ho_Chi_Minh`; Flyway tạo đủ V1–V36; seeder `@Profile("demo")` tạo tài
  khoản (bỏ qua nếu email đã có, nên khởi động lại không nhân bản).
- Chỉ Postgres (như trước khi có trợ lý): `docker compose up -d postgres` — không cần profile.

## 3. Tài khoản demo (mật khẩu = `DEMO_USER_PASSWORD`)

| Email | Vai trò | Dùng để xem |
|---|---|---|
| `demo.hocvien@ptutor.local` | Học viên (ví 250.000 đ, 2 yêu cầu hỗ trợ) | Widget trợ lý, tra cứu, gửi câu hỏi cho nhân viên |
| `demo.giasu@ptutor.local` | Gia sư | Widget trợ lý phía gia sư |
| `demo.cskh@ptutor.local` | Nhân viên `CUSTOMER_SERVICE` | Hàng chờ yêu cầu hỗ trợ |
| `demo.admin@ptutor.local` | ADMIN | Kho tri thức, câu hỏi chưa trả lời, thống kê trợ lý |

## 4. Kịch bản demo

1. Đăng nhập học viên → trang **Ví** → bấm nút tròn góc phải → hỏi "Nạp ví tối thiểu bao nhiêu?" (trả lời từ kho tri
   thức, có chip trích dẫn) rồi "Số dư ví của tôi còn bao nhiêu?" (tra cứu dữ liệu thật qua backend: 250.000 đ).
2. Hỏi "Giải giúp tôi phương trình 2x + 3 = 11" → bị chặn ngoài phạm vi, kèm câu hỏi gợi ý.
3. Hỏi câu kho tri thức chưa có ("PTutor có chương trình giảm giá cho học sinh nghèo không?") → trợ lý từ chối, có nút
   **Gửi câu hỏi cho nhân viên** → hộp thoại (ô đính kèm hội thoại mặc định tắt) → mã yêu cầu `HT-…`.
4. Menu "⋯" của widget → **Yêu cầu hỗ trợ của tôi** → xem yêu cầu đã trả lời → **Đã giải quyết**.
5. Đăng nhập `demo.cskh` → **Yêu cầu hỗ trợ** → **Nhận xử lý** → trả lời → học viên nhận thông báo.
6. Đăng nhập `demo.admin` → **Trợ lý → Kho tri thức**: tài liệu có nhãn "Công bố khi còn câu hỏi mở" (do `--allow-todo`).
7. Đăng nhập gia sư rồi gọi thẳng `/api/v1/admin/support-requests` → 403 (phân quyền theo chức năng nhân viên).

## 5. Dọn dẹp

```bash
docker compose -p ptutor-demo --profile assistant down        # giữ dữ liệu
docker compose -p ptutor-demo --profile assistant down -v     # xoá cả volume của project demo
```

## 6. Ghi chú

- Dockerfile backend / frontend / assistant-service và các dịch vụ compose dưới profile `assistant` là **thêm mới** cho
  demo (chưa có trên `dev`).
- Ảnh trong `demo/` chụp bằng Playwright với model thật `gpt-4.1-mini` (12 lượt hỏi, ≈ 0,012 $) và embedding giả lập.
  Ảnh trong `demo/scripted/` là bộ cũ với `ASSISTANT_LLM_PROVIDER=scripted` (câu trả lời chép từ tài liệu).
- Khi chạy với khoá thật: sau khi demo xong, `down` (không chỉ `stop`) container assistant-service — container dừng vẫn
  giữ `OPENAI_API_KEY` trong cấu hình môi trường của nó.
- Ảnh hiển thị đúng giờ Việt Nam cho cả trường ghi theo giờ JVM (`createdAt`) lẫn trường ghi theo UTC (`answeredAt`)
  nhờ `supportFormat.ts`; các trang cũ của hệ thống vẫn lệch 7 giờ ở trường UTC — xem
  [BAO-CAO-DANH-GIA.md](./BAO-CAO-DANH-GIA.md) mục 10.
