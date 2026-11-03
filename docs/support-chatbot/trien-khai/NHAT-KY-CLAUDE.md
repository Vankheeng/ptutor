# Nhật ký triển khai của Claude (bản tham khảo)

> Worktree `D:\DATN\ptutor-claude`, nhánh `feat/support-chatbot-claude` (tạo từ `feat/support-chatbot`, đã merge
> `dev@27f6eeb`). Phiên mới: đọc mục **Đang ở đâu** trước, rồi làm tiếp việc tiếp theo.

## Đang ở đâu

- **Chặng hiện tại:** 1 — **bước:** M1 xong, đang làm M2.
- **Việc tiếp theo:** M2 (`LlmProvider`, `ScriptedLlm`, `OpenAiLlm`, `POST /v1/chat`).
- **Chờ người dùng:** bật Docker Desktop (cần cho T1, nạp kho, compose, Playwright); chọn model LLM (cần cho T3–T4).

## Danh sách việc

- [x] Chuẩn bị: worktree, merge `dev`, chép `knowledge-base/`, `tool-catalog/`, kiểm tra danh mục khớp code
- [x] M1 Khung `assistant-service`: `/health`, cấu hình
- [ ] M2 `LlmProvider` (`ScriptedLlm`, `OpenAiLlm`), `/v1/chat`
- [ ] M3 Đọc và chia tài liệu
- [ ] M4 Postgres + pgvector + unaccent, Alembic, import
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
