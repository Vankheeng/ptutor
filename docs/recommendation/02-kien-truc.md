# 2. Kiến trúc tổng thể

## 2.1. Thành phần

```mermaid
flowchart LR
    subgraph FE["Frontend (React)"]
        UI1["Màn hình tìm gia sư<br/>(bộ lọc + ô câu tự nhiên)"]
        UI2["Màn hình 'Học viên phù hợp'<br/>(trong tin dạy của gia sư)"]
        UI3["Thông báo"]
    end

    subgraph BE["Spring Boot backend — module recommendation"]
        API["RecommendationController"]
        ORC["RecommendationService<br/>(điều phối pipeline)"]
        CAND["CandidateRepository<br/>(SQL lọc cứng + vector)"]
        FEAT["FeatureCalculator (a)"]
        WILL["WillingnessCalculator (b)"]
        RANK["Ranker (R, Q, G)"]
        EXP["ExplorationSelector (R6)"]
        RELAX["RelaxationService (C6)"]
        REF["SessionRefineService (B5)"]
        EXPL["ExplanationBuilder"]
        LOG["RecommendationLogger"]
        JOBS["Job nền:<br/>EnrichmentWorker (A1)<br/>EmbeddingWorker<br/>PairScoringWorker (R3, C7)<br/>AggregateRefreshJob (P1)<br/>ExposureStatsJob (R6)"]
        AIC["AiServiceClient<br/>(HTTP, timeout, fallback)"]
    end

    subgraph AI["AI service (Python / FastAPI) — chỉ mạng nội bộ"]
        EMB["/embed — bge-m3 (ONNX int8)"]
        RR["/rerank — bge-reranker-v2-m3 (ONNX int8)"]
        ENR["/enrich — LLM trích xuất (A1)"]
        PQ["/parse-query — LLM tách câu (A2)"]
    end

    DB[("PostgreSQL + pgvector")]
    LLM["LLM API bên ngoài"]

    UI1 & UI2 --> API --> ORC
    ORC --> CAND & FEAT & WILL & RANK & EXP & RELAX & EXPL & LOG
    API --> REF
    CAND & FEAT & WILL & LOG & JOBS --> DB
    ORC & JOBS --> AIC --> EMB & RR & ENR & PQ
    ENR & PQ --> LLM
    JOBS -->|"sự kiện RECOMMENDATION_MATCH_FOUND"| UI3
```

Nguyên tắc:
- **Backend Java là nơi quyết định.** Toàn bộ lọc, chấm điểm, xếp hạng nằm trong Spring Boot.
  AI service chỉ là "máy tính" không trạng thái: nhận văn bản, trả vector, điểm hoặc JSON.
- **AI service không mở ra Internet.** Chỉ backend gọi được, kèm header bí mật dùng chung.
- **Mọi lời gọi AI service đều có timeout và phương án dự phòng.** Đề xuất vẫn chạy khi AI
  service chết (chất lượng giảm, không lỗi).

## 2.2. Hai loại luồng

### Luồng nền (không ảnh hưởng độ trễ tìm kiếm)

| Sự kiện | Việc làm |
|---|---|
| Lưu hồ sơ học sinh / gia sư, tạo/sửa tin học / tin dạy | Ghi một job vào `ai_enrichment_jobs` trong cùng transaction (outbox) |
| `EnrichmentWorker` (mỗi 30 giây) | Gọi `/enrich` → ghi tag, lịch rảnh suy ra; rồi tạo job embedding |
| `EmbeddingWorker` | Ghép "văn bản nhu cầu" / "văn bản cung cấp", gọi `/embed`, ghi vector |
| Tin dạy hoặc tin học chuyển `OPEN`, hoặc vừa có embedding mới | `PairScoringWorker` chấm cặp tin mới với các tin `OPEN` phía bên kia, gọi `/rerank` cho các cặp tốt, lưu `recommendation_pair_scores`, gửi thông báo nếu vượt ngưỡng (C7) |
| Hằng ngày 02:30 UTC | `AggregateRefreshJob` (P1) → tính lại `recommendation_score` → `ExposureStatsJob` |

### Luồng tìm kiếm (đồng bộ)

```mermaid
sequenceDiagram
    participant U as Người dùng
    participant API as Backend
    participant AI as AI service
    participant DB as PostgreSQL

    U->>API: POST /recommendations (bộ lọc, câu tự nhiên?)
    opt Có câu tự nhiên (A2)
        API->>AI: /parse-query
        AI-->>API: JSON bộ lọc + phần ngữ nghĩa
    end
    API->>DB: Lọc cứng + lấy ứng viên theo vector
    API->>DB: Đọc đặc trưng, lịch sử chấp nhận, điểm rerank đã lưu
    API->>API: Tính a, b, R, Q, G → xếp hạng sơ bộ
    opt Top-K chưa có điểm rerank
        API->>AI: /rerank (timeout 800 ms)
    end
    API->>API: Xếp hạng lại, chèn slot khám phá (R6), sinh lý do
    opt Ít kết quả tốt
        API->>API: Thử nới tiêu chí (C6)
    end
    API->>DB: Ghi recommendation_logs
    API-->>U: Danh sách + lý do + gợi ý nới + logId
    U->>API: Click / tym / bỏ qua → POST /refine (B5)
```

## 2.3. Ngân sách độ trễ (mục tiêu p95)

| Bước | S→T không có câu tự nhiên | Có câu tự nhiên |
|---|---|---|
| `/parse-query` (LLM) | — | ≤ 2 000 ms |
| Embed câu truy vấn | — | ≤ 150 ms |
| SQL lọc cứng + vector | ≤ 150 ms | ≤ 150 ms |
| Chấm điểm ≤ 200 ứng viên | ≤ 50 ms | ≤ 50 ms |
| Rerank top-20 khi chưa có cache | ≤ 800 ms (timeout) | ≤ 800 ms |
| Nới tiêu chí (song song) | ≤ 400 ms | ≤ 400 ms |
| **Tổng** | **≤ 1,5 s** | **≤ 3,5 s** |

Giao diện hiển thị trạng thái "Đang hiểu yêu cầu…" khi có bước LLM.

## 2.4. Vị trí code đề xuất

```
backend/src/main/java/com/ptutor/backend/recommendation/
  controller/RecommendationController.java
  service/RecommendationService.java          # điều phối
  service/FeatureCalculator.java              # a
  service/WillingnessCalculator.java          # b
  service/Ranker.java                         # R, Q, G
  service/ExplorationSelector.java            # R6
  service/RelaxationService.java              # C6
  service/SessionRefineService.java           # B5
  service/ExplanationBuilder.java
  service/RecommendationLogger.java
  job/EnrichmentWorker.java  job/EmbeddingWorker.java
  job/PairScoringWorker.java job/AggregateRefreshJob.java job/ExposureStatsJob.java
  client/AiServiceClient.java
  repository/CandidateRepository.java         # native SQL
  model/  (QueryContext, Candidate, FeatureVector, ScoredCandidate…)
ai-service/   (thư mục mới ngang hàng backend/, frontend/)
  app/main.py  app/embed.py  app/rerank.py  app/llm.py  app/schemas.py
  models/      (volume, không commit)
```

Đặt tất cả trong một package `recommendation` giúp tách bạch với phần nghiệp vụ hiện có và dễ
trình bày trong báo cáo.
