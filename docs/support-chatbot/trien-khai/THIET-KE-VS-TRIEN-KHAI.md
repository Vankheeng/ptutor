# Đối chiếu thiết kế ↔ triển khai (bản tham khảo)

Mỗi dòng: phần thiết kế → nơi triển khai → trạng thái. "Khác" = có chủ ý, lý do ở mục "Khác thiết kế" của module tương
ứng trong [NHAT-KY-TRIEN-KHAI.md](./NHAT-KY-TRIEN-KHAI.md). "Chưa" = chưa làm, có ghi ở mục "Còn dở".

| Thiết kế | Triển khai | Trạng thái |
|---|---|---|
| 01 Phạm vi, QĐ1–QĐ14 | Toàn bộ; QĐ7 (chỉ đọc) kiểm ở `app/tools/registry.py` + grader `no_write_calls` | Đúng |
| 02 Kiến trúc: backend là cửa vào duy nhất | `AssistantController`, `AssistantProxyService` (token nội bộ, `X-User-*`) | Khác: file theo cấu trúc sẵn có của backend (M10) |
| 02 Thứ tự hook / middleware | `app/agent/harness.py`, `runner.py` | Khác: `after_agent` theo thứ tự đăng ký; ToolSelection trước ContextInjection (M8) |
| 03 Kho tri thức: front matter, kiểm tra, chia đoạn ≤ 400 token | `app/kb/parser.py`, `validate.py`, `chunker.py` | Đúng; thêm cột `published_with_open_todo`, `error_codes` (M4) |
| 03 Nạp và công bố, `--allow-todo` | `app/kb/importer.py` | Đúng theo điều kiện của người dùng; chỉ lệnh import có `--allow-todo` |
| 04.6 Trích dẫn + verifier + 1 lần thử lại | `middleware/citation_verifier.py`, `agent/verification.py` | Khác: verifier lỗi → UNSUPPORTED; PARTIAL dựng lại từ khẳng định đạt (M13) |
| 04.9 Truy xuất lai, RRF, rerank, NO_CONTEXT | `app/retrieval/*` | Khác: định nghĩa "full-text khớp" = phủ ≥ 50% từ khoá (M6); rerank có code, **chưa đo** |
| 04.10 Bộ nhớ: 6 lượt + sổ tay `focus` | `middleware/memory.py`, `query.py` | Khác: thêm `mentioned_turn` (chặng 8, do eval E4) |
| 04.11 Chặn ngoài phạm vi 3 tầng | `middleware/scope_guard.py`, `app/scope/*` | Khác: câu có từ nghiệp vụ không bị chặn ở tầng 1–2 (M12, chặng 8) |
| 05 Công cụ tra cứu | `app/tools/*`, `tool-catalog/tools.yaml` (38 công cụ) | Khác: theo `tools.yaml` thay bảng 16 công cụ (quyết định người dùng); `list_my_transactions` cố định `source=PAYMENT` |
| 05 Lọc kết quả, múi giờ | `app/tools/result_filter.py` | Đúng theo quyết định 2026-11-03 (nhãn `time_zone` từng trường) |
| 06 Schema `assistant` | Flyway `V35__create_assistant_schema.sql` + Alembic `migrations/` | Khác: V25 → V35 (trùng số với `dev`); eval / test dùng DB riêng (M4) |
| 07 API | `app/api/*`, backend `AssistantController`, `AdminAssistantController` | Khác: thêm `/documents/preview`, `/scope-exemplars` (M16), `suggestNewConversation` (M9) |
| 08 Widget | `frontend/src/components/assistant/*` | Đúng; Markdown tự hiển thị bằng phần tử React (M11) |
| 08 Trang quản trị kho tri thức | `frontend/src/pages/admin/assistant/*` | Đúng; chỉ ADMIN |
| 09 Bảo mật: GET "của tôi", lọc trường, che PII, ToolGate, cách ly hội thoại | `registry.py`, `result_filter.py`, `privacy/redact.py`, `tool_gate.py`, `input_guard.py` | Đúng; bộ an toàn 26/26 |
| 10 Đánh giá: chỉ số, E1 / E2 / E4, B2 | `eval/*`, [BAO-CAO-DANH-GIA.md](./BAO-CAO-DANH-GIA.md) | T0–T2 xong; T3 (`gpt-4.1-mini`) xong dev, test, E1, E2 — **chưa**: E4, đo riêng verifier (hết credit); T4 chưa; rerank chưa đo |
| 11 Vận hành, cấu hình | `app/config.py`, `.env.example`, `docker-compose.yml` (profile `assistant`) | Khác: mặc định embedding `fake`, rerank tắt (ai-service chưa vào `dev`) |
| 12 Cơ sở lý thuyết | — (tài liệu) | — |
| 13 Thiết kế eval | `eval/` | Khác: Actor trích xuất cho T0–T2; "ổn định" = đạt mọi lần trong k (M15); nhãn verifier chưa qua 2 người |
| 14 Yêu cầu hỗ trợ | Backend `SupportRequest*`, Flyway V36, `/excerpt`, frontend `components/support`, `pages/*/support-requests` | Khác: trang người dùng dùng chung; thêm `get_my_support_request`; tỉ lệ yêu cầu / lượt từ chối tách riêng từ chối (NONE) và một phần (PARTIAL), lấy mẫu số qua `GET /v1/admin/refusal-counts` |

## Thêm mới ngoài thiết kế

| Mục | Lý do |
|---|---|
| Dockerfile backend, frontend, assistant-service; dịch vụ compose dưới profile `assistant` | Yêu cầu demo bằng `docker compose --profile assistant up` (quyết định 2026-11-03) |
| Seeder demo `@Profile("demo")` (`backend/.../demo/DemoDataSeeder.java`) | Dữ liệu demo idempotent theo email, mật khẩu từ biến môi trường, không dùng Flyway |
| `ASSISTANT_LLM_PROVIDER=scripted` | Demo / chụp màn hình khi chưa có model (không bao giờ là mặc định) |
| `MemoryKnowledgeStore` | Test T0 và eval nhanh không cần DB (M6) |
| Sửa khoá volume trong `docker-compose.yml` | Trước đây đặt `POSTGRES_VOLUME_NAME` làm compose báo lỗi |
| Trường `coverage` (FULL / PARTIAL / NONE) trong JSON của Actor; `grounded` = min(verifier, coverage) | T3 dev: model trích câu "gần nghĩa" cho câu không có đáp án → NO_ANSWER 48,6% → 73,0% |
| `RouteGuard` che UUID chưa từng có trong kết quả tool trong văn bản trả lời (`mask_unseen_ids`) | T3: model nhắc lại mã người dùng tự gõ (thử đoán ID) |
| `parallel_tool_calls=False` khi gọi model | T3: model bắn 10 lời gọi song song trong một bước |
| Endpoint OpenAI mặc định ghi rõ khi `OPENAI_BASE_URL` rỗng | Compose đặt biến rỗng → SDK báo `Connection error` |
| Eval: trần chi phí theo USD, sổ chi tiêu chung (`pricing.yaml`, `BudgetLedger`); lỗi tài khoản (hết credit) dừng lần chạy | Yêu cầu ngân sách 10 $; T3 hết credit giữa chừng từng sinh báo cáo rác |
