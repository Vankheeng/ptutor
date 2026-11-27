# Chạy đầy đủ tính năng AI trên dữ liệu kiểm thử

Nhánh `feat/realistic-test-data` đã tích hợp mã gợi ý và chatbot từ `feat/tutor-recommendation-support-chatbot-dev`. `docker-compose.local-assistant.yml` chạy backend, assistant-service, ai-service, frontend và PostgreSQL trong cùng project Compose và mạng `ptutor_default`. Backend và chatbot cùng dùng database `ptutor_app_seeded`; không tạo PostgreSQL thứ hai.

## Chuẩn bị

- `.env` ở gốc repo cần có `POSTGRES_PASSWORD`, `JWT_SECRET`, hai khóa mã hóa, `ASSISTANT_INTERNAL_TOKEN`, `AI_INTERNAL_TOKEN`, `OPENAI_API_KEY`, `ASSISTANT_LLM_MODEL` và `VNPAY_HASH_SECRET`. Không commit `.env`.
- `ptutor_app_seeded` cần đã chạy Flyway đến V54 và Alembic đến `0003`, đã nạp dữ liệu kiểm thử và kho tài liệu. Hiện kho có 76 tài liệu công bố, 455 chunk BGE-M3.
- `ai-service/models/bge-m3/onnx/model_int8.onnx` và `ai-service/models/bge-m3/tokenizer.json` cần có sẵn. `AI_AUTO_DOWNLOAD=false` để không tải model ngầm khi khởi động.

## Chạy từ mã nguồn

Tại gốc repo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local-assistant.yml --profile assistant up -d --build
```

Build từ mã nguồn cần kết nối tới kho Maven, npm và PyPI trong lần đầu. Các image được build từ `backend/`, `frontend/`, `assistant-service/` và `ai-service/` của nhánh đang checkout. PostgreSQL giữ volume `ptutor_postgres_data`.

Trên máy đã có sẵn dependency và image nền, có thể build không tải thêm package:

```powershell
cd backend
mvn -o -DskipTests clean package
cd ..
cd frontend
npm run build -- --outDir ../tmp/frontend_dist_current
cd ..
docker build -f deploy/backend-local.Dockerfile -t ptutor-local-backend:latest backend/target
docker build -f deploy/assistant-local.Dockerfile -t ptutor-local-assistant:latest .
docker build -f deploy/ai-local.Dockerfile -t ptutor-local-ai:latest ai-service
docker build -f deploy/frontend-local.Dockerfile -t ptutor-local-frontend:latest tmp/frontend_dist_current
docker compose -f docker-compose.yml -f docker-compose.local-assistant.yml --profile assistant up -d --no-build --no-deps ai-service assistant-service backend frontend
```

Lệnh `--no-deps` ở trên dùng container `ptutor-postgres` đang chạy sẵn; dùng lệnh build từ mã nguồn khi bắt đầu trên máy mới.

## Kiểm tra

- Giao diện: `http://127.0.0.1:8089/`; API backend: cổng `8091`.
- Chatbot: cổng `8103`; AI service: cổng `8003`. Các cổng dịch vụ này chỉ bind vào `127.0.0.1`.
- `GET http://127.0.0.1:8103/health` cần báo `db`, `llm`, `embedding` là `ready`. `rerank` là `disabled` theo cấu hình hiện tại, nên `status` tổng là `degraded`; tìm kiếm kết hợp vẫn hoạt động.
- `GET http://127.0.0.1:8003/health` cần báo `status=ready`, `embed_model=bge-m3-int8`, `llm=ready`.
- Thử qua giao diện với tài khoản dữ liệu kiểm thử: tìm gia sư, gợi ý học viên, điền bộ lọc từ câu tự nhiên và hỏi chatbot về ví. API gợi ý cần trả kết quả từ tin đang mở; câu trả lời chatbot cần có trích dẫn.

Dữ liệu học trọng số đã đạt ngưỡng cho bốn mô hình (`scripts/realistic_data/check_learning.py --database ptutor_app_seeded`). Stack cục bộ đặt cả bốn mô hình ở `SHADOW` và tắt tự kích hoạt (`SPRING_APPLICATION_JSON`) vì dữ liệu học là mô phỏng.

Các container demo cũ chạy ở project Compose khác không thuộc stack này. Không dùng `docker compose down -v`: lệnh đó sẽ xóa volume PostgreSQL.
