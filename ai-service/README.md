# PTutor AI service

Service nội bộ, không trạng thái: nhận văn bản, trả vector. Chỉ backend gọi (header `X-Internal-Token`),
không mở ra Internet. Thiết kế: `docs/recommendation/02-kien-truc.md`, `10-van-hanh-va-huong-phat-trien.md` mục 10.2.

| Endpoint | Mô tả |
|---|---|
| `GET /health` | `{"status": "ready" \| "loading" \| "error", "embed_model": "bge-m3-int8"}`. Không cần token. |
| `POST /enrich` | A1: trích tag và lịch rảnh từ văn bản tự do bằng LLM (OpenAI, `gpt-6-luna` mặc định), trả JSON theo schema: `{"model", "schedule": [{dayOfWeek, start, end, confidence}], "tags": [{group, code, role, confidence}]}`. Chỉ nhận mã có trong `vocabulary` gửi kèm. Không có `OPENAI_API_KEY` → `503`; nhà cung cấp lỗi → `502`. |
| `POST /rerank` | R3: `{"query": "...", "documents": ["..."]}` → `{"model": "bge-reranker-v2-m3-int8", "scores": [0.91, 0.02]}`. Điểm trong [0, 1], đúng thứ tự `documents`. Tối đa 40 văn bản mỗi lần; câu truy vấn và mỗi văn bản cắt còn 256 token. Model chưa nạp / đang tắt → `503`; `/health` có thêm `"rerank": "ready" \| "loading" \| "error" \| "disabled"`. |
| `POST /embed` | `{"texts": ["..."]}` → `{"model": "bge-m3-int8", "dimensions": 1024, "embeddings": [[...]]}`. Vector đã chuẩn hoá độ dài 1 (cosine = tích vô hướng). Tối đa 32 văn bản mỗi lần, mỗi văn bản cắt còn 512 token. |

Model: `BAAI/bge-m3` bản ONNX int8 từ `Xenova/bge-m3` (`onnx/model_int8.onnx`, ~586 MB kể cả tokenizer).
Model rerank: `BAAI/bge-reranker-v2-m3` bản ONNX int8 từ `onnx-community/bge-reranker-v2-m3-ONNX` (~588 MB).
Embedding không phụ thuộc model này: thiếu hoặc tắt (`AI_RERANK_ENABLED=false`) thì `/embed` vẫn chạy.
File model nằm trong `models/` (đã bị git bỏ qua), không đóng gói vào image.

## Chạy trên máy (không Docker)

```powershell
cd ai-service
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install -r requirements-dev.txt
.venv\Scripts\python scripts\download_models.py --dry-run   # xem dung lượng, chưa tải
.venv\Scripts\python scripts\download_models.py             # tải 2 model (~1,2 GB) vào models\; --only embed | rerank để tải một
$env:AI_INTERNAL_TOKEN = "<chuỗi bí mật>"
.venv\Scripts\python -m uvicorn app.main:create_app --factory --port 8000
```

## Chạy bằng Docker

```powershell
# trong .env ở thư mục gốc: AI_INTERNAL_TOKEN=<chuỗi bí mật>
docker compose --profile ai up -d --build ai-service
```

Cổng chỉ mở trên `127.0.0.1`. Lần chạy đầu container tự tải model vào `ai-service/models`
(`AI_AUTO_DOWNLOAD=true`) nếu chưa có.

## Backend

Backend đọc `app.ai-service.*` (mặc định `http://127.0.0.1:8000`; dùng `127.0.0.1` thay vì `localhost` vì trên Windows `localhost` có thể thử IPv6 trước và chậm khoảng 2 giây mỗi kết nối). Đặt cùng chuỗi bí mật bằng biến môi
trường `APP_AI_SERVICE_INTERNAL_TOKEN` hoặc thêm vào `application.yml` cục bộ:

```yaml
app:
  ai-service:
    internal-token: <chuỗi bí mật>
```

Văn bản gửi tới OpenAI đã được backend xoá số điện thoại và email, không kèm họ tên hay định danh; lời gọi đặt
`store=False` để nhà cung cấp không lưu lại.

AI service tắt hoặc chậm thì backend không lỗi: `AiServiceClient` trả kết quả rỗng và nơi gọi dùng phương án dự phòng.

## Kiểm thử và đo tốc độ

```powershell
.venv\Scripts\python -m pytest -q
.venv\Scripts\python scripts\benchmark_embed.py                                   # nạp model trực tiếp
.venv\Scripts\python scripts\benchmark_embed.py --url http://127.0.0.1:8000 --token <chuỗi bí mật>
.venv\Scripts\python scripts\benchmark_rerank.py                                  # 20 cặp ngắn / dài + kiểm tra tiếng Việt
```

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `AI_INTERNAL_TOKEN` | (bắt buộc) | Chuỗi bí mật dùng chung với backend |
| `AI_ALLOW_ANONYMOUS` | `false` | `true` để chạy thử không cần token |
| `AI_MODEL_DIR` | `./models` | Thư mục chứa model |
| `AI_AUTO_DOWNLOAD` | `false` (Docker: `true`) | Tự tải model khi thiếu |
| `AI_EMBED_MAX_TOKENS` / `AI_EMBED_MAX_BATCH` / `AI_EMBED_MAX_CHARS` | 512 / 32 / 8000 | Giới hạn đầu vào |
| `AI_THREADS` | 0 (tự chọn) | Số luồng ONNX Runtime |
| `AI_RERANK_ENABLED` | `true` | `false` để không nạp model rerank (tiết kiệm ~1 GB RAM) |
| `AI_RERANK_MAX_TOKENS` / `AI_RERANK_MAX_DOCUMENTS` / `AI_RERANK_MAX_CHARS` / `AI_RERANK_BATCH_SIZE` | 256 / 40 / 8000 / 4 | Giới hạn mỗi bên của một cặp, số văn bản mỗi lần gọi, độ dài, cỡ lô suy luận |
| `OPENAI_API_KEY` | (trống) | Khoá OpenAI cho `/enrich`. Trống thì `/enrich` tắt, `/health` báo `"llm": "disabled"`, embedding vẫn chạy |
| `AI_LLM_MODEL` | `gpt-6-luna` | Model dùng cho `/enrich` |
| `AI_LLM_TIMEOUT_SECONDS` / `AI_LLM_REASONING_EFFORT` / `AI_ENRICH_MAX_CHARS` | 20 / (không gửi) / 8000 | Thời gian chờ, mức suy luận (tuỳ chọn), giới hạn độ dài văn bản |
