# Duyệt mẫu bộ nhãn nháp (26/10/2026)

40 nhãn chọn ngẫu nhiên (hạt giống 20261026) trong 196 nhãn nháp chưa sửa, để nhóm duyệt mẫu theo 13 mục 13.6.
Ghi `OK` hoặc cách sửa vào cột cuối.

**Lượt duyệt 1 — Claude, 26/10/2026 (theo yêu cầu người dùng).** 33/40 OK; 1 nhãn sai rõ (#30); 2 nhãn có thể bổ sung (#23, #35); 4 nhãn (#5, #11, #20, #27) vướng một quy ước còn thiếu (quận không kèm hình thức). Lưu ý: nhãn nháp cũng do Claude soạn, nên lượt duyệt này **không độc lập**; nhóm cần xác nhận ít nhất 7 dòng đánh dấu. Quy ước: bảng 13.6 và 05 mục 5.4.

| # | Phần | Câu | Nhãn | Nhóm |
|---|---|---|---|---|
| 1 | A1 tag | Đại số con ok, chỉ cần luyện thêm hình. Mục tiêu 9 điểm thi vào 10. | `['TOPIC:hinh_hoc_phang', 'GOAL:thi_vao_10']` || OK |
| 2 | A1 tag | Cô giáo tiểu học chuyển sang dạy toán THCS, kiên nhẫn với các bạn học chậm | `['STYLE:kien_nhan']` || OK |
| 3 | A2 S | lớp 11 học lý trên 200k | `{'subject': 'Vật lý', 'grades': [11], 'price_min': 200000}` || OK |
| 4 | A1 tag | Cô giáo trẻ, dạy tiếng Anh thiếu nhi qua trò chơi, bài hát, vui vẻ năng động | `['STYLE:vui_ve']` || OK |
| 5 | A2 S | Tìm gia sư Anh văn lớp 8 ở Thanh Xuân hoặc Hà Đông, cuối tuần | `{'subject': 'Tiếng Anh', 'grades': [8], 'mode': 'OFFLINE', 'districts': ['Thanh Xuân', 'Hà Đông'], 'days': [6, 7]}` || ⚠ Thiếu quy ước: câu có quận mà không nói hình thức. Nhãn ghi OFFLINE (theo prompt A2: "có tên quận → OFFLINE"), nhưng bộ độc lập câu 6 ("ở Đống Đa") không ghi mode. Cần chốt quy ước T10 rồi sửa một phía |
| 6 | A1 lịch | Tối thứ hai đến thứ năm | `[(1, '19:00', '21:00'), (2, '19:00', '21:00'), (3, '19:00', '21:00'), (4, '19:00', '21:00')]` || OK |
| 7 | A2 S | Môn hoá lớp 10 | `{'subject': 'Hóa học', 'grades': [10]}` || OK |
| 8 | A1 tag | Con cần người kiên nhẫn, giải thích kỹ, con hay hỏi lại nhiều lần. Phần chuyển động thẳng. | `['STYLE:kien_nhan', 'STYLE:giai_thich_ky', 'TOPIC:co_hoc']` || OK |
| 9 | A1 tag | Bé nhà em mất gốc tiếng Anh, ngữ pháp gần như không biết gì. Mong cô kiên nhẫn ạ. | `['SEGMENT:mat_goc', 'TOPIC:ngu_phap', 'STYLE:kien_nhan']` || OK |
| 10 | A1 lịch | 17h30 thứ 6 | `[(5, '17:30', '19:30')]` || OK |
| 11 | A2 S | Toán lớp 11 cải thiện điểm, thứ 3 thứ 6 tối, Cầu Giấy | `{'subject': 'Toán', 'grades': [11], 'goalTags': ['cai_thien_diem'], 'days': [2, 5], 'mode': 'OFFLINE', 'districts': ['Cầu Giấy']}` || ⚠ Như #5 (OFFLINE suy từ quận "Cầu Giấy"); phần còn lại OK |
| 12 | A1 lịch | thứ bảy chủ nhật 15h | `[(6, '15:00', '17:00'), (7, '15:00', '17:00')]` || OK |
| 13 | A1 lịch | Thứ 7, CN buổi sáng | `[(6, '08:00', '11:00'), (7, '08:00', '11:00')]` || OK |
| 14 | A1 lịch | Sáng thứ 2 (con học ca chiều) | `[(1, '08:00', '11:00')]` || OK |
| 15 | A1 tag | Hình học không gian là thế mạnh, có phần mềm vẽ hình 3D minh hoạ | `['TOPIC:hinh_hoc_khong_gian', 'STYLE:tuong_tac_cong_nghe']` || OK |
| 16 | A2 T | Tìm học viên tiếng Anh lớp 9 ôn chuyên | `{'subject': 'Tiếng Anh', 'grades': [9], 'goalTags': ['thi_chuyen']}` || OK |
| 17 | A2 S | tiếng anh lớp 10 luyện chứng chỉ, kiên nhẫn | `{'subject': 'Tiếng Anh', 'grades': [10], 'goalTags': ['chung_chi_ngoai_ngu'], 'styleTags': ['kien_nhan']}` || OK |
| 18 | A1 lịch | thứ 4 sau 8h tối | `[(3, '20:00', '22:00')]` || OK |
| 19 | A2 S | Toán 12 ôn tốt nghiệp từ 150k đến 250k | `{'subject': 'Toán', 'grades': [12], 'goalTags': ['thi_tot_nghiep_thpt'], 'price_min': 150000, 'price_max': 250000}` || OK |
| 20 | A2 S | toán lớp 11 ở Hoàn Kiếm từ 200k | `{'subject': 'Toán', 'grades': [11], 'mode': 'OFFLINE', 'districts': ['Hoàn Kiếm'], 'price_min': 200000}` || ⚠ Như #5 (OFFLINE suy từ quận "Hoàn Kiếm"); phần còn lại OK |
| 21 | A2 S | tiếng anh 11 cho học sinh khá, luyện đề nhiều | `{'subject': 'Tiếng Anh', 'grades': [11], 'segmentTags': ['kha'], 'styleTags': ['luyen_tap_nhieu']}` || OK |
| 22 | A1 lịch | Tối thứ 6 và sáng thứ 7 | `[(5, '19:00', '21:00'), (6, '08:00', '11:00')]` || OK |
| 23 | A1 tag | Con phát âm sai nhiều, ngại giao tiếp với người nước ngoài. | `['TOPIC:phat_am', 'STYLE:giao_tiep']` || OK; có thể thêm TOPIC:noi ("ngại giao tiếp" là kỹ năng nói) — nhóm quyết |
| 24 | A1 tag | 10 năm dạy toán THPT, luyện thi đại học, phong cách nghiêm khắc | `['GOAL:thi_dai_hoc', 'STYLE:nghiem_khac']` || OK |
| 25 | A2 S | Gia sư toán lớp 8 tại nhà Hà Đông, tối thứ 3 thứ 5 thứ 7 | `{'subject': 'Toán', 'grades': [8], 'mode': 'OFFLINE', 'districts': ['Hà Đông'], 'days': [2, 4, 6]}` || OK |
| 26 | A2 S | Toán 7 học vượt online sáng chủ nhật | `{'subject': 'Toán', 'grades': [7], 'goalTags': ['hoc_vuot'], 'mode': 'ONLINE', 'days': [7]}` || OK |
| 27 | A2 T | tìm học sinh toán lớp 9 ôn thi vào 10 ở Cầu Giấy | `{'subject': 'Toán', 'grades': [9], 'goalTags': ['thi_vao_10'], 'mode': 'OFFLINE', 'districts': ['Cầu Giấy']}` || ⚠ Như #5 (OFFLINE suy từ quận "Cầu Giấy"); phần còn lại OK |
| 28 | A1 tag | Yếu cơ học, thích học qua video và mô phỏng | `['TOPIC:co_hoc', 'STYLE:tuong_tac_cong_nghe']` || OK |
| 29 | A2 S | toán 9 ôn vào 10 kiên nhẫn vui vẻ | `{'subject': 'Toán', 'grades': [9], 'goalTags': ['thi_vao_10'], 'styleTags': ['kien_nhan', 'vui_ve']}` || OK |
| 30 | A1 tag | Sinh viên Bách khoa, dạy lý cấp 3 cho các bạn mất gốc, giải thích bằng thí nghiệm | `['SEGMENT:mat_goc', 'STYLE:giai_thich_ky']` || Sửa: bỏ STYLE:giai_thich_ky — "giải thích bằng thí nghiệm" là cách dạy, không phải "giải thích kỹ" (theo tinh thần T6: chỉ gắn khi nói rõ). Còn SEGMENT:mat_goc |
| 31 | A1 lịch | T7 tối từ 19h đến 20h30 | `[(6, '19:00', '20:30')]` || OK |
| 32 | A1 lịch | chủ nhật cả ngày | `[(7, '08:00', '21:00')]` || OK |
| 33 | A1 lịch | Tối T2 T4 sau 7h30 | `[(1, '19:30', '21:30'), (3, '19:30', '21:30')]` || OK |
| 34 | A2 T | Nhận học sinh toán ở đâu cũng được | `{'subject': 'Toán', 'scope': 'ANY'}` || OK |
| 35 | A2 S | toán cho học sinh khá lớp 7 muốn lên giỏi | `{'subject': 'Toán', 'grades': [7], 'segmentTags': ['kha']}` || OK; có thể thêm GOAL:cai_thien_diem ("muốn lên giỏi") — nhóm quyết |
| 36 | A1 lịch | cuối tuần buổi chiều | `[(6, '14:00', '17:00'), (7, '14:00', '17:00')]` || OK |
| 37 | A1 lịch | T2-T6 buổi sáng | `[(1, '08:00', '11:00'), (2, '08:00', '11:00'), (3, '08:00', '11:00'), (4, '08:00', '11:00'), (5, '08:00', '11:00')]` || OK |
| 38 | A1 lịch | 16h-18h chiều thứ 4 | `[(3, '16:00', '18:00')]` || OK |
| 39 | A1 lịch | thứ bảy 9h sáng đến 11h | `[(6, '09:00', '11:00')]` || OK |
| 40 | A1 tag | Cần lộ trình rõ ràng, kiểm tra hàng tháng để theo dõi tiến độ ngữ pháp và từ vựng | `['STYLE:ke_hoach_ro', 'TOPIC:ngu_phap', 'TOPIC:tu_vung']` || OK |
