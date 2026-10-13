# 11. Lộ trình, vận hành và rủi ro

## 11.1. Lộ trình

Nguyên tắc: **phần "máy" làm trước, phần "dữ liệu" bổ sung dần**; mỗi giai đoạn kết thúc bằng một bản chạy được
từ đầu đến cuối và một lần chạy đánh giá.

### Giai đoạn 0 — Khung và harness

- [ ] Khung `assistant-service`: FastAPI, `/health`, cấu hình, `LlmProvider`, `EmbeddingProvider` (`ai-service`, `fake`).
- [ ] `AgentRunner` + giao diện `Middleware`; `TraceMiddleware`, `BudgetMiddleware`, `InputGuardMiddleware`,
      `ModelCallMiddleware`, `OutputSchemaMiddleware`, `PersistenceMiddleware`; test từng middleware.
- [ ] Alembic: schema `assistant`, các bảng ở [06](./06-du-lieu-va-schema.md); Flyway backend: schema, `unaccent`.
- [ ] `KnowledgeBaseService`: tạo / lưu nháp / công bố / lưu trữ, chia đoạn ≤ 400 token, embed qua `ai-service`;
      importer bản khởi tạo từ `knowledge-base/`.
- [ ] Thêm service vào `docker-compose.yml` (profile `assistant`).
- [ ] Khung kiểm thử và eval: `ScriptedLlm`, `FakeEmbedding`, T0/T1, `eval.run`, `eval.compare`
      ([13](./13-thiet-ke-eval.md) mục 13.13 — việc eval của từng giai đoạn).

### Giai đoạn 1 — Hỏi đáp quy định và hướng dẫn

- [ ] `RetrievalMiddleware` (viết lại câu hỏi, truy xuất lai, rerank, lọc audience, ngưỡng), `MemoryMiddleware`,
      `ContextInjectionMiddleware`.
- [ ] `CitationVerifierMiddleware`, `RouteGuardMiddleware`, `RedactMiddleware`; đo chất lượng verifier (10.2).
- [ ] Backend: `AssistantController`, `AssistantProxyService`, rate limit, `SecurityConstants.ASSISTANT_API`.
- [ ] Frontend: widget trong `StudentLayout`, `TutorLayout`.
- [ ] Bộ tài liệu seed (~8 tài liệu, [03](./03-kho-tri-thuc.md) mục 3.7) + ~30 câu hỏi mẫu; thí nghiệm E1, E2.

### Giai đoạn 2 — Tra cứu (tool calling)

- [ ] `ToolRegistry`, `BackendClient` (chỉ GET, allowlist), `ToolGateMiddleware`, `LoopGuardMiddleware`,
      `ToolSelectionMiddleware`; chế độ mock; contract test với backend.
- [ ] 15 tool mỗi vai trò ([05](./05-cong-cu-tra-cuu.md) mục 5.3); tài liệu glossary cho các enum trạng thái.
- [ ] Mở rộng câu hỏi mẫu `LOOKUP`, hội thoại nhiều lượt + chỉ số trajectory; thí nghiệm E4.

### Giai đoạn 3 — Quản trị kho tri thức

- [ ] Backend `AdminAssistantController` + API 7.3.
- [ ] Frontend trang quản trị: danh sách, soạn, xem trước cách chia đoạn, công bố, khôi phục phiên bản, câu hỏi chưa
      trả lời được, thống kê ([08](./08-giao-dien.md) mục 8.2).

### Giai đoạn 4 — Nội dung đầy đủ (khi dự án hoàn thiện)

- [ ] Sinh bản khởi tạo từ code cho toàn bộ danh mục ([03](./03-kho-tri-thuc.md) mục 3.6) + báo cáo `todo`.
- [ ] Admin duyệt, trả lời `todo`, công bố; bộ câu hỏi mẫu đạt ~100 câu; đánh giá cuối + đánh giá với người dùng.


**Phụ thuộc:** nhánh recommendation (có `ai-service`) phải vào `dev` trước khi nạp kho tri thức thật; trước đó
dùng `EmbeddingProvider = fake` cho phát triển và test. Trang quản trị cần layout quản trị chung trên frontend.

## 11.2. Biến môi trường

### `assistant-service`

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `ASSISTANT_INTERNAL_TOKEN` | (bắt buộc) | Chuỗi bí mật dùng chung với backend |
| `ASSISTANT_DATABASE_URL` | (bắt buộc) | Kết nối bằng role `assistant_app` |
| `ASSISTANT_BACKEND_BASE_URL` | `http://127.0.0.1:8080` | Backend cho tool tra cứu |
| `ASSISTANT_TOOLS_MODE` | `live` | `live` \| `mock` \| `off` |
| `ASSISTANT_LLM_MODEL` | (bắt buộc khi bật) | Model Actor |
| `ASSISTANT_REWRITE_MODEL` | = `ASSISTANT_LLM_MODEL` | Có thể dùng model nhỏ hơn |
| `ASSISTANT_VERIFIER_MODEL` | = `ASSISTANT_LLM_MODEL` | Nên khác Actor nếu có điều kiện |
| `OPENAI_API_KEY` | (trống) | Trống → `/health` báo `llm: disabled`, `/v1/chat` trả 503 |
| `ASSISTANT_EMBEDDING_PROVIDER` | `ai-service` | `ai-service` \| `fake` |
| `AI_SERVICE_URL` / `AI_INTERNAL_TOKEN` | `http://127.0.0.1:8000` / (bắt buộc) | Gọi `/embed`, `/rerank` |
| `ASSISTANT_RERANK_ENABLED` | `true` | Tắt → dùng thứ tự RRF |
| `ASSISTANT_MIN_RERANK_SCORE`, `ASSISTANT_MIN_COSINE`, `ASSISTANT_CONTEXT_K`, `ASSISTANT_HISTORY_TURNS` | 0,3 / 0,45 / 6 / 6 | [04](./04-luong-hoi-dap.md) mục 4.9 |
| `ASSISTANT_MAX_TOOL_CALLS`, `ASSISTANT_MAX_MODEL_CALLS`, `ASSISTANT_MAX_VERIFY_RETRIES`, `ASSISTANT_TURN_TOKEN_BUDGET` | 3 / 4 / 1 / 40000 | Ngân sách mỗi lượt |
| `ASSISTANT_HISTORY_TOKEN_BUDGET`, `ASSISTANT_MAX_CONVERSATION_TURNS` | 1500 / 20 | Bộ nhớ hội thoại ([04](./04-luong-hoi-dap.md) mục 4.10) |
| `ASSISTANT_RETENTION_DAYS` / `ASSISTANT_TRACE_RETENTION_DAYS` | 90 / 180 | [09](./09-bao-mat-va-quyen-rieng-tu.md) |

Thay đổi các tham số này cũng phải qua cổng hồi quy ([10](./10-danh-gia.md) mục 10.3).

### Backend (`application.yml`)

```yaml
app:
  assistant-service:
    enabled: ${APP_ASSISTANT_ENABLED:false}
    base-url: ${APP_ASSISTANT_SERVICE_BASE_URL:http://127.0.0.1:8100}
    internal-token: ${APP_ASSISTANT_SERVICE_INTERNAL_TOKEN:}
    timeout-ms: ${APP_ASSISTANT_TIMEOUT_MS:30000}
    rate-limit:
      messages: 20
      window-seconds: 300
```

`enabled=false` → API `/api/v1/assistant/**` trả `503 ASSISTANT_UNAVAILABLE`; frontend ẩn widget (biến Vite
`VITE_ASSISTANT_ENABLED` hoặc API cấu hình).

## 11.3. Theo dõi

- Log có cấu trúc kèm `X-Request-Id` ở backend và `assistant-service`; trace từng lượt trong `turn_trace`.
- Trang thống kê cho admin: số lượt, tỉ lệ `grounded` FULL / PARTIAL / NONE, lý do fallback, 👍/👎, độ trễ p50/p95,
  token, top câu hỏi chưa trả lời được.

## 11.4. Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Kho tri thức mỏng giai đoạn đầu | Cao | Seed tài liệu cho luồng ổn định; abstain đúng; log câu hỏi thiếu để admin bổ sung |
| Tài liệu lệch với code khi nghiệp vụ còn đổi | Cao | Trường `source`; sinh lại bản khởi tạo khi dự án hoàn thiện; chạy bộ câu hỏi mẫu sau mỗi đợt cập nhật nội dung; khôi phục phiên bản |
| Phụ thuộc nhánh recommendation (`ai-service`) | Trung bình | `EmbeddingProvider = fake` để phát triển; gộp nhánh trước giai đoạn 1 |
| Verifier chấm sai (bác nhầm → abstain thừa, hoặc bỏ lọt) | Trung bình | Rubric, ngữ cảnh tách biệt, kiểm tra trích đoạn bằng code, đo riêng chất lượng verifier |
| Độ trễ tăng do bước kiểm chứng | Trung bình | Chỉ kiểm chứng `POLICY` / `HOWTO`; một lời gọi cho cả câu trả lời; thử lại tối đa 1 lần |
| DTO backend đổi tên trường làm hỏng tool | Trung bình | Contract test ([05](./05-cong-cu-tra-cuu.md) mục 5.6) |
| Chi phí LLM | Trung bình | Ngân sách mỗi lượt, rate limit, model nhỏ cho viết lại câu hỏi |
| Tiếng Việt không dấu / viết tắt | Trung bình | Full-text trên văn bản đã `unaccent`; `keywords`; câu hỏi mẫu không dấu |

## 11.5. Ngoài phạm vi hiện tại (hướng phát triển)

Các phần sau được thiết kế nhưng **không làm** trong phạm vi đồ án để tập trung vào phần lõi. Ghi vào mục "Hạn chế và
hướng phát triển" của báo cáo cùng lý do và cách đang bù.

| Phần | Lý do không làm | Cách bù hiện tại |
|---|---|---|
| Tóm tắt hội thoại bằng LLM; `session_state.topics`, `unresolved` | Hội thoại hỗ trợ hiếm khi dài; lợi ích nhỏ so với thêm một lời gọi LLM | `focus` giữ đối tượng đang bàn; gợi ý trò chuyện mới khi vượt 20 lượt; câu bị từ chối vẫn được ghi log cho admin |
| Thử hỏi trên bản nháp (sandbox) | Cần thêm API + giao diện + chế độ truy xuất trên bản nháp | Xem trước cách chia đoạn; khôi phục phiên bản nếu công bố sai |
| Kiểm tra hồi quy tự động khi công bố | Cần chạy LLM trong lúc admin chờ, phức tạp | Nhóm chạy bộ câu hỏi mẫu bằng tay sau mỗi đợt cập nhật nội dung lớn |
| LLM chấm điểm + hiệu chỉnh bằng Cohen's kappa | Tốn công gán nhãn hai người, hiệu chỉnh | Chấm bằng code là chính; "đúng ý" chấm tay theo rubric trên tập test (~40 câu) |
| Chạy mỗi câu 5 lần | Chi phí, thời gian | Chạy 3 lần (`gate`), báo cáo khoảng tin cậy và câu dao động |
| Thí nghiệm E3 (chọn tool động), E5 (so sánh model embedding), E6 (so sánh cấu hình bộ nhớ) | Thời gian | Lập luận bằng lý thuyết ([12](./12-co-so-ly-thuyet.md)); số liệu token theo lớp vẫn ghi trong trace |
| Workflow CI cho `assistant-service` | Không bắt buộc với nhóm nhỏ | Chạy `pytest` trên máy trước khi gộp; ô đánh dấu trong mẫu pull request |
| Streaming SSE; danh sách lịch sử hội thoại trong widget | Không ảnh hưởng chức năng chính | Trả lời một lần; nút "Cuộc trò chuyện mới" |
| Benchmark B1: so sánh toàn hệ thống với một chatbot RAG đơn giản (chỉ vector, không kiểm chứng, không viết lại câu hỏi, gửi hết tool) | Thời gian | E1, E2, E4 so từng thành phần; B2 cho chi phí của từng thành phần |
| Benchmark B3: tải đồng thời (1 / 5 / 10 / 20 người dùng ảo, k6 hoặc Locust) | Thời gian; chi phí LLM khi chạy tải | B2 đo độ trễ khi chạy tuần tự; script có sẵn của `ai-service` (`benchmark_embed.py`, `benchmark_rerank.py`) đo tốc độ embed / rerank |
