# Mô tả các bước của biểu đồ tuần tự

## Gợi ý gia sư cho học viên

Biểu đồ mô tả một lượt tìm kiếm có đủ tiêu chí và mô tả nhu cầu học để tạo vector ngữ nghĩa. Lifeline tạo vector gộp `AiServiceClient` của Spring Boot với dịch vụ FastAPI/BGE-M3 thành một phần xử lý; giao tiếp HTTP `/embed` vẫn diễn ra trong hệ thống. Các bước xử lý bên trong repository thể hiện việc framework hoặc truy vấn SQL thực thi; chúng không phải lời gọi đến một dịch vụ mới.

1. Học viên nhập tiêu chí tìm kiếm và mô tả nhu cầu học trên giao diện.
2. Học viên yêu cầu hệ thống gợi ý gia sư phù hợp.
3. Giao diện gửi dữ liệu tìm kiếm đến API đề xuất gia sư.
4. Bộ điều khiển chuyển yêu cầu đến dịch vụ xử lý đề xuất.
5. Dịch vụ gọi `findProfileByUserId` để lấy hồ sơ học viên đang đăng nhập.
6. Spring Data JPA thực thi truy vấn JPQL đã khai báo trong `StudentRepository` để lấy dữ liệu học viên, người dùng và địa bàn.
7. JPA ánh xạ dữ liệu truy vấn thành entity `Student`.
8. Entity `Student` được trả cho `StudentRepository`.
9. `StudentRepository` trả hồ sơ học viên cho dịch vụ đề xuất.
10. Dịch vụ yêu cầu `QueryContextBuilder` tổng hợp tiêu chí tìm kiếm.
11. Bộ tổng hợp tra cứu thông tin liên quan và ghép tiêu chí nhập vào với hồ sơ, yêu cầu học và nhãn nhu cầu.
12. Bộ tổng hợp trả ngữ cảnh tìm kiếm đã chuẩn hóa.
13. Dịch vụ yêu cầu `QueryEmbeddingService` lấy vector biểu diễn nhu cầu học.
14. Bộ xử lý vector gửi văn bản qua `AiServiceClient` đến API `/embed` của FastAPI để tạo embedding.
15. Dịch vụ AI dùng mô hình BGE-M3 mã hóa văn bản thành vector ngữ nghĩa.
16. Vector kết quả được chuyển qua `AiServiceClient` về bộ xử lý vector.
17. Bộ xử lý vector trả vector nhu cầu học cho dịch vụ đề xuất.
18. Dịch vụ yêu cầu `CandidateRepository` tìm các yêu cầu dạy phù hợp với tiêu chí bắt buộc.
19. Repository thực thi truy vấn lọc yêu cầu dạy còn hiệu lực và tính độ tương đồng với vector nhu cầu.
20. Repository trả danh sách yêu cầu dạy vượt qua bộ lọc ban đầu.
21. Dịch vụ yêu cầu repository lấy tỷ lệ tiếp nhận chung phục vụ tính điểm.
22. Repository truy vấn lịch sử chấp nhận yêu cầu để tính tỷ lệ tiếp nhận chung.
23. Repository trả tỷ lệ tiếp nhận đã tính từ dữ liệu lịch sử.
24. Dịch vụ yêu cầu repository tải dữ liệu chi tiết của các ứng viên đã lọc.
25. Repository truy vấn hồ sơ, yêu cầu dạy và dữ liệu liên quan của các ứng viên đã lọc.
26. `CandidateRepository` khởi tạo DTO `TeachingCandidate` từ dữ liệu đã tổng hợp.
27. Đối tượng `TeachingCandidate` được trả về cho repository để tạo danh sách ứng viên.
28. Repository trả danh sách ứng viên đủ thông tin cho bước xếp hạng.
29. Dịch vụ chuyển tập ứng viên đến `Ranker` để chấm điểm và sắp thứ tự.
30. `Ranker` tính các đặc trưng, mức độ phù hợp và khả năng tiếp nhận của từng ứng viên.
31. `Ranker` trả danh sách ứng viên đã được chấm điểm và xếp hạng.
32. Dịch vụ xây dựng lý do phù hợp và gợi ý điều chỉnh tiêu chí từ kết quả xếp hạng.
33. Dịch vụ chuyển tiêu chí, thứ hạng và kết quả cho `RecommendationLogger`.
34. `RecommendationLogger` tạo entity `RecommendationLog` từ tiêu chí và kết quả đã xếp hạng.
35. Entity `RecommendationLog` được chuyển lại cho bộ ghi nhật ký để lưu.
36. Bộ ghi nhật ký gọi repository lưu bản ghi lượt gợi ý.
37. Spring Data JPA ghi bản ghi `RecommendationLog` vào cơ sở dữ liệu.
38. Repository trả bản ghi đã lưu cho bộ ghi nhật ký.
39. Bộ ghi nhật ký trả mã lượt gợi ý để gắn với các tương tác tiếp theo.
40. Dịch vụ đóng gói danh sách gia sư, lý do phù hợp và mã lượt gợi ý thành kết quả trả về.
41. Bộ điều khiển gửi kết quả đề xuất đến giao diện.
42. Giao diện hiển thị danh sách gia sư được đề xuất cho học viên.


## Tự học trọng số đề xuất

Biểu đồ mô tả luồng chính của chu kỳ định kỳ có dữ liệu mới và có ít nhất một phiên bản trọng số đủ điều kiện kích hoạt. Các bước từ 2 đến 23 được lặp lại cho từng mô hình cần cập nhật.

1. Tác vụ định kỳ bắt đầu chu kỳ cập nhật trọng số đề xuất.
2. Dịch vụ học trọng số lấy chế độ hoạt động và trọng số ban đầu của mô hình.
3. `WeightProvider` trả các giá trị hiện hành để làm mốc so sánh.
4. Dịch vụ yêu cầu repository tìm phiên bản trọng số gần nhất của mô hình.
5. Spring Data JPA truy vấn các phiên bản theo mô hình và thứ tự phiên bản.
6. Spring Data JPA ánh xạ bản ghi vừa đọc thành entity `RecommendationLearnedWeights`.
7. Entity trọng số được trả cho repository.
8. Repository trả trọng số cũ cùng mốc dữ liệu đã được xử lý.
9. Dịch vụ yêu cầu `LearningDataRepository` lấy dữ liệu huấn luyện trong kỳ.
10. Repository truy vấn bằng SQL các lượt gợi ý, tương tác và kết quả đề nghị phù hợp với mô hình đang học.
11. Repository trả các mẫu phản hồi hoặc mẫu quyết định đã tổng hợp.
12. Dịch vụ chuyển các mẫu mới đến `LinearBayesModel`.
13. Mô hình Bayes cập nhật thống kê từ dữ liệu mới và tính bộ trọng số dự kiến.
14. Mô hình trả trọng số dự kiến cho dịch vụ học.
15. Dịch vụ yêu cầu dữ liệu giữ lại để đánh giá bộ trọng số mới.
16. Repository truy vấn bằng SQL các mẫu thuộc khoảng thời gian đánh giá.
17. Repository trả các mẫu dùng để kiểm tra chất lượng trọng số.
18. Dịch vụ so sánh chất lượng trọng số mới với trọng số ban đầu và xét điều kiện kích hoạt.
19. `WeightLearningService` tạo entity `RecommendationLearnedWeights` mới, chứa trọng số và kết quả đánh giá.
20. Entity mới được chuyển lại cho dịch vụ để thực hiện lưu.
21. Dịch vụ yêu cầu lưu phiên bản trọng số mới cùng kết quả đánh giá.
22. Spring Data JPA ghi phiên bản trọng số mới vào cơ sở dữ liệu.
23. Repository xác nhận phiên bản đã được lưu.
24. Khi có phiên bản được kích hoạt, dịch vụ phát thông báo nội bộ để `WeightProvider` nạp lại trọng số.
25. `WeightProvider` yêu cầu đọc phiên bản đang hoạt động.
26. Spring Data JPA truy vấn phiên bản đã được kích hoạt của mô hình.
27. JPA ánh xạ phiên bản đang hoạt động thành entity `RecommendationLearnedWeights`.
28. Entity phiên bản đang hoạt động được trả cho repository.
29. Repository trả trọng số mới để sử dụng cho các lượt đề xuất tiếp theo.
30. Dịch vụ trả kết quả cập nhật của chu kỳ cho tác vụ định kỳ.
