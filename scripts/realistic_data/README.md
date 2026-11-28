# Dữ liệu kiểm thử PTutor

Nhánh này tách từ `dev`. `seed.py` sinh dữ liệu cục bộ có ID cố định, chạy lại không nhân bản ghi. Tên, số định danh và giao dịch đều là dữ liệu giả lập. Email học viên và gia sư có cú pháp Gmail, nhưng chỉ dùng trong database kiểm thử; cấu hình Docker cục bộ chặn gửi thư ra ngoài để tránh gửi nhầm đến người thật. Nội dung hồ sơ và tin đăng nằm trong `narratives.json`: các đoạn văn được viết sẵn theo bối cảnh từng người, không gọi LLM mỗi lần nạp dữ liệu.

## Nạp dữ liệu

Tạo một database **riêng** và chạy Flyway của backend từ `dev` trước. Migration gốc tạo địa bàn và tài khoản admin; script bổ sung môn và lớp nếu database còn trống. Sau đó:

```powershell
python scripts/realistic_data/seed.py --database ptutor_realistic_data > scripts/realistic_data/generated.sql
python scripts/realistic_data/seed.py --load --database ptutor_realistic_data
```

Khi đã tích hợp migration từ `feat/tutor-recommendation`, thêm `--ai` để nạp snapshot cho hai mô hình học mức sẵn sàng (`B_S2T`, `B_T2S`). Khi đã tích hợp migration từ `feat/support-chatbot-reference`, thêm `--chatbot` để nạp hàng chờ yêu cầu hỗ trợ. Dùng `--finance` để tạo tài khoản ngân hàng đã mã hóa và yêu cầu rút tiền; cờ này cần `BANK_ACCOUNT_ENCRYPTION_KEY` (hoặc `CITIZEN_ID_ENCRYPTION_KEY`) trùng với backend, lấy từ biến môi trường hoặc `.env` cục bộ. Có thể kết hợp các cờ. Không chạy lệnh nạp trên database `ptutor` đang phục vụ ứng dụng; script chặn tên này.

Nếu nạp lại seed trên DB đã có vector và nội dung cũ thay đổi, chạy `python scripts/realistic_data/queue_stale_embeddings.py --database ptutor_app_seeded`. Script chỉ xếp hàng các hồ sơ/tin có vector không còn khớp văn bản đã nạp; worker backend sẽ tạo lại vector. Không cần chạy bước này trên DB mới hoặc khi khôi phục một snapshot có sẵn vector.

`--finance` cần gói Python `cryptography` (`pip install cryptography`).

Mật khẩu chung của tài khoản kiểm thử là `Demo@2026` (hash bcrypt từ bộ seed cục bộ hiện có). Tài khoản điển hình: `nguyenhuuquan825581@gmail.com` (học viên) và `nguyenvanminh898609@gmail.com` (gia sư). Không bật gửi email hoặc thanh toán thật cho database này. Giá trị mã thanh toán `LOCAL-TOPUP-*` là mã nội bộ, không gọi VNPay.

## Phạm vi

- 404 gia sư, 384 học viên, hai nhân viên, 1.128 tin dạy, 726 tin học. Bộ tin mở bổ sung phủ 16 môn và 130 cặp môn–lớp phù hợp chương trình, mỗi cặp có cả tin trực tuyến và trực tiếp ở cả hai chiều tìm kiếm. Hồ sơ được phân bố trên 24 phường thuộc 8 tỉnh/thành, với nhiều mức giá và khung giờ.
- Tin chờ duyệt, mở, đóng, từ chối; hồ sơ và chứng chỉ chờ duyệt, đã duyệt hoặc bị từ chối; tài khoản hoạt động, ngưng hoạt động và tạm khóa.
- Đơn đăng ký, đề nghị dạy, hợp đồng chờ ký/đang học/hoàn tất/đã hủy, buổi học và kỳ học phí ở nhiều trạng thái, đánh giá, yêu thích, khiếu nại, yêu cầu hủy và hoàn tiền, thông báo cá nhân, chiến dịch nháp và đã lên lịch, ví cho mọi tài khoản được seed và giao dịch. Có 32 bộ lọc tìm kiếm đã lưu ở hai chiều gợi ý. `--finance` bổ sung tài khoản ngân hàng, các yêu cầu rút tiền, học phí, phí đăng tin, thu nhập gia sư và hoàn tiền gắn với bản ghi nghiệp vụ tương ứng. Mỗi tin đã qua bước thanh toán (kể cả đang chờ duyệt, sau đó bị từ chối, đóng hoặc hủy) có đúng một giao dịch phí; tin nháp không bị thu phí. Phí tin học là 100.000 VND, còn phí tin dạy là 100.000 VND nhân với số học viên cần tuyển theo logic backend. Bộ dữ liệu hiện tại có 725 tin học và 1.127 tin dạy đã thanh toán, tổng phí phát sinh 297.500.000 VND; 7 tin bị từ chối được hoàn 1.400.000 VND vào ví, nên doanh thu phí hiện còn 296.100.000 VND.
- Tìm kiếm hai chiều với kết quả và thao tác tương tác. Trên schema `dev`, hành động giới hạn ở `CLICKED`, `VIEWED`, `CONTACTED`. Cờ `--ai` mở thêm `FAVORITED`, `DISMISSED`, vector đặc trưng, hướng tìm kiếm và snapshot liên kết đúng ID đơn để thử job học trọng số. Các giá trị feature được sinh theo kịch bản kiểm thử, không phải đo đạc thực tế; không dùng để đánh giá chất lượng mô hình.
- Với `--ai`, seed tính thống kê hiển thị từ log đã nạp và xếp hàng cho worker tạo hồ sơ sở thích từ tín hiệu tương tác. Chờ các job `PROFILE_UPDATE` hoàn tất trước khi xuất snapshot; không tự điền vector sở thích giả.
- 2.240 hồ sơ/tin bổ sung dùng nội dung riêng trong `narratives.json`; tin mới phục vụ tìm kiếm, không tạo thêm click hoặc nhãn hành vi giả cho job học trọng số. Chạy `python scripts/realistic_data/check_quality.py` trước khi nạp để kiểm tra nội dung và độ phủ dự kiến; sau khi nạp chạy `python scripts/realistic_data/check_search_coverage.py --database ptutor_app_seeded` để kiểm tra các tin thực sự lọt qua điều kiện tìm kiếm của backend. Worker embedding sẽ tạo vector cho các tin và hồ sơ mới.
- `--chatbot` tạo 40 yêu cầu hỗ trợ, bao gồm yêu cầu đang mở, đang xử lý, đã trả lời, đã đóng và đã hủy. Nội dung hỏi và trả lời gắn với nghiệp vụ cụ thể. Không tự tạo câu trả lời hoặc trích dẫn giả trong bảng hội thoại của trợ lý.

Seed phủ các bảng nghiệp vụ cần cho luồng demo, không điền mọi bảng chỉ để có bản ghi. Bảng mã đặt lại mật khẩu, thử thách bảo mật ví, bộ nhớ đệm rerank, nhật ký xóa hội thoại và phản hồi chatbot chỉ có dữ liệu khi sự kiện tương ứng xảy ra. `recommendation_learning_modes` để trống thì backend dùng chế độ từ cấu hình. Bảng `evidences` cần tệp thực trên S3; không tạo đường dẫn giả. Sau khi dựng assistant-service và kết nối AI embedding, nạp câu mẫu phân loại phạm vi bằng `docker exec ptutor-unified-assistant python -m app.scope.exemplars --load`.

Kho tri thức 65 tài liệu và bộ câu hỏi tại `knowledge-base/` được lấy từ `feat/support-chatbot-reference`. Tài liệu đó còn ở trạng thái **draft** và có câu hỏi cần giải quyết, nên chỉ dùng để kiểm thử luồng nhập, rà soát, xuất bản; không tự công bố tất cả. Database Docker `ptutor` hiện có 16 tài liệu đã xuất bản và 100 chunk đã nhúng bằng `bge-m3-int8`; có thể tiếp tục dùng database đó để kiểm thử RAG. Trên database mới, dùng lệnh nhập chính thức của assistant-service sau khi tích hợp nhánh chatbot:

```powershell
cd assistant-service
python -m app.kb.import --path ../knowledge-base
```

Sau nhập, nhân viên cần rà soát và xuất bản những tài liệu đã hoàn chỉnh qua API quản trị; service sẽ tạo chunk và embedding. Bộ `knowledge-base/_eval/cau-hoi-mau.csv` dùng để kiểm tra câu hỏi theo chủ đề.

Để tận dụng trực tiếp KB đã có trong Docker, sau khi chạy Alembic cho DB đích, dùng:

```powershell
python scripts/realistic_data/import_kb.py --source ptutor --target ptutor_realistic_data
```

Script này chỉ sao chép `kb_document`, `kb_document_version`, `kb_chunk`, `kb_setting` vào KB đích còn trống, kiểm tra số lượng và khóa ngoại. Không sao chép hội thoại, tin nhắn hoặc đánh giá người dùng. Trong Docker hiện tại, lệnh đã nạp 66 tài liệu (16 đã xuất bản) và 100 chunk vào `ptutor_realistic_clean`.

Đã kiểm tra `ptutor_eval_l1` trong Docker: 600 gia sư, 400 học viên, 6.101 lượt tìm, 24.762 tương tác và 3.028 snapshot. Đây là tập mô phỏng để đánh giá thuật toán, có văn bản lặp và mốc thời gian cũ. Giữ nguyên database đó để benchmark; không sao chép trọng số đã học hoặc snapshot sang dữ liệu mới vì chúng phụ thuộc vào cặp hồ sơ và thời điểm gốc. Seed mới tạo snapshot liên kết với chính các đơn mới. Các mô hình `A_S2T` và `A_T2S` học từ lịch sử tìm kiếm; các mô hình `B_*` học từ snapshot và kết quả trả lời.

### Đủ ngưỡng tự học

Thiết kế hiện đặt ngưỡng **2.000 quan sát từ 300 lượt tìm có tương tác** cho từng mô hình A và **300 mẫu quyết định/khởi xướng** cho từng mô hình B. Dữ liệu `--ai` trong DB mới `ptutor_learning_final` có:

| Mô hình | Lượt tìm huấn luyện | Quan sát/mẫu huấn luyện | Tập giữ lại |
|---|---:|---:|---:|
| `A_S2T` | 496 | 2.480 | 124 lượt tìm, 620 quan sát |
| `A_T2S` | 616 | 2.464 | 154 lượt tìm, 616 quan sát |
| `B_S2T` | — | 680 | 130 mẫu |
| `B_T2S` | — | 680 | 130 mẫu |

Số quan sát A đã tính theo giới hạn độ sâu người dùng tương tác cộng `viewed-depth-margin=2`, không lấy toàn bộ kết quả. Mốc thời gian cho A tính đủ 7 ngày chờ phản hồi rồi tách 7 ngày giữ lại; B tách 7 ngày giữ lại. Job có dữ liệu để huấn luyện và đánh giá, nhưng cấu hình mặc định của bốn mô hình là `SHADOW`: muốn dùng trọng số phục vụ gợi ý cần chuyển sang chế độ phù hợp và đạt điều kiện đánh giá chất lượng. Không coi dữ liệu sinh này là chứng cứ mô hình đã tốt hơn trên người dùng thật.

Kiểm tra lại các ngưỡng sau khi nạp:

```powershell
python scripts/realistic_data/check_learning.py --database ptutor_learning_final
```

## Kiểm tra nhanh

```sql
SELECT count(*) FROM users WHERE email LIKE '%@gmail.com'; -- 148
SELECT count(*) FROM recommendation_logs; -- 1.390 trên DB tích hợp mới
SELECT count(*) FROM recommendation_click_logs; -- 3.400 trên DB tích hợp mới
SELECT count(*) FROM recommendation_decision_snapshots; -- 1.620, chỉ khi --ai
SELECT count(*) FROM support_requests WHERE code LIKE 'HT-LOCAL-%'; -- 36, chỉ khi --chatbot
```

Seed là dữ liệu cho môi trường cục bộ. Các luồng tải ảnh chứng chỉ, chi trả thật và hội thoại thật cần API hoặc dịch vụ tương ứng để kiểm tra đầy đủ; không tạo URL tệp hay kết quả chuyển khoản giả nhằm tránh trạng thái không thể mở được.

Sau khi nạp `--finance`, chạy `python scripts/realistic_data/check_finance.py --database ptutor_app_seeded`. Script kiểm tra sổ ví, khoản rút đang giữ, thanh toán đúng kỳ học phí, buổi hoàn tất đã được giải ngân, nguồn tiền nạp ví và thu nhập gia sư, học phí còn giữ không âm, các khoản hoàn khớp thanh toán lẫn giao dịch ví và phí đăng tin theo công thức của backend. Mọi dòng phải bằng `0`.

DB đích `ptutor_app_seeded` trong Docker chạy Flyway chính thức của backend và Alembic của assistant-service. Nạp `--ai --chatbot --finance` để có dữ liệu tài chính và AI đầy đủ, sau đó kiểm tra bằng `check_finance.py`, `check_learning.py` và `check_search_coverage.py`. Kho tri thức đã công bố có 76 tài liệu. Cách chạy đầy đủ được ghi trong `deploy/README.md`.
