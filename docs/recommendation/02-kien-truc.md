# 2. Kiến trúc tổng thể

## 2.1. Thành phần

```mermaid
flowchart LR
    subgraph FE["Frontend (React)"]
        UI1["Tìm gia sư<br/>(bộ lọc + ô câu tự nhiên)"]
        UI2["Tìm học viên<br/>(bộ lọc của gia sư)"]
        UI3["Tìm kiếm đã lưu · Thông báo"]
    end

    subgraph BE["Spring Boot backend — module recommendation"]
        API["RecommendationController<br/>SavedSearchController"]
        ORC["RecommendationService<br/>(dựng QueryContext, điều phối)"]
        CAND["CandidateRepository<br/>(SQL lọc cứng + vector)"]
        FEAT["FeatureCalculator (a, gồm f_hist)"]
        WILL["WillingnessCalculator (b)"]
        RANK["Ranker (R, Q, G)"]
        EXP["ExplorationSelector (R6)"]
        RELAX["RelaxationService (C6)"]
        REF["SessionRefineService (B5)"]
        EXPL["ExplanationBuilder"]
        LOG["RecommendationLogger"]
        JOBS["Job nền:<br/>EnrichmentWorker (A1) · EmbeddingWorker<br/>MatchAlertWorker (C7) · RerankPrefetchJob<br/>ProfileUpdater (H1) · AggregateRefreshJob (P1)<br/>ExposureStatsJob (R6)"]
        AIC["AiServiceClient<br/>(HTTP, timeout, dự phòng)"]
    end

    subgraph AI["AI service (Python / FastAPI) — chỉ mạng nội bộ"]
        EMB["/embed — bge-m3 (ONNX int8)"]
        RR["/rerank — bge-reranker-v2-m3 (ONNX int8)"]
        ENR["/enrich — LLM trích xuất (A1)"]
        PQ["/parse-query — LLM tách câu (A2)"]
    end

    DB[("PostgreSQL + pgvector")]
    LLM["LLM API bên ngoài"]

    UI1 & UI2 & UI3 --> API --> ORC
    ORC --> CAND & FEAT & WILL & RANK & EXP & RELAX & EXPL & LOG
    API --> REF
    CAND & FEAT & WILL & LOG & JOBS --> DB
    ORC & JOBS --> AIC --> EMB & RR & ENR & PQ
    ENR & PQ --> LLM
    JOBS -->|"RECOMMENDATION_MATCH_FOUND"| UI3
```

Nguyên tắc:
- **Backend Java ra quyết định**: lọc, chấm điểm, xếp hạng. AI service chỉ là "máy tính" không
  trạng thái (văn bản → vector / điểm / JSON).
- **AI service không mở ra Internet**; chỉ backend gọi, kèm header bí mật dùng chung.
- **Mọi lời gọi AI service có timeout và dự phòng**; AI service chết thì chất lượng giảm, không lỗi.

## 2.2. Hai loại luồng

### Luồng nền

| Sự kiện | Việc làm |
|---|---|
| Lưu hồ sơ, tạo/sửa tin học / tin dạy | Ghi job `ENRICH` vào `ai_enrichment_jobs` trong cùng transaction (outbox) |
| `EnrichmentWorker` (mỗi 30 s) | Gọi `/enrich` → ghi tag, lịch suy ra → tạo job `EMBED` |
| `EmbeddingWorker` | Ghép văn bản, gọi `/embed`, ghi vector của tin đăng / hồ sơ |
| Tin đăng chuyển `OPEN` hoặc có embedding mới | `MatchAlertWorker`: đối chiếu với tin đăng và tìm kiếm đã lưu phía bên kia, gửi thông báo (C7) |
| Click / tym / đơn / đổi trạng thái đơn | `ProfileUpdater`: cập nhật hồ sơ sở thích (H1) |
| Hằng đêm | `RerankPrefetchJob` (rerank sẵn top-30 cho tin học `OPEN` và tìm kiếm đã lưu); 02:30 UTC: `AggregateRefreshJob` → tính lại `recommendation_score` → `ExposureStatsJob`; tính lại hồ sơ sở thích (giảm theo thời gian) |

### Luồng tìm kiếm

```mermaid
sequenceDiagram
    participant U as Người dùng
    participant API as Backend
    participant AI as AI service
    participant DB as PostgreSQL

    opt Gõ câu tự nhiên (A2)
        U->>API: POST /recommendations/parse-query
        API->>AI: /parse-query
        AI-->>API: JSON
        API-->>U: Giá trị bộ lọc (điền vào form để xem lại, sửa)
    end
    U->>API: POST /…/tutor-recommendations (bộ lọc)
    API->>DB: Đọc hồ sơ, hồ sơ sở thích → dựng QueryContext
    API->>AI: /embed văn bản nhu cầu (có cache)
    API->>DB: Lọc cứng + lấy ứng viên theo vector
    API->>DB: Đặc trưng, lịch sử quyết định, cache rerank
    API->>API: Tính a, b, R, Q, G → xếp hạng sơ bộ
    opt Top-20 chưa có rerank
        API->>AI: /rerank (timeout 800 ms)
    end
    API->>API: Xếp lại, slot khám phá, lý do khớp, (nới tiêu chí nếu ít kết quả)
    API->>DB: Ghi recommendation_logs
    API-->>U: Danh sách + lý do + gợi ý nới + logId
    U->>API: Xem / tym / bỏ qua → /events, /refine (B5)
```

## 2.3. Ngân sách độ trễ (mục tiêu p95)

| Bước | Thời gian |
|---|---|
| `/parse-query` (chỉ khi gõ câu; là bước riêng, trước khi bấm tìm) | ≤ 2 000 ms |
| Embed văn bản nhu cầu (cache theo mã băm) | ≤ 150 ms |
| SQL lọc cứng + vector | ≤ 150 ms |
| Chấm điểm ≤ 200 ứng viên | ≤ 50 ms |
| Rerank top-20 khi chưa có cache | ≤ 800 ms (timeout) |
| Nới tiêu chí (song song) | ≤ 400 ms |
| **Tổng cho một lần bấm tìm** | **≤ 1,5 s** |

## 2.4. Vị trí code đề xuất

```
backend/src/main/java/com/ptutor/backend/recommendation/
  controller/  RecommendationController, SavedSearchController
  service/     RecommendationService, QueryContextBuilder, FeatureCalculator, WillingnessCalculator,
               Ranker, ExplorationSelector, RelaxationService, SessionRefineService,
               ExplanationBuilder, RecommendationLogger, SavedSearchService, PreferenceProfileService
  job/         EnrichmentWorker, EmbeddingWorker, MatchAlertWorker, RerankPrefetchJob,
               ProfileUpdater, AggregateRefreshJob, ExposureStatsJob
  client/      AiServiceClient
  repository/  CandidateRepository (native SQL), …
  model/       QueryContext, Candidate, FeatureVector, ScoredCandidate, …
ai-service/    (thư mục mới ngang hàng backend/, frontend/)
  app/main.py  app/embed.py  app/rerank.py  app/llm.py  app/schemas.py
  models/      (volume, không commit)
```
