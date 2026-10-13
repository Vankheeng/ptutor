"""Small demo data set for trying the recommendation screens by hand.

    python scripts/recommendation_demo/seed_demo.py            # insert into the dev database (docker)
    python scripts/recommendation_demo/seed_demo.py --print    # only print the SQL
    python scripts/recommendation_demo/seed_demo.py --disable  # hide the demo data again

20 verified tutors with 40 OPEN teaching postings and 6 students with 20 OPEN studying postings, all
in Hà Nội. Everything is written in Vietnamese the way real users write, so that embedding, tag
extraction and match reasons have something to work with. Rows are recognisable by their e-mail
(demo.*@ptutor.local) and are inserted with fixed IDs: running the script twice changes nothing.

Local use only. All demo accounts share the password below; never run this against production.
After inserting, start the backend with the AI service running: its backfill embeds and enriches
the new rows within a few minutes (the enrichment calls the LLM, a few cents in total).
"""

import argparse
import random
import subprocess
import sys
import uuid

DEMO_PASSWORD = "Demo@2026"
# bcrypt hash of DEMO_PASSWORD (cost 10).
PASSWORD_HASH = "$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG"
NAMESPACE = uuid.UUID("7b0d2f6e-5a34-4c1e-9d55-0d6f1e2a9c11")
PROVINCE = "Hà Nội"
DISTRICTS = ["Cầu Giấy", "Đống Đa", "Ba Đình", "Hà Đông", "Hai Bà Trưng", "Hoàng Mai", "Thanh Xuân", "Long Biên"]

# (first name, last name, gender, district, years, quality score, rating, reviews,
#  introduction, methodology, target students)
TUTORS = [
    ("Lan", "Nguyễn Thị", "FEMALE", "Cầu Giấy", 6, 86, 4.8, 21,
     "Giáo viên Toán THCS, 6 năm kèm học sinh ôn thi vào lớp 10 công lập.",
     "Dạy chậm mà chắc, hệ thống lại kiến thức nền rồi mới luyện đề. Kiên nhẫn giảng lại đến khi con hiểu.",
     "Học sinh trung bình, mất gốc"),
    ("Minh", "Trần Quang", "MALE", "Đống Đa", 4, 78, 4.5, 12,
     "Cựu học sinh chuyên Toán, sinh viên năm cuối Sư phạm Toán.",
     "Luyện tập nhiều, mỗi buổi một chuyên đề kèm bài tập về nhà có chấm chữa. Kế hoạch học rõ ràng theo tuần.",
     "Học sinh khá, giỏi muốn thi chuyên"),
    ("Hương", "Lê Thu", "FEMALE", "Ba Đình", 8, 90, 4.9, 34,
     "Giáo viên Tiếng Anh, chứng chỉ IELTS 8.0, từng dạy ở trung tâm 8 năm.",
     "Vui vẻ, khuyến khích con nói nhiều. Lấy lại gốc ngữ pháp bằng ví dụ gần gũi, không học vẹt.",
     "Học sinh mất gốc, sợ nói tiếng Anh"),
    ("Tuấn", "Phạm Anh", "MALE", "Hà Đông", 3, 70, 4.3, 8,
     "Sinh viên Bách khoa, gia sư Vật lý và Toán cấp 2.",
     "Giải thích bằng thí nghiệm và hình vẽ, nghiêm khắc về việc làm bài tập đầy đủ.",
     "Học sinh trung bình khá"),
    ("Mai", "Hoàng Thị", "FEMALE", "Hai Bà Trưng", 10, 88, 4.7, 40,
     "Giáo viên Ngữ văn THCS 10 năm kinh nghiệm, chấm thi vào 10 nhiều năm.",
     "Rèn kỹ năng lập dàn ý và viết đoạn, sửa bài chi tiết từng câu. Kiên nhẫn với học sinh ít ý, ngại viết.",
     "Học sinh trung bình, viết văn lủng củng"),
    ("Đức", "Vũ Minh", "MALE", "Hoàng Mai", 5, 74, 4.4, 15,
     "Thạc sĩ Hoá học, dạy Hoá và Khoa học tự nhiên cho học sinh cấp 2.",
     "Đi từ căn bản, cho nhiều bài tập tính toán. Giảng kỹ phần phương trình hoá học và mol.",
     "Học sinh mất gốc Hoá"),
    ("Ngọc", "Đỗ Bảo", "FEMALE", "Thanh Xuân", 2, 62, 4.1, 5,
     "Sinh viên Ngoại thương, IELTS 7.5, dạy Tiếng Anh giao tiếp và ngữ pháp cho cấp 2.",
     "Học qua trò chơi và hội thoại, vui vẻ, nhẹ nhàng.",
     "Học sinh tiểu học và đầu cấp 2"),
    ("Hải", "Bùi Thanh", "MALE", "Long Biên", 7, 82, 4.6, 19,
     "Giáo viên Toán, chuyên luyện thi học sinh giỏi cấp quận và thi vào trường chuyên.",
     "Nghiêm khắc, tốc độ nhanh, bài khó. Luyện đề liên tục và phân tích lỗi sai.",
     "Học sinh giỏi"),
    ("Trang", "Phan Thu", "FEMALE", "Cầu Giấy", 4, 76, 4.5, 11,
     "Cử nhân Sư phạm Ngữ văn, kèm Văn và Tiếng Việt cho học sinh lớp 6 đến lớp 9.",
     "Hướng dẫn cảm thụ thơ văn bằng sơ đồ tư duy, khuyến khích con tự diễn đạt.",
     "Học sinh trung bình khá"),
    ("Sơn", "Đặng Hoàng", "MALE", "Đống Đa", 9, 84, 4.7, 27,
     "Giáo viên Vật lý THCS, phụ trách đội tuyển học sinh giỏi của trường.",
     "Giải thích kỹ bản chất hiện tượng rồi mới làm bài tập. Kiên nhẫn, hay lấy ví dụ thực tế.",
     "Học sinh trung bình, khá"),
    ("Linh", "Ngô Khánh", "FEMALE", "Ba Đình", 3, 68, 4.2, 7,
     "Sinh viên Sư phạm Toán, kèm Toán lớp 6, 7 lấy lại căn bản.",
     "Dạy chậm, giảng từng bước, kiểm tra bài cũ đầu mỗi buổi.",
     "Học sinh mất gốc, hay sợ Toán"),
    ("Quân", "Lý Đức", "MALE", "Hà Đông", 6, 80, 4.6, 16,
     "Kỹ sư, dạy Toán và Tin học, có kinh nghiệm luyện thi vào 10.",
     "Kế hoạch rõ theo từng tuần, luyện đề tính giờ, tổng kết lỗi sau mỗi đề.",
     "Học sinh khá muốn nâng điểm"),
    ("Thảo", "Trịnh Phương", "FEMALE", "Hai Bà Trưng", 5, 79, 4.6, 14,
     "Giáo viên Tiếng Anh THCS, chuyên ôn thi vào lớp 10 môn Anh.",
     "Hệ thống ngữ pháp theo chuyên đề, luyện đề thi các năm, sửa lỗi cẩn thận.",
     "Học sinh trung bình khá"),
    ("Khoa", "Đinh Văn", "MALE", "Hoàng Mai", 1, 55, 4.0, 2,
     "Sinh viên năm hai, nhận kèm Toán và Lý lớp 6, 7.",
     "Gần gũi, vui vẻ, hướng dẫn làm bài tập trên lớp.",
     "Học sinh trung bình"),
    ("Yến", "Cao Hải", "FEMALE", "Thanh Xuân", 12, 92, 4.9, 52,
     "Giáo viên Hoá học 12 năm, nhiều học sinh đỗ trường chuyên.",
     "Giảng kỹ lý thuyết, luyện tập nhiều dạng bài, nghiêm khắc nhưng tận tâm.",
     "Học sinh khá, giỏi"),
    ("Bình", "Tạ Quốc", "MALE", "Long Biên", 4, 72, 4.3, 9,
     "Cử nhân Sư phạm Lý, dạy Vật lý và Khoa học tự nhiên.",
     "Dạy bằng hình ảnh và mô phỏng, chậm mà chắc.",
     "Học sinh mất gốc, trung bình"),
    ("Hà", "Lương Thu", "FEMALE", "Cầu Giấy", 7, 85, 4.8, 23,
     "Giáo viên Ngữ văn, chuyên nghị luận xã hội và nghị luận văn học lớp 9.",
     "Cho dàn ý mẫu, luyện viết theo đề, chấm chữa kỹ. Kế hoạch ôn thi vào 10 rõ ràng.",
     "Học sinh khá muốn đạt điểm cao môn Văn"),
    ("Nam", "Chu Hoàng", "MALE", "Đống Đa", 2, 60, 4.0, 4,
     "Sinh viên Sư phạm Anh, dạy Tiếng Anh cấp 2 theo sách giáo khoa.",
     "Bám sát chương trình trên lớp, vui vẻ, kiên nhẫn.",
     "Học sinh trung bình"),
    ("Vy", "Mạc Tường", "FEMALE", "Ba Đình", 5, 77, 4.5, 13,
     "Dạy Toán tư duy và Toán nâng cao cho học sinh lớp 6, 7.",
     "Gợi mở để con tự tìm lời giải, luyện tập nhiều bài toán thực tế.",
     "Học sinh khá, giỏi"),
    ("Long", "Kiều Gia", "MALE", "Hà Đông", 8, 83, 4.7, 25,
     "Giáo viên Toán THCS, chuyên hình học và các bài chứng minh.",
     "Dạy cách vẽ hình và trình bày chứng minh từng bước, kiên nhẫn sửa lỗi lập luận.",
     "Học sinh trung bình, yếu hình học"),
]

# subject -> list of (grades, title, description)
FOCUSES = {
    "Toán": [
        ([9], "Luyện thi vào lớp 10 môn Toán", "Ôn toàn bộ chương trình Toán 9, trọng tâm hệ phương trình, hàm số và hình học phẳng. Luyện đề thi vào 10 các năm."),
        ([8, 9], "Toán 8, 9: lấy lại gốc hình học", "Dành cho học sinh yếu hình: tứ giác, tam giác đồng dạng, đường tròn và cách trình bày bài chứng minh."),
        ([6, 7], "Toán 6, 7 căn bản", "Số học, phân số, tỉ lệ thức và hình học cơ bản. Lấy lại căn bản cho học sinh mới lên cấp 2."),
        ([8, 9], "Toán nâng cao, bồi dưỡng học sinh giỏi", "Chuyên đề bất đẳng thức, phương trình nghiệm nguyên, hình học nâng cao. Luyện thi học sinh giỏi và thi chuyên."),
        ([8], "Toán 8: phân tích đa thức và phương trình", "Phân tích đa thức thành nhân tử, phân thức đại số, phương trình bậc nhất. Nâng điểm kiểm tra trên lớp."),
    ],
    "Tiếng Anh": [
        ([7, 8], "Tiếng Anh lấy lại gốc ngữ pháp", "Các thì cơ bản, câu bị động, câu điều kiện. Dành cho học sinh mất gốc, kết hợp luyện nói."),
        ([9], "Ôn thi vào lớp 10 môn Tiếng Anh", "Hệ thống ngữ pháp và từ vựng theo chuyên đề, luyện đề thi vào 10, kỹ năng đọc hiểu và viết lại câu."),
        ([6, 7], "Tiếng Anh giao tiếp cho học sinh cấp 2", "Phát âm, phản xạ nghe nói qua hội thoại và trò chơi. Giúp con tự tin nói tiếng Anh."),
        ([8, 9], "Luyện IELTS nền tảng cho học sinh THCS", "Xây nền bốn kỹ năng hướng tới IELTS 5.5 đến 6.5, từ vựng học thuật cơ bản."),
    ],
    "Ngữ văn": [
        ([9], "Ngữ văn 9 ôn thi vào lớp 10", "Nghị luận xã hội, nghị luận văn học các tác phẩm trọng tâm, luyện viết theo đề thi."),
        ([6, 7], "Ngữ văn 6, 7: rèn viết đoạn và lập dàn ý", "Rèn kỹ năng lập dàn ý, viết đoạn văn, miêu tả và kể chuyện. Sửa bài chi tiết."),
        ([8, 9], "Cảm thụ thơ văn và tiếng Việt", "Phân tích thơ, biện pháp tu từ, kiến thức tiếng Việt theo sách giáo khoa."),
    ],
    "Vật lý": [
        ([8, 9], "Vật lý 8, 9: điện học và cơ học", "Định luật Ôm, mạch điện, công và công suất, áp suất. Giải thích bản chất rồi luyện bài tập."),
        ([6, 7], "Vật lý 6, 7 cho học sinh mới bắt đầu", "Khối lượng, lực, nhiệt, quang học cơ bản bằng thí nghiệm và ví dụ thực tế."),
        ([9], "Vật lý 9 bồi dưỡng học sinh giỏi", "Chuyên đề mạch điện phức tạp, quang hình, chuyển động. Luyện đề học sinh giỏi cấp quận."),
    ],
    "Hóa học": [
        ([8, 9], "Hoá 8, 9 lấy lại căn bản", "Nguyên tử, phân tử, phương trình hoá học, tính theo mol. Dành cho học sinh mất gốc."),
        ([9], "Hoá 9: kim loại, phi kim và hữu cơ", "Tính chất hoá học, chuỗi phản ứng, bài tập hỗn hợp. Nâng điểm và ôn thi học kỳ."),
        ([8, 9], "Hoá học nâng cao thi chuyên", "Bài tập biện luận, hiệu suất, dung dịch. Luyện thi vào lớp 10 chuyên Hoá."),
    ],
}

# Which subjects each tutor teaches, in the order of TUTORS.
TUTOR_SUBJECTS = [
    ["Toán"], ["Toán"], ["Tiếng Anh"], ["Vật lý", "Toán"], ["Ngữ văn"], ["Hóa học"], ["Tiếng Anh"], ["Toán"],
    ["Ngữ văn"], ["Vật lý"], ["Toán"], ["Toán"], ["Tiếng Anh"], ["Toán", "Vật lý"], ["Hóa học"], ["Vật lý"],
    ["Ngữ văn"], ["Tiếng Anh"], ["Toán"], ["Toán"],
]

SCHEDULES = [
    ("Tối thứ 2, thứ 4", [(1, "19:00", "21:00"), (3, "19:00", "21:00")]),
    ("Tối thứ 3, thứ 5", [(2, "19:00", "21:00"), (4, "19:00", "21:00")]),
    ("Tối thứ 6 và sáng chủ nhật", [(5, "19:00", "21:00"), (7, "08:00", "11:00")]),
    ("Cuối tuần, sáng thứ 7 và chủ nhật", [(6, "08:00", "11:00"), (7, "08:00", "11:00")]),
    ("Chiều thứ 7, chiều chủ nhật", [(6, "14:00", "17:00"), (7, "14:00", "17:00")]),
    ("Các buổi tối trong tuần", [(1, "19:30", "21:30"), (2, "19:30", "21:30"), (4, "19:30", "21:30")]),
]

# (first name, last name, gender, district, level text, weak points, goals)
STUDENTS = [
    ("An", "Nguyễn Gia", "MALE", "Cầu Giấy", "Trung bình", "Yếu hình học phẳng, hay sai bài chứng minh", "Thi vào lớp 10 công lập"),
    ("Chi", "Trần Mai", "FEMALE", "Đống Đa", "Mất gốc", "Mất gốc ngữ pháp tiếng Anh, sợ nói", "Lấy lại căn bản, tự tin giao tiếp"),
    ("Dũng", "Lê Quốc", "MALE", "Hà Đông", "Khá", "Chưa quen dạng bài nâng cao", "Thi học sinh giỏi và thi chuyên Toán"),
    ("Giang", "Phạm Hương", "FEMALE", "Hai Bà Trưng", "Trung bình", "Viết văn lủng củng, ít ý", "Cải thiện điểm Văn trên lớp"),
    ("Huy", "Hoàng Minh", "MALE", "Thanh Xuân", "Trung bình", "Không hiểu bài Hoá và Lý, tính toán chậm", "Nâng điểm kiểm tra"),
    ("My", "Vũ Hà", "FEMALE", "Ba Đình", "Khá", "Kỹ năng viết lại câu và đọc hiểu còn yếu", "Thi vào lớp 10 đạt điểm cao môn Anh"),
]

# (student index, subject, grade, mode, max price, title, description, goals)
STUDYING = [
    (0, "Toán", 9, "OFFLINE", 200000, "Tìm gia sư Toán 9 ôn thi vào 10", "Con học lực trung bình, yếu hình học, hay sai bài chứng minh. Mong thầy cô kiên nhẫn, giảng chậm.", "Thi vào lớp 10 công lập"),
    (0, "Vật lý", 9, "ONLINE", 150000, "Cần gia sư Lý 9 phần điện học", "Con chưa hiểu mạch điện hỗn hợp, cần người giải thích kỹ bản chất.", "Nâng điểm kiểm tra"),
    (0, "Ngữ văn", 9, "OFFLINE", 180000, "Tìm cô dạy Văn 9 nghị luận", "Con viết nghị luận xã hội thiếu ý, cần hướng dẫn lập dàn ý.", "Thi vào lớp 10"),
    (1, "Tiếng Anh", 7, "ONLINE", 150000, "Gia sư Tiếng Anh lớp 7 lấy lại gốc", "Bé mất gốc ngữ pháp, sợ nói. Cần cô vui vẻ, dạy chậm.", "Lấy lại căn bản"),
    (1, "Toán", 7, "OFFLINE", 140000, "Kèm Toán 7 cho bé mất gốc", "Con yếu phân số và tỉ lệ thức, cần đi từ căn bản.", "Theo kịp chương trình trên lớp"),
    (1, "Tiếng Anh", 7, "OFFLINE", 170000, "Luyện nói tiếng Anh cho học sinh lớp 7", "Muốn con tự tin giao tiếp, học qua hội thoại.", "Tự tin giao tiếp"),
    (2, "Toán", 9, "OFFLINE", 300000, "Tìm thầy luyện Toán chuyên lớp 9", "Con học khá, muốn luyện bất đẳng thức và hình học nâng cao, tốc độ nhanh.", "Thi vào trường chuyên"),
    (2, "Vật lý", 9, "OFFLINE", 250000, "Bồi dưỡng học sinh giỏi Lý 9", "Cần luyện chuyên đề mạch điện và quang hình mức học sinh giỏi.", "Thi học sinh giỏi cấp quận"),
    (2, "Hóa học", 9, "ONLINE", 250000, "Luyện Hoá 9 nâng cao", "Muốn luyện bài tập biện luận và hiệu suất để thi chuyên.", "Thi vào lớp 10 chuyên Hoá"),
    (3, "Ngữ văn", 6, "OFFLINE", 150000, "Tìm cô kèm Văn lớp 6", "Con viết văn lủng củng, ít ý, cần rèn cách lập dàn ý và viết đoạn.", "Cải thiện điểm Văn"),
    (3, "Tiếng Anh", 6, "ONLINE", 130000, "Tiếng Anh lớp 6 bám sát sách giáo khoa", "Cần người kèm theo chương trình trên lớp, kiên nhẫn.", "Theo kịp chương trình"),
    (3, "Toán", 6, "OFFLINE", 150000, "Gia sư Toán 6 căn bản", "Con mới lên cấp 2, chưa quen số nguyên và phân số.", "Nắm chắc căn bản"),
    (4, "Hóa học", 8, "OFFLINE", 160000, "Cần gia sư Hoá 8 cho con mất gốc", "Con không hiểu phương trình hoá học và cách tính mol, cần giảng lại từ đầu.", "Nâng điểm kiểm tra"),
    (4, "Vật lý", 8, "OFFLINE", 160000, "Kèm Lý 8 phần áp suất và công", "Con tính toán chậm, cần luyện tập nhiều bài tập cơ bản.", "Nâng điểm kiểm tra"),
    (4, "Toán", 8, "ONLINE", 150000, "Toán 8 phân tích đa thức thành nhân tử", "Con hay sai phần phân tích đa thức và hình bình hành.", "Cải thiện điểm trên lớp"),
    (5, "Tiếng Anh", 9, "ONLINE", 220000, "Ôn thi vào 10 môn Anh", "Con học khá, cần luyện viết lại câu, đọc hiểu và luyện đề.", "Thi vào lớp 10 đạt điểm cao"),
    (5, "Tiếng Anh", 9, "OFFLINE", 280000, "Luyện IELTS nền tảng cho học sinh lớp 9", "Muốn xây nền bốn kỹ năng, mục tiêu 6.0 sau một năm.", "IELTS 6.0"),
    (5, "Ngữ văn", 9, "ONLINE", 200000, "Văn 9 luyện đề thi vào 10", "Con cần luyện viết theo đề và được chấm chữa kỹ.", "Thi vào lớp 10"),
    (5, "Toán", 9, "ONLINE", 200000, "Toán 9 luyện đề tính giờ", "Con khá, hay mất điểm vì trình bày. Cần luyện đề có tính giờ.", "Thi vào lớp 10 điểm cao"),
    (2, "Toán", 8, "ONLINE", 220000, "Toán 8 nâng cao", "Muốn học trước chương trình và làm bài nâng cao.", "Chuẩn bị thi học sinh giỏi"),
]


def uid(*parts) -> str:
    return str(uuid.uuid5(NAMESPACE, "/".join(str(part) for part in parts)))


def q(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def district(name: str) -> str:
    return (f"(SELECT d.id FROM districts d JOIN provinces p ON p.id = d.province_id "
            f"WHERE p.name = {q(PROVINCE)} AND d.name = {q(name)} LIMIT 1)")


def subject(name: str) -> str:
    return f"(SELECT id FROM subjects WHERE name = {q(name)} LIMIT 1)"


def grade(level: int) -> str:
    return f"(SELECT id FROM grades WHERE level = {level} ORDER BY name LIMIT 1)"


def user_sql(key: str, index: int, first: str, last: str, gender: str, home: str) -> str:
    # A 12-digit citizen ID in plain text: the backend encrypts it the next time it starts.
    citizen_id = f"09{index:010d}" if key.startswith("tutor") else f"08{index:010d}"
    return (f"INSERT INTO users (id, district_id, citizen_id, email, password, first_name, last_name, gender, status, "
            f"suspension_count) VALUES ({q(uid('user', key))}, {district(home)}, {q(citizen_id)}, "
            f"{q('demo.' + key + '@ptutor.local')}, {q(PASSWORD_HASH)}, {q(first)}, {q(last)}, {q(gender)}, 'ACTIVE', 0) "
            f"ON CONFLICT DO NOTHING;")


def build_sql() -> str:
    rng = random.Random(20261013)
    out = ["BEGIN;"]
    # Both review columns point at employees, not at users.
    reviewer = "(SELECT e.id FROM employees e ORDER BY e.created_at, e.id LIMIT 1)"

    for index, tutor in enumerate(TUTORS, start=1):
        first, last, gender, home, years, score, rating, reviews, intro, method, target = tutor
        key = f"tutor{index:02d}"
        out.append(user_sql(key, index, first, last, gender, home))
        out.append(
            f"INSERT INTO tutors (id, user_id, introduction, experience_years, teaching_methodology, "
            f"target_student_type, average_rating, total_reviews, profile_status, profile_reviewed_by, "
            f"profile_reviewed_at, recommendation_score) VALUES ({q(uid('tutor', key))}, {q(uid('user', key))}, "
            f"{q(intro)}, {years}, {q(method)}, {q(target)}, {rating}, {reviews}, 'VERIFIED', {reviewer}, "
            f"CURRENT_TIMESTAMP, {score}) ON CONFLICT DO NOTHING;")
        subjects = TUTOR_SUBJECTS[index - 1]
        for number in range(2):
            subject_name = subjects[number % len(subjects)]
            grades, title, description = FOCUSES[subject_name][(index + number * 2) % len(FOCUSES[subject_name])]
            mode = "ONLINE" if rng.random() < 0.3 else "OFFLINE"
            price = rng.choice([120000, 150000, 180000, 200000, 220000, 250000]) + (30000 if score >= 85 else 0)
            schedule_text, slots = SCHEDULES[(index + number) % len(SCHEDULES)]
            posting = uid("teaching", key, number)
            out.append(
                f"INSERT INTO teaching_requests (id, tutor_id, subject_id, title, description, quantity, "
                f"expected_price, teaching_mode, preferred_schedule, status, reviewed_by, reviewed_at, created_at) "
                f"VALUES ({q(posting)}, {q(uid('tutor', key))}, {subject(subject_name)}, {q(title)}, "
                f"{q(description + ' ' + method)}, {rng.choice([1, 2, 3])}, {price}, {q(mode)}, {q(schedule_text)}, "
                f"'OPEN', {reviewer}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '{rng.randint(0, 20)} days') "
                f"ON CONFLICT DO NOTHING;")
            for level in grades:
                out.append(
                    f"INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES "
                    f"({q(uid('tr-grade', posting, level))}, {grade(level)}, {q(posting)}) ON CONFLICT DO NOTHING;")
            if mode == "OFFLINE":
                for name in {home, rng.choice(DISTRICTS)}:
                    out.append(
                        f"INSERT INTO teaching_request_districts (id, teaching_request_id, district_id) VALUES "
                        f"({q(uid('tr-district', posting, name))}, {q(posting)}, {district(name)}) ON CONFLICT DO NOTHING;")
            for day, start, end in slots:
                out.append(
                    f"INSERT INTO teaching_request_availabilities (id, teaching_request_id, day_of_week, start_time, "
                    f"end_time, source) VALUES ({q(uid('tr-slot', posting, day))}, {q(posting)}, {day}, {q(start)}, "
                    f"{q(end)}, 'USER') ON CONFLICT DO NOTHING;")

    for index, student in enumerate(STUDENTS, start=1):
        first, last, gender, home, level, weak, goals = student
        key = f"student{index}"
        out.append(user_sql(key, index, first, last, gender, home))
        out.append(
            f"INSERT INTO students (id, user_id, current_level, weak_points, goals_description) VALUES "
            f"({q(uid('student', key))}, {q(uid('user', key))}, {q(level)}, {q(weak)}, {q(goals)}) "
            f"ON CONFLICT DO NOTHING;")

    for number, row in enumerate(STUDYING):
        owner, subject_name, level, mode, max_price, title, description, goals = row
        key = f"student{owner + 1}"
        home = STUDENTS[owner][3]
        schedule_text, slots = SCHEDULES[number % len(SCHEDULES)]
        posting = uid("studying", number)
        out.append(
            f"INSERT INTO studying_requests (id, district_id, student_id, subject_id, grade_id, title, description, "
            f"learning_goals, max_price, learning_mode, preferred_schedule, status, created_at) VALUES "
            f"({q(posting)}, {district(home) if mode == 'OFFLINE' else 'NULL'}, {q(uid('student', key))}, "
            f"{subject(subject_name)}, {grade(level)}, {q(title)}, {q(description)}, {q(goals)}, {max_price}, {q(mode)}, "
            f"{q(schedule_text)}, 'OPEN', CURRENT_TIMESTAMP - INTERVAL '{rng.randint(0, 15)} days') "
            f"ON CONFLICT DO NOTHING;")
        for day, start, end in slots:
            out.append(
                f"INSERT INTO studying_request_availabilities (id, studying_request_id, day_of_week, start_time, "
                f"end_time, source) VALUES ({q(uid('sr-slot', posting, day))}, {q(posting)}, {day}, {q(start)}, {q(end)}, "
                f"'USER') ON CONFLICT DO NOTHING;")
    out.append("COMMIT;")
    return "\n".join(out) + "\n"


DISABLE_SQL = """BEGIN;
UPDATE teaching_requests SET status = 'CLOSED' WHERE status = 'OPEN' AND tutor_id IN (
    SELECT t.id FROM tutors t JOIN users u ON u.id = t.user_id WHERE u.email LIKE 'demo.%@ptutor.local');
UPDATE studying_requests SET status = 'CLOSED' WHERE status = 'OPEN' AND student_id IN (
    SELECT s.id FROM students s JOIN users u ON u.id = s.user_id WHERE u.email LIKE 'demo.%@ptutor.local');
UPDATE users SET status = 'INACTIVE' WHERE email LIKE 'demo.%@ptutor.local';
COMMIT;
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--print", action="store_true", help="print the SQL instead of running it")
    parser.add_argument("--disable", action="store_true", help="close the demo postings and deactivate the demo accounts")
    parser.add_argument("--container", default="ptutor-postgres")
    parser.add_argument("--database", default="ptutor")
    parser.add_argument("--user", default="postgres")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    sql = DISABLE_SQL if args.disable else build_sql()
    if args.print:
        print(sql)
        return 0
    result = subprocess.run(
        ["docker", "exec", "-i", args.container, "psql", "-v", "ON_ERROR_STOP=1", "-q", "-U", args.user, "-d", args.database],
        input=sql.encode("utf-8"), capture_output=True)
    if result.returncode != 0:
        print(result.stderr.decode("utf-8", errors="replace"), file=sys.stderr)
        return result.returncode
    if args.disable:
        print("Demo postings closed and demo accounts deactivated.")
    else:
        print(f"Demo data is in place: {len(TUTORS)} tutors, {len(TUTORS) * 2} teaching postings, "
              f"{len(STUDENTS)} students, {len(STUDYING)} studying postings.")
        print(f"Log in with demo.student1@ptutor.local ... demo.student{len(STUDENTS)}@ptutor.local or "
              f"demo.tutor01@ptutor.local ... demo.tutor{len(TUTORS):02d}@ptutor.local, password {DEMO_PASSWORD}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
