# Đối chiếu các lớp tham gia AI đề xuất

Sơ đồ `tuan-tu-ai-de-xuat` mô tả **một lần học viên tìm gia sư mới** với tiêu chí đã xác nhận. Các lời gọi tùy chọn trước và sau lần tìm kiếm nằm trong hai sơ đồ tuần tự bổ sung.

## Các dependency của `RecommendationService`

| Lớp | Vai trò trong code | Trong sơ đồ tìm kiếm mới |
| --- | --- | --- |
| `StudentRepository` | Tải hồ sơ học viên | Có |
| `CandidateRepository` | Lọc yêu cầu dạy, tìm tương đồng, nạp dữ liệu ứng viên | Có |
| `RecommendationLogRepository` | Đọc lần tìm kiếm cũ khi phân trang; ghi qua `RecommendationLogger` | Có ở bước lưu |
| `QueryContextBuilder` | Ghép tiêu chí từ bộ lọc, hồ sơ, yêu cầu học | Có |
| `QueryEmbeddingService` | Lấy vector cache, gọi AI hoặc dùng vector hồ sơ | Có |
| `Ranker` | Tính điểm xếp hạng | Có |
| `RerankService` | Vẫn được gọi nhưng trả `DISABLED` khi mode `OFF` | Có; ghi rõ trạng thái tắt theo cấu hình hiện tại |
| `MatchReasonBuilder` | Tạo lý do phù hợp cho từng kết quả | Có |
| `RelaxationService` | Tạo gợi ý nới điều kiện | Có |
| `ExplorationService` | Bổ sung ứng viên khám phá theo cấu hình | Có |
| `PreferenceProfileService` | Đọc lịch sử tương tác và mức quan tâm theo nhóm | Có |
| `SavedSearchService` | Nạp tiêu chí nếu người dùng chọn bộ tìm kiếm đã lưu | Có, trong nhánh tùy chọn |
| `QueryParseService` | Đọc lại kết quả phân tích câu tự nhiên để lưu nhật ký | Có, trong nhánh tùy chọn; luồng phân tích mới có sơ đồ riêng |
| `RecommendationTagRepository` | Lấy tên và mã nhãn đang hoạt động | Có |
| `RecommendationLogger` | Tạo và lưu nhật ký xếp hạng | Có |
| `RecommendationSettings` | Cung cấp cấu hình thuật toán | Không tạo lifeline riêng vì không phải đối tượng nghiệp vụ trao đổi dữ liệu với người dùng |
| `CertificateFileStorageService` | Đổi đường dẫn ảnh đại diện S3 thành URL khi tạo response, nếu có | Có, trong nhánh tùy chọn |

Trong `Ranker`, `FeatureCalculator` và `WillingnessCalculator` tính điểm bằng các trọng số do `WeightProvider` cung cấp. Các lớp này được thể hiện trong phần xếp hạng. `PreferenceProfileService` đọc `RecommendationUserProfile` qua `RecommendationUserProfileRepository` khi tính đặc trưng lịch sử.

`QueryContextBuilder` đọc qua `StudyingRequestRepository`, `SubjectRepository`, `GradeRepository`, `ProvinceRepository`, `RecommendationTagRepository` và `RecommendationEntityTagRepository`. Sơ đồ thể hiện tường minh các truy xuất chính; bước kiểm tra tỉnh chỉ xảy ra khi bộ lọc chọn phạm vi tỉnh. `CandidateRepository` dùng JDBC để lấy dữ liệu từ nhiều bảng và dựng `TeachingCandidate` (record DTO); không tạo hay gọi các đối tượng JPA `TeachingRequest`, `Tutor` và `User` trong luồng này.

Các DTO như `TutorRecommendationRequest`, `QueryContext`, `TeachingCandidate`, `ScoredCandidate`, `FeatureVector`, `History`, `MatchReasons`, `RelaxationSuggestion` và `TutorRecommendationResponse` là dữ liệu truyền giữa các lớp. Chỉ hai DTO được tạo thành đối tượng trung gian nổi bật (`QueryContext`, `TeachingCandidate`) có lifeline riêng. Sơ đồ tuần tự không dùng để liệt kê toàn bộ lớp hay trường dữ liệu của hệ thống.

## Phạm vi các sơ đồ bổ sung

- `tuan-tu-phan-tich-nhu-cau`: API phân tích câu tự nhiên bằng LLM, chạy **trước** yêu cầu đề xuất khi người dùng chọn chức năng này.
- `tuan-tu-tuong-tac-ket-qua`: ghi nhận hành động, tinh chỉnh danh sách chưa xem và đọc trang tiếp theo, chạy **sau** khi đã có `RecommendationLog`.

Theo `RecommendationSettings`, `app.recommendation.rerank.mode` mặc định là `OFF`; `docker-compose.yml` cũng để `AI_RERANK_ENABLED=false`. Mã `RecommendationService` vẫn gọi `RerankService`, nhưng ở cấu hình này dịch vụ trả `DISABLED` và không thay đổi thứ hạng.
