# 11. Cơ sở lý thuyết và công trình liên quan

> 💡 **File này nói về gì:** Các nghiên cứu đã công bố mà thiết kế dựa vào, và mỗi nghiên cứu được áp dụng ở đâu. Dùng khi viết chương cơ sở lý thuyết của báo cáo hoặc trả lời câu hỏi "ý tưởng này lấy từ đâu".

## 11.1. Bài toán thuộc lĩnh vực nào

> 💡 Ghép gia sư – học sinh là **gợi ý hai chiều** (reciprocal recommendation): gợi ý người cho người, và chỉ thành khi cả hai cùng đồng ý — khác gợi ý phim, nơi bộ phim không từ chối người xem. Không có mô hình làm sẵn cho bài toán gia sư; cái dùng lại được là phương pháp và các mô hình ngôn ngữ huấn luyện sẵn.

Ghép gia sư với học sinh là bài toán **gợi ý hai chiều (reciprocal recommendation)**: gợi ý *người*
cho *người*, và một ghép đôi chỉ thành công khi **cả hai phía cùng đồng ý**. Khác với gợi ý sản phẩm
(phim, hàng hoá — chỉ cần người dùng thích), ở đây "món hàng" cũng có quyền từ chối và có **sức chứa
giới hạn** (mỗi tin dạy nhận tối đa `quantity` học sinh). Lĩnh vực này đã được nghiên cứu nhiều trong
hẹn hò trực tuyến, tuyển dụng, học tập và cố vấn.

**Không có mô hình huấn luyện sẵn** cho bài toán gia sư: mô hình gợi ý luôn phải học từ dữ liệu tương
tác của chính nền tảng. Những gì dùng lại được là **phương pháp** đã công bố và **mô hình ngôn ngữ
huấn luyện sẵn** cho phần hiểu văn bản (`bge-m3`, `bge-reranker-v2-m3`, LLM).

## 11.2. Gợi ý hai chiều dựa trên nội dung — RECON

> 💡 **RECON** (2010) là hệ gợi ý cho hẹn hò trực tuyến: học sở thích mỗi người từ những ai họ từng liên hệ, tính điểm hai chiều riêng, rồi gộp bằng **trung bình điều hòa** (bị kéo về số nhỏ, nên phải hợp cả hai). Không cần huấn luyện mô hình nên hợp khi ít dữ liệu. Đồ án dùng ý này cho `b_hist` và công thức R.

**Pizzato và cộng sự (2010), "RECON: A Reciprocal Recommender for Online Dating", RecSys.**
Sở thích của mỗi người được học từ những người họ đã liên hệ; tính điểm hai chiều riêng rẽ, gộp bằng
**trung bình điều hoà**. Không cần huấn luyện mô hình, phù hợp khi ít dữ liệu.

→ Áp dụng: thành phần `b_hist` ước lượng từ lịch sử nhận/từ chối của gia sư (06, mục 6.6); công thức
`R = 2ab/(a+b)`.

**Palomares, Porcel, Pizzato, Guy, Herrera-Viedma (2020/2021), "Reciprocal Recommender Systems:
Analysis of State-of-Art Literature, Challenges and Opportunities towards Social Recommendation".**
Bài tổng quan: các họ phương pháp (dựa trên nội dung, lọc cộng tác, lai), mô hình tiêu biểu (RECON,
RCF, LFRR, CCR…), các cách gộp hai chiều (trung bình điều hoà được ưa dùng vì bị kéo về giá trị thấp),
chỉ số đánh giá theo hướng "cả hai cùng đồng ý", ứng dụng trong học tập trực tuyến và cố vấn.

→ Dùng làm cơ sở lý thuyết cho cách phân loại và lựa chọn hàm gộp.

## 11.3. Dồn đơn trong thị trường ghép đôi

> 💡 Su và cộng sự (2022) chỉ ra: nếu chỉ xếp theo sở thích một phía, mọi người dồn vào vài người nổi tiếng và phần lớn bị từ chối. Xếp hai chiều tốt hơn hẳn. Đồ án lấy ý này cho hệ số chống dồn đơn và cách bố trí các cấu hình so sánh.

**Su, Bayoumi, Joachims (2022), "Optimizing Rankings for Recommendation in Matching Markets", WWW.**
Xếp hạng theo độ phù hợp một chiều làm ứng viên dồn vào vài người nổi tiếng, nhiều người khác bị bỏ
quên. So sánh ba cách: một chiều, hai chiều (reciprocal), và tối ưu toàn cục số ghép đôi kỳ vọng. Trên
dữ liệu thật, reciprocal tốt hơn một chiều rõ rệt, phương pháp tối ưu toàn cục còn tốt hơn.

→ Áp dụng: chống dồn đơn mức đơn giản `b_cap` (06, mục 6.6); cách bố trí các cấu hình so sánh trong
đánh giá (09, mục 9.4); tối ưu toàn cục là hướng phát triển.

## 11.4. Gợi ý hai chiều có học từ dữ liệu (tuyển dụng)

> 💡 Nghiên cứu ở trang tuyển dụng Wantedly: huấn luyện hai mô hình một chiều (công ty chọn ứng viên, ứng viên chọn công ty) rồi kết hợp. Tham khảo cho hướng phát triển khi có dữ liệu thật.

**Goda, Hayashi, Saito (2024), "A Best-of-Both Approach to Improve Match Predictions and Reciprocal
Recommendations for Job Search" (Wantedly).** Huấn luyện hai mô hình một chiều (công ty → ứng viên,
ứng viên → công ty), kết hợp tích hai dự đoán với nhãn ghép đôi thật, dùng gradient boosting.

**"Modeling Two-Way Selection Preference for Person-Job Fit" (2022).** Mô hình hoá lựa chọn của cả hai
phía trong ghép người–việc.

→ Tham khảo cho hướng phát triển "mô hình a và b học từ dữ liệu" (10, mục 10.5).

## 11.5. Gợi ý gia sư

> 💡 Công trình gần nhất với đồ án (SOS TUTORÍA UC, 2023): gợi ý gia sư theo năng lực và tính cách, nhưng thử nghiệm nhỏ, không có số liệu đánh giá. Đoạn cuối nêu điểm khác biệt của đồ án.

**Achón và cộng sự (2023), "SOS TUTORÍA UC: A Diversity-Aware Application for Tutor Recommendation
Based on Competence and Personality", HHAI-WS (CEUR).** Gợi ý gia sư đồng đẳng: lọc cứng theo năng lực
môn học, kết hợp tính cách (Big Five; người dùng chọn muốn gia sư giống/khác/không quan tâm) và khoảng
cách; có xử lý đa dạng; gia sư được chọn có thể chấp nhận/từ chối. Thử nghiệm nhỏ (43 sinh viên),
không có chỉ số đánh giá định lượng.

→ Điểm khác biệt của đồ án: mô hình hai chiều có học từ lịch sử quyết định, chống dồn đơn, hiểu văn
bản tự do tiếng Việt bằng LLM/embedding, thích ứng theo hành vi, và đánh giá định lượng.

Các nền tảng gia sư thương mại có chức năng ghép nhưng không công bố thuật toán; một số cách làm chỉ
xuất hiện dưới dạng bằng sáng chế, không dùng làm căn cứ học thuật.

## 11.6. Contextual bandit — cơ sở cho L1

> 💡 **Bandit** là dạng đơn giản nhất của học tăng cường: mỗi lượt chọn một hành động, nhận phần thưởng, học dần — khó ở chỗ cân bằng giữa dùng cái đang tốt nhất và thử cái chưa chắc. **Contextual** = lựa chọn phụ thuộc bối cảnh của lượt đó. **Thompson Sampling** giải quyết bằng cách rút ngẫu nhiên từ "khoảng ước đoán": chưa chắc thì thử nhiều, chắc rồi thì ít thử. **IPW** là cách bù cho việc tin ở trên được bấm nhiều chỉ vì đứng trên. Đây là nền tảng của L1 (12).

**Bài toán bandit** là dạng đơn giản nhất của học tăng cường: mỗi lượt, tác tử chọn một hành động, nhận
phần thưởng, không có trạng thái chuyển tiếp (Sutton & Barto, 2018, chương 2). Khó khăn cốt lõi là
**cân bằng khai thác – khám phá**: dùng lựa chọn đang tốt nhất hay thử lựa chọn chưa chắc chắn.
**Contextual bandit** thêm ngữ cảnh: phần thưởng phụ thuộc vào đặc trưng của lượt đó.

**Li, Chu, Langford, Schapire (2010), "A Contextual-Bandit Approach to Personalized News Article
Recommendation", WWW.** Đặt bài toán gợi ý tin tức là contextual bandit với phần thưởng tuyến tính theo
đặc trưng (LinUCB); thử nghiệm trên Yahoo! Front Page. Là công trình nền tảng đưa bandit vào hệ gợi ý.

**Agrawal & Goyal (2013), "Thompson Sampling for Contextual Bandits with Linear Payoffs", ICML.** Linear
Thompson Sampling: giữ phân phối hậu nghiệm Gauss của vector trọng số, mỗi lượt rút một mẫu và hành động
theo mẫu đó; chứng minh cận regret. **Chapelle & Li (2011)** cho thấy Thompson sampling tốt ngang hoặc hơn
UCB trên dữ liệu quảng cáo và tin tức, và chịu được phản hồi trễ / cập nhật theo lô.

**Joachims, Swaminathan, Schnabel (2017), "Unbiased Learning-to-Rank with Biased Feedback", WSDM.** Click
bị thiên lệch theo vị trí; dùng trọng số nghịch đảo xác suất được nhìn (IPW) để ước lượng không chệch.

**Yue & Joachims (2009), "Interactively Optimizing Information Retrieval Systems as a Dueling Bandits
Problem", ICML.** Học trọng số của bộ xếp hạng tuyến tính từ so sánh click giữa hai danh sách trộn
(interleaving) — phương án thay thế cho L1, không chọn vì phức tạp khi hiển thị và hội tụ chậm hơn (12,
mục 12.12).

→ Áp dụng (L1, [12](./12-hoc-trong-so.md)): vector đặc trưng a là ngữ cảnh; trọng số tự đặt là **trung
bình của prior Gauss**; Linear Thompson Sampling cho phía người tìm (phản hồi phụ thuộc thứ tự hiển thị nên
cần khám phá); hồi quy Bayes trực tuyến cho phía đồng ý (nhãn nhận / từ chối không phụ thuộc vị trí); IPW
và slot khám phá để giảm thiên lệch vị trí. Khác các công trình trên: hành động ở đây là **chọn bộ trọng
số cho cả danh sách**, có kẹp quanh prior và giới hạn bước để an toàn.

## 11.7. Các kỹ thuật khác được dùng

> 💡 Bảng tra nhanh các kỹ thuật còn lại và chỗ dùng: tạo vector + chấm kỹ (rerank), Rocchio (xếp lại trong phiên), Thompson Sampling, hồi quy Bayes, làm trơn Bayes (kéo ước lượng về mức chung khi ít dữ liệu), chuẩn hóa văn bản bằng LLM.

| Kỹ thuật | Dùng ở | Ghi chú |
|---|---|---|
| Bi-encoder embedding + cross-encoder rerank | R3 | Mô hình huấn luyện sẵn `bge-m3`, `bge-reranker-v2-m3` (BAAI) |
| Phản hồi liên quan Rocchio | B5 | Kỹ thuật kinh điển trong truy hồi thông tin |
| Thompson sampling | R6, L1 | Cân bằng khai thác/khám phá (Beta cho slot khám phá; Linear TS cho trọng số) |
| Hồi quy tuyến tính Bayes trực tuyến | L1 | Prior = trọng số tự đặt; cập nhật bằng thống kê đủ |
| Làm trơn Bayes | b, `recommendation_score` | Tránh đánh giá sai khi ít dữ liệu |
| Chuẩn hoá có schema bằng LLM | A1, A2 | LLM chỉ trích xuất, đầu ra luôn được kiểm tra |

## 11.8. Cách mô tả trong báo cáo

> 💡 Một đoạn văn mẫu tóm tắt toàn bộ cách tiếp cận bằng thuật ngữ học thuật, dùng được trong báo cáo hoặc khi bảo vệ.

> Hệ gợi ý hai chiều **dựa trên nội dung theo hướng RECON**, tính riêng hai chiều và gộp bằng trung
> bình điều hoà; bổ sung **cơ chế chống dồn đơn đơn giản** lấy cảm hứng từ Su và cộng sự (2022); dùng
> **LLM và embedding để hiểu văn bản tự do tiếng Việt**; **thích ứng theo hành vi** trong phiên
> (Rocchio) và dài hạn (hồ sơ sở thích). Trọng số do nhóm thiết lập đóng vai trò **prior cho giai đoạn
> khởi đầu lạnh**; khi vận hành, hệ thống **tự tinh chỉnh trọng số bằng contextual bandit** (Linear
> Thompson Sampling — Agrawal & Goyal, 2013) cho phía người tìm và **hồi quy Bayes trực tuyến** cho phía
> đồng ý; cơ chế học được kiểm chứng trên dữ liệu mô phỏng có đáp án ẩn. Hướng phát triển là mô hình
> phi tuyến học từ dữ liệu theo Goda và cộng sự (2024).

## 11.9. Tài liệu tham khảo

> 💡 Danh sách bài báo và sách được trích, kèm đường dẫn.

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
- Li, L., Chu, W., Langford, J., Schapire, R. E. (2010). A Contextual-Bandit Approach to Personalized News
  Article Recommendation. WWW. https://arxiv.org/abs/1003.0146
- Agrawal, S., Goyal, N. (2013). Thompson Sampling for Contextual Bandits with Linear Payoffs. ICML.
  https://arxiv.org/abs/1209.3352
- Chapelle, O., Li, L. (2011). An Empirical Evaluation of Thompson Sampling. NeurIPS.
- Joachims, T., Swaminathan, A., Schnabel, T. (2017). Unbiased Learning-to-Rank with Biased Feedback. WSDM.
  https://arxiv.org/abs/1608.04468
- Lattimore, T., Szepesvári, C. (2020). Bandit Algorithms. Cambridge University Press.
- Sutton, R. S., Barto, A. G. (2018). Reinforcement Learning: An Introduction (2nd ed.), chương 2. MIT Press.
- Yue, Y., Joachims, T. (2009). Interactively Optimizing Information Retrieval Systems as a Dueling Bandits
  Problem. ICML.
