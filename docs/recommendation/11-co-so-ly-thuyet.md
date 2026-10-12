# 11. Cơ sở lý thuyết và công trình liên quan

## 11.1. Bài toán thuộc lĩnh vực nào

Ghép gia sư với học sinh là bài toán **gợi ý hai chiều (reciprocal recommendation)**: gợi ý *người*
cho *người*, và một ghép đôi chỉ thành công khi **cả hai phía cùng đồng ý**. Khác với gợi ý sản phẩm
(phim, hàng hoá — chỉ cần người dùng thích), ở đây "món hàng" cũng có quyền từ chối và có **sức chứa
giới hạn** (mỗi tin dạy nhận tối đa `quantity` học sinh). Lĩnh vực này đã được nghiên cứu nhiều trong
hẹn hò trực tuyến, tuyển dụng, học tập và cố vấn.

**Không có mô hình huấn luyện sẵn** cho bài toán gia sư: mô hình gợi ý luôn phải học từ dữ liệu tương
tác của chính nền tảng. Những gì dùng lại được là **phương pháp** đã công bố và **mô hình ngôn ngữ
huấn luyện sẵn** cho phần hiểu văn bản (`bge-m3`, `bge-reranker-v2-m3`, LLM).

## 11.2. Gợi ý hai chiều dựa trên nội dung — RECON

**Pizzato và cộng sự (2010), "RECON: A Reciprocal Recommender for Online Dating", RecSys.**
Sở thích của mỗi người được học từ những người họ đã liên hệ; tính điểm hai chiều riêng rẽ, gộp bằng
**trung bình điều hoà**. Không cần huấn luyện mô hình, phù hợp khi ít dữ liệu.

→ Áp dụng: điểm b học từ lịch sử nhận/từ chối của gia sư (06, mục 6.6); công thức `R = 2ab/(a+b)`.

**Palomares, Porcel, Pizzato, Guy, Herrera-Viedma (2020/2021), "Reciprocal Recommender Systems:
Analysis of State-of-Art Literature, Challenges and Opportunities towards Social Recommendation".**
Bài tổng quan: các họ phương pháp (dựa trên nội dung, lọc cộng tác, lai), mô hình tiêu biểu (RECON,
RCF, LFRR, CCR…), các cách gộp hai chiều (trung bình điều hoà được ưa dùng vì bị kéo về giá trị thấp),
chỉ số đánh giá theo hướng "cả hai cùng đồng ý", ứng dụng trong học tập trực tuyến và cố vấn.

→ Dùng làm cơ sở lý thuyết cho cách phân loại và lựa chọn hàm gộp.

## 11.3. Dồn đơn trong thị trường ghép đôi

**Su, Bayoumi, Joachims (2022), "Optimizing Rankings for Recommendation in Matching Markets", WWW.**
Xếp hạng theo độ phù hợp một chiều làm ứng viên dồn vào vài người nổi tiếng, nhiều người khác bị bỏ
quên. So sánh ba cách: một chiều, hai chiều (reciprocal), và tối ưu toàn cục số ghép đôi kỳ vọng. Trên
dữ liệu thật, reciprocal tốt hơn một chiều rõ rệt, phương pháp tối ưu toàn cục còn tốt hơn.

→ Áp dụng: chống dồn đơn mức đơn giản `b_cap` (06, mục 6.6); cách bố trí các cấu hình so sánh trong
đánh giá (09, mục 9.4); tối ưu toàn cục là hướng phát triển.

## 11.4. Gợi ý hai chiều có học từ dữ liệu (tuyển dụng)

**Goda, Hayashi, Saito (2024), "A Best-of-Both Approach to Improve Match Predictions and Reciprocal
Recommendations for Job Search" (Wantedly).** Huấn luyện hai mô hình một chiều (công ty → ứng viên,
ứng viên → công ty), kết hợp tích hai dự đoán với nhãn ghép đôi thật, dùng gradient boosting.

**"Modeling Two-Way Selection Preference for Person-Job Fit" (2022).** Mô hình hoá lựa chọn của cả hai
phía trong ghép người–việc.

→ Tham khảo cho hướng phát triển "mô hình a và b học từ dữ liệu" (10, mục 10.5).

## 11.5. Gợi ý gia sư

**Achón và cộng sự (2023), "SOS TUTORÍA UC: A Diversity-Aware Application for Tutor Recommendation
Based on Competence and Personality", HHAI-WS (CEUR).** Gợi ý gia sư đồng đẳng: lọc cứng theo năng lực
môn học, kết hợp tính cách (Big Five; người dùng chọn muốn gia sư giống/khác/không quan tâm) và khoảng
cách; có xử lý đa dạng; gia sư được chọn có thể chấp nhận/từ chối. Thử nghiệm nhỏ (43 sinh viên),
không có chỉ số đánh giá định lượng.

→ Điểm khác biệt của đồ án: mô hình hai chiều có học từ lịch sử quyết định, chống dồn đơn, hiểu văn
bản tự do tiếng Việt bằng LLM/embedding, thích ứng theo hành vi, và đánh giá định lượng.

Các nền tảng gia sư thương mại có chức năng ghép nhưng không công bố thuật toán; một số cách làm chỉ
xuất hiện dưới dạng bằng sáng chế, không dùng làm căn cứ học thuật.

## 11.6. Các kỹ thuật khác được dùng

| Kỹ thuật | Dùng ở | Ghi chú |
|---|---|---|
| Bi-encoder embedding + cross-encoder rerank | R3 | Mô hình huấn luyện sẵn `bge-m3`, `bge-reranker-v2-m3` (BAAI) |
| Phản hồi liên quan Rocchio | B5 | Kỹ thuật kinh điển trong truy hồi thông tin |
| Thompson sampling | R6 | Cân bằng khai thác/khám phá |
| Làm trơn Bayes | b, `recommendation_score` | Tránh đánh giá sai khi ít dữ liệu |
| Chuẩn hoá có schema bằng LLM | A1, A2 | LLM chỉ trích xuất, đầu ra luôn được kiểm tra |

## 11.7. Cách mô tả trong báo cáo

> Hệ gợi ý hai chiều **dựa trên nội dung theo hướng RECON**, tính riêng hai chiều và gộp bằng trung
> bình điều hoà; bổ sung **cơ chế chống dồn đơn đơn giản** lấy cảm hứng từ Su và cộng sự (2022); dùng
> **LLM và embedding để hiểu văn bản tự do tiếng Việt**; **thích ứng theo hành vi** trong phiên
> (Rocchio) và dài hạn (hồ sơ sở thích). Trọng số được thiết lập cho giai đoạn khởi đầu lạnh và hiệu
> chỉnh trên dữ liệu mô phỏng; hướng phát triển là học trọng số từ dữ liệu theo Goda và cộng sự (2024).

## 11.8. Tài liệu tham khảo

- Pizzato, L. và cộng sự (2010). RECON: A Reciprocal Recommender for Online Dating. RecSys.
  https://ls13-www.cs.tu-dortmund.de/homepage/ITWP2010/papers/ReciprocalRecommender.pdf
- Palomares, I., Porcel, C., Pizzato, L., Guy, I., Herrera-Viedma, E. Reciprocal Recommender Systems:
  Analysis of State-of-Art Literature, Challenges and Opportunities towards Social Recommendation.
  https://arxiv.org/abs/2007.16120
- Su, Y., Bayoumi, M., Joachims, T. (2022). Optimizing Rankings for Recommendation in Matching Markets.
  WWW. https://arxiv.org/abs/2106.01941
- Goda, S., Hayashi, Y., Saito, Y. A Best-of-Both Approach to Improve Match Predictions and Reciprocal
  Recommendations for Job Search. https://arxiv.org/abs/2409.10992
- Modeling Two-Way Selection Preference for Person-Job Fit. https://arxiv.org/abs/2208.08612
- Achón, L. và cộng sự (2023). SOS TUTORÍA UC: A Diversity-Aware Application for Tutor Recommendation
  Based on Competence and Personality. https://arxiv.org/abs/2309.10869
- Xia, P. và cộng sự. Reciprocal Recommendation System for Online Dating (RCF).
  https://arxiv.org/abs/1501.06247
- Tham khảo cài đặt RECON (Python, MIT): https://github.com/m-ochi/recon
