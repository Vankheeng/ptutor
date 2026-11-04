# assistant-service — trợ lý hỗ trợ PTutor

Dịch vụ Python (FastAPI) trả lời câu hỏi của học viên và gia sư về cách dùng nền tảng (kho tri thức) và tra cứu dữ
liệu của chính họ (công cụ chỉ-đọc gọi API "của tôi" của backend). Chỉ backend gọi vào dịch vụ này (header
`X-Internal-Token`); frontend không bao giờ gọi thẳng. Thiết kế: [`docs/support-chatbot/`](../docs/support-chatbot/).

## Chạy trên máy dev

```bash
cd assistant-service
python -m venv .venv && .venv/Scripts/pip install -r requirements-dev.txt   # Linux / macOS: .venv/bin/...
cp ../.env.example .env        # điền ASSISTANT_* (không commit .env)
.venv/Scripts/alembic upgrade head                                         # schema assistant
.venv/Scripts/python -m app.kb.import --path ../knowledge-base             # nạp kho tri thức (nháp)
.venv/Scripts/uvicorn app.main:create_app --factory --port 8100
```

Biến môi trường chính (tiền tố `ASSISTANT_`, xem `app/config.py`):

| Biến | Ý nghĩa |
|---|---|
| `ASSISTANT_INTERNAL_TOKEN` | Bí mật dùng chung với backend (`APP_ASSISTANT_SERVICE_INTERNAL_TOKEN`). Trống → mọi `/v1/**` trả 503 |
| `ASSISTANT_DATABASE_URL` | `postgresql+psycopg://…` (cần extension `vector`, `unaccent`) |
| `ASSISTANT_LLM_MODEL`, `OPENAI_API_KEY`, `OPENAI_BASE_URL` | Model tương thích OpenAI. Thiếu → `/v1/chat` trả 503 |
| `ASSISTANT_LLM_PROVIDER` | `openai` (mặc định) hoặc `scripted` — chỉ để demo khi chưa có model |
| `ASSISTANT_TURN_TOKEN_BUDGET` | Trần token của một lượt hỏi (mọi lời gọi model) |
| `ASSISTANT_EMBEDDING_PROVIDER` | `fake` (mặc định) hoặc `ai-service` (bge-m3, cần `AI_SERVICE_URL`, `AI_INTERNAL_TOKEN`) |
| `ASSISTANT_TOOLS_MODE` | `live` (gọi backend), `mock` (fixture eval), `off` |
| `ASSISTANT_BACKEND_JVM_TIME_ZONE` | Múi giờ JVM của backend, để đổi đúng các trường thời gian (nhãn `time_zone` trong `tools.yaml`) |

Chạy cả hệ thống bằng Docker: [`HUONG-DAN-DEMO.md`](../docs/support-chatbot/trien-khai/HUONG-DAN-DEMO.md).

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `app/api/` | `/v1/chat`, hội thoại (`/excerpt` cho yêu cầu hỗ trợ), đánh giá 👍👎, quản trị kho tri thức |
| `app/agent/` | `AgentRunner` + 17 middleware (thứ tự ở `harness.py`), prompt, xác minh trích dẫn |
| `app/kb/` | Đọc, kiểm tra, chia đoạn, nạp, công bố tài liệu |
| `app/retrieval/` | Truy xuất lai (pgvector + full-text, RRF), rerank, mở rộng đoạn |
| `app/tools/` | Danh mục công cụ từ `tool-catalog/tools.yaml`, gọi backend, lọc kết quả |
| `app/scope/`, `app/privacy/` | Chặn ngoài phạm vi, che thông tin cá nhân |
| `migrations/` | Alembic (schema `assistant`) |
| `eval/` | Bộ đánh giá: dữ liệu, fixture, bộ chấm, thí nghiệm — xem [báo cáo](../docs/support-chatbot/trien-khai/BAO-CAO-DANH-GIA.md) |
| `tests/` | `unit` (không mạng, không DB), `integration` (cần Postgres), `contract` (cần backend chạy) |

## Kiểm tra

```bash
.venv/Scripts/python -m pytest -q -m "not contract"     # integration tự bỏ qua nếu chưa đặt ASSISTANT_TEST_ADMIN_DATABASE_URL
.venv/Scripts/ruff check app tests eval scripts
.venv/Scripts/mypy app
.venv/Scripts/python scripts/check_catalog.py           # danh mục công cụ và route khớp code backend / frontend
.venv/Scripts/python -m eval.run --mode quick --split dev   # eval nhanh, không tốn tiền (Actor trích xuất)
OPENAI_API_KEY=... bash eval/run_experiments_real.sh t3     # T3 model thật: in ước tính, trần 10 $ (sổ chi tiêu chung)
```

Đọc code bắt đầu từ [`NHAT-KY-TRIEN-KHAI.md`](../docs/support-chatbot/trien-khai/NHAT-KY-TRIEN-KHAI.md) mục "Đọc code này thế
nào" của từng module.
