# Nhật ký triển khai của Claude (bản tham khảo)

> Worktree `D:\DATN\ptutor-claude`, nhánh `feat/support-chatbot-claude` (tạo từ `feat/support-chatbot`, đã merge
> `dev@27f6eeb`). Phiên mới: đọc mục **Đang ở đâu** trước, rồi làm tiếp việc tiếp theo.

## Đang ở đâu

- **Chặng hiện tại:** 2 — **bước:** M4 xong (T1 viết, chưa chạy được vì chưa có Docker), bắt đầu M5.
- **Việc tiếp theo:** M5 (`EmbeddingProvider` fake / ai-service, embed khi công bố, `reembed`).
- **Chờ người dùng:** bật Docker Desktop (cần cho T1, nạp kho, compose, Playwright); chọn model LLM (cần cho T3–T4).

## Danh sách việc

- [x] Chuẩn bị: worktree, merge `dev`, chép `knowledge-base/`, `tool-catalog/`, kiểm tra danh mục khớp code
- [x] M1 Khung `assistant-service`: `/health`, cấu hình
- [x] M2 `LlmProvider` (`ScriptedLlm`, `OpenAiLlm`), `/v1/chat`
- [x] M3 Đọc và chia tài liệu
- [x] M4 Postgres + pgvector + unaccent, Alembic, import
- [ ] M5 `EmbeddingProvider` (`fake`, `ai-service`)
- [ ] M6 Truy xuất lai, RRF, rerank, hit@k
- [ ] M7 Trả lời có trích dẫn, small-to-big
- [ ] M8 `AgentRunner` + middleware cơ bản
- [ ] M9 Bộ nhớ hội thoại, viết lại câu hỏi, kết thúc phiên
- [ ] M10 Backend proxy (`AssistantController`, `AssistantProxyService`)
- [ ] M11 Widget chat
- [ ] M12 ScopeGuard
- [ ] M13 CitationVerifier, RouteGuard, Redact
- [ ] M14 ToolRegistry, BackendClient, ToolGate, LoopGuard, ToolSelection, mock
- [ ] M15 `eval.run`, `eval.compare`
- [ ] M16 Trang quản trị kho tri thức
- [ ] M17 Yêu cầu hỗ trợ
- [ ] Chặng 8: đánh giá đầy đủ (4b) và hoàn thiện (4c)

## Quyết định chung (áp cho mọi module)

| # | Quyết định | Lý do |
|---|---|---|
| Q-1 | Merge `dev@27f6eeb` vào nhánh (người dùng duyệt) | Danh mục công cụ, route, kho tri thức sinh từ commit này |
| Q-2 | Múi giờ: backend giữ giờ Việt Nam; chỉ trường ghi bằng `now(clock)` là UTC; `createdAt`, `updatedAt`, `occurredAt`, `signedAt` theo JVM (`ASSISTANT_BACKEND_JVM_TIME_ZONE=Asia/Ho_Chi_Minh`) | Quyết định của người dùng 2026-11-03. Đã sửa trên bản chép `tool-catalog/tools.yaml` (bản gốc ở nhánh người dùng vẫn theo quyết định cũ) |
| Q-3 | `--publish --allow-todo` chỉ cho eval/demo, đánh dấu `published_with_open_todo` | Người dùng duyệt |
| Q-4 | Code đồng bộ (không `async`), không dùng framework agent | Dễ đọc với người mới; thiết kế 02 mục 2.3 |

## Múi giờ — việc riêng cho nhóm

(sẽ bổ sung danh sách trang hiển thị lệch giờ ở chặng 8)

---

## M1 — Khung assistant-service

**Đã làm.** FastAPI `create_app()`, `/health`, `Settings` đọc biến `ASSISTANT_*`; kiểm tra token nội bộ và header
người hỏi (`app/api/deps.py`, dùng từ M2). Script `scripts/check_catalog.py` kiểm danh mục công cụ và route khớp code.

- File chính: `app/main.py`, `app/config.py`, `app/api/health.py`, `app/api/deps.py`, `tests/unit/test_health.py`.
- Chạy: `cd assistant-service; .venv\Scripts\uvicorn app.main:app --port 8100` rồi mở `http://127.0.0.1:8100/health`.
- Kiểm tra: `.venv\Scripts\python -m pytest` (4 test), `.venv\Scripts\ruff check .`, `.venv\Scripts\mypy app`.

**Khác thiết kế.** `/health` theo thiết kế 07 mục 7.5 (`ready | degraded | error`), không theo KE-HOACH M1
(`{"status": "ok"}`) — thiết kế ưu tiên hơn. Chưa có LLM → `degraded`.

**Còn dở.** Không.

**Đọc code này thế nào.**
1. Đọc `app/config.py` trước: mỗi tham số của trợ lý là một trường; tiền tố `ASSISTANT_` tự ghép với tên trường.
2. Rồi `app/main.py`: `create_app(settings)` nhận cấu hình làm tham số — đây là chỗ đáng học nhất: test truyền cấu
   hình riêng (`tests/conftest.py`) nên không phụ thuộc file `.env` trên máy.
3. `app/api/health.py`: mỗi thành phần đăng ký một hàm kiểm tra vào `app.state.health_checks`; module sau chỉ cần
   thêm một dòng.
4. `app/api/deps.py`: vì sao so token bằng `hmac.compare_digest` (chống đoán token theo thời gian phản hồi).

## M2 — Gọi model qua lớp trung gian

**Đã làm.** `LlmProvider` với `complete(LlmRequest) -> LlmResponse`; `ScriptedLlm` (theo danh sách bước hoặc theo
hàm `policy`), `OpenAiLlm` (Chat Completions, gọi tool, JSON schema `strict`, `store=False`, đổi lỗi thành
`LlmError(retryable=...)`). `POST /v1/chat` gọi model trực tiếp; không có khoá / model → 503 `ASSISTANT_UNAVAILABLE`.

- File chính: `app/providers/llm.py`, `app/api/chat.py`, `app/api/schemas.py`, `tests/unit/test_llm_and_chat.py`.
- Kiểm tra: `pytest` 13 test xanh (không mạng: `OpenAiLlm` được test bằng client giả).
- Chạy thật (khi có khoá): đặt `OPENAI_API_KEY`, `ASSISTANT_LLM_MODEL` trong `.env`, chạy uvicorn, gọi
  `POST /v1/chat` với header `X-Internal-Token`, `X-User-Id`, `X-User-Role: STUDENT`.

**Khác thiết kế / KE-HOACH.** KE-HOACH ghi `complete(messages) -> str`; bản này nhận cả `LlmRequest` (tool,
schema, timeout, mục đích) vì M7–M14 cần gọi tool và JSON — đổi chữ ký về sau sẽ phải sửa nhiều chỗ.

**Eval chặng 1.** Chưa có kho tri thức nên chưa có gì để đo chất lượng; khung test pytest (T0) đã dựng.

**Còn dở.** Chưa chạy thật với model (chưa chọn model).

**Đọc code này thế nào.**
1. `app/providers/llm.py` từ trên xuống: các dataclass (`ChatMessage`, `ToolSpec`, `LlmRequest`, `LlmResponse`) là
   "hợp đồng" giữa harness và model; `LlmProvider` chỉ có một hàm.
2. `ScriptedLlm`: đáng học — bản giả ghi lại mọi request (`requests`), nên test vừa điều khiển được câu trả lời,
   vừa kiểm tra được harness đã gửi gì cho model.
3. `OpenAiLlm.complete`: chỗ đổi lỗi của thư viện thành `LlmError(retryable=...)` — tầng trên không cần biết
   thư viện OpenAI.
4. `app/api/chat.py`: `llm is None` → 503, không phải 500.

## M3 — Đọc và chia tài liệu

**Đã làm.** `parse_document` (front matter YAML + thân bài), `chunk_document` (chia theo `##`/`###`, gộp mục ngắn
< 40 token vào mục kế cùng mục cha, mục dài > 400 token chia theo đoạn văn chồng lấn 40 token), `validate_document`
(bảng kiểm tra 3.3, trừ trùng `doc_key` cần DB), đếm token bằng tokenizer bge-m3.

- File chính: `app/kb/parser.py`, `app/kb/chunker.py`, `app/kb/tokens.py`, `app/kb/validate.py`, `app/kb/routes.py`,
  `app/text.py`, `tests/unit/test_kb_chunking.py`.
- Tải tokenizer (17 MB, một lần): `.venv\Scripts\python -m app.kb.tokens --download` → `models/` (git bỏ qua).
- Số liệu trên toàn kho (65 tài liệu, tokenizer bge-m3 thật): **373 đoạn**, dài nhất 275 token, trung bình 83 token,
  không đoạn nào vượt 400.

**Khác thiết kế.** Không có tokenizer thì đếm ước lượng `1,3 × số chữ` (hệ số đo trên chính kho: 21.773 chữ →
28.110 token). Test dùng bộ ước lượng để không cần file 17 MB; tên bộ đếm được ghi trong kết quả kiểm tra.
Phần mở đầu trước `##` đầu tiên (nếu có) thành một đoạn mang heading là tiêu đề tài liệu.

**Còn dở.** Không.

**Đọc code này thế nào.**
1. `app/kb/parser.py`: `split_front_matter` — cắt ở hai dòng `---`, phần giữa đưa cho `yaml.safe_load`.
2. `app/kb/chunker.py` theo thứ tự gọi trong `chunk_document`: `split_sections` (duyệt từng dòng, gặp heading thì
   "xả" bộ đệm) → `merge_short_sections` → `split_long_text`.
3. Đáng học: `search_text = heading_path + nội dung` — câu "Tối thiểu 10.000 đ" đứng một mình không biết nói về
   gì; thêm "Nạp ví > Hạn mức" thì vector và full-text đều tìm đúng.
4. `_tail` + `split_long_text`: chồng lấn để một điều kiện nằm ngay ranh giới vẫn có đủ ở một phần.

## M4 — Cơ sở dữ liệu và lưu kho tri thức

**Đã làm.** Model SQLAlchemy cho `kb_document`, `kb_document_version`, `kb_chunk`, `kb_setting`; migration Alembic
viết tay `0001` (extension `vector`, `unaccent`, hàm `assistant.f_unaccent`, chỉ mục HNSW một phần, GIN `tsv`, GIN
`audience`, B-tree cho small-to-big); `KnowledgeBaseService` (lưu nháp, kiểm tra, công bố nguyên tử, khôi phục phiên
bản, lưu trữ, bỏ nháp); `python -m app.kb.import` (`--overwrite-drafts`, `--publish`, `--allow-todo`); Flyway backend
`V25__create_assistant_schema.sql`; `python -m app.db.bootstrap` tạo role `assistant_app`.

- File chính: `app/db/models.py`, `migrations/versions/0001_kb_tables.py`, `app/kb/service.py`, `app/kb/importer.py`,
  `app/db/bootstrap.py`, `tests/integration/test_kb_service.py`.
- Chạy (khi có Postgres): `python -m app.db.bootstrap` → `alembic upgrade head` → `python -m app.kb.import --path ../knowledge-base`.
- Kiểm tra: 7 test T1 (`-m db`) **đã viết nhưng chưa chạy** — Docker Desktop chưa khởi động được; test tự bỏ qua
  kèm lý do. SQL của migration đã kiểm bằng `alembic upgrade head --sql`.

**Khác thiết kế.**
- Thêm cột `kb_document_version.published_with_open_todo` (điều kiện của người dùng cho `--allow-todo`) và
  `error_codes` (front matter có trường này; 06 chưa có cột).
- Eval dùng **database riêng** (`ptutor_eval`, schema vẫn là `assistant`) thay cho schema `assistant_eval` (13 mục
  13.3): cùng mục đích cách ly, nhưng không phải truyền tên schema vào mọi câu SQL. Test T1 dùng DB `ptutor_test`.
- `alembic.ini` để chữ không dấu: Alembic đọc file bằng mã hoá mặc định của Windows (cp1252), có dấu thì lỗi.
- Khôi phục phiên bản dùng trạng thái tạm `RESTORING` trong transaction (không thể là `DRAFT` vì tài liệu có thể
  đang có một bản nháp khác — chỉ mục "một nháp").

**Còn dở.** Chạy T1 khi có Docker. Embedding thêm ở M5 (hiện cột `embedding` để trống).

**Đọc code này thế nào.**
1. `app/db/models.py`: bảng nào giữ gì; chú ý `use_alter` cho khoá ngoại vòng `kb_document ↔ kb_document_version`.
2. `migrations/versions/0001_kb_tables.py`: đọc song song với 06 mục 6.3 — từng cột khớp bảng thiết kế.
3. `app/kb/service.py` → `_activate`: **đáng học nhất**. Thứ tự: kiểm tra → tắt đoạn của bản cũ → `flush` (để chỉ
   mục "một bản PUBLISHED" không báo trùng) → thêm đoạn mới → đổi bản active. Tất cả trong `session_factory.begin()`
   nên lỗi ở bất kỳ bước nào cũng quay lui, bản cũ vẫn chạy.
4. `_insert_chunks`: `tsv` tính bằng `to_tsvector('simple', assistant.f_unaccent(...))` ngay trong câu INSERT.
