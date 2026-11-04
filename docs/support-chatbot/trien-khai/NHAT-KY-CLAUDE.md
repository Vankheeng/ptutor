# Nhật ký triển khai của Claude (bản tham khảo)

> Worktree `D:\DATN\ptutor-claude`, nhánh `feat/support-chatbot-reference` (tạo từ `feat/support-chatbot`, đã merge
> `dev@27f6eeb`). Phiên mới: đọc mục **Đang ở đâu** trước, rồi làm tiếp việc tiếp theo.

## Đang ở đâu

- **Chặng hiện tại:** Chặng 8 / T3 — model thật gpt-4.1-mini (dev, test, E1, E2 xong; dừng vì hết credit)
- **Việc tiếp theo:** Nạp credit rồi chạy `bash eval/run_experiments_real.sh t3 rest` (E2 không thử lại, E4, verifier); chấm tay phiếu `cham-tay/sheet.csv`
- **Chờ người dùng:** chọn model LLM (cần cho T3–T4).
- **Môi trường:** Docker chạy (ổ C từng đầy → Docker dừng; giữ ổ C trống ≥ 5 GB). Postgres = container `ptutor-postgres`, cổng **5433**. DB phát triển của trợ lý: `ptutor_assistant_dev` (đã nạp 65 tài liệu — chưa nạp tài liệu M17, `--allow-todo`, đã embed lại bằng bge-m3). Container `ptutor-ai-service-claude` (cổng 8001, model mount chỉ đọc) đang **tắt** vì máy thiếu RAM — bật bằng `docker start ptutor-ai-service-claude`; job đo rerank bị hệ thống dừng giữa chừng, chưa có số. Test T1: đặt `ASSISTANT_TEST_ADMIN_DATABASE_URL=postgresql+psycopg://postgres:<mật khẩu>@127.0.0.1:5433/postgres`.

## Danh sách việc

- [x] Chuẩn bị: worktree, merge `dev`, chép `knowledge-base/`, `tool-catalog/`, kiểm tra danh mục khớp code
- [x] M1 Khung `assistant-service`: `/health`, cấu hình
- [x] M2 `LlmProvider` (`ScriptedLlm`, `OpenAiLlm`), `/v1/chat`
- [x] M3 Đọc và chia tài liệu
- [x] M4 Postgres + pgvector + unaccent, Alembic, import
- [x] M5 `EmbeddingProvider` (`fake`, `ai-service`)
- [x] M6 Truy xuất lai, RRF, rerank, hit@k
- [x] M7 Trả lời có trích dẫn, small-to-big
- [x] M8 `AgentRunner` + middleware cơ bản
- [x] M9 Bộ nhớ hội thoại, viết lại câu hỏi, kết thúc phiên
- [x] M10 Backend proxy (`AssistantController`, `AssistantProxyService`)
- [x] M11 Widget chat
- [x] M12 ScopeGuard
- [x] M13 CitationVerifier, RouteGuard, Redact
- [x] M14 ToolRegistry, BackendClient, ToolGate, LoopGuard, ToolSelection, mock
- [x] M15 `eval.run`, `eval.compare`
- [x] M16 Trang quản trị kho tri thức
- [x] M17 Yêu cầu hỗ trợ
- [x] Chặng 8: đánh giá đầy đủ (4b) và hoàn thiện (4c)

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
`V25__create_assistant_schema.sql` (sau đổi thành **V35** vì V25 đã có trên dev — xem M17); `python -m app.db.bootstrap` tạo role `assistant_app`.

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

## M5 — Vector hoá (embedding)

**Đã làm.** `EmbeddingProvider` với `FakeEmbedding` và `AiServiceEmbedding` (đúng API `ai-service` trên
`feat/tutor-recommendation`: `POST /embed {"texts"}` → `{"model","dimensions","embeddings"}`, header
`X-Internal-Token`, lô 32); công bố tài liệu embed `search_text` và lưu `embedding`, `embedding_model`; embed lỗi →
`KbError("EMBEDDING_FAILED")`, bản cũ vẫn chạy; lệnh `python -m app.kb.reembed [--all]`.

- File chính: `app/providers/embedding.py`, `app/providers/factory.py`, `app/kb/reembed.py`, `tests/unit/test_embedding.py`.
- Kiểm tra: 29 test T0 xanh; 9 test T1 (thêm 2 cho embedding) chờ Docker.

**Khác thiết kế.**
- **Mặc định `ASSISTANT_EMBEDDING_PROVIDER=fake`, `ASSISTANT_RERANK_ENABLED=false`** (11.2 ghi mặc định
  `ai-service` / bật) — theo yêu cầu của người dùng vì `ai-service` chưa vào `dev`.
- `FakeEmbedding` băm **từng từ, cặp từ và cụm 3 ký tự** (đã bỏ dấu) thay vì băm cả câu (13 mục 13.1 chỉ ghi "vector
  tất định từ băm nội dung"). Lý do: băm cả câu cho vector ngẫu nhiên → tìm theo vector vô nghĩa, E1 và ngưỡng
  `τ_in` không đo được gì. Băm đặc trưng vẫn tất định, không cần mạng, và cho cosine cao khi hai câu chung từ.
  Hạn chế: chỉ giống *từ ngữ*, không giống *nghĩa* — báo cáo phải ghi rõ.
- Lưu ý cho rerank (M6): `ai-service` cắt văn bản rerank ở 256 token (`AI_RERANK_MAX_TOKENS`), ngắn hơn giới hạn đoạn
  400 token.

**Còn dở.** Chạy với `ai-service` thật khi người dùng bật được.

**Đọc code này thế nào.**
1. `app/providers/embedding.py`: lớp cơ sở chỉ có `embed(texts) -> EmbeddingResult`.
2. `FakeEmbedding.vector` + `_features`: "feature hashing" — mỗi đặc trưng (từ, cặp từ, cụm 3 ký tự) băm ra một ô
   trong 1024 ô, cộng dồn, rồi chuẩn hoá độ dài về 1. Đáng học vì nó cho thấy cosine đo "độ chồng đặc trưng".
3. `AiServiceEmbedding.embed`: chia lô 32, kiểm `dimensions` để phát hiện nhầm model sớm.
4. `KnowledgeBaseService._insert_chunks`: embed **trước** khi ghi đoạn nào → lỗi thì quay lui sạch.

## M6 — Truy xuất lai, RRF, rerank

**Đã làm.** `KnowledgeStore` với `PgKnowledgeStore` (SQL pgvector `<=>`, `ts_rank_cd` trên `tsv` đã bỏ dấu, nối từ khoá
bằng OR) và `MemoryKnowledgeStore` (cùng thuật toán, chạy trong bộ nhớ); `rrf_merge` (k = 60); `Reranker`
(`AiServiceReranker` gọi `/rerank`, `DisabledReranker`); `expand_to_sections` (small-to-big, ≤ 1.200 token mỗi mục,
≤ 3.000 token tổng, tối đa 6 mục `D1…D6`); `Retriever` (lọc audience, RRF, ưu tiên trang đang mở, rerank, ngưỡng
`NO_CONTEXT`); script `python -m eval.hit_at_k` trên `cau-hoi-mau.csv`.

- File chính: `app/retrieval/store.py`, `rrf.py`, `rerank.py`, `expand.py`, `retriever.py`, `eval/hit_at_k.py`,
  `tests/unit/test_retrieval.py`, `tests/integration/test_pg_retrieval.py`.
- Kiểm tra: 52 test (T0 + T1 trên Postgres thật) xanh.

**Số liệu (220 câu, embedding `fake`, không rerank, ngưỡng mặc định, 2026-11-03):**

| Kho | hit@1 | hit@3 | **hit@6** | MRR | NO_CONTEXT |
|---|---|---|---|---|---|
| PostgreSQL (chính thức) | 0,609 | 0,782 | **0,891** | 0,710 | 0,9% |
| Trong bộ nhớ | 0,668 | 0,850 | 0,932 | 0,764 | 0,5% |
| Trong bộ nhớ, chỉ vector | 0,618 | 0,832 | 0,914 | 0,733 | 1,4% |

Theo loại (Postgres): POLICY 0,964 · HOWTO 0,972 · ERROR 0,917 · FAQ 0,846 · NO_ANSWER 0,803. Lưu ý: số này đo trên
**toàn bộ** 220 câu, chưa chia dev / test (chia ở M15); embedding `fake` chỉ giống *từ ngữ*, chưa phải bge-m3.

**Khác thiết kế.**
- "Full-text khớp" (ngưỡng NO_CONTEXT khi không rerank) thiết kế chưa định nghĩa → đặt là *đoạn tốt nhất chứa ≥ 50%
  từ khoá của câu hỏi* (`ASSISTANT_MIN_TEXT_COVERAGE`), hiệu chỉnh trên tập dev ở M15.
- Ưu tiên trang đang mở: cộng 0,004 vào điểm RRF (≈ 1/4 điểm hạng 1) — thiết kế chỉ ghi "điểm nhỏ".
- Full-text nối từ khoá bằng OR (`to_tsquery('a | b')`), bỏ từ dừng tiếng Việt *có dấu* trước khi bỏ dấu.
- Có thêm `MemoryKnowledgeStore` (thiết kế không có) để test T0 và đo nhanh không cần DB.

**Lỗi tìm ra khi đo.** Lọc từ dừng sau khi bỏ dấu làm mất "ví" (trùng "vì") và "tối" (trùng "tôi") → sửa lọc trước
khi bỏ dấu: hit@6 (bộ nhớ) 0,914 → 0,932.

**Câu tìm sai tiêu biểu** (`--misses`): câu hỏi về vai trò / bộ phận xử lý ("bộ phận nào xử lý hoàn tiền") bị kéo về
tài liệu trạng thái hoàn tiền; câu hỏi chung chung ("có phí tạo hợp đồng không") trùng từ với nhiều tài liệu hợp đồng.
Đây là giới hạn của so khớp từ ngữ — cần embedding theo nghĩa (bge-m3) hoặc rerank.

**Chạy lại với bge-m3 thật (sơ bộ, 220 câu, Postgres, chưa chia dev/test):** container riêng
`ptutor-ai-service-claude` (cổng 8001, image `ptutor-ai-service` có sẵn trên máy, model mount **chỉ đọc** từ
`D:\DATN\ptutor\ai-service\models`, token nội bộ ngẫu nhiên chỉ nằm trong env của container).

| Cấu hình | hit@1 | hit@3 | hit@6 | MRR |
|---|---|---|---|---|
| bge-m3, chỉ vector | 0,527 | 0,668 | 0,768 | 0,612 |
| bge-m3, vector + full-text (RRF) | 0,659 | 0,795 | 0,895 | 0,739 |

Embed lại 373 đoạn bằng CPU mất 2 phút 18 giây. Lô 32 đoạn dài hơn 10 giây → timeout embedding tính theo cỡ lô
(5 giây + 1,5 giây / văn bản).

**Còn dở.** Số chính thức của E1 (gồm cả rerank) chạy ở chặng 8 trên tập test.

**Đọc code này thế nào.**
1. `app/retrieval/retriever.py` → `retrieve`: đọc từ trên xuống là thấy đủ 6 bước của bảng 4.3.
2. `app/retrieval/rrf.py`: 10 dòng, có ví dụ tính tay trong docstring — **đáng học nhất**: vì sao gộp theo *hạng*
   chứ không cộng *điểm*.
3. `app/retrieval/store.py`: so `PgKnowledgeStore.text_search` (SQL) với `MemoryKnowledgeStore.text_search` (Python)
   để hiểu `tsvector` / `ts_rank_cd` làm gì; `query_terms` giải thích lỗi từ dừng.
4. `app/retrieval/expand.py`: `_pick_members` nới dần sang hai bên; `join_overlapping` bỏ phần chồng lấn khi ghép.

## M7 — Trả lời có trích dẫn

**Đã làm.** `ACTOR_OUTPUT_SCHEMA` + `parse_actor_output` (04 mục 4.5); prompt Actor có phiên bản
(`agent/prompts/actor_v1.md`); ngữ cảnh: `<user_context>`, `<routes>`, `<docs><doc id="D1" ...>`, `<question>`;
`/v1/chat` mới: truy xuất → `NO_CONTEXT` thì từ chối **không gọi model** → Actor (JSON schema strict) → bỏ khẳng
định có nguồn bịa → đổi `[D3]` thành `[1]` theo thứ tự xuất hiện + danh sách `citations` → bỏ action có route lạ →
cắt câu trả lời > 1.200 ký tự theo đoạn. `Services` gom mọi thành phần; `Clock` / `FixedClock`; `RouteCatalog`;
gợi ý câu hỏi theo trang (`kb/suggestions.py`).

- File chính: `app/agent/output_schema.py`, `app/agent/prompts/`, `app/agent/context.py`, `app/agent/answer.py`,
  `app/api/chat.py`, `app/services.py`, `app/clock.py`, `tests/unit/test_answer_with_citations.py`, `tests/fakes.py`.
- Chạy: `.venv\Scripts\uvicorn app.main:create_app --factory --port 8100` (đổi từ `app.main:app`: không dựng app khi
  import module, để test không phụ thuộc `.env`).
- Kiểm tra: 62 test xanh (T0 + T1).

**Khác thiết kế.**
- Câu trả lời từ chối (`NO_CONTEXT`, JSON hỏng) tạm ghi `intent = POLICY` (thiết kế không có intent riêng cho
  abstain; lý do nằm ở `fallback_reason` từ M8).
- M7 chỉ giữ action có route **không tham số** khớp danh sách; route có tham số do `RouteGuard` (M13) xử lý.
- `grounded` ở M7: `FULL` khi còn ≥ 1 khẳng định có nguồn hợp lệ; kiểm chứng thật (verifier) ở M13.

**Còn dở.** Chưa thử với model thật (chưa có khoá / model) — "hỏi 10 câu mẫu" của KE-HOACH M7 chạy khi có model.

**Đọc code này thế nào.**
1. `app/api/chat.py` → `chat`: đọc từ trên xuống — đây là toàn bộ luồng RAG tối thiểu trước khi tách middleware.
2. `app/agent/prompts/actor_v1.md` + `app/agent/context.py`: model nhận gì. Chú ý không có tên / email người hỏi.
3. `app/agent/answer.py` → `number_citations`: **đáng học nhất** — vì sao đánh số bằng code (luôn liên tục, nguồn
   bịa `[D9]` tự rơi mất) thay vì bảo model tự đánh `[1]`.
4. `tests/fakes.py`: cách dựng cả hệ thống bằng bản giả để test không cần mạng.

## M8 — Harness: AgentRunner và middleware

**Đã làm.** `TurnState` (phiếu theo dõi một lượt), `Decision` (ACCEPT / RETRY / ABSTAIN), `Budget`, `Middleware` với 6
hook, `AgentRunner` (vòng lặp model ↔ tool, giới hạn cứng 8 vòng), `Composer` (ghép câu trả lời / từ chối / "đang
bận"), trace (`timed`, `add_span`, span `SKIPPED`), `config_hash`. Tám middleware: Trace, Budget, InputGuard,
Retrieval, ContextInjection, ModelCall, OutputSchema, Persistence. Che thông tin cá nhân (`app/privacy/redact.py`).
Bảng hội thoại (migration `0002`): `conversation`, `message`, `message_citation`, `tool_call_log`, `turn_trace`,
`message_feedback`, `deletion_log`; `ConversationStore` (Postgres + bộ nhớ) có `purge` xoá có kiểm chứng.
`/v1/chat` giờ chỉ dựng `TurnState` → `AgentRunner.run`.

- File chính: `app/agent/state.py`, `app/agent/runner.py`, `app/agent/harness.py`, `app/agent/middleware/*.py`,
  `app/agent/compose.py`, `app/agent/trace.py`, `app/db/conversations.py`, `migrations/versions/0002_*.py`,
  `tests/unit/test_harness.py`, `tests/unit/test_redact.py`, `tests/integration/test_conversations.py`.
- Kiểm tra: toàn bộ test (T0 + T1) xanh; `ruff`, `mypy` sạch. Test T1 xoá hội thoại kiểm cả 6 bảng = 0 dòng.

**Khác thiết kế.**
- `after_agent` chạy **theo thứ tự đăng ký** (không đảo ngược) để Trace (đăng ký đầu) đóng span trước, Persistence
  (đăng ký cuối) lưu sau cùng.
- Thứ tự ở hook ②: ToolSelection đăng ký **trước** ContextInjection (thiết kế liệt kê ngược lại) vì ContextInjection
  cần biết đã chọn tool nào để tính ngân sách lớp 2 và để quyết "NO_CONTEXT + không tool → từ chối".
- Model / mạng lỗi sau 1 lần thử lại → câu "Trợ lý đang bận…" (`fallback_reason = TIMEOUT | MODEL_ERROR`, vẫn lưu
  trace) thay vì 503: người dùng không bị báo nhầm "chưa có thông tin chính thức".
- Thêm cột `message.citations` (jsonb): trích dẫn đã đánh số, để mở lại hội thoại cũ vẫn thấy chip `[1]`.
- Lịch sử đưa vào ngữ cảnh dạng các cặp tin `user` / `assistant` trước tin hỏi hiện tại (thiết kế chỉ nói "lớp 7").
- Khoá theo hội thoại (409 BUSY) dùng khoá trong tiến trình; chạy nhiều bản assistant-service cần
  `pg_advisory_lock` (ghi ở hướng phát triển).

**Còn dở.** Không (Memory / Query thêm ở M9).

**Đọc code này thế nào.**
1. `app/agent/harness.py` → `build_middlewares`: danh sách đăng ký chính là "dây chuyền" — đọc trước tiên.
2. `app/agent/runner.py` → `run` rồi `_loop`: mỗi vòng là ② → ③ → ④ → (⑤) ; chú ý runner không tự quyết điều gì
   về an toàn.
3. `_call_model` + `_bind_model`: **đáng học nhất** — cách xếp các `wrap_model_call` lồng nhau như củ hành; lớp ngoài
   (Trace) đo cả thời gian thử lại của lớp trong (ModelCall).
4. Một middleware ngắn để hiểu khuôn: `output_schema.py` (RETRY 1 lần rồi ABSTAIN), `budget.py` (cầu dao).
5. `tests/unit/test_harness.py` → `test_runner_calls_hooks_in_registration_order...`: in ra đúng thứ tự gọi hook.

## M9 — Bộ nhớ hội thoại

**Đã làm.** `MemoryMiddleware` (① nạp 6 lượt gần nhất — chỉ câu hỏi đã che + câu trả lời cuối — và `session_state`;
⑥ `update_focus`: thêm đối tượng **chỉ từ kết quả tool không bị chặn**, cập nhật `last_turn` khi được nhắc lại hoặc
dùng làm tham số, tối đa 20 mục); `QueryMiddleware` (viết lại câu hỏi khi có lịch sử, prompt `rewrite_v1`, timeout 3
giây, lỗi → dùng câu gốc; embed một lần mỗi lượt); `POST /v1/conversations/{id}/end`; khoá theo hội thoại →
`409 CONVERSATION_BUSY`; `409 CONVERSATION_ENDED`; `suggestNewConversation` khi tới lượt 20.

- File chính: `app/agent/middleware/memory.py`, `app/agent/middleware/query.py`, `app/agent/prompts/rewrite_v1.md`,
  `app/api/conversations.py`, `app/api/chat.py`, `tests/unit/test_memory.py`.
- Kiểm tra: 103 test xanh (T0 + T1). Có test của chính thiết kế 13 mục 13.1 `test_session_state_ignores_user_claims`.

**Khác thiết kế.**
- Thêm trường `suggestNewConversation` vào `ChatResponse` (thiết kế chỉ nói widget "gợi ý" khi vượt 20 lượt, chưa nói
  widget biết số lượt bằng cách nào).
- Việc nạp `session_state` chuyển từ InputGuard sang Memory (đúng bảng 02 mục 2.3: Memory ① "nạp lịch sử +
  session_state").
- Viết lại câu hỏi dùng 3 lượt gần nhất (cắt câu trả lời ở 400 ký tự) để lời gọi phụ ngắn và rẻ; Actor vẫn thấy đủ
  6 lượt.
- Câu hỏi đã viết lại được đưa cho Actor dưới dạng "(Câu hỏi đầy đủ: …)" kèm câu gốc, để Actor vẫn trả lời đúng
  giọng người hỏi.

**Còn dở.** `focus_items` sẽ được lớp lọc kết quả tool điền ở M14 (hiện chưa có tool nào chạy thật).

**Đọc code này thế nào.**
1. `app/agent/middleware/memory.py` → `update_focus`: **đáng học nhất** — vì sao chỉ đọc `record.focus_items` (dữ
   liệu backend trả) mà không đọc câu hỏi của người dùng: chặn được việc "cài" ID lạ vào bộ nhớ.
2. `app/agent/middleware/query.py` → `_rewrite`: lời gọi phụ có timeout riêng, lỗi thì lặng lẽ dùng câu gốc.
3. `app/api/chat.py`: `lock.acquire(blocking=False)` → 409 thay vì chờ; nhớ `finally: lock.release()`.
4. `tests/unit/test_memory.py` → `test_second_turn_with_pronoun_finds_document_through_rewrite`: luồng hai lượt đầy
   đủ, câu "còn cách làm thì sao?" tìm đúng mục "Cách nạp" nhờ câu đã viết lại.

## M10 — Backend Spring: cửa vào an toàn

**Đã làm.**
- Backend: `SecurityConstants.ASSISTANT_API` (`STUDENT`, `TUTOR`) và `ADMIN_ASSISTANT_API` (`ADMIN`) đăng ký trong
  `SecurityConfig`; `AssistantController` (`/api/v1/assistant/messages`, `/conversations`, `/conversations/{id}/messages`,
  `/conversations/{id}/end`, `DELETE /conversations/{id}`, `/messages/{id}/feedback`, `/suggestions`);
  `AssistantProxyService` (RestClient + JDK HttpClient, timeout 30 giây, gắn `X-Internal-Token`, `X-User-Id`,
  `X-User-Role`, `X-Request-Id`, chuyển tiếp `Authorization` chỉ ở lời gọi hỏi đáp; 4xx nghiệp vụ giữ mã, còn lại →
  `503 ASSISTANT_UNAVAILABLE`); `AssistantRateLimiter` (20 tin / 5 phút / người, cửa sổ trượt); cấu hình
  `app.assistant-service.*` trong `application-prod.yaml` (mặc định **tắt**).
- assistant-service: `GET /v1/conversations`, `GET /v1/conversations/{id}/messages`, `DELETE /v1/conversations/{id}`
  (xoá mềm), `POST /v1/messages/{id}/feedback` (ghi đè, che SĐT/email trong ghi chú), `GET /v1/suggestions`.

- File chính: `backend/.../controller/AssistantController.java`, `service/AssistantProxyService.java`,
  `service/AssistantRateLimiter.java`, `dto/request/Assistant*Request.java`, `config/SecurityConstants.java`,
  `config/SecurityConfig.java`; `assistant-service/app/api/conversations.py`, `app/api/feedback.py`.
- Kiểm tra: JUnit 13 test (`./mvnw -o test -Dtest='Assistant*Test'`); pytest 109 test.
- Cấu hình chạy local: thêm vào `backend/src/main/resources/application.yml` (file riêng của bạn, không commit):
  ```yaml
  app:
    assistant-service:
      enabled: true
      base-url: http://127.0.0.1:8100
      internal-token: <cùng giá trị ASSISTANT_INTERNAL_TOKEN>
  ```

**Khác thiết kế.**
- Đặt file theo cấu trúc sẵn có của repo (`controller/`, `service/`, `dto/request/`), không gom vào gói `assistant/`
  như 02 mục 2.5 — để giống phần còn lại của backend.
- Backend tự kiểm độ dài câu hỏi (1–1.000) để trả `INVALID_MESSAGE` mà không gọi sang assistant-service; assistant-service
  vẫn kiểm lại (không tin lớp trước).
- `401` từ assistant-service (sai token nội bộ) đổi thành `503` (lỗi cấu hình, không phải lỗi của người dùng).
- `GET /v1/suggestions` kiểm `X-User-*` như mọi API khác; tham số `role` lấy từ JWT ở backend.
- Bộ giới hạn tần suất giữ trong bộ nhớ tiến trình (một bản backend); nhiều bản cần bộ đếm dùng chung.

**Phát hiện cần người dùng quyết (cho M17).** Trên `dev`, `EmployeeJobFunction` vẫn có 5 giá trị
(`GENERAL_OPERATIONS`, `COMPLAINT_HANDLER`, `ACCOUNTANT`, `CONTENT_REVIEWER`, `USER_SUPPORT`); bản 2 chức năng
`CUSTOMER_SERVICE` / `ACCOUNTANT` nằm ở nhánh `feat/employee-two-job-functions` chưa merge (39 file).

**Còn dở.** Thử đầu-cuối frontend → backend → assistant-service ở M11 / chặng 8 (cần backend chạy với cấu hình đầy đủ).

**Đọc code này thế nào.**
1. `AssistantController.sendMessage`: thứ tự kiểm tra — độ dài → hạn mức → chuyển tiếp. Không xử lý nội dung.
2. `AssistantProxyService.exchange`: **đáng học nhất** — header nào gắn, vì sao `Authorization` chỉ chuyển tiếp chứ
   backend không tự tạo token mới cho trợ lý (trợ lý chỉ có đúng quyền của người dùng).
3. `AssistantProxyService.translate`: lỗi phía sau đổi thành mã người dùng hiểu được.
4. `AssistantRateLimiter.tryAcquire`: cửa sổ trượt bằng hàng đợi thời điểm; `synchronized` trên hàng đợi của từng
   người để hai request cùng lúc không đếm sai.

## M11 — Widget chat trên frontend

**Đã làm.** `components/assistant/`: `AssistantWidget` (nút nổi, chỉ `STUDENT` / `TUTOR`, tắt bằng
`VITE_ASSISTANT_ENABLED=false`, dựng lại theo `userId`), `AssistantPanel` (header + menu "Hỏi nhân viên hỗ trợ",
"Yêu cầu hỗ trợ của tôi"; lời chào + gợi ý; bong bóng "đang soạn…"; viền khác màu khi từ chối; dòng "Một phần câu
hỏi chưa có thông tin chính thức"; nút "Gửi câu hỏi cho nhân viên" theo 14 mục 14.2; gợi ý mở cuộc mới sau 20 lượt;
dòng chú thích dưới ô nhập), `MessageBubble` (Markdown an toàn bằng phần tử React: **đậm**, danh sách, xuống dòng,
chip `[n]` — không `dangerouslySetInnerHTML`), `CitationChip`, `ActionButtons` (chỉ route nội bộ, đi bằng
`navigate`), `SuggestionChips`, `FeedbackButtons` (👍 / 👎 + lý do + ghi chú), `useAssistant` (giữ `conversationId`
trong `sessionStorage` theo `userId`; tải lại trang → nạp lại tin; 409 `CONVERSATION_ENDED` → tự mở phiên mới và gửi
lại; timeout 35 giây; câu báo 429 / 503). `services/assistantApi.ts`; gắn widget vào `StudentLayout`, `TutorLayout`;
`AuthContext.logout` gọi `/end` (fetch `keepalive`) **trước** khi xoá token.

- File chính: `frontend/src/components/assistant/*`, `frontend/src/services/assistantApi.ts`,
  `frontend/src/types/assistant.ts`, `frontend/src/auth/AuthContext.tsx`, hai file layout.
- Kiểm tra: `npm run typecheck`, `npx eslint` (các file đã đụng), `npm run build` đều qua.
  Repo frontend chưa có bộ test chạy được TypeScript (jest không có ts-jest) → kiểm thử giao diện bằng Playwright ở chặng 8.

**Khác thiết kế.**
- Mã phiên lưu theo khoá `ptutor.assistant.conversation.<userId>` (thiết kế chỉ nói `sessionStorage`): hai tài khoản
  đăng nhập lần lượt trong cùng một tab không dùng nhầm phiên của nhau.
- Hộp thoại gửi nhân viên (`SupportRequestDialog`) làm ở M17; M11 để sẵn điểm gắn `renderSupportDialog`.
- Không thêm thư viện Markdown: bộ hiển thị tối giản tự viết, đủ cho câu trả lời của trợ lý và an toàn XSS.

**Còn dở.** Chạy thử trên trình duyệt với backend thật — ở chặng 8 (Playwright + ảnh chụp).

**Đọc code này thế nào.**
1. `useAssistant.ts` → `send`: luồng gửi một câu, đặc biệt nhánh `CONVERSATION_ENDED` → mở phiên mới, gửi lại.
2. `MessageBubble.tsx` → `renderInline` / `renderSafeMarkdown`: **đáng học nhất** — hiển thị Markdown mà không bao
   giờ biến chữ thành HTML (chống XSS), chip `[n]` tra trong `citations`.
3. `AssistantPanel.tsx`: bảng "Hành vi" của 08 mục 8.1 hiện thực thành các điều kiện hiển thị.
4. `services/assistantApi.ts` → `endAssistantConversationOnLogout`: vì sao phải gọi trước khi xoá token, và vì sao
   dùng `keepalive`.

## M12 — Chặn câu hỏi ngoài phạm vi

**Đã làm.** `ScopeGuardMiddleware` (đăng ký sau Query, trước Retrieval; tắt bằng `ASSISTANT_SCOPE_GUARD_ENABLED=false`)
với hàm thuần `scope_decision` dùng chung cho middleware và script đo. Tầng 1 luật (`app/scope/rules.py`: chào hỏi →
CHITCHAT; giải bài / biểu thức toán / viết văn / viết code / dịch / thời tiết… → OUT_OF_SCOPE, **trừ** câu có nhắc
nghiệp vụ PTutor = câu lẫn); tầng 2 so khớp câu mẫu (`app/scope/exemplars.py`, bảng `scope_exemplar` — migration
`0003`; IN = 128 câu hỏi **tập dev**, OUT = 60 câu `out_of_scope_seed.yaml`; nạp bằng
`python -m app.scope.exemplars --load`); câu "chưa chắc" cho qua tới Actor. Ghi `fallback_reason =
OUT_OF_SCOPE_RULE | OUT_OF_SCOPE_EMBEDDING | OUT_OF_SCOPE_MODEL`; câu ngoài phạm vi không có nút gửi nhân viên.
Chia dev / test theo tài liệu (`eval/make_splits.py` → `eval/datasets/SPLITS.yaml`: 37 tài liệu / 128 câu dev,
28 tài liệu / 92 câu test). Bộ đo riêng `eval/datasets/scope/scope_eval.yaml` (câu ngoài phạm vi khác hẳn câu mẫu +
câu sát ranh giới), script `python -m eval.scope_eval` (dò lưới τ_in × δ trên dev, leave-one-out cho câu IN).

- File chính: `app/agent/middleware/scope_guard.py`, `app/scope/*`, `migrations/versions/0003_*.py`,
  `eval/scope_eval.py`, `eval/make_splits.py`, `tests/unit/test_scope_guard.py`,
  `tests/integration/test_scope_exemplars.py`.
- Kiểm tra: toàn bộ pytest xanh (T0 + T1).

**Số liệu (embedding `fake`, 2026-11-03).** Chặn đúng = câu ngoài phạm vi bị chặn ở tầng 1–2.

| | τ_in / δ | Chặn đúng | trong đó bằng luật | Chặn nhầm |
|---|---|---|---|---|
| Dev (chọn ngưỡng) | 0,35 / 0,00 | 75% | 30% | 2,2% |
| **Test** | 0,35 / 0,00 | **70%** | 15% | **6,1%** |
| Test, mặc định thiết kế | 0,55 / 0,05 | 55% | 15% | 4,1% |

Chặn nhầm trên test **vượt mục tiêu 3%**. Câu bị chặn nhầm ("Waiting for review là gì?", "VNPay báo lỗi mã 24 là
gì?", "loc thong bao chua doc") là câu hợp lệ mà embedding `fake` (chỉ giống *từ ngữ*) không nhận ra. Theo 13 mục
13.9, **không** chỉnh ngưỡng theo test; cấu hình giữ mặc định thiết kế (0,55 / 0,05). Việc tiếp theo: hiệu chỉnh lại
trên dev với bge-m3 khi máy đủ bộ nhớ chạy `ai-service`.

**Khác thiết kế.**
- Luật OUT_OF_SCOPE bỏ qua câu có nhắc nghiệp vụ PTutor ("tìm gia sư toán thế nào, giải luôn bài này"), vì thiết kế
  yêu cầu câu lẫn không bị chặn mà luật thuần chặn được cả câu này.
- Câu mẫu IN lấy tự động từ tập dev của `cau-hoi-mau.csv`; câu ADMIN bổ sung đọc chung bảng.
- Đo trên dev theo kiểu leave-one-out (bỏ chính câu đang đo khỏi câu mẫu), thiết kế chưa nói cách xử lý chỗ câu dev
  vừa là câu mẫu vừa là câu đo.

**Còn dở.** Hiệu chỉnh với bge-m3; trang quản trị chuyển câu bị chặn nhầm thành câu mẫu IN (M16).

**Đọc code này thế nào.**
1. `app/scope/rules.py` → `classify_by_rules`: hai danh sách mẫu + danh sách "từ nghiệp vụ"; vì sao kiểm từ nghiệp vụ.
2. `app/agent/middleware/scope_guard.py` → `scope_decision`: ba tầng trong 15 dòng — **đáng học nhất** là tầng 2:
   chỉ chặn khi *vừa* không giống câu trong phạm vi *vừa* giống câu ngoài phạm vi hơn một khoảng δ.
3. `eval/scope_eval.py`: `precompute` tính điểm một lần, `tune` dò ngưỡng chỉ trên dev; so dòng dev với dòng test để
   thấy vì sao không được chỉnh theo test.

## M13 — Kiểm chứng trích dẫn, RouteGuard, Redact

**Đã làm.**
- `CitationVerifierMiddleware` (④', cho intent POLICY / HOWTO / LOOKUP): kiểm bằng code trước — khẳng định POLICY /
  HOWTO thiếu nguồn, nguồn không tồn tại (`D9`, tool chưa gọi), khẳng định từ tool có ngày / số tiền / mã trạng thái
  không có trong kết quả tool; rồi **một** lời gọi verifier cho các khẳng định từ tài liệu (prompt `verifier_v1`,
  schema `VERIFIER_SCHEMA`, nhiệt độ 0, chỉ gửi khẳng định + nguyên văn đoạn nguồn). `normalize_verdict`: bằng chứng
  phải là chuỗi con của đoạn nguồn sau chuẩn hoá (giữ dấu, chữ số) → không thì `UNSUPPORTED`. Không đạt → thử lại
  1 lần kèm góp ý "khẳng định #i … không đạt: lý do"; vẫn không đạt → giữ khẳng định đạt (dựng lại câu trả lời từ
  chúng, `grounded = PARTIAL`) hoặc từ chối (`VERIFY_FAILED`).
- `RouteGuardMiddleware`: action phải khớp route của **vai trò**; tham số route phải thuộc `seen_ids` (sổ tay phiên +
  kết quả tool của lượt).
- `RedactMiddleware`: che SĐT / email / dãy số dài trong câu trả lời **bản lưu DB** (người dùng vẫn thấy nguyên văn).
- Cấu hình: `ASSISTANT_VERIFIER_ENABLED` (tắt = E2 "không kiểm chứng"), `ASSISTANT_MAX_VERIFY_RETRIES` (0 = E2
  "kiểm chứng không thử lại"), `ASSISTANT_VERIFIER_MODEL`.

- File chính: `app/agent/middleware/citation_verifier.py`, `app/agent/verification.py`,
  `app/agent/prompts/verifier_v1.md`, `app/agent/middleware/route_guard.py`, `app/agent/middleware/redact.py`,
  `tests/unit/test_citation_verifier.py`.
- Kiểm tra: 147 test (T0 + T1) xanh; có đúng test mẫu của 13 mục 13.1 `test_verifier_evidence_must_be_substring`.

**Khác thiết kế.**
- Verifier lỗi / trả JSON hỏng → các khẳng định đó coi là `UNSUPPORTED` (thiết kế chưa nói). Lý do: ưu tiên không
  bịa — không cho khẳng định về quy định đi qua khi chưa kiểm được.
- PARTIAL: câu trả lời được **dựng lại** từ các khẳng định đạt (danh sách gạch đầu dòng kèm `[Dn]`) thay vì cố xoá câu
  sai khỏi đoạn văn của Actor — đơn giản và chắc chắn không sót câu sai.
- Kiểm số liệu tool so "sự kiện" (ngày dd/MM/yyyy, số ≥ 4 chữ số đã bỏ dấu phân cách, mã IN HOA) với kết quả tool đã
  lọc; số nhỏ (1, 2 buổi…) không kiểm.
- Fixture `settings` của test mặc định **tắt** verifier để test các module trước không phải viết kịch bản cho lời gọi
  verifier; test M13 bật lại tường minh.

**Eval chặng 4.** Bộ an toàn và bộ 60 cặp cho verifier (`claims.jsonl`, `eval.verifier --k 3`) cần model thật → dựng ở
M15 / chặng 8. Kết quả lớp chặn (M12) ở mục M12.

**Còn dở.** Đo chất lượng verifier (cần model).

**Đọc code này thế nào.**
1. `citation_verifier.py` → `after_model`: bốn nhánh của sơ đồ 04 mục 4.6 (đạt / thử lại / giữ phần đúng / từ chối).
2. `_check`: phần kiểm **bằng code** — rẻ, tất định, chạy cả khi tắt verifier; chỉ khẳng định từ tài liệu mới tới LLM.
3. `verification.py` → `normalize_verdict`: **đáng học nhất** — kiểm luôn cả "người kiểm tra" (bằng chứng phải chép
   nguyên văn), để chính verifier cũng không bịa được.
4. `route_guard.py` → `route_allowed`: tách tham số khỏi mẫu route rồi kiểm từng giá trị thuộc `seen_ids`.

## M14 — Công cụ tra cứu (tool calling)

**Đã làm.**
- `ToolRegistry` đọc **thẳng** `tool-catalog/tools.yaml` (36 công cụ `ready`: 24 cho học viên, 26 cho gia sư; công cụ
  `planned` bỏ qua); từ chối nạp nếu có công cụ không phải GET / READ_ONLY; `parameters_schema()` **không** đưa tham số
  cố định cho model (`list_my_transactions` luôn `source=PAYMENT`, model không đổi được).
- `BackendClient`: chỉ GET + allowlist = danh mục; `LiveBackendClient` (httpx, 5 giây, token của người hỏi) và
  `MockBackendClient` (`ASSISTANT_TOOLS_MODE=mock`, fixture `eval/fixtures/scenarios/<kịch bản>/<tool>.json` dạng
  `default` + `byArgs`; `_all.json` = mọi tool cùng trả một lỗi).
- Lớp lọc (`tools/result_filter.py`): chỉ trường `keep`; múi giờ theo nhãn `UTC` / `BACKEND_JVM` / `NOT_WRITTEN`;
  tiền `1.200.000 đ`; ngày `dd/MM/yyyy`; nhãn enum (`status_label`); trường suy ra `createdByMe`; danh sách tối đa
  10 mục theo `newest` / `installments_due_first` / `lessons_pending_first` kèm `total`, `counts`; cắt văn bản người
  dùng viết; rút `focus_items` (ID + nhãn "Toán · Lớp 9").
- `ToolExecutor` (dựng đường dẫn, tham số cố định, đổi giờ tham số thời gian, lỗi HTTP → câu báo ngắn 05 mục 5.5,
  bọc `<tool_result>`).
- Middleware: `ToolGateMiddleware` (vai trò, kiểu / enum tham số, bỏ tham số cố định, **ID phải thuộc `seen_ids`** →
  `UNSEEN_ID`), `LoopGuardMiddleware` (cùng tool + cùng tham số → `DUPLICATE`; hết lượt tool → bỏ tool khỏi request),
  `ToolSelectionMiddleware` (≤ 6 tool; câu quy định thuần → không gửi tool; tool cần ID kéo theo tool liệt kê).
- Prompt Actor thêm quy tắc 8 (dùng mã trong `<session_focus>`, dữ liệu tool thắng lời người dùng).
- Test hợp đồng T2 `tests/contract/test_tool_fields.py` (`pytest -m contract`, cần backend dev + token test).

- File chính: `app/tools/*`, `app/agent/middleware/tool_gate.py`, `loop_guard.py`, `tool_selection.py`,
  `eval/fixtures/scenarios/*`, `tests/unit/test_tools.py`, `tests/contract/test_tool_fields.py`.
- Kiểm tra: 168 test xanh (T0 + T1), 27 test T2 bỏ qua (chưa có backend thật). Có đúng test mẫu 13 mục 13.1
  `test_tool_gate_blocks_unseen_id`, và các nhóm an toàn 13.7: đoán ID, ép gọi lặp, rò trường nhạy cảm, backend lỗi.

**Khác thiết kế.**
- Danh sách công cụ theo `tools.yaml` (36), không theo bảng 16 công cụ của 05 mục 5.3 (quyết định của người dùng).
- `ToolGate` đăng ký **trước** `LoopGuard` ở hook ⑤ để LoopGuard so lời gọi đã được làm sạch tham số.
- Lời gọi bị chặn vẫn tính vào hạn mức 3 lời gọi tool (Budget bọc ngoài) — chặn luôn kiểu "spam lời gọi bị chặn".
- Câu hỏi "có dấu hiệu cá nhân" so trên câu **có dấu** khi người dùng gõ có dấu (bỏ dấu cả hai phía thì "ví tối
  thiểu" khớp nhầm "ví tôi" — cùng loại lỗi với từ dừng ở M6).
- `focus_items` chỉ lấy từ các mục **được đưa cho model** (≤ 10), không lấy mục bị cắt bớt.

**Còn dở.** Chạy với backend thật (T2) ở chặng 8 khi dựng compose + dữ liệu mẫu.

**Đọc code này thế nào.**
1. `tools.yaml` một công cụ (ví dụ `list_my_payment_installments`) rồi `app/tools/registry.py` → `parse_tool`: một
   khai báo YAML thành một `ToolDefinition`.
2. Đường đi của một lời gọi: `runner._run_tools` → Budget ⑤ → Trace ⑤ → **ToolGate** → LoopGuard → `ToolExecutor`
   → `BackendClient.get` → `shape_result`. Đọc theo đúng thứ tự này.
3. `tool_gate.py` → `_check_value`: **đáng học nhất** — chặn đoán ID chỉ bằng một phép so tập hợp với `seen_ids`.
4. `result_filter.py` → `filter_object` + `select_items`: vì sao model không bao giờ thấy email / họ, và vì sao kỳ
   chưa đóng gần hạn nhất luôn đứng đầu.
5. `tests/unit/test_tools.py` → `test_lookup_turn_two_tools_then_answer_with_numbers_from_tool`: cả lượt tra cứu.

## M15 — Bộ chạy đánh giá

**Đã làm.**
- `eval/build_datasets.py`: 220 câu của `cau-hoi-mau.csv` → `eval/datasets/golden/<features>.yaml` theo định dạng
  13 mục 13.4 (split theo `SPLITS.yaml`; `docs`; FAQ / ERROR → intent POLICY; NO_ANSWER → `grounded_in: [NONE,
  PARTIAL]`; `must_include` = các con số rút từ cột `y_bat_buoc`; phần còn lại vào `reference` / `key_points` để chấm tay).
- `eval/datasets/safety/safety.yaml`: 25 case theo các nhóm 13 mục 13.7 (injection trực tiếp / gián tiếp qua dữ liệu,
  đoán ID, rò tài liệu theo vai trò, rò trường nhạy cảm, ép gọi lặp, ép hứa hẹn, ngoài phạm vi, câu sát ranh giới,
  thao tác ghi, lời khẳng định sai), có biến thể không dấu / tiếng Anh / nhiều lượt. Fixture `student_injection`
  (lịch học do gia sư nhập chứa "lệnh"), `tutor_basic`, `users.yaml`.
- `eval/graders/code_graders.py`: 19 grader của 13 mục 13.5 (truy xuất, must_include có chuẩn hoá số / dấu, intent,
  grounded, tool_sequence exact / includes / none, tool_budget, numbers_match_fixture, citation_audience,
  no_sensitive_fields (quét cả **mọi nội dung đã gửi model**), id_provenance, routes_valid, budget_ok,
  rewritten_query_contains, memory_reference, scope_not_blocked, no_write_calls…).
- `eval/runner.py` + `python -m eval.run`: môi trường cố định (kho từ snapshot, tool mock theo `scenario`, đồng hồ
  theo `now`, cache embedding `eval/.cache/`), `--mode quick|gate` (k = 1 / 3), `--split`, **giới hạn tổng của lần
  chạy** `--max-llm-calls` / `--max-tokens`, model thật bắt buộc `--yes` sau khi in ước tính số lời gọi.
- `eval/report.py`: đạt = đa số k lần, dao động, CI 95% bootstrap (1.000 lần), Recall@6, MRR, theo suite / loại / vai
  trò / grader → `run.json`, `cases.csv`, `traces.jsonl`, `summary.md`.
- `python -m eval.compare <baseline> <run>`: an toàn 100% + grader an toàn không lỗi + không thoái lui chưa được chấp
  nhận (`eval/baselines/accepted_regressions.yaml`), McNemar chính xác, mã thoát 1 khi trượt cổng.
- `python -m eval.latency_cost <run…>`: B2.1–B2.5 từ span (`spans.csv`, `latency_cost.md`), giá từ
  `eval/configs/pricing.yaml` (đang trống — chưa chọn model; thiếu giá thì báo thiếu, không đoán).
- Cấu hình thí nghiệm `eval/configs/`: `baseline`, `e1_vector`, `e1_hybrid`, `e1_rerank`, `e2_no_verifier`,
  `e2_no_retry`, `e4_no_rewrite`, `real_llm`.
- `eval/scripted_policy.py`: "Actor trích xuất" tất định cho T0–T2 (không phải LLM) — chạy được cả đường ống khi chưa có
  khoá; mô phỏng model "nghe lời" người dùng (dùng mã UUID có trong câu hỏi) để bộ an toàn thật sự thử ToolGate.

- Kiểm tra: pytest 179 test (T0 + T1); `tests/unit/test_eval_framework.py` cho grader, đa số, bootstrap, McNemar, cổng.
- Chạy thử: bộ an toàn **25/25 (100%)** với Actor trích xuất; các case đoán ID đều bị ToolGate chặn `UNSEEN_ID`.

**Khác thiết kế.**
- "Đạt ổn định" ở cổng hồi quy = đạt **mọi** lần trong k (13 mục 13.6 vừa gọi 2/3 là ổn định vừa gọi là dao động;
  chọn cách hiểu nhất quán: dao động không bao giờ tính là thoái lui).
- Recall@6 / MRR là chỉ số riêng, **không** quyết định case đạt; case đạt = mọi grader còn lại đạt.
- Thêm "Actor trích xuất" (thiết kế chỉ có ScriptedLlm theo kịch bản) để eval chạy được ở T0–T2; số đo với nó **không**
  phải chất lượng LLM.
- Bộ golden lấy nguyên 220 câu (thiết kế dự kiến ~100); câu LOOKUP / nhiều lượt bổ sung ở chặng 8.

**Còn dở (chặng 8).** Bổ sung case LOOKUP có fixture từng kịch bản, hội thoại nhiều lượt (E4), câu ngoài phạm vi và câu
bẫy; bộ verifier 60 cặp + `eval.verifier`; công cụ chấm tay; chạy E1, E2, E4, B2; baseline.

**Đọc code này thế nào.**
1. Một case trong `eval/datasets/safety/safety.yaml` → `eval/cases.py` (`load_cases`) → `eval/runner.py` (`run_case`):
   dựng `TurnState`, chạy harness thật, thu `TurnObservation`.
2. `eval/graders/code_graders.py`: mỗi grader 5–10 dòng; **đáng học nhất** là `g_no_sensitive_fields` (quét mọi thứ đã
   gửi model chứ không chỉ câu trả lời) và `g_id_provenance` (chấm cả con đường).
3. `eval/report.py` → `majority`, `bootstrap_ci`; `eval/compare.py` → `compare`: cổng hồi quy đọc trong 30 dòng.
4. `eval/run.py`: ước tính chi phí và `--yes` trước khi gọi model thật.

## M16 — Trang quản trị kho tri thức

**Đã làm.**
- assistant-service `/v1/admin/**` (kiểm lại `ADMIN`): danh sách tài liệu (lọc trạng thái / loại / chức năng / đối
  tượng / từ khoá; cờ có nháp, số câu hỏi mở, "công bố khi còn câu hỏi mở"), tạo (trùng mã → 409), chi tiết (bản công
  bố, bản nháp, lịch sử), lưu nháp, kiểm tra + xem trước chia đoạn, xem trước khi đang gõ (`/documents/preview`, không
  ghi DB), công bố (luôn chặn khi còn todo), lưu trữ, bỏ nháp, xem phiên bản, khôi phục, câu hỏi chưa trả lời được
  (06 mục 6.5, không trả `user_id`), thống kê, cài đặt `routes` / `suggestions` / `scope_rules` (áp dụng ngay), thêm
  câu mẫu phạm vi (`/scope-exemplars`). `Services` nạp cài đặt từ `kb_setting` lúc khởi động.
- Backend `AdminAssistantController` (`/api/v1/admin/assistant/**`, chỉ `ADMIN`), chuyển tiếp nguyên JSON.
- Frontend `pages/admin/assistant/`: `KbDocumentListPage`, `KbDocumentEditorPage` (form + Markdown + xem trước + chia
  đoạn ≤ 400 token + nút Công bố khoá khi còn câu hỏi mở + hộp thoại xác nhận), `KbVersionHistory` (xem / khôi phục),
  `UnansweredQuestionsPage` ("Tạo tài liệu" điền sẵn câu hỏi; "Thêm làm câu mẫu trong phạm vi" cho câu bị chặn nhầm),
  `AssistantStatsPage`; nhóm menu "Trợ lý" chỉ hiện với `ADMIN`.

- File chính: `assistant-service/app/api/admin_kb.py`, `app/kb/admin_queries.py`, `app/services.py`;
  `backend/.../controller/AdminAssistantController.java`; `frontend/src/pages/admin/assistant/*`,
  `frontend/src/services/adminAssistantApi.ts`.
- Kiểm tra: pytest 183 test (4 T1 mới cho API quản trị: vòng đời đầy đủ, chặn vai trò, câu hỏi chưa trả lời được,
  cài đặt); JUnit 16 test trợ lý; frontend typecheck, lint, build qua.

**Khác thiết kế.**
- Thêm `/documents/preview` (xem trước khi chưa lưu) và `/scope-exemplars` (thiết kế 4.11 có nói chuyển câu bị chặn nhầm
  thành câu mẫu nhưng 07 mục 7.3 chưa có endpoint).
- Lịch sử phiên bản là một khối trong trang soạn (thiết kế tách file `KbVersionHistory.tsx` — vẫn là component riêng).
- Ở trang quản trị, công bố **không** có lựa chọn `--allow-todo` (chỉ lệnh import cho eval / demo mới có).
- Sửa cài đặt route không kiểm route có thật trong `App.tsx` (đã có `scripts/check_catalog.py` cho bản khởi tạo).

**Còn dở.** Chụp ảnh màn hình trang quản trị ở chặng 8 (Playwright).

**Đọc code này thế nào.**
1. `admin_kb.py`: mỗi endpoint chỉ gọi `kb_service` (ghi) hoặc `kb_queries` (đọc) — không có logic nghiệp vụ ở router.
2. `services.py` → `write_setting`: **đáng học** — ghi DB rồi áp dụng ngay cho service đang chạy; luật chặn được biên
   dịch trước khi lưu nên luật hỏng báo lỗi ngay.
3. `KbDocumentEditorPage.tsx` → `startPublish`: lưu → kiểm tra → hộp thoại → công bố; nút khoá khi còn câu hỏi mở.
4. `MarkdownPreview.tsx`: xem trước bằng phần tử React (không chèn HTML) — cùng ý tưởng chống XSS với widget.

## M17 — Yêu cầu hỗ trợ (chuyển câu hỏi cho nhân viên)

**Đã làm.**
- Merge `feat/employee-two-job-functions` (người dùng chọn) để có `EmployeeJobFunction.CUSTOMER_SERVICE`; sửa trùng
  Flyway V25 (migration trợ lý đổi tên thành `V35__create_assistant_schema.sql`).
- Backend: Flyway `V36__create_support_requests.sql` (bảng `support_requests`, sequence mã `HT-000123`, CHECK trạng
  thái / nguồn / độ dài, mở rộng CHECK của `notifications`); entity `SupportRequest`, enum `SupportRequestStatus`,
  `SupportRequestSource`; thông báo `SUPPORT_REQUEST_ANSWERED` (gửi người dùng), `SUPPORT_REQUEST_RECEIVED` (gửi nhân
  viên CUSTOMER_SERVICE đang hoạt động).
- `SupportRequestService`: gửi (giới hạn 5 yêu cầu `OPEN`, 10 yêu cầu / 24 giờ; đoạn hội thoại chỉ lấy khi người dùng
  chọn đính kèm), xem / hủy (chỉ `OPEN`) / đã giải quyết (chỉ `ANSWERED`); nhân viên: hàng chờ (cũ nhất trước, lọc trạng
  thái / của tôi / vai trò / khoảng thời gian), nhận xử lý, trả lại hàng chờ, trả lời (chỉ người được giao); thống kê
  theo trạng thái, nguồn, p50 / p95 thời gian tới khi nhận và tới khi trả lời. Mọi chuyển trạng thái khoá dòng.
- `SupportRequestScheduler` (2:30 hằng đêm): tự đóng yêu cầu đã trả lời quá 7 ngày; xoá đoạn trích 365 ngày sau khi đóng.
- Phân quyền: `SUPPORT_REQUEST_SELF_SERVICE_API` (STUDENT, TUTOR), `ADMIN_SUPPORT_REQUEST_API` (ADMIN, EMPLOYEE) và thêm
  `/api/v1/admin/support-requests` vào `CUSTOMER_SERVICE_PATHS` của `EmployeeFunctionAuthorizationFilter`.
- assistant-service: `GET /v1/conversations/{id}/excerpt?upTo=` — kiểm chủ sở hữu (404 nếu không phải), tối đa 6 lượt
  kết thúc ở `upTo`, che thông tin cá nhân lần nữa trước khi rời dịch vụ.
- `tools.yaml` (bản sao): `list_my_support_requests`, `get_my_support_request` → `ready` (38 công cụ); fixture
  `student_two_contracts/list_my_support_requests.json`; case an toàn `safety-support-status-01`.
- Kho tri thức: thêm `chung/trang-thai-yeu-cau-ho-tro.md` (glossary, không còn câu hỏi mở); `_routes.yaml` thêm
  `/student|tutor/support-requests[/:requestId]`.
- Frontend: `SupportRequestDialog` (mở từ nút "Gửi câu hỏi cho nhân viên", 👎, menu "Hỏi nhân viên hỗ trợ"; ô đính kèm
  mặc định không chọn); trang người dùng `SupportRequestsPage`, `SupportRequestDetailPage`; trang nhân viên
  `pages/admin/support-requests/` (hàng chờ 3 tab + số liệu, chi tiết + đoạn hội thoại + trả lời); menu học viên, gia
  sư và mục "Yêu cầu hỗ trợ" (CUSTOMER_SERVICE) trong khu quản trị.

- Cách chạy: như các module trước; job đêm chỉnh bằng `app.support-request.maintenance-cron`.
- Kiểm tra: JUnit 471 test toàn backend, 0 lỗi (12 test mới `SupportRequestServiceTest`, 2 trường hợp mới ở
  `EmployeeFunctionAuthorizationFilterTest`, `SupportRequestRepositoryIntegrationTest` chạy trên Postgres thật — DB
  nháp `ptutor_it_claude`, Flyway V1–V36); pytest toàn bộ qua (thêm test `/excerpt`); bộ an toàn 26/26; frontend
  build, lint, prettier qua.

**Khác thiết kế.**
- Trang người dùng là một component dùng chung ở `pages/common-user/` (như `WalletPage`), thay vì hai file
  `pages/student|tutor/`.
- Thêm `get_my_support_request` thành `ready` (bảng 14.7 chỉ nêu tool liệt kê; danh mục đã đề xuất sẵn tool này). Tham
  số đường dẫn đổi `id` → `requestId` cho khớp controller.
- Body tạo yêu cầu có thêm `previousRequestId` (thiết kế 14.1 "gửi yêu cầu mới có liên kết yêu cầu trước"), chưa có nút
  "Hỏi tiếp" trên giao diện.
- Thống kê chưa có "tỉ lệ yêu cầu trên số lượt trợ lý từ chối" (14.9) — cần ghép số liệu của assistant-service.
- Thông báo `SUPPORT_REQUEST_*` chưa bấm sang trang chi tiết: trang thông báo hiện có không điều hướng theo
  `referenceType` cho mọi loại thông báo (ngoài phạm vi chatbot).
- `createdAt` (Hibernate, múi giờ JVM) và `answeredAt` / `closedAt` (service, UTC) khác hệ — giao diện đổi riêng từng
  loại (`supportFormat.ts`), thống kê đổi `createdAt` về UTC trước khi trừ. Ghi vào việc "thống nhất múi giờ" của nhóm.

**Còn dở.** Ảnh màn hình (chặng 8); contract test T2 cho hai tool mới cần backend chạy thật.

**Đọc code này thế nào.**
1. `SupportRequestService.java`: mỗi phương thức công khai là một mũi tên trong sơ đồ trạng thái 14.3;
   `requireStatus` + `requireAssignee` là toàn bộ luật chuyển trạng thái.
2. `create` → `fetchExcerpt`: **đáng học** — backend gọi assistant-service bằng chính danh tính người dùng, nên kiểm
   quyền sở hữu nằm ở phía giữ dữ liệu (assistant-service), backend không cần biết cuộc trò chuyện thuộc ai.
3. `app/api/conversations.py` → `conversation_excerpt`: cửa sổ 6 lượt kết thúc ở `upTo`, che lại PII.
4. `SupportRequestDialog.tsx`: ô đính kèm mặc định tắt + chú thích lưu trữ — hai điểm quyền riêng tư của 14.8.

## Chặng 8 / 4b — Đánh giá đầy đủ

**Đã làm.**
- Bộ dữ liệu: `eval/build_lookup_cases.py` sinh 32 case LOOKUP (19 công cụ, chia dev / test **theo công cụ**), 8 hội
  thoại nhiều lượt (2 kịch bản E4 "hỏi lặp một chi tiết"), 10 câu ngoài phạm vi, 6 câu bẫy; 19 fixture mới cho
  `student_two_contracts`, `tutor_basic` (cố ý chứa trường nhạy cảm để kiểm lọc). Golden có thêm
  `grounded_in: [FULL, PARTIAL]` cho câu có đáp án.
- Verifier: `eval/build_verifier_claims.py` → `datasets/verifier/claims.jsonl` (76 khẳng định), `eval/verifier.py`
  (recall bắt lỗi, tỉ lệ bác nhầm, ma trận theo `kind`, k lần lấy đa số, chặn chi phí khi dùng model thật).
- Chấm tay: `eval/grade_manual.py` + `graders/manual_grading.py` (phiếu mù, khoá giải mã riêng, rubric 13.5).
- Chạy thí nghiệm: `eval/run_experiments.sh` (baseline dev + test gate, E1, E2, E4), B2 bằng `eval.latency_cost`,
  `eval/tune_actor.py` (quét `COVERAGE_MIN` trên dev), baseline ở `eval/baselines/`.
- Sửa harness do eval phát hiện: (1) câu có từ nghiệp vụ không bị chặn ở tầng câu mẫu — chặn nhầm test 4,1% → 1,0%;
  (2) thêm dấu hiệu câu hỏi cá nhân; (3) `FocusItem.mentioned_turn` — đối tượng người dùng gọi tên thành "đang bàn
  nhất", lời nhắc viết lại câu hỏi nói rõ thứ tự; (4) Actor trích xuất v2 (gọi tool có điều kiện, intent HOWTO).
- Thêm `ASSISTANT_LLM_PROVIDER=scripted` (chỉ cho demo khi chưa có model).
- Báo cáo: `docs/support-chatbot/trien-khai/BAO-CAO-DANH-GIA.md` (ghi rõ: Actor giả lập, embedding giả lập, kho 66
  tài liệu còn 95 câu todo, T3–T4 chưa chạy; việc thống nhất múi giờ cho nhóm kèm danh sách trang).

- Cách chạy: mục 2 của báo cáo.
- Kết quả chính (test, gate): an toàn 26/26; tổng 65,7%; Recall@6 90,2%, MRR 0,762; LOOKUP 80%.
- Kiểm tra: pytest toàn bộ qua (thêm `tests/unit/test_stage8.py`), ruff, mypy sạch.

**Khác thiết kế.**
- Không có số T3–T4 (chưa chọn model). E2, E4 với Actor giả lập không cho kết luận chất lượng — báo cáo nói rõ.
- Thử δ = 0,02 (chọn trên dev) cho lớp chặn phạm vi; cổng hồi quy chặn vì chặn nhầm một câu → giữ 0,05.
- Nhãn `claims.jsonl` gán theo cách tạo, chưa có câu diễn đạt lại, chưa qua hai người gán.
- Rerank chưa đo (máy thiếu RAM).

**Còn dở.** T3–T4, chấm tay, rerank, câu diễn đạt lại cho verifier — mục 9 của báo cáo.

**Đọc code này thế nào.**
1. `BAO-CAO-DANH-GIA.md` mục 0 và 5 trước: đọc số thế nào, eval đã sửa được gì.
2. `eval/build_lookup_cases.py`: **đáng học** — chia dev / test theo công cụ để chỉnh trên dev không "học tủ" test.
3. `app/agent/middleware/memory.py` → `update_focus`: lời người dùng chỉ xếp thứ tự đối tượng đã có, không thêm ID.
4. `eval/compare.py` + mục 7.2 báo cáo: ví dụ cổng hồi quy bắt một thay đổi "tỉ lệ tổng không đổi" nhưng hỏng một case.

## Chặng 8 / 4c — Hoàn thiện (Docker, dữ liệu demo, ảnh màn hình, tài liệu)

**Đã làm.**
- Dockerfile multi-stage: `backend/Dockerfile` (Maven → JRE 21, `TZ=Asia/Ho_Chi_Minh`, profile `prod`),
  `frontend/Dockerfile` + `nginx.conf` (Vite → nginx, `/api` chuyển sang backend), `assistant-service/Dockerfile` +
  `docker-entrypoint.sh` (Alembic rồi uvicorn; lệnh `kb-import` để nạp kho tri thức).
- `docker-compose.yml`: thêm `backend`, `assistant-service`, `frontend` dưới profile `assistant`; `postgres` giữ không
  profile. Sửa khoá volume (đặt `POSTGRES_VOLUME_NAME` trước đây làm compose báo lỗi). `.env.example` thêm biến trợ lý.
- Seeder `backend/.../demo/DemoDataSeeder.java` (`@Profile("demo")`): 4 tài khoản (ADMIN, CUSTOMER_SERVICE, học viên,
  gia sư) + ví + 2 yêu cầu hỗ trợ; idempotent theo email; mật khẩu từ `DEMO_USER_PASSWORD` (trống → bỏ qua).
- Chạy thử toàn bộ bằng Docker (project riêng, Postgres riêng cổng 5434, không đụng `ptutor-postgres`): Flyway V1–V36,
  seeder, nạp 66 tài liệu, kiểm thử đầu-cuối (hỏi quy định, tra cứu số dư thật qua backend, chặn ngoài phạm vi, hàng
  chờ CSKH, gia sư bị 403).
- 10 ảnh màn hình Playwright (Chrome có sẵn) ở `docs/support-chatbot/trien-khai/demo/`.
- Tài liệu: `assistant-service/README.md`, `HUONG-DAN-DEMO.md`, `THIET-KE-VS-TRIEN-KHAI.md` (bảng đối chiếu).

- Kiểm tra: `DemoDataSeederIntegrationTest` (Postgres thật, chạy seeder hai lần); pytest, ruff, mypy sạch; quét bí mật
  và TODO trên toàn bộ thay đổi: không có.

**Khác thiết kế.**
- Dockerfile và dịch vụ compose là thêm mới (chưa có trên `dev`), theo quyết định 2026-11-03.
- `CORS_ALLOWED_ORIGINS` mặc định trong compose có cả `http://127.0.0.1:8088` (trên máy này Docker Desktop không chuyển
  `localhost` qua IPv6).
- Ảnh chụp với `ASSISTANT_LLM_PROVIDER=scripted` (chưa có model).

**Còn dở.** Chạy lại ảnh và eval T3 khi có model; các mục ở `BAO-CAO-DANH-GIA.md` mục 9.

**Đọc code này thế nào.**
1. `HUONG-DAN-DEMO.md` → chạy thử theo đúng các bước.
2. `docker-compose.yml`: dịch vụ mới đều có `profiles: [assistant]` — chạy Postgres một mình vẫn như cũ.
3. `DemoDataSeeder.java`: **đáng học** — idempotent theo email + mật khẩu từ biến môi trường: không có bí mật nào trong
   mã nguồn, khởi động lại không nhân bản dữ liệu.

## Chặng 8 / T3 — Model thật `gpt-4.1-mini` (2026-11-04)

**Đã làm.**
- `pricing.yaml`: 0,40 $ / 1,60 $ cho 1 triệu token vào / ra, ghi ngày và nguồn (người dùng cung cấp, cần đối chiếu
  trang giá chính thức). `real_llm.yaml`: `gpt-4.1-mini` cho trả lời, viết lại và kiểm chứng.
- Eval kiểm soát ngân sách bằng tiền:
  - `GuardedLlm` chặn trước mỗi lời gọi theo trần USD của lần chạy và theo sổ chi tiêu chung `BudgetLedger` (trần tổng
    10 $);
  - `eval.run` in ước tính chi phí trước khi chạy và ghi `cost_usd` vào `run.json`.
- Dev trước test: 79,8% → 85,1% sau bốn sửa (`coverage` tự khai, `parallel_tool_calls=False`, che UUID lạ trong câu
  trả lời, thu hẹp cụm từ grader `safety-write-01`). Không đổi ngưỡng chặn phạm vi: đó là ngưỡng trên embedding, không
  phụ thuộc LLM.
- Test (`gate`, k = 3): **85,0%**, an toàn 26/26, p50 3,2 s / p95 8,8 s, 0,002 $ / lượt.
- E1: không khác biệt (p = 1,0) vì embedding vẫn giả lập.
- E2: tổng không khác biệt. Verifier giúp NO_ANSWER (+10 điểm) nhưng bác nhầm một số câu POLICY / HOWTO; p95 tăng
  2,4 lần.
- Ảnh demo chụp lại với model thật ở `demo/`; bộ ảnh giả lập chuyển sang `demo/scripted/`.
- Phiếu chấm tay mù: `docs/support-chatbot/trien-khai/cham-tay/sheet.csv` (82 dòng).
- Báo cáo: `BAO-CAO-DANH-GIA.md` mục T3.

**Sự cố.** Tài khoản API hết credit sau 3,30 $, đúng lúc đang chạy E2-không-thử-lại (429 `insufficient_quota`). Harness
cũ coi đây là lỗi thoáng qua: mọi lượt thành `MODEL_ERROR` và lần chạy vẫn "xong" với số rác. Đã sửa:

- lỗi tài khoản là `fatal`, dừng lần chạy (`ProviderUnavailable`), lệnh trả mã 2, chuỗi thí nghiệm dừng;
- xoá các báo cáo rác;
- thêm `run_experiments_real.sh t3 rest` để chạy nốt phần còn thiếu.

**Còn dở.** Cần nạp credit để chạy E2-không-thử-lại, E4, đo verifier và phần safety của E2-không-verifier (≈ 1,4 $).
Phiếu chấm tay chờ người chấm.

**Đọc code này thế nào.**
1. `eval/runner.py` `GuardedLlm`: ba trần (lời gọi, token, USD) cùng sổ chi tiêu, tất cả kiểm **trước** khi gọi.
   `BudgetLedger` ghi lại sau mỗi lần chạy, kể cả khi lần chạy dừng sớm.
2. `app/agent/compose.py` `_grounded`: kết luận cuối là min(verifier, `coverage`) — **đáng học**. Model tự khai "tài
   liệu chỉ liên quan, không trả lời" là tín hiệu rẻ mà verifier (kiểm từng câu trích) không thấy được.
3. `app/agent/middleware/route_guard.py` `mask_unseen_ids`: cùng nguyên tắc nguồn gốc ID của ToolGate, áp cho văn bản.
