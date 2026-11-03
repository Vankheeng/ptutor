# Báo cáo sinh tài liệu kho tri thức

## 1. Nguồn đã đọc

- Nhánh `dev`, commit `27f6eeb09815385bf92d3d552b0f8f705070a23c` (`git rev-parse dev`).
- Cách đọc: `git show dev:<đường dẫn>`, `git grep … dev` và một bản sao chỉ-đọc bằng `git archive dev` vào thư mục tạm ngoài repo. Không tạo worktree, không chuyển nhánh, không sửa thư mục làm việc. Bản sao tạm đã được xóa khi xong.
- Đặc tả: `feat/support-chatbot:docs/support-chatbot/03-kho-tri-thuc.md` (3.1–3.8), `00-doc-truoc.md`, `01-pham-vi-va-quyet-dinh.md`.
- Không tìm thấy file `quy-dinh-nen-tang-tu-code.md` cạnh prompt hay trong `D:\DATN`, nên tài liệu chỉ dựa vào code `dev`. Mục 9 vì vậy không có nội dung.
- Ngày sinh: 2026-11-02. Bản cập nhật 2026-11-03 thêm 2 tài liệu lỗi (`loi-buoi-hoc`, `loi-rut-tien-tai-khoan-ngan-hang`), đổi chức năng nhóm buổi học sang `lesson`, ghi rõ nhân viên chăm sóc khách hàng là người duyệt nội dung; các tài liệu đã sửa có `updated: 2026-11-03`.

## 2. Gộp tài liệu ngắn và PDF theo nhóm (cập nhật 2026-11-03)

Các tài liệu có phần thân dưới 200 từ được gộp vào tài liệu cùng chủ đề và cùng đối tượng gần nhất. Mọi mục `##` của tài liệu nguồn được giữ nguyên và nối vào cuối tài liệu đích; đoạn mở đầu (nếu có) đặt dưới mục `##` đầu tiên của phần gộp. Trường `routes`, `source`, `error_codes`, `keywords`, `todo` được hợp nhất; `id`, `type`, `audience` giữ của tài liệu đích. Tài liệu nguồn bị xóa; câu hỏi mẫu chuyển sang id đích và giữ tối đa 5 câu mỗi tài liệu (vẫn có ít nhất 1 câu không dấu và 1 câu bẫy).

| Tài liệu nguồn (số từ) | Gộp vào | Ghi chú |
|---|---|---|
| tai-khoan-doi-mat-khau (126) | tai-khoan-quen-mat-khau | Đích có đối tượng PUBLIC, bao trùm học viên và gia sư; không có tài liệu mật khẩu nào cùng đối tượng học viên + gia sư |
| trang-thai-ky-hoc-phi (129) | ky-hoc-phi-quy-dinh | Cùng chức năng payment, cùng đối tượng học viên |
| danh-gia-gia-su (137) | viec-chua-ho-tro | Không có tài liệu review nào khác; đích có chức năng review. Đối tượng nguồn PUBLIC, đích học viên + gia sư; trợ lý chỉ phục vụ hai vai trò này nên không mất phạm vi. Đích có hai mục hỏi gần trùng về viết đánh giá |
| trang-thai-chung-chi (147) | chung-chi-quy-dinh | Cùng chức năng, cùng đối tượng gia sư |
| hop-dong-huy (169) | hop-dong-ky-tu-choi | Cùng chức năng, cùng đối tượng; đổi tiêu đề thành "Ký, từ chối hoặc hủy hợp đồng" |
| lien-he-ho-tro (179) | gioi-thieu-vai-tro | Cùng chức năng account, cùng đối tượng PUBLIC |
| ho-so-hoc-vien (182) | tai-khoan-dang-ky | Không có tài liệu account nào chỉ dành cho học viên; đích PUBLIC bao trùm học viên. Gia sư cũng truy xuất được phần hồ sơ học viên; các mục đã ghi rõ "hồ sơ học viên" |
| vi-nap-tien-huong-dan (184) | vi-nap-tien-quy-dinh | Cùng chức năng, cùng đối tượng |
| trang-thai-tai-khoan (191) | tai-khoan-bi-khoa | Cùng chức năng, cùng đối tượng |
| trang-thai-ho-so-gia-su (192) | ho-so-gia-su-duyet | Cùng chức năng, cùng đối tượng gia sư |

Sau khi gộp, các dẫn chiếu giữa tài liệu được sửa theo tiêu đề mới; sửa luôn một dẫn chiếu sai có từ trước ("Gia sư soạn hợp đồng từ đơn đăng ký học" thành "Gia sư soạn hợp đồng và quản lý hợp đồng").

PDF lẻ theo từng tài liệu được thay bằng PDF gộp theo nhóm chức năng: `knowledge-base/pdf/<features>.pdf`, một nhóm một tệp, mỗi tài liệu là một chương có khối thông tin ở đầu. Tài liệu có nhiều chức năng được xếp vào nhóm của chức năng đầu tiên trong trường `features`. Mỗi PDF có mục lục (chương và mục `##`), bookmark ba cấp, số trang và metadata (Title, Subject = mã nhóm, Keywords = hợp các từ khóa). Nhóm `review` không còn tệp riêng vì tài liệu đánh giá đã gộp vào `viec-chua-ho-tro` (nhóm contract).

## 3. Tổng quan bộ tài liệu

- Tổng số tài liệu: 65.
- Theo loại: Quy định 25, Hướng dẫn 18, Hỏi đáp 3, Giải nghĩa trạng thái 9, Lỗi thường gặp 10.
- Theo thư mục: Chung (chung/) 39, Học viên (hoc-vien/) 13, Gia sư (gia-su/) 13.
- Theo đối tượng (một tài liệu có thể có nhiều đối tượng): Mọi người 5, Học viên 47, Gia sư 47.
- Tổng số câu hỏi todo: 95.
- Mọi tài liệu đều ở trạng thái draft.
- PDF: pdf/account.pdf, pdf/certificate.pdf, pdf/complaint.pdf, pdf/contract.pdf, pdf/lesson.pdf, pdf/matching.pdf, pdf/notification.pdf, pdf/payment.pdf, pdf/refund.pdf, pdf/studying-request.pdf, pdf/teaching-request.pdf, pdf/tutor-profile.pdf, pdf/wallet.pdf, pdf/withdrawal.pdf.
- Bộ câu hỏi mẫu: knowledge-base/_eval/cau-hoi-mau.csv.

## 4. Danh sách tài liệu

| Thư mục | Mã | Tiêu đề | Loại | Đối tượng | Tệp PDF | Số todo |
|---|---|---|---|---|---|---|
| chung | buoi-hoc-quy-dinh | Buổi học: đánh dấu đã dạy, xác nhận, tự hoàn tất sau 3 ngày, buổi bị giữ lại | Quy định | Học viên, Gia sư | lesson.pdf | 1 |
| chung | gioi-thieu-vai-tro | PTutor, các vai trò và liên hệ hỗ trợ | Quy định | Mọi người | account.pdf | 5 |
| chung | hop-dong-gia-han | Gia hạn hợp đồng | Quy định | Học viên, Gia sư | contract.pdf | 0 |
| chung | hop-dong-ky-tu-choi | Ký, từ chối hoặc hủy hợp đồng | Quy định | Học viên, Gia sư | contract.pdf | 3 |
| chung | hop-dong-soan-va-sua | Ai soạn hợp đồng và được sửa những gì | Quy định | Học viên, Gia sư | contract.pdf | 1 |
| chung | hop-dong-tu-ket-thuc | Hợp đồng tự kết thúc khi hết hạn | Quy định | Học viên, Gia sư | contract.pdf | 1 |
| chung | khieu-nai-bang-chung | Bổ sung bằng chứng cho khiếu nại | Quy định | Học viên, Gia sư | complaint.pdf | 2 |
| chung | khieu-nai-bi-khieu-nai | Khi bạn bị khiếu nại: xem và phản hồi | Quy định | Học viên, Gia sư | complaint.pdf | 1 |
| chung | khieu-nai-gui | Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy | Quy định | Học viên, Gia sư | complaint.pdf | 2 |
| chung | khieu-nai-ket-luan | Kết luận khiếu nại, bên có lỗi, ảnh hưởng tới buổi học và hoàn tiền | Quy định | Học viên, Gia sư | complaint.pdf | 2 |
| chung | loi-buoi-hoc | Lỗi thường gặp với buổi học | Lỗi thường gặp | Học viên, Gia sư | lesson.pdf | 2 |
| chung | loi-de-nghi-don-dang-ky | Lỗi thường gặp với đề nghị dạy và đơn đăng ký học | Lỗi thường gặp | Học viên, Gia sư | matching.pdf | 0 |
| chung | loi-hop-dong | Lỗi thường gặp với hợp đồng | Lỗi thường gặp | Học viên, Gia sư | contract.pdf | 0 |
| chung | loi-khieu-nai | Lỗi thường gặp với khiếu nại | Lỗi thường gặp | Học viên, Gia sư | complaint.pdf | 1 |
| chung | loi-rut-tien-tai-khoan-ngan-hang | Lỗi thường gặp khi rút tiền và khai báo tài khoản ngân hàng | Lỗi thường gặp | Học viên, Gia sư | withdrawal.pdf | 2 |
| chung | loi-tai-khoan | Lỗi thường gặp khi đăng ký, đăng nhập, đặt lại mật khẩu | Lỗi thường gặp | Mọi người | account.pdf | 1 |
| chung | loi-tin-dang | Lỗi thường gặp khi đăng, sửa, đóng tin | Lỗi thường gặp | Học viên, Gia sư | studying-request.pdf | 0 |
| chung | loi-vi-thanh-toan | Lỗi thường gặp khi nạp ví và thanh toán | Lỗi thường gặp | Học viên, Gia sư | wallet.pdf | 0 |
| chung | phi-dang-tin | Phí đăng tin và hoàn phí khi tin bị từ chối | Quy định | Học viên, Gia sư | studying-request.pdf | 3 |
| chung | rut-tien | Rút tiền và hủy yêu cầu rút tiền | Quy định | Học viên, Gia sư | withdrawal.pdf | 2 |
| chung | tai-khoan-bi-khoa | Tài khoản bị tạm khóa hoặc khóa vĩnh viễn | Quy định | Học viên, Gia sư | account.pdf | 5 |
| chung | tai-khoan-dang-ky | Đăng ký tài khoản và cập nhật hồ sơ học viên | Hướng dẫn | Mọi người | account.pdf | 5 |
| chung | tai-khoan-dang-nhap | Đăng nhập, đăng xuất và thời hạn phiên | Hướng dẫn | Mọi người | account.pdf | 2 |
| chung | tai-khoan-ngan-hang | Khai báo tài khoản ngân hàng nhận tiền | Quy định | Học viên, Gia sư | withdrawal.pdf | 2 |
| chung | tai-khoan-quen-mat-khau | Quên mật khẩu và đổi mật khẩu | Hướng dẫn | Mọi người | account.pdf | 3 |
| chung | thanh-toan-vnpay | Thanh toán qua VNPay | Quy định | Học viên, Gia sư | payment.pdf | 2 |
| chung | thong-bao | Xem thông báo và đánh dấu đã đọc | Hướng dẫn | Học viên, Gia sư | notification.pdf | 1 |
| chung | trang-thai-buoi-hoc | Giải nghĩa trạng thái buổi học | Giải nghĩa trạng thái | Học viên, Gia sư | lesson.pdf | 0 |
| chung | trang-thai-de-nghi-don-dang-ky | Giải nghĩa trạng thái đề nghị dạy và đơn đăng ký học | Giải nghĩa trạng thái | Học viên, Gia sư | matching.pdf | 1 |
| chung | trang-thai-giao-dich-vi | Giải nghĩa loại và trạng thái giao dịch ví | Giải nghĩa trạng thái | Học viên, Gia sư | wallet.pdf | 1 |
| chung | trang-thai-hoan-tien | Giải nghĩa trạng thái yêu cầu hoàn tiền | Giải nghĩa trạng thái | Học viên, Gia sư | refund.pdf | 1 |
| chung | trang-thai-hop-dong | Giải nghĩa trạng thái hợp đồng | Giải nghĩa trạng thái | Học viên, Gia sư | contract.pdf | 0 |
| chung | trang-thai-khieu-nai | Giải nghĩa trạng thái khiếu nại | Giải nghĩa trạng thái | Học viên, Gia sư | complaint.pdf | 2 |
| chung | trang-thai-rut-tien | Giải nghĩa trạng thái yêu cầu rút tiền | Giải nghĩa trạng thái | Học viên, Gia sư | withdrawal.pdf | 2 |
| chung | trang-thai-thanh-toan | Giải nghĩa trạng thái thanh toán | Giải nghĩa trạng thái | Học viên, Gia sư | payment.pdf | 1 |
| chung | trang-thai-tin-dang | Giải nghĩa trạng thái tin đăng | Giải nghĩa trạng thái | Học viên, Gia sư | studying-request.pdf | 2 |
| chung | vi-giao-dich | Xem số dư và lịch sử giao dịch ví | Hướng dẫn | Học viên, Gia sư | wallet.pdf | 1 |
| chung | vi-nap-tien-quy-dinh | Nạp ví: quy định và cách nạp | Quy định | Học viên, Gia sư | wallet.pdf | 2 |
| chung | viec-chua-ho-tro | Những việc nền tảng chưa hỗ trợ | Hỏi đáp | Học viên, Gia sư | contract.pdf | 3 |
| hoc-vien | buoi-hoc-xac-nhan | Cách xác nhận buổi học | Hướng dẫn | Học viên | lesson.pdf | 0 |
| hoc-vien | de-nghi-day-xu-ly | Xem, chấp nhận, từ chối đề nghị dạy của gia sư | Hướng dẫn | Học viên | matching.pdf | 1 |
| hoc-vien | don-dang-ky-hoc-gui | Xem tin nhận dạy và gửi đơn đăng ký học | Hướng dẫn | Học viên | matching.pdf | 1 |
| hoc-vien | hoan-tien-gui-yeu-cau | Cách gửi, theo dõi và hủy yêu cầu hoàn tiền | Hướng dẫn | Học viên | refund.pdf | 1 |
| hoc-vien | hoan-tien-quy-dinh | Quy định hoàn tiền: điều kiện, gia sư phản hồi trong 48 giờ, mức hoàn | Quy định | Học viên | refund.pdf | 3 |
| hoc-vien | hop-dong-tao-hoc-vien | Cách soạn hợp đồng sau khi chấp nhận đề nghị dạy | Hướng dẫn | Học viên | contract.pdf | 0 |
| hoc-vien | hop-dong-xem-ky-hoc-vien | Cách xem, ký, từ chối hợp đồng | Hướng dẫn | Học viên | contract.pdf | 0 |
| hoc-vien | ky-hoc-phi-quy-dinh | Kỳ học phí: chu kỳ thanh toán, nhắc đóng tiền, trạng thái | Quy định | Học viên | payment.pdf | 3 |
| hoc-vien | ky-hoc-phi-thanh-toan | Cách đóng học phí bằng VNPay hoặc ví | Hướng dẫn | Học viên | payment.pdf | 1 |
| hoc-vien | loi-hoan-tien | Lỗi thường gặp khi gửi yêu cầu hoàn tiền | Lỗi thường gặp | Học viên | refund.pdf | 0 |
| hoc-vien | tin-tim-gia-su-dang | Cách đăng tin tìm gia sư | Hướng dẫn | Học viên | studying-request.pdf | 1 |
| hoc-vien | tin-tim-gia-su-quy-dinh | Quy định tin tìm gia sư: chờ duyệt, sửa, tạm đóng, hủy | Quy định | Học viên | studying-request.pdf | 2 |
| hoc-vien | tin-tim-gia-su-sua-dong-huy | Cách sửa, tạm đóng, hủy tin tìm gia sư | Hướng dẫn | Học viên | studying-request.pdf | 0 |
| gia-su | buoi-hoc-gia-su | Tạo buổi học và đánh dấu đã dạy | Quy định | Gia sư | lesson.pdf | 1 |
| gia-su | chung-chi-nop | Cách nộp chứng chỉ | Hướng dẫn | Gia sư | certificate.pdf | 0 |
| gia-su | chung-chi-quy-dinh | Quy định và trạng thái chứng chỉ: duyệt, hết hạn, sửa, xóa | Quy định | Gia sư | certificate.pdf | 4 |
| gia-su | de-nghi-day-gui | Cách gửi, sửa, hủy đề xuất giảng dạy | Hướng dẫn | Gia sư | matching.pdf | 1 |
| gia-su | diem-de-xuat | Điểm đề xuất của gia sư là gì | Hỏi đáp | Gia sư | tutor-profile.pdf | 2 |
| gia-su | don-dang-ky-hoc-xu-ly | Xử lý đơn đăng ký học của học viên | Quy định | Gia sư | matching.pdf | 1 |
| gia-su | ho-so-gia-su-duyet | Duyệt hồ sơ gia sư và trạng thái hồ sơ | Quy định | Gia sư | tutor-profile.pdf | 3 |
| gia-su | ho-so-gia-su | Hoàn thiện hồ sơ gia sư và gửi duyệt | Hướng dẫn | Gia sư | tutor-profile.pdf | 0 |
| gia-su | hoan-tien-phan-hoi | Phản hồi yêu cầu hoàn tiền trong 48 giờ | Hướng dẫn | Gia sư | refund.pdf | 1 |
| gia-su | hop-dong-tao-gia-su | Gia sư soạn hợp đồng và quản lý hợp đồng | Quy định | Gia sư | contract.pdf | 1 |
| gia-su | loi-ho-so-chung-chi | Lỗi thường gặp với hồ sơ và chứng chỉ | Lỗi thường gặp | Gia sư | tutor-profile.pdf | 0 |
| gia-su | thu-nhap-gia-su | Gia sư nhận tiền dạy như thế nào | Hỏi đáp | Gia sư | wallet.pdf | 1 |
| gia-su | tin-nhan-day-quy-dinh | Quy định tin nhận dạy | Quy định | Gia sư | teaching-request.pdf | 2 |

## 5. Câu hỏi todo theo từng tài liệu

### buoi-hoc-quy-dinh — Buổi học: đánh dấu đã dạy, xác nhận, tự hoàn tất sau 3 ngày, buổi bị giữ lại

- Sau khi buổi học được xác nhận hoặc tự hoàn tất, gia sư nhận tiền dạy của buổi đó khi nào và bằng cách nào?

### gioi-thieu-vai-tro — PTutor, các vai trò và liên hệ hỗ trợ

- Phụ huynh muốn tìm gia sư cho con thì đăng ký tài khoản với vai trò nào? Nền tảng có cho một người dùng một tài khoản cho nhiều con không?
- Một người có thể vừa là học viên vừa là gia sư (hai tài khoản, hai email) không?
- Kênh liên hệ chính thức của PTutor là gì (email, số điện thoại, Zalo, fanpage)?
- Giờ hỗ trợ trong tuần là mấy giờ đến mấy giờ?
- Thời gian phản hồi dự kiến cho từng loại yêu cầu là bao lâu?

### hop-dong-ky-tu-choi — Ký, từ chối hoặc hủy hợp đồng

- Hợp đồng chờ ký có thời hạn ký riêng không, hay chỉ tự hủy khi tới ngày kết thúc như code hiện tại?
- Học viên hoặc gia sư muốn chấm dứt hợp đồng đang hoạt động thì làm thế nào, liên hệ ai?
- Khi chấm dứt hợp đồng đang hoạt động, học phí đã đóng cho các buổi chưa học được xử lý thế nào?

### hop-dong-soan-va-sua — Ai soạn hợp đồng và được sửa những gì

- Giá trong hợp đồng là giá cho mỗi kỳ thanh toán (mỗi buổi, mỗi tuần, mỗi tháng hoặc cả gói). Admin xác nhận cách diễn giải này với người dùng.

### hop-dong-tu-ket-thuc — Hợp đồng tự kết thúc khi hết hạn

- Khi hợp đồng tự kết thúc mà vẫn còn kỳ học phí chưa đóng hoặc buổi học chờ xác nhận thì xử lý thế nào?

### khieu-nai-bang-chung — Bổ sung bằng chứng cho khiếu nại

- Người khiếu nại có thời hạn bao lâu để bổ sung bằng chứng? Quá hạn thì khiếu nại xử lý thế nào? (code không có thời hạn)
- Người dùng tải ảnh, video lên đâu để có đường dẫn làm bằng chứng?

### khieu-nai-bi-khieu-nai — Khi bạn bị khiếu nại: xem và phản hồi

- Bên bị khiếu nại có thời hạn phản hồi không? Không phản hồi thì có bất lợi gì?

### khieu-nai-gui — Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy

- Khi nào có giao diện gửi và theo dõi khiếu nại cho học viên và gia sư? Tên trang và tên nút là gì?
- Có thời hạn gửi khiếu nại sau khi sự việc xảy ra hoặc sau khi hợp đồng kết thúc không?

### khieu-nai-ket-luan — Kết luận khiếu nại, bên có lỗi, ảnh hưởng tới buổi học và hoàn tiền

- Khi kết luận một bên có lỗi, bên đó bị xử lý thế nào (cảnh cáo, khóa tài khoản, bồi thường)? Hệ thống không tự áp dụng hình phạt nào.
- Tiêu chí nhân viên dùng để xác định bên có lỗi là gì?

### loi-buoi-hoc — Lỗi thường gặp với buổi học

- Khi có giao diện buổi học cho gia sư, cập nhật các câu báo lỗi phía gia sư theo câu hiển thị thật.
- Hợp đồng Mỗi buổi đã tạo đủ số buổi mà cần dạy thêm buổi bù thì gia sư làm thế nào?

### loi-khieu-nai — Lỗi thường gặp với khiếu nại

- Khi có giao diện khiếu nại, cập nhật các câu báo lỗi theo câu hiển thị thật.

### loi-rut-tien-tai-khoan-ngan-hang — Lỗi thường gặp khi rút tiền và khai báo tài khoản ngân hàng

- Khi có giao diện rút tiền và tài khoản ngân hàng, cập nhật các câu báo lỗi theo câu hiển thị thật.
- Có mức rút tối thiểu hoặc tối đa mỗi lần không? (code chỉ yêu cầu lớn hơn 0)

### loi-tai-khoan — Lỗi thường gặp khi đăng ký, đăng nhập, đặt lại mật khẩu

- Người dùng bị người khác dùng mất email hoặc số CCCD để đăng ký thì xử lý thế nào, liên hệ ai?

### phi-dang-tin — Phí đăng tin và hoàn phí khi tin bị từ chối

- Mức phí đăng tin tìm gia sư và phí đăng tin nhận dạy hiện là bao nhiêu? (cấu hình không có giá trị mặc định)
- Khi người dùng tự hủy tin đã trả phí thì có được hoàn phí không? (code không hoàn)
- Khi tin hết hạn hoặc không có ai ứng tuyển thì có được hoàn phí không?

### rut-tien — Rút tiền và hủy yêu cầu rút tiền

- Ai xử lý yêu cầu rút tiền, trong bao lâu, có phí rút không, có mức rút tối thiểu hay tối đa không? (hệ thống chưa có quy trình xử lý)
- Khi nào có giao diện rút tiền? Tên trang và tên nút là gì?

### tai-khoan-bi-khoa — Tài khoản bị tạm khóa hoặc khóa vĩnh viễn

- Những hành vi nào dẫn tới tạm khóa và những hành vi nào dẫn tới khóa vĩnh viễn?
- Người dùng muốn khiếu nại quyết định khóa tài khoản thì liên hệ kênh nào, trong bao lâu?
- Khi tài khoản bị khóa, hợp đồng đang hiệu lực, buổi học và số dư ví của người đó được xử lý thế nào?
- Khi nào một tài khoản chuyển sang trạng thái Ngừng hoạt động? (code không có thao tác nào đặt trạng thái này) Người dùng làm gì để kích hoạt lại?
- Nhãn 'Tạm khóa' và 'Khóa vĩnh viễn' do người soạn đặt vì giao diện người dùng không hiển thị; admin xác nhận nhãn.

### tai-khoan-dang-ky — Đăng ký tài khoản và cập nhật hồ sơ học viên

- Vì sao PTutor yêu cầu số CCCD khi đăng ký và dùng số này vào việc gì? (để trả lời người dùng hỏi về quyền riêng tư)
- Người dưới 18 tuổi hoặc chưa có CCCD (ví dụ học sinh tiểu học) đăng ký học viên bằng cách nào?
- Nhập sai CCCD lúc đăng ký thì người dùng liên hệ ai để sửa?
- Học viên muốn đổi email hoặc số CCCD đã đăng ký thì làm thế nào?
- Thông tin học tập trong hồ sơ (phong cách học, trình độ, mục tiêu, điểm yếu) được dùng vào việc gì, ai xem được?

### tai-khoan-dang-nhap — Đăng nhập, đăng xuất và thời hạn phiên

- Xác nhận thời hạn phiên đăng nhập trên môi trường thật (mặc định cấu hình: 10 giờ cho phiên làm việc, 30 ngày cho phiên ghi nhớ).
- Nền tảng có giới hạn số lần đăng nhập sai không? (code hiện không giới hạn)

### tai-khoan-ngan-hang — Khai báo tài khoản ngân hàng nhận tiền

- Khi nào có giao diện khai báo tài khoản ngân hàng? Tên trang và tên nút là gì?
- Tên chủ tài khoản có bắt buộc trùng họ tên đăng ký trên PTutor không?

### tai-khoan-quen-mat-khau — Quên mật khẩu và đổi mật khẩu

- Không nhận được email OTP (kể cả trong thư rác) thì người dùng liên hệ kênh nào?
- Có giới hạn số lần gửi lại OTP hoặc số lần nhập sai OTP không? (code hiện không giới hạn)
- Trang đổi mật khẩu khi đã đăng nhập sẽ đặt ở đâu và tên nút là gì? Khi có giao diện cần viết lại các bước.

### thanh-toan-vnpay — Thanh toán qua VNPay

- Bị trừ tiền ở ngân hàng nhưng PTutor chưa ghi nhận thanh toán thì người dùng liên hệ ai, cần cung cấp thông tin gì, xử lý trong bao lâu?
- PTutor có thu phí giao dịch VNPay của người dùng không?

### thong-bao — Xem thông báo và đánh dấu đã đọc

- PTutor có gửi thông báo qua email hoặc điện thoại không, hay chỉ trong ứng dụng? (code chỉ gửi email cho mã OTP)

### trang-thai-de-nghi-don-dang-ky — Giải nghĩa trạng thái đề nghị dạy và đơn đăng ký học

- Đề xuất hoặc đơn đăng ký đang chờ có tự hết hạn sau một thời gian không? (code không có)

### trang-thai-giao-dich-vi — Giải nghĩa loại và trạng thái giao dịch ví

- Giao diện chưa có nhãn cho loại và trạng thái giao dịch ví. Admin xác nhận các nhãn trong tài liệu: Tiền vào, Tiền ra, Đang chờ, Hoàn tất, Thất bại, Đã hủy.

### trang-thai-hoan-tien — Giải nghĩa trạng thái yêu cầu hoàn tiền

- Kế toán tiếp nhận và kết luận yêu cầu hoàn tiền trong bao lâu?

### trang-thai-khieu-nai — Giải nghĩa trạng thái khiếu nại

- Nhãn lấy từ trang quản trị vì trang người dùng chưa có; admin xác nhận dùng cùng nhãn cho học viên và gia sư.
- Khiếu nại được tiếp nhận và kết luận trong bao lâu?

### trang-thai-rut-tien — Giải nghĩa trạng thái yêu cầu rút tiền

- Nhãn trạng thái rút tiền do người soạn đặt vì chưa có giao diện: Chờ xử lý, Đang xử lý, Đã hủy, Bị từ chối, Hoàn tất. Admin xác nhận.
- Khi yêu cầu rút bị từ chối, tiền tạm giữ có được trả lại ví không và trong bao lâu?

### trang-thai-thanh-toan — Giải nghĩa trạng thái thanh toán

- Giao diện học viên, gia sư chưa hiển thị trạng thái của từng giao dịch thanh toán; nhãn dùng trong tài liệu lấy từ trang quản trị. Admin xác nhận.

### trang-thai-tin-dang — Giải nghĩa trạng thái tin đăng

- Trang của học viên hiện hiển thị trạng thái tin bằng chữ tiếng Anh viết thường (draft, pending review, open, matched, closed, cancelled, rejected). Admin xác nhận các nhãn tiếng Việt dùng trong tài liệu: Bản nháp, Chờ duyệt, Đang mở, Đã ghép, Tạm đóng, Đã hủy, Bị từ chối.
- Tin chờ duyệt được xét trong bao lâu?

### vi-giao-dich — Xem số dư và lịch sử giao dịch ví

- Trang ví chỉ hiện 20 giao dịch gần nhất và không có phân trang; người dùng muốn xem giao dịch cũ hơn thì làm thế nào?

### vi-nap-tien-quy-dinh — Nạp ví: quy định và cách nạp

- Số dư ví có rút ra được không, và khi nào kế toán xử lý yêu cầu rút? (hiện chưa có quy trình xử lý)
- Tiền nạp vào ví có hết hạn sử dụng không?

### viec-chua-ho-tro — Những việc nền tảng chưa hỗ trợ

- Với từng mục dưới đây, PTutor hướng dẫn người dùng liên hệ ai và bằng kênh nào trong thời gian chưa hỗ trợ?
- Khi nào học viên viết được đánh giá gia sư, và đánh giá sau mỗi hợp đồng hay mỗi buổi học?
- Gia sư có phản hồi hoặc yêu cầu gỡ đánh giá không đúng sự thật được không?

### de-nghi-day-xu-ly — Xem, chấp nhận, từ chối đề nghị dạy của gia sư

- Học viên có xem được hồ sơ, chứng chỉ, đánh giá của gia sư trước khi chấp nhận không? Nếu có, ở trang nào?

### don-dang-ky-hoc-gui — Xem tin nhận dạy và gửi đơn đăng ký học

- Ô Grade ID trên form đăng ký học yêu cầu nhập mã lớp dạng chuỗi ký tự, người dùng không biết mã này. Khi giao diện đổi sang chọn lớp từ danh sách, cần cập nhật bước 3.

### hoan-tien-gui-yeu-cau — Cách gửi, theo dõi và hủy yêu cầu hoàn tiền

- Minh chứng hiện chỉ nhập bằng đường dẫn, chưa tải tệp lên được; người dùng nên tải ảnh, video lên đâu để lấy đường dẫn?

### hoan-tien-quy-dinh — Quy định hoàn tiền: điều kiện, gia sư phản hồi trong 48 giờ, mức hoàn

- Vì sao học viên tự nghỉ vẫn được hoàn 80% và gia sư nhận 20%? (để giải thích khi người dùng hỏi)
- Muốn hoàn học phí cho buổi đã qua hạn xác nhận 3 ngày hoặc đã xác nhận nhầm thì làm thế nào?
- Tiền hoàn vào ví có rút về ngân hàng được không, khi nào?

### ky-hoc-phi-quy-dinh — Kỳ học phí: chu kỳ thanh toán, nhắc đóng tiền, trạng thái

- Đóng học phí trễ hạn thì có bị phạt, bị tạm dừng hợp đồng hay bị hủy không? (code không có xử lý trễ hạn)
- Học phí học viên đóng được giữ ở đâu và chuyển cho gia sư khi nào?
- Khi nào một kỳ học phí chuyển sang Đã hủy? (code không có thao tác nào đặt trạng thái này)

### ky-hoc-phi-thanh-toan — Cách đóng học phí bằng VNPay hoặc ví

- Khi nào giao diện có lựa chọn đóng học phí bằng số dư ví?

### tin-tim-gia-su-dang — Cách đăng tin tìm gia sư

- Giao diện tạo tin tìm gia sư đang dùng nhãn tiếng Anh; khi Việt hóa cần cập nhật tên nút và tên ô trong tài liệu.

### tin-tim-gia-su-quy-dinh — Quy định tin tìm gia sư: chờ duyệt, sửa, tạm đóng, hủy

- Tin tìm gia sư đang mở có tự hết hạn sau một thời gian không? (code không có)
- Tiêu chí PTutor dùng để duyệt hoặc từ chối tin có môn học tự nhập là gì?

### buoi-hoc-gia-su — Tạo buổi học và đánh dấu đã dạy

- Khi nào có giao diện để gia sư tạo buổi học và đánh dấu đã dạy? Tên trang và tên nút là gì?

### chung-chi-quy-dinh — Quy định và trạng thái chứng chỉ: duyệt, hết hạn, sửa, xóa

- Trạng thái Đã hết hạn chưa được hệ thống tự đặt khi chứng chỉ qua ngày hết hạn. Chứng chỉ đã duyệt mà hết hạn thì xử lý thế nào?
- Gia sư muốn cập nhật chứng chỉ đã duyệt (ví dụ điểm mới) thì làm thế nào ngoài việc xóa và thêm lại?
- Tiêu chí duyệt chứng chỉ và thời gian duyệt là bao lâu?
- Hệ thống chưa tự đặt trạng thái Đã hết hạn; admin xác nhận chứng chỉ chuyển sang trạng thái này khi nào.

### de-nghi-day-gui — Cách gửi, sửa, hủy đề xuất giảng dạy

- Gia sư chưa được duyệt hồ sơ có được gửi đề xuất không? (code hiện không chặn)

### diem-de-xuat — Điểm đề xuất của gia sư là gì

- Điểm đề xuất hiện được dùng ở những chỗ nào mà học viên thấy (gợi ý gia sư, thứ tự kết quả tìm kiếm)?
- Gia sư có được xem điểm của mình không? Hiện giao diện gia sư không hiển thị.

### don-dang-ky-hoc-xu-ly — Xử lý đơn đăng ký học của học viên

- Khi nào có giao diện để gia sư xem và xử lý đơn đăng ký học? Tên trang và tên nút là gì?

### ho-so-gia-su-duyet — Duyệt hồ sơ gia sư và trạng thái hồ sơ

- Tiêu chí duyệt hồ sơ gia sư là gì? Hồ sơ được duyệt trong bao lâu?
- Gia sư chưa được duyệt hồ sơ bị hạn chế những gì? (code hiện không chặn gửi đề xuất hay nhận hợp đồng)
- Trang hồ sơ gia sư đang hiển thị trạng thái bằng tiếng Anh; admin xác nhận nhãn tiếng Việt: Chờ duyệt, Đã xác minh, Không được duyệt.

### hoan-tien-phan-hoi — Phản hồi yêu cầu hoàn tiền trong 48 giờ

- Nếu gia sư không phản hồi trong 48 giờ thì kế toán kết luận theo nguyên tắc nào?

### hop-dong-tao-gia-su — Gia sư soạn hợp đồng và quản lý hợp đồng

- Khi nào có trang Hợp đồng cho gia sư? Tên các nút ký, từ chối, hủy, gia hạn trên trang gia sư là gì?

### thu-nhap-gia-su — Gia sư nhận tiền dạy như thế nào

- Gia sư nhận học phí của các buổi đã dạy khi nào, bằng cách nào (vào ví hay chuyển khoản), PTutor giữ lại bao nhiêu phần trăm?

### tin-nhan-day-quy-dinh — Quy định tin nhận dạy

- Khi nào có giao diện để gia sư tạo, sửa, đóng, hủy tin nhận dạy? Tên trang và tên nút là gì?
- Tiêu chí PTutor dùng để duyệt tin nhận dạy có môn học tự nhập hoặc có ghi chú là gì?


## 6. Giá trị cấu hình cần xác nhận

Nguồn: `application-prod.yaml` và giá trị mặc định trong code (`@Value`, hằng số). Trên `dev` không có `application.yml` mặc định.

| Cấu hình / hằng số | Biến môi trường | Giá trị dùng trong tài liệu | Ghi chú |
|---|---|---|---|
| app.payment.fees.studying-request | PAYMENT_STUDYING_REQUEST_FEE | Không ghi số; "theo mức hiển thị khi thanh toán" | Không có giá trị mặc định |
| app.payment.fees.teaching-request | PAYMENT_TEACHING_REQUEST_FEE | Không ghi số | Không có giá trị mặc định |
| app.payment.vnpay.expiry-minutes | VNPAY_EXPIRY_MINUTES | 15 phút | Mặc định trong yaml và code |
| PaymentService.MIN_WALLET_TOP_UP / MAX_WALLET_TOP_UP | — | 10.000đ – 100.000.000đ | Hằng số |
| app.refund.rates.tutor-absent | — | 100% | Mặc định trong code, không có trong yaml |
| app.refund.rates.student-absent | — | 80% (gia sư nhận 20%) | Mặc định trong code, không có trong yaml |
| app.refund.tutor-response-hours | — | 48 giờ | Mặc định trong code, không có trong yaml |
| app.lesson.auto-completion-grace-days | — | 3 ngày kể từ lúc đánh dấu đã dạy | Mặc định trong code; frontend ghi cứng CONFIRMATION_GRACE_DAYS = 3 |
| app.lesson.auto-completion-interval-ms | — | Kiểm tra mỗi giờ | 3600000 ms |
| app.notification.payment-reminder-cron | NOTIFICATION_PAYMENT_REMINDER_CRON | 9 giờ sáng hằng ngày, giờ Việt Nam | Số ngày nhắc trước (3) ghi cứng trong PaymentInstallmentReminderService |
| app.contract.lifecycle.expiration-cron | CONTRACT_EXPIRATION_CRON | 0 giờ hằng ngày, giờ Việt Nam | — |
| app.account-suspension.expiration-interval-ms | ACCOUNT_SUSPENSION_EXPIRATION_INTERVAL_MS | Kiểm tra mỗi phút | 60000 ms |
| app.tutor-recommendation.recalculation-cron | — | 2 giờ 30 UTC = 9 giờ 30 sáng giờ Việt Nam | Mặc định trong code |
| app.security.jwt.access-token-ttl | JWT_ACCESS_TOKEN_TTL | 10 giờ | yaml prod 36000 giây; mặc định trong code 3600 giây (1 giờ) |
| app.security.jwt.refresh-token-ttl | JWT_REFRESH_TOKEN_TTL | 30 ngày | 2592000 giây |
| ForgotPasswordService.OTP_TTL | — | 5 phút | Hằng số |
| CertificateFileStorageService.MAX_FILE_SIZE | — | 10 MB | Hằng số; multipart max 10MB |

## 7. Chỗ code và giao diện lệch nhau, chỗ nên sửa

### 7.1. Giao diện hiển thị tiếng Anh

1. Menu và nhiều trang học viên dùng nhãn tiếng Anh: Dashboard, My Studying Requests, Browse Teaching Requests, My requests to studying, Notifications, Wallet & Payments, My Profile; toàn bộ form tạo tin tìm gia sư, form đăng ký học, trang hồ sơ học viên, trang ví, trang thông báo.
2. Trạng thái tin tìm gia sư hiển thị bằng mã viết thường (`statusLabel` trong `studyingRequestUtils.ts`): "pending review", "matched"… Tài liệu đề xuất nhãn tiếng Việt kèm todo.
3. Trạng thái đơn đăng ký học ở `StudentApplicationsPage` hiển thị mã tiếng Anh.
4. Trạng thái hồ sơ gia sư ở `TutorProfilePage`: "Waiting for review", "Verified profile", "Not approved" và các câu hướng dẫn tiếng Anh.
5. `httpClient.ts` hiển thị nguyên câu tiếng Anh của máy chủ cho hầu hết lỗi. Chỉ các trang hoàn tiền có bảng chuyển mã lỗi sang tiếng Việt (`refundFormat.ts`). Tài liệu lỗi trích câu tiếng Anh kèm giải thích.
6. Toàn bộ tiêu đề, nội dung thông báo (`NotificationTemplateFactory`) và mô tả giao dịch ví là tiếng Anh. Danh sách giao dịch định dạng ngày và tiền theo kiểu Mỹ (`en-US`, "VND").
7. Câu báo lỗi khi đăng nhập tài khoản bị tạm khóa ghi thời điểm theo UTC dạng ISO ("until 2026-11-05T03:00 UTC").
8. Trang ví của gia sư: menu ghi "Ví tiền" nhưng tiêu đề trang là "Wallet & Payments"; trang thông báo gia sư có tiêu đề "Notifications".

### 7.2. Chức năng có backend nhưng chưa có giao diện

1. Gia sư: tạo, sửa, tạm đóng, hủy, trả phí tin nhận dạy (mục "Yêu cầu dạy của tôi" là trang Sắp ra mắt).
2. Gia sư: xem và chấp nhận, từ chối đơn đăng ký học.
3. Gia sư: xem, soạn, ký, từ chối, hủy, gia hạn hợp đồng (mục "Hợp đồng" là trang Sắp ra mắt).
4. Gia sư: tạo, sửa, hủy buổi học và đánh dấu đã dạy (mục "Lịch dạy" là trang Sắp ra mắt). Vì vậy luồng xác nhận buổi học của học viên chưa chạy được đầu-cuối trên giao diện.
5. Học viên và gia sư: toàn bộ luồng khiếu nại (gửi, sửa, hủy, bổ sung bằng chứng, phản hồi khi bị khiếu nại).
6. Khai báo tài khoản ngân hàng, gửi và hủy yêu cầu rút tiền.
7. Đổi mật khẩu khi đang đăng nhập.
8. Trả phí đăng tin và đóng học phí bằng số dư ví (giao diện chỉ gọi đường VNPay).

### 7.3. Trạng thái có trong enum nhưng không có luồng đặt

1. `CertificateStatus.EXPIRED`: không có tác vụ nào đặt; chỉ chặn duyệt chứng chỉ quá hạn.
2. `PaymentInstallmentStatus.CANCELLED`: không có thao tác nào đặt.
3. `UserStatus.INACTIVE`: không có thao tác nào đặt.
4. `WithdrawalRequestStatus.PROCESSING`, `REJECTED`, `COMPLETED`: không có endpoint xử lý rút tiền cho nhân viên, dù hệ thống gửi thông báo `WITHDRAWAL_REVIEW_REQUIRED` cho nhân viên.
5. `CancelContractRequestStatus`: enum tồn tại nhưng không có endpoint yêu cầu hủy hợp đồng.

### 7.4. Lệch khác giữa code và giao diện hoặc nghiệp vụ

1. Form "Apply to study" yêu cầu nhập "Grade ID" (mã lớp dạng chuỗi), người dùng không biết mã này.
2. Bảng "Các kỳ thanh toán" hiện nút **Thanh toán** cho kỳ của hợp đồng Mỗi buổi chưa gắn buổi học; bấm sẽ lỗi "This lesson installment is not scheduled yet".
3. Giao diện không hiển thị mức phí đăng tin trước khi chuyển sang VNPay.
4. Liên kết "Liên hệ Ptutor", "Trung tâm trợ giúp", "Chatbot hỗ trợ" ở trang chủ trỏ về `#top`.
5. Gia sư chưa được duyệt hồ sơ vẫn gửi đề xuất và nhận hợp đồng được (không có kiểm tra trạng thái hồ sơ).
6. Duyệt tin tìm gia sư (`/api/v1/admin/studying-requests`) không có trong danh sách của `EmployeeFunctionAuthorizationFilter`. Bộ lọc cho qua đường dẫn không có trong danh sách, nên trên `dev` mọi nhân viên (cùng quản trị viên) đều duyệt được tin tìm gia sư, không phân theo chức năng. Duyệt tin nhận dạy, hồ sơ, chứng chỉ trên `dev` thuộc chức năng cũ `CONTENT_REVIEWER`. Theo cách chia hai chức năng mới, việc duyệt nội dung thuộc nhân viên chăm sóc khách hàng; tài liệu đã ghi đúng như vậy.
7. Học viên hủy tin đã trả phí không được hoàn phí (code không hoàn) và không có thông báo cho người dùng về việc này.
8. Nhãn buổi học "Đã học" dùng chung cho hai trạng thái xác nhận bởi học viên và tự hoàn tất.
9. Nội dung nhắc học phí ghi cứng "due in 3 days" trong khi lịch nhắc đọc từ cấu hình.
10. Học phí của buổi đã dạy không được cộng cho gia sư ở bất kỳ luồng nào; chỉ phần 20% buổi học viên nghỉ được cộng qua hoàn tiền.

## 8. Chức năng đặt draft và lý do

Mọi tài liệu đều có `status: draft` để admin duyệt. Các tài liệu dưới đây mô tả chức năng chưa hoàn thiện trên giao diện hoặc thiếu quy trình, cần chú ý khi duyệt:

| Tài liệu | Lý do |
|---|---|
| tai-khoan-quen-mat-khau (phần đổi mật khẩu) | Chưa có giao diện đổi mật khẩu khi đang đăng nhập |
| tin-nhan-day-quy-dinh | Chưa có giao diện tin nhận dạy cho gia sư |
| don-dang-ky-hoc-xu-ly | Chưa có giao diện xử lý đơn đăng ký học cho gia sư |
| hop-dong-tao-gia-su | Chưa có trang hợp đồng cho gia sư |
| buoi-hoc-gia-su | Chưa có giao diện tạo buổi học, đánh dấu đã dạy |
| buoi-hoc-quy-dinh, buoi-hoc-xac-nhan, loi-buoi-hoc | Phụ thuộc việc gia sư đánh dấu đã dạy, chưa có giao diện phía gia sư; câu lỗi phía gia sư là câu tiếng Anh của máy chủ |
| khieu-nai-gui, khieu-nai-bang-chung, khieu-nai-bi-khieu-nai, khieu-nai-ket-luan, trang-thai-khieu-nai, loi-khieu-nai | Chưa có giao diện khiếu nại cho người dùng; nhãn trạng thái lấy từ trang quản trị |
| tai-khoan-ngan-hang, rut-tien, trang-thai-rut-tien, loi-rut-tien-tai-khoan-ngan-hang | Chưa có giao diện; chưa có quy trình xử lý yêu cầu rút |
| thu-nhap-gia-su | Chưa có luồng trả tiền dạy cho gia sư |
| viec-chua-ho-tro (phần đánh giá) | Chưa có chức năng viết đánh giá |
| ky-hoc-phi-thanh-toan, phi-dang-tin | Trả bằng ví chỉ có ở backend |
| trang-thai-tin-dang, trang-thai-giao-dich-vi, trang-thai-thanh-toan, ho-so-gia-su-duyet (phần trạng thái hồ sơ) | Giao diện chưa có nhãn tiếng Việt; nhãn do người soạn đề xuất hoặc lấy từ trang quản trị |
| gioi-thieu-vai-tro (phần liên hệ hỗ trợ) | Code không có kênh liên hệ, giờ hỗ trợ |

## 9. Những chủ đề cố ý không viết

1. Quy trình nội bộ của nhân viên và quản trị viên: hàng đợi duyệt tin, hồ sơ, chứng chỉ; tiếp nhận, chuyển giao khiếu nại và hoàn tiền; quản lý nhân viên; quản lý môn học, lớp; soạn và gửi thông báo chung.
2. Công thức chi tiết của điểm đề xuất (theo yêu cầu chỉ giải thích ở mức dễ hiểu).
3. Chi tiết kỹ thuật VNPay (IPN, chữ ký, mã phản hồi), mã hóa CCCD và số tài khoản, lưu trữ tệp.
4. Tìm và gợi ý gia sư bằng AI, chatbot hỗ trợ học tập, yêu cầu hỗ trợ gửi nhân viên: chưa có trên `dev`.
5. Lịch dạy, danh sách buổi học sắp tới của gia sư ở mức chi tiết: buổi học chỉ phục vụ thanh toán theo buổi.
6. Tên chức năng nhân viên cũ trên `dev` (`GENERAL_OPERATIONS`, `COMPLAINT_HANDLER`, `CONTENT_REVIEWER`, `USER_SUPPORT`): không đưa vào tài liệu; dùng "nhân viên chăm sóc khách hàng", "kế toán" hoặc "nhân viên PTutor".

## 10. Khác với quy-dinh-nen-tang-tu-code.md

Lúc sinh tài liệu (2026-11-02) không có file này cạnh prompt. File đã được thêm vào nhánh `feat/support-chatbot` ở commit `ce15c61` (`docs/support-chatbot/quy-dinh-nen-tang-tu-code.md`); chưa đối chiếu bộ tài liệu với file này.

## 11. Ghi chú khác

- Danh mục gửi duyệt ở đợt 1 ghi tổng 63 tài liệu là đếm nhầm; các bảng trong danh mục có 43 + 15 + 15 = 73 tài liệu và đã viết đủ 73. Bản cập nhật thêm 2 tài liệu lỗi (75), sau đó gộp 10 tài liệu ngắn, còn 65 (xem mục 2).
- Mã chức năng `lesson`, `refund` và loại tài liệu `error` đã có trong đặc tả (commit `ce15c61`).
- Kiểm tra tự động sau khi viết: không còn chuỗi dạng mã kỹ thuật (chữ hoa nối gạch dưới), đường dẫn `/api/` hay tên dạng lớp Java trong thân bài; mọi route có trong `_routes.yaml`; mục dài nhất dưới 300 chữ.
- Một số câu báo lỗi tiếng Anh của máy chủ có chứa tên trạng thái (ví dụ "Only DRAFT or OPEN requests can be updated") được trích nguyên văn vì đó là câu người dùng thấy.
