# 2. Kiến trúc tổng thể và harness

## 2.1. Thành phần

### Sơ đồ tổng quan

Bốn tầng, mọi mũi tên đi từ trên xuống. API nghiệp vụ thuộc backend nhưng vẽ ở tầng dưới cùng vì với trợ lý nó
chỉ là một nguồn dữ liệu (tool tra cứu gọi tới bằng token của người hỏi).

```mermaid
flowchart TB
    subgraph FE["Frontend · React"]
        direction LR
        W["Widget chat<br/>học viên · gia sư"]
        ADM["Trang quản trị<br/>kho tri thức · admin"]
    end

    subgraph GWL["Backend · cổng trợ lý"]
        GW["AssistantController · AdminAssistantController<br/>→ AssistantProxyService<br/>JWT · rate limit · timeout"]
    end

    subgraph AS["assistant-service · FastAPI · nội bộ"]
        direction LR
        AG["Agent hỏi đáp<br/>agent đơn + harness 6 hook"]
        KB["Quản lý kho tri thức<br/>công bố · chia đoạn · embed"]
    end

    subgraph EXT["Dữ liệu và dịch vụ"]
        direction LR
        BIZ["Backend · API nghiệp vụ<br/>chỉ GET /me"]
        LLM["LLM API"]
        AIS["ai-service<br/>embed · rerank"]
        DB[("PostgreSQL + pgvector<br/>schema assistant")]
    end

    W --> GW
    ADM --> GW
    GW -->|hỏi đáp| AG
    GW -->|quản trị| KB
    AG -->|"tool tra cứu<br/>token người hỏi"| BIZ
    AG --> LLM
    AG --> AIS
    AG --> DB
    KB --> AIS
    KB --> DB
```

| Tầng | Thành phần | Vai trò |
|---|---|---|
| Frontend | Widget chat | Nút nổi trong `StudentLayout`, `TutorLayout` ([08](./08-giao-dien.md)); hộp thoại gửi câu hỏi cho nhân viên |
| | Trang yêu cầu hỗ trợ | Học viên / gia sư xem câu trả lời; nhân viên xử lý hàng chờ ([14](./14-yeu-cau-ho-tro.md)) |
| | Trang quản trị kho tri thức | Admin soạn, công bố, khôi phục tài liệu; xem câu hỏi chưa trả lời được |
| Backend | `AssistantController`, `AdminAssistantController` | Nhận request, kiểm vai trò (`STUDENT` / `TUTOR`; `ADMIN`) |
| | `AssistantProxyService` | Rate limit; gọi `assistant-service` với `X-Internal-Token`, `X-User-*`, chuyển tiếp `Authorization`; timeout |
| | API nghiệp vụ sẵn có | Nguồn dữ liệu cho tool tra cứu — không sửa gì |
| | Yêu cầu hỗ trợ (mới) | Bảng, API, thông báo cho việc chuyển câu hỏi cho nhân viên `USER_SUPPORT` ([14](./14-yeu-cau-ho-tro.md)) |
| assistant-service | Agent hỏi đáp | `AgentRunner` + chuỗi middleware (mục 2.3); `Retriever`, `ToolRegistry` + `BackendClient`, `CitationVerifier` |
| | Quản lý kho tri thức | `KnowledgeBaseService`: phiên bản, công bố, chia đoạn, embed; import bản khởi tạo từ `knowledge-base/` trong repo |
| | Nhà cung cấp | `LlmProvider` (LLM API), `EmbeddingProvider` (`ai-service`) — đổi được bằng cấu hình |
| Dùng chung | PostgreSQL + pgvector | Schema `assistant` riêng ([06](./06-du-lieu-va-schema.md)) |
| | `ai-service` | Embed (bge-m3) và rerank, dùng chung với tính năng đề xuất gia sư |
| | LLM API | Viết lại câu hỏi, sinh câu trả lời, kiểm chứng |

### Một lượt hỏi đáp bên trong `assistant-service`

```mermaid
flowchart LR
    Q(["Câu hỏi"]) --> P1["Bước 1 · Chuẩn bị<br/>kiểm tra đầu vào<br/>nạp lịch sử + focus"]
    P1 --> P2["Bước 2 · Tìm tài liệu<br/>viết lại câu hỏi<br/>vector + full-text + rerank"]
    P2 --> P3["Bước 3 · Sinh câu trả lời<br/>LLM"]
    P3 <-->|"cần dữ liệu cá nhân<br/>tối đa 3 lần"| T["Tool tra cứu<br/>qua ToolGate"]
    P3 --> P4{"Bước 4<br/>Kiểm chứng<br/>trích dẫn"}
    P4 -->|đạt| OK(["Trả lời + lưu"])
    P4 -.->|"không đạt<br/>thử lại 1 lần"| P3
    P4 -->|"vẫn không đạt"| NO(["Từ chối trả lời<br/>gợi ý kênh hỗ trợ"])
```

Chi tiết từng bước và hook tương ứng: mục 2.3 và [04](./04-luong-hoi-dap.md).

Nguyên tắc:

- **Frontend không gọi thẳng `assistant-service`** (QĐ2).
- **`assistant-service` không đọc bảng nghiệp vụ.** Dữ liệu người dùng chỉ lấy qua API GET của backend bằng
  token của chính người đó (QĐ5, QĐ6).
- **Kho tri thức là dữ liệu.** Admin công bố tài liệu → chunk + embed ngay; không cần sửa code hay deploy lại.
- **Mọi quy tắc an toàn là code ở hook**, prompt chỉ mô tả hành vi mong muốn (mục 2.4).
- **Lỗi không lan.** `assistant-service` hoặc `ai-service` tắt → API trợ lý trả `503`, phần còn lại của nền
  tảng không bị ảnh hưởng.

## 2.2. Agent đơn — vì sao không phức tạp hơn

Một lượt hội thoại chỉ cần ba năng lực: **truy xuất**, **gọi tool có sẵn**, **trả kết quả có cấu trúc**. Vì vậy:

| Lựa chọn | Dùng? | Lý do |
|---|---|---|
| Vòng lặp ReAct ngắn (model ↔ tool, tối đa 3 vòng) | **Có** | Đủ cho câu hỏi tra cứu cần 1–2 lời gọi API |
| Lớp critic (kiểm chứng sau khi sinh) | **Có**, chỉ cho câu trả lời cuối | Bắt lỗi trích dẫn sai; thử lại tối đa 1 lần |
| Multi-agent (orchestrator–worker) | Không | Không có subtask song song thật; thêm chi phí điều phối và khó debug |
| Tree search (LATS / ToT) | Không | Chi phí gấp nhiều lần, lợi ích không tương xứng với câu hỏi ngắn |
| Plan file, compaction bằng LLM | Không | Hội thoại ngắn; loại bỏ có cấu trúc (eviction) là đủ |

Tín hiệu để xem xét lại: số tool mỗi vai trò vượt ~15 mà chọn tool động không đủ; câu hỏi cần nhiều bước phụ
thuộc nhau. Căn cứ lý thuyết ở [12](./12-co-so-ly-thuyet.md).

## 2.3. Harness: 6 hook middleware

Vòng lặp agent (`AgentRunner`) gọi các middleware tại 6 điểm cố định. Mỗi middleware là một lớp Python cài đặt
một hoặc vài hook; thứ tự đăng ký quyết định thứ tự chạy.

```mermaid
flowchart TB
    S([Yêu cầu từ backend]) --> BA["① before_agent<br/>Trace · Budget · InputGuard · Memory ·<br/>Query · ScopeGuard · Retrieval"]
    BA --> BM["② before_model<br/>Memory · ContextInjection · ToolSelection"]
    BM --> WM["③ wrap_model_call<br/>ModelCall (timeout, retry, đo token)"]
    WM --> AM["④ after_model<br/>OutputSchema · LoopGuard · Budget"]
    AM -->|có tool_call| WT["⑤ wrap_tool_call<br/>ToolGate (allowlist, gắn token,<br/>kiểm ID, lọc kết quả, chặn lặp)"]
    WT --> BM
    AM -->|câu trả lời cuối| CV["④' after_model (câu trả lời cuối)<br/>CitationVerifier · RouteGuard · Redact"]
    CV -->|không đạt, còn lượt thử| BM
    CV -->|đạt / hết lượt → abstain| AA["⑥ after_agent<br/>Memory · Persist · Budget (chốt) · Trace (đóng)"]
    AA --> E([Trả kết quả])
```

| Middleware | Hook | Việc làm |
|---|---|---|
| `TraceMiddleware` | ①, ③, ⑤, ⑥ | Mở root span theo `X-Request-Id`; mỗi lời gọi model / tool là một span con (thời gian, token, trạng thái); đóng span ở ⑥ |
| `BudgetMiddleware` | ①, ④, ⑤, ⑥ | Khởi tạo hạn mức mỗi lượt: ≤ 4 lời gọi model (không tính verifier), ≤ 3 lời gọi tool, ≤ 40.000 token, ≤ 25 giây. Trừ dần; chạm trần → dừng có kiểm soát (circuit breaker), trả phần đã có + ghi `fallback_reason = BUDGET` |
| `InputGuardMiddleware` | ① | Độ dài ≤ 1.000 ký tự; hội thoại thuộc đúng người dùng; che SĐT, email, dãy số dài trước khi model thấy |
| `QueryMiddleware` | ① | Viết lại câu hỏi (khi có lịch sử) và embed câu hỏi qua `ai-service` (một lần mỗi lượt; vector dùng chung cho ScopeGuard và Retrieval) |
| `ScopeGuardMiddleware` | ① | Lớp chặn câu hỏi ngoài phạm vi: luật → so khớp embedding với câu mẫu; ngoài phạm vi → trả câu từ chối mẫu, dừng lượt (không truy xuất, không gọi Actor) ([04](./04-luong-hoi-dap.md) mục 4.11) |
| `RetrievalMiddleware` | ① | Truy xuất lai, rerank, lọc audience; đặt kết quả vào state (chạy **một lần** mỗi lượt) |
| `MemoryMiddleware` | ①, ②, ⑥ | Bộ nhớ ngắn hạn ([04](./04-luong-hoi-dap.md) mục 4.10). ① nạp lịch sử + `session_state` của hội thoại (đã kiểm quyền sở hữu). ② cửa sổ trượt 6 lượt, **loại bỏ có cấu trúc** (lượt cũ chỉ giữ câu hỏi + câu trả lời cuối, bỏ kết quả tool thô). ⑥ cập nhật `session_state.focus` từ kết quả tool |
| `ContextInjectionMiddleware` | ② | Dựng ngữ cảnh theo **thứ tự lớp và ngân sách token từng lớp** ([04](./04-luong-hoi-dap.md) mục 4.10): system prompt theo vai trò (có phiên bản, không bao giờ cắt), tool, ngữ cảnh người dùng, trạng thái phiên, đoạn `[D1]…[Dk]`, kết quả tool, lịch sử; khi thử lại sau kiểm chứng: chèn **góp ý** của verifier |
| `ToolSelectionMiddleware` | ② | **Chọn tool động**: chỉ tool của vai trò; ưu tiên tool cùng nhóm chức năng (`features`) với các đoạn truy xuất được / trang đang mở; tối đa 6 tool mỗi lời gọi; câu hỏi `POLICY` thuần không cần tool → không gửi tool |
| `ModelCallMiddleware` | ③ | Timeout 15 giây; thử lại 1 lần khi lỗi mạng / 5xx; đếm token |
| `OutputSchemaMiddleware` | ④ | Đầu ra phải đúng JSON schema ([04](./04-luong-hoi-dap.md) mục 4.5); sai → yêu cầu sinh lại 1 lần, vẫn sai → abstain |
| `LoopGuardMiddleware` | ④, ⑤ | Phát hiện cùng tool + cùng tham số lần 2 → chặn ở ⑤ và báo model "đã gọi, dùng kết quả cũ"; quá số vòng → buộc trả lời |
| `ToolGateMiddleware` | ⑤ | Cổng gác mọi tool ([05](./05-cong-cu-tra-cuu.md)): tool có trong allowlist của vai trò; chỉ GET; **harness gắn `Authorization`**, model không thấy token; ID tham số phải xuất hiện trong kết quả tool trước đó; lọc trường, cắt danh sách, che dữ liệu; ánh xạ lỗi HTTP |
| `CitationVerifierMiddleware` | ④' | Kiểm chứng từng khẳng định với đoạn được trích ([04](./04-luong-hoi-dap.md) mục 4.6); không đạt → thử lại tối đa 1 lần kèm góp ý → vẫn không đạt → bỏ khẳng định sai hoặc abstain |
| `RouteGuardMiddleware` | ④' | `actions[].route` phải thuộc danh sách route; tham số route phải lấy từ kết quả tool |
| `RedactMiddleware` | ④', ⑥ | Che thông tin cá nhân trong nội dung trước khi lưu log |
| `PersistenceMiddleware` | ⑥ | Lưu tin nhắn, đoạn đã dùng, log tool (không lưu dữ liệu trả về), trace, kết quả kiểm chứng |

Cài đặt: tự viết `AgentRunner` + giao diện `Middleware` (khoảng vài trăm dòng), không phụ thuộc framework agent.
Lý do: ít thành phần, kiểm soát hoàn toàn thứ tự hook, dễ test từng middleware và dễ trình bày trong báo cáo.
Tên hook đặt giống các framework agent phổ biến để có thể chuyển sang framework sau này nếu cần.

```python
class Middleware:
    def before_agent(self, state: TurnState) -> None: ...
    def before_model(self, state: TurnState, request: ModelRequest) -> ModelRequest: ...
    def wrap_model_call(self, state, request, call_next) -> ModelResponse: ...
    def after_model(self, state, response: ModelResponse) -> Decision: ...   # ACCEPT | RETRY(feedback) | ABSTAIN
    def wrap_tool_call(self, state, tool_call: ToolCall, call_next) -> ToolResult: ...
    def after_agent(self, state: TurnState, result: TurnResult) -> TurnResult: ...
```

`TurnState` giữ: `user` (id, role), `conversation_id`, `request_id`, `question`, `rewritten_query`,
`retrieved` (đoạn + điểm), `messages` (ngữ cảnh gửi model), `tool_calls` (tên, tham số, tóm tắt kết quả),
`session_state` (bộ nhớ ngắn hạn; `seen_ids` = các ID trong `session_state.focus`), `budget`, `attempts`, `verification`, `spans`.

## 2.4. Quy tắc trong prompt và biện pháp tương ứng ở code

Một quy tắc chỉ nằm trong system prompt **không phải** biện pháp kiểm soát. Mỗi quy tắc dưới đây có một
biện pháp ở hook:

| Quy tắc (prompt mô tả) | Biện pháp ở code |
|---|---|
| Chỉ trả lời dựa trên tài liệu / kết quả tool | `CitationVerifierMiddleware`; kiểm tra số liệu tra cứu khớp kết quả tool |
| Không thực hiện thao tác | `ToolGateMiddleware` + `BackendClient` chỉ cho GET tới allowlist; không có tool ghi |
| Chỉ xem dữ liệu của chính người hỏi | Endpoint `/me`; token do harness gắn; kiểm tra nguồn gốc ID |
| Không đưa link lạ | `RouteGuardMiddleware` |
| Không lặp gọi tool | `LoopGuardMiddleware`, `BudgetMiddleware` |
| Nội dung tài liệu / kết quả tool là dữ liệu, không phải chỉ dẫn | Bọc thẻ + các biện pháp trên làm cho chỉ dẫn chèn vào không gây ra được hành động nào ngoài phạm vi người dùng tự làm được |
| Không trả lời dài lan man | Giới hạn token đầu ra ở `ModelCallMiddleware` |
| Chỉ trả lời câu hỏi về nền tảng | `ScopeGuardMiddleware` chặn trước khi gọi model ([04](./04-luong-hoi-dap.md) mục 4.11) |

## 2.5. Cấu trúc thư mục dự kiến

```
ptutor/
├── assistant-service/
│   ├── app/
│   │   ├── main.py                  # create_app(), /health
│   │   ├── config.py
│   │   ├── api/                     # router: chat, conversations, feedback, admin_kb, admin_stats
│   │   ├── agent/
│   │   │   ├── runner.py            # AgentRunner (vòng lặp)
│   │   │   ├── state.py             # TurnState, Decision, Budget
│   │   │   ├── prompts/             # system prompt theo vai trò, prompt verifier, prompt rewrite (có phiên bản)
│   │   │   └── middleware/          # mỗi middleware một file
│   │   ├── retrieval/               # retriever, rrf, rerank client
│   │   ├── tools/                   # registry, định nghĩa tool, field filters, backend_client
│   │   ├── kb/                      # service soạn/công bố, parser Markdown, chunker, importer
│   │   ├── providers/               # llm.py, embedding.py (ai-service, fake)
│   │   └── db/                      # model, session, repository
│   ├── migrations/                  # Alembic, chỉ schema assistant
│   ├── eval/                        # bộ câu hỏi mẫu, runner, báo cáo
│   ├── tests/
│   ├── Dockerfile
│   └── requirements.txt
├── knowledge-base/                  # bản khởi tạo (sinh từ code), import vào DB dạng nháp
│   ├── _templates/  _routes.yaml  _suggestions.yaml
│   └── chung/  hoc-vien/  gia-su/
├── backend/.../assistant/           # AssistantController, AdminAssistantController, AssistantProxyService, DTO
└── frontend/src/components/assistant/ · frontend/src/pages/admin/assistant/
```

## 2.6. Triển khai

Thêm vào `docker-compose.yml` theo cùng khuôn với `ai-service` (profile riêng, cổng chỉ bind `127.0.0.1`).
Hai container cùng mạng compose nên `assistant-service` gọi `ai-service` bằng tên service.

```yaml
  assistant-service:
    profiles: ["assistant"]
    build: ./assistant-service
    environment:
      ASSISTANT_INTERNAL_TOKEN: ${ASSISTANT_INTERNAL_TOKEN:-}
      ASSISTANT_DATABASE_URL: ${ASSISTANT_DATABASE_URL:-}        # role assistant_app, schema assistant
      ASSISTANT_BACKEND_BASE_URL: ${ASSISTANT_BACKEND_BASE_URL:-http://host.docker.internal:8080}
      ASSISTANT_EMBEDDING_PROVIDER: ai-service
      AI_SERVICE_URL: http://ai-service:8000
      AI_INTERNAL_TOKEN: ${AI_INTERNAL_TOKEN:-}
      ASSISTANT_LLM_MODEL: ${ASSISTANT_LLM_MODEL:-}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
    ports:
      - "127.0.0.1:${ASSISTANT_SERVICE_PORT:-8100}:8100"
    depends_on: [postgres, ai-service]
```

Chạy: `docker compose --profile ai --profile assistant up -d`. Danh sách đầy đủ biến môi trường ở
[11](./11-lo-trinh-va-van-hanh.md).

## 2.7. Đánh đổi kiến trúc

| Điểm | Đánh đổi | Lý do chọn / hướng xử lý sau |
|---|---|---|
| **Gọi vòng** backend → `assistant-service` → backend (tool tra cứu) | Hai service phụ thuộc lẫn nhau; một request giữ hai kết nối tới backend | Tool dùng chính token người hỏi nên hưởng nguyên phân quyền sẵn có, không viết lại logic quyền. Phương án khác — backend tự chạy tool, `assistant-service` chỉ trả "cần gọi tool X" — tránh gọi vòng nhưng chia logic agent ra hai nơi; không cần ở quy mô đồ án |
| **Chuỗi gọi đồng bộ dài** (frontend → backend → assistant → LLM / ai-service / backend) | Độ trễ cộng dồn; lỗi một mắt xích làm hỏng cả lượt | Timeout xếp bậc từ trong ra ngoài (25 s → 30 s → 35 s); lỗi tool / rerank có phương án dự phòng (mục 4.3, [05](./05-cong-cu-tra-cuu.md) mục 5.5). Streaming / bất đồng bộ để hướng phát triển |
| **Dùng chung `ai-service`** với tính năng đề xuất gia sư | Embed / rerank chạy CPU, hai tính năng tranh tài nguyên khi tải cao | Chấp nhận ở quy mô đồ án; cần thì chạy thêm instance `ai-service` riêng cho trợ lý (chỉ đổi `AI_SERVICE_URL`) |
