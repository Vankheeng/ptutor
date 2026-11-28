"""Generate repeatable, Vietnamese local test data for the dev schema.

The SQL is printed by default. Pass --load only for an isolated local database.
No network calls or real payment credentials are used; Gmail-format addresses are synthetic.
"""

import argparse
import base64
import hashlib
import json
import os
import random
import subprocess
import sys
import unicodedata
import uuid
from pathlib import Path

NS = uuid.UUID("fd2bb787-9ab1-4fa2-8d46-40ac71294a6e")
HASH = "$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG"
FAMILIES = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Ngô", "Dương", "Lý"]
MALE = ["Minh", "Huy", "Quân", "Đức", "Khang", "Long", "Phong", "Tuấn", "Bình", "Sơn", "Khải", "Nam"]
FEMALE = ["Linh", "Trang", "Hà", "Thảo", "Ngọc", "Mai", "Vy", "Hương", "Yến", "Anh", "Nhi", "Phương"]
MIDDLE_MALE = ["Văn", "Minh", "Quang", "Anh", "Hữu", "Đức"]
MIDDLE_FEMALE = ["Thị", "Ngọc", "Thu", "Khánh", "Phương", "Thanh"]
DISTRICTS = ["Cầu Giấy", "Nghĩa Đô", "Yên Hoà", "Ba Đình", "Giảng Võ", "Ngọc Hà", "Long Biên", "Bồ Đề", "Tây Hồ", "Phú Thượng", "Hoàn Kiếm", "Cửa Nam"]
TOPICS = {
    "Toán": [(6, "phân số và số nguyên"), (8, "phân tích đa thức, phương trình"), (9, "hàm số và hình học ôn thi vào 10"), (11, "lượng giác và dãy số"), (12, "khảo sát hàm số, xác suất")],
    "Tiếng Anh": [(6, "từ vựng và phát âm"), (8, "ngữ pháp, đọc hiểu"), (9, "luyện đề vào 10"), (11, "giao tiếp và viết luận"), (12, "ôn thi tốt nghiệp THPT")],
    "Ngữ văn": [(6, "đọc hiểu và viết đoạn văn"), (8, "nghị luận xã hội"), (9, "ôn thi vào 10"), (11, "phân tích tác phẩm"), (12, "nghị luận văn học")],
    "Vật lý": [(7, "chuyển động và lực"), (9, "điện học, quang học"), (10, "động học và năng lượng"), (12, "dao động và sóng")],
    "Hóa học": [(8, "phương trình hóa học và mol"), (9, "dung dịch và kim loại"), (11, "hóa hữu cơ"), (12, "ôn thi tốt nghiệp")],
    "Sinh học": [(8, "cơ thể người"), (9, "di truyền học"), (11, "sinh học tế bào"), (12, "ôn thi tốt nghiệp")],
    "Tin học": [(6, "tư duy thuật toán"), (8, "lập trình Python cơ bản"), (10, "cấu trúc dữ liệu"), (12, "ôn thi học sinh giỏi")],
}
LEGACY_SUBJECTS = tuple(TOPICS)
LEGACY_TOPICS = {name: tuple(rows) for name, rows in TOPICS.items()}
TOPICS.update({
    "Âm nhạc": [(1, "nhịp và giai điệu"), (3, "đọc nốt nhạc"), (6, "hát và cảm thụ âm nhạc"), (9, "lý thuyết âm nhạc")],
    "Công nghệ": [(3, "đồ dùng trong gia đình"), (6, "thiết kế và vật liệu"), (9, "định hướng nghề nghiệp"), (12, "công nghệ trong sản xuất")],
    "Địa lý": [(6, "bản đồ và khí hậu"), (9, "địa lý kinh tế Việt Nam"), (12, "địa lý vùng kinh tế")],
    "Giáo dục công dân": [(6, "quyền và trách nhiệm"), (9, "pháp luật trong đời sống"), (12, "kinh tế và pháp luật")],
    "Giáo dục thể chất": [(1, "vận động cơ bản"), (6, "kỹ thuật chạy và phối hợp"), (10, "sức bền và thể lực")],
    "Khoa học tự nhiên": [(6, "chất và sự biến đổi"), (8, "cơ thể sống và năng lượng"), (9, "điện và sinh vật")],
    "Khoa học xã hội": [(6, "lịch sử và địa lý địa phương"), (8, "đọc bản đồ và sự kiện lịch sử"), (9, "địa lý dân cư")],
    "Lịch sử": [(6, "các nền văn minh cổ"), (9, "lịch sử Việt Nam hiện đại"), (12, "lịch sử thế giới hiện đại")],
    "Mỹ thuật": [(1, "màu sắc và hình khối"), (5, "bố cục và vẽ tranh"), (9, "thiết kế và tạo hình")],
})
TOPICS["Toán"] = [(1, "phép cộng và phép trừ"), (3, "nhân chia và bài toán có lời văn"),
                   (5, "phân số và số thập phân")] + TOPICS["Toán"]
TOPICS["Tiếng Anh"] = [(1, "từ vựng và nghe nói cơ bản"), (3, "mẫu câu giao tiếp"),
                        (5, "đọc hiểu và ngữ pháp cơ bản")] + TOPICS["Tiếng Anh"]
TOPICS["Ngữ văn"] = [(1, "đọc và viết câu"), (3, "đọc hiểu và viết đoạn"),
                     (5, "cảm thụ văn học và tập làm văn")] + TOPICS["Ngữ văn"]
TOPICS["Tin học"] = [(3, "sử dụng máy tính an toàn"), (5, "soạn thảo và trình bày")]+TOPICS["Tin học"]
SUBJECT_GRADES = {
    "Toán": range(1, 13), "Tiếng Anh": range(1, 13), "Ngữ văn": range(1, 13),
    "Giáo dục thể chất": range(1, 13), "Âm nhạc": range(1, 10), "Mỹ thuật": range(1, 10),
    "Tin học": range(3, 13), "Công nghệ": range(3, 13),
    "Khoa học tự nhiên": range(6, 10), "Khoa học xã hội": range(6, 10),
    "Lịch sử": range(6, 13), "Địa lý": range(6, 13),
    "Giáo dục công dân": range(6, 13), "Vật lý": range(8, 13),
    "Hóa học": range(8, 13), "Sinh học": range(8, 13),
}
EXPANSION_SUBJECTS = tuple(SUBJECT_GRADES)
EXPANSION_PER_SUBJECT = 20
EXPANSION_COUNT = len(EXPANSION_SUBJECTS) * EXPANSION_PER_SUBJECT
EXPECTED_NARRATIVES = 438 + EXPANSION_COUNT * 7
# Wards from the project's province/district catalog. The province disambiguates repeated ward names.
EXPANSION_AREAS = (
    ("Hà Nội", "Cầu Giấy"), ("Hà Nội", "Yên Hoà"), ("Hà Nội", "Bạch Mai"),
    ("Hồ Chí Minh", "An Đông"), ("Hồ Chí Minh", "An Lạc"), ("Hồ Chí Minh", "An Phú"),
    ("Đà Nẵng", "An Hải"), ("Đà Nẵng", "An Khê"), ("Đà Nẵng", "Bà Nà"),
    ("Hải Phòng", "Hồng Bàng"), ("Hải Phòng", "An Dương"), ("Hải Phòng", "An Biên"),
    ("Cần Thơ", "An Bình"), ("Cần Thơ", "Cái Khế"), ("Cần Thơ", "Cái Răng"),
    ("Bắc Ninh", "Từ Sơn"), ("Bắc Ninh", "Bắc Giang"), ("Bắc Ninh", "An Lạc"),
    ("Nghệ An", "Bạch Ngọc"), ("Nghệ An", "An Châu"), ("Nghệ An", "Anh Sơn"),
    ("Huế", "An Cựu"), ("Huế", "A Lưới 1"), ("Huế", "Chân Mây - Lăng Cô"),
)
METHODS = [
    "Mỗi buổi kiểm tra phần cũ, giải thích bằng ví dụ rồi mới cho con tự làm bài.",
    "Chia kiến thức thành từng chủ đề nhỏ; cuối tuần có bài kiểm tra và nhận xét cụ thể.",
    "Ưu tiên tìm chỗ con chưa hiểu, giảng lại nền tảng trước khi luyện đề.",
    "Dùng sơ đồ và bài tập gần với chương trình ở trường, không thúc ép học quá nhanh.",
    "Cho học sinh trình bày cách nghĩ, sửa lỗi ngay trong lúc làm và giao bài vừa sức.",
]
REVIEW_DETAILS = [
    "Con bớt ngại hỏi khi gặp bài khó.", "Gia đình nhận được nhận xét sau mỗi tuần học.",
    "Phần bài tập trên lớp đã được giải thích lại khá rõ.", "Lịch học được thống nhất trước nên dễ sắp xếp.",
    "Con biết tự kiểm tra lại lời giải trước khi nộp bài.", "Các ví dụ sát với phần con đang học ở trường.",
    "Thầy cô chỉ ra lỗi trình bày mà trước đây con thường bỏ qua.",
]
APPLICATION_MESSAGES = [
    "Em đang học lớp {level} và muốn ôn lại {topic} trong môn {subject}. Gia đình có thể trao đổi lịch học buổi tối ạ.",
    "Gia đình đang tìm người kèm {subject} cho con lớp {level}; phần {topic} con còn chưa chắc. Mong thầy cô cho biết lịch còn trống.",
    "Con học {subject} lớp {level} nhưng làm bài về {topic} còn chậm. Em muốn học đều hai buổi mỗi tuần nếu lịch phù hợp.",
    "Em muốn được hướng dẫn kỹ phần {topic} của {subject} lớp {level}. Nhà mình sắp xếp được các buổi tối trong tuần.",
    "Lớp {level} năm nay có phần {topic} con tiếp thu chưa tốt. Gia đình muốn trao đổi cách kèm {subject} trước khi bắt đầu.",
    "Em đang cần củng cố {subject} lớp {level}, nhất là {topic}. Thầy cô có thể nhận lịch học sau giờ học ở trường không ạ?",
    "Gia đình thấy cách dạy của thầy cô phù hợp với nhu cầu ôn {topic} của con lớp {level}. Con có thể học {subject} vào buổi tối.",
    "Con muốn học lại nền tảng {subject} lớp {level} trước kỳ kiểm tra. Phần {topic} hiện là chỗ con hay sai nhất.",
    "Em đang tìm người giúp em tự làm được bài {topic} trong môn {subject} lớp {level}. Gia đình xin trao đổi thêm về lịch học.",
    "Gia đình ưu tiên học chắc {topic} thay vì luyện đề quá nhanh. Con đang học {subject} lớp {level}, có thể bắt đầu trong tuần này.",
]
OFFER_MESSAGES = [
    "Tôi đang nhận kèm {subject} lớp {level} và có thể bắt đầu với phần {topic}. Nếu gia đình thuận tiện, mình trao đổi lịch trước nhé.",
    "Nhu cầu ôn {topic} của con khá phù hợp với nội dung tôi đang dạy. Tôi có thể nhận lớp {subject} này vào buổi tối.",
    "Tôi thường kiểm tra kiến thức nền trước khi cho học sinh làm bài {topic}. Gia đình có thể cho tôi biết lịch học mong muốn không?",
    "Với {subject} lớp {level}, tôi muốn xem con đang vướng ở đâu trong phần {topic} rồi mới lên bài. Tôi còn lịch trong tuần.",
    "Tôi có thể hỗ trợ con học {subject} lớp {level}, nhất là nội dung {topic}. Mình có thể trao đổi một buổi học đầu để thống nhất cách học.",
    "Lớp {subject} này phù hợp chuyên môn của tôi. Nếu con cần ôn {topic}, tôi sẽ điều chỉnh bài tập theo phần còn yếu.",
    "Tôi hiện dạy {subject} cho học sinh lớp {level}. Phần {topic} có thể chia thành từng buổi ngắn để con dễ theo kịp.",
    "Gia đình có thể gửi thêm phần bài con đang học ở trường không? Tôi nhận kèm {subject} lớp {level} và có thể chuẩn bị bài về {topic}.",
    "Tôi muốn trao đổi mục tiêu học của con trước khi nhận lớp. Nội dung {topic} trong {subject} lớp {level} là phần tôi có thể hỗ trợ.",
    "Nếu lịch học của gia đình phù hợp, tôi có thể nhận dạy {subject} lớp {level}. Trước mắt nên củng cố {topic} cho con.",
]
LESSON_NOTES = [
    "Đã ôn {topic} và chữa các bài con làm sai ở nhà.",
    "Con làm được phần cơ bản của {topic}; buổi sau sẽ luyện thêm bài vận dụng.",
    "Dành thời gian kiểm tra kiến thức cũ rồi thực hành bài về {topic}.",
    "Con còn lúng túng ở {topic}, cần xem lại ví dụ đã ghi trong vở.",
    "Đã chữa bài kiểm tra trên lớp và giao thêm bài tập về {topic}.",
    "Buổi học tập trung vào cách trình bày khi giải bài {topic}.",
    "Con chủ động hỏi nhiều hơn; tiếp tục ôn {topic} vào buổi tới.",
    "Đã hoàn thành phần ôn {topic} theo kế hoạch của tuần này.",
]
TEACHING_TITLES = [
    "Nhận kèm {subject} lớp {grade}: {focus} ({place})",
    "Gia sư {subject} lớp {grade}, trọng tâm {focus} - {place}",
    "Củng cố {focus} môn {subject} lớp {grade} ({place})",
    "Dạy {subject} lớp {grade}, ôn phần {focus} - {place}",
    "Ôn {focus} môn {subject} cho học sinh lớp {grade} ({place})",
    "Nhận dạy {subject} lớp {grade} về {focus} - {place}",
    "Học chắc {focus} cùng gia sư {subject} lớp {grade} ({place})",
    "Kèm {subject} lớp {grade}, ưu tiên {focus} - {place}",
    "Gia sư nhận dạy {subject} lớp {grade}: {focus} ({place})",
    "Luyện {focus} môn {subject} cho lớp {grade} - {place}",
    "Dạy kèm {subject} lớp {grade}, tập trung {focus} ({place})",
    "Đồng hành học {subject} lớp {grade}: {focus} - {place}",
]
STUDYING_TITLES = [
    "Tìm gia sư {subject} lớp {grade}: {focus} ({place})",
    "Cần người kèm {subject} lớp {grade} về {focus} - {place}",
    "Học {subject} lớp {grade}, muốn ôn {focus} ({place})",
    "Gia đình tìm gia sư {subject} lớp {grade} - {focus}, {place}",
    "Cần củng cố {focus} môn {subject} lớp {grade} ({place})",
    "Tìm thầy cô dạy {subject} lớp {grade}: {focus} - {place}",
    "Ôn {focus} môn {subject} lớp {grade} cùng gia sư ({place})",
    "Con học lớp {grade}, cần kèm {subject} phần {focus} - {place}",
    "Tìm người hướng dẫn {focus} môn {subject} lớp {grade} ({place})",
    "Cần học thêm {subject} lớp {grade}, trọng tâm {focus} - {place}",
    "Gia sư {subject} lớp {grade} cho phần {focus} ({place})",
    "Tìm gia sư kèm riêng {subject} lớp {grade}: {focus} - {place}",
]
NEEDS = [
    "Con làm bài trên lớp còn chậm, cần người kiên nhẫn kèm từng bước.",
    "Muốn củng cố kiến thức nền trước khi bắt đầu luyện đề thường xuyên.",
    "Con học khá nhưng hay mất điểm ở phần trình bày và bài vận dụng.",
    "Gia đình cần lịch học đều hai buổi mỗi tuần, có báo lại tiến độ sau buổi học.",
    "Con ngại hỏi trên lớp nên cần học riêng để mạnh dạn trao đổi hơn.",
    "Đang chuẩn bị cho kỳ thi tới, muốn luyện dạng bài còn yếu và quản lý thời gian làm bài.",
]
REVIEW_TEXT = [
    "Cô giải thích dễ hiểu, con đã chủ động làm bài hơn sau vài tuần.",
    "Thầy đúng giờ và báo lại phần con cần ôn thêm sau mỗi buổi.",
    "Bài tập được giao vừa sức, cách sửa bài cụ thể nên con tiến bộ rõ.",
    "Lịch học ổn định, trao đổi với gia đình khá đầy đủ.",
    "Con hợp cách dạy, mong tiếp tục học trong học kỳ sau.",
]
SUPPORT_QUESTIONS = [
    "Tôi đã chuyển học phí nhưng trang hợp đồng vẫn báo chưa thanh toán, nhờ kiểm tra giúp.",
    "Gia sư xin dời buổi học tối nay, tôi cần xác nhận lịch mới ở đâu?",
    "Tôi muốn đổi quận nhận lớp trực tiếp trên tin đăng hiện tại.",
    "Tôi đã nộp chứng chỉ hôm qua, bao lâu nữa mới có kết quả duyệt?",
    "Ví đã trừ tiền đăng tin nhưng yêu cầu vẫn ở trạng thái chờ duyệt.",
    "Tôi cần bổ sung minh chứng cho khiếu nại buổi học tuần trước.",
    "Gia đình muốn gia hạn hợp đồng thêm một tháng với cùng lịch học.",
    "Tôi đã bấm xác nhận buổi học nhầm, có thể được hỗ trợ kiểm tra lại không?",
    "Yêu cầu rút tiền đã được duyệt nhưng tài khoản ngân hàng chưa nhận được.",
    "Tôi muốn biết vì sao tin tìm gia sư bị từ chối để sửa lại nội dung.",
    "Tôi không thấy đề nghị dạy vừa được thông báo trên trang cá nhân.",
    "Học viên đồng ý học thử nhưng tôi chưa thấy hợp đồng cần ký.",
]
SUPPORT_ROLES = [
    "STUDENT", "STUDENT", "TUTOR", "TUTOR", "STUDENT", "STUDENT",
    "STUDENT", "STUDENT", "TUTOR", "STUDENT", "TUTOR", "TUTOR",
]
SUPPORT_ANSWERS = [
    "Chúng tôi đã đối chiếu giao dịch và cập nhật trạng thái thanh toán trong hợp đồng.",
    "Bạn mở lịch buổi học trong hợp đồng, chọn buổi cần dời và xác nhận thời gian đã thống nhất với gia sư.",
    "Bạn vào tin dạy đang mở, chỉnh khu vực nhận lớp rồi lưu. Các đề nghị đã gửi trước đó vẫn giữ nguyên.",
    "Chứng chỉ của bạn đang trong hàng chờ duyệt. Khi có kết quả, hệ thống sẽ gửi thông báo trong tài khoản.",
    "Khoản phí đã được ghi nhận. Tin sẽ hiển thị sau khi nhân viên hoàn tất bước duyệt nội dung.",
    "Bạn mở khiếu nại đang xử lý và tải thêm minh chứng tại mục Bổ sung thông tin.",
    "Hai bên có thể tạo đề nghị gia hạn trong trang hợp đồng; lịch và học phí cần được xác nhận lại trước khi ký.",
    "Chúng tôi đã ghi nhận thao tác xác nhận nhầm và chuyển buổi học sang bộ phận phụ trách để kiểm tra.",
    "Yêu cầu rút tiền đã được chuyển khoản. Bạn vui lòng đối chiếu mã giao dịch trong lịch sử rút tiền.",
    "Lý do từ chối đã được cập nhật trong chi tiết tin. Bạn có thể sửa nội dung và gửi duyệt lại.",
    "Đề nghị dạy hiện nằm trong mục Đề nghị của tôi. Vui lòng kiểm tra cả bộ lọc trạng thái.",
    "Hợp đồng được tạo sau khi hai bên xác nhận đề nghị. Bạn có thể xem tiến độ trong trang chi tiết đề nghị.",
]
STUDENT_QUERIES = [
    "Tìm gia sư {subject} lớp {level} học buổi tối",
    "Con cần ôn lại {subject} lớp {level}, ưu tiên người kiên nhẫn",
    "Gia sư {subject} gần nhà, hai buổi một tuần",
    "Muốn học {subject} lớp {level} theo chương trình ở trường",
    "Tìm thầy cô kèm {subject} và báo tiến độ sau mỗi buổi",
    "Con yếu phần {subject}, cần học lại từ cơ bản",
    "Gia sư {subject} lớp {level} có thể dạy thứ bảy",
    "Cần luyện bài tập {subject} trước kỳ kiểm tra",
    "Tìm gia sư {subject} dạy trực tuyến buổi tối",
    "Muốn so sánh thêm gia sư {subject} lớp {level}",
]
TUTOR_QUERIES = [
    "Tìm học viên {subject} lớp {level} gần khu vực nhận dạy",
    "Có lớp {subject} buổi tối nào phù hợp lịch của tôi không",
    "Học viên cần củng cố {subject} cấp hai hoặc cấp ba",
    "Tìm lớp {subject} có thể học hai buổi mỗi tuần",
    "Ưu tiên học viên {subject} đang cần ôn kiểm tra",
    "Có nhu cầu học {subject} trực tuyến trong tuần không",
    "Tìm học viên {subject} ở gần khu vực tôi dạy",
    "Lớp {subject} nào còn nhận gia sư",
    "Muốn xem thêm nhu cầu học {subject} mới đăng",
    "Tìm học viên {subject} phù hợp phương pháp của tôi",
]


def uid(*parts):
    return str(uuid.uuid5(NS, "/".join(map(str, parts))))


def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def sql_id(table, *parts):
    return q(uid(table, *parts))


def catalog(table, name):
    return f"(SELECT id FROM {table} WHERE name={q(name)} ORDER BY id LIMIT 1)"


def grade(level):
    return f"(SELECT id FROM grades WHERE level={level} ORDER BY id LIMIT 1)"


def district(name, province="Hà Nội"):
    return ("(SELECT d.id FROM districts d JOIN provinces p ON p.id=d.province_id "
            f"WHERE p.name={q(province)} AND d.name={q(name)} ORDER BY d.id LIMIT 1)")


def insert(table, columns, values):
    return f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({', '.join(map(str, values))}) ON CONFLICT DO NOTHING;"


def wallet_credit(user, amount, transaction_id, purpose, reference_type, reference_id, description, occurred_at):
    """Insert one completed credit and advance its wallet only on the first seed run."""
    return f"""
        WITH added AS (
            INSERT INTO wallet_transactions
                (id, wallet_id, transaction_type, purpose, amount, balance_after,
                 pending_balance_after, reference_type, reference_id, description,
                 idempotency_key, status, created_at)
            SELECT {q(transaction_id)}::uuid, w.id, 'CREDIT', {q(purpose)}, {amount},
                   w.balance + {amount}, w.pending_balance, {q(reference_type)},
                   {q(reference_id)}::uuid, {q(description)},
                   {q('seed-credit-' + transaction_id)}, 'COMPLETED', {occurred_at}
            FROM wallets w WHERE w.user_id = {q(user)}::uuid
            ON CONFLICT DO NOTHING RETURNING wallet_id, amount
        )
        UPDATE wallets w SET balance = w.balance + added.amount, updated_at = CURRENT_TIMESTAMP
        FROM added WHERE w.id = added.wallet_id;
    """


def refresh(table, row_id, **values):
    """Refresh only deterministic seed rows when the generator is run again."""
    assignments = ", ".join(f"{column}={q(value)}" for column, value in values.items())
    return f"UPDATE {table} SET {assignments} WHERE id={q(row_id)};"


def seed_email(full_name, birth_year):
    """Natural Gmail syntax for isolated demo accounts; local SMTP must stay disabled."""
    suffix = 100000 + uuid.uuid5(NS, f"demo-email/{full_name}/{birth_year}").int % 900000
    local = "".join(bank_holder(full_name).lower().split()) + str(suffix)
    return f"{local}@gmail.com"


def expanded_personas(role):
    """Every valid subject/grade gets both modes, with extra supply per combination."""
    for subject_pos, subject in enumerate(EXPANSION_SUBJECTS):
        grades = tuple(SUBJECT_GRADES[subject])
        for cohort in range(EXPANSION_PER_SUBJECT):
            ordinal = subject_pos * EXPANSION_PER_SUBJECT + cohort
            area = EXPANSION_AREAS[(subject_pos * 7 + cohort * 5) % len(EXPANSION_AREAS)]
            yield role, ordinal, subject, grades[cohort % len(grades)], area


def expanded_name(role, ordinal):
    old_names = set()
    for old_role, count in (("tutor", 84), ("student", 64)):
        for i in range(count):
            index = i if old_role == "tutor" else i + 85
            given = (MALE if i % 2 == 0 else FEMALE)[(index * 7 + index // 12) % 12]
            middle = (MIDDLE_MALE if i % 2 == 0 else MIDDLE_FEMALE)[(index // 3) % 6]
            surname = FAMILIES[(index * 5 + index // 12) % 12]
            old_names.add(f"{surname} {middle} {given}")
    male = ordinal % 2 == 0
    given_names = MALE if male else FEMALE
    middle_names = MIDDLE_MALE if male else MIDDLE_FEMALE
    pool = [f"{family} {middle} {given}" for family in FAMILIES
            for middle in middle_names for given in given_names]
    random.Random(1831 if male else 4729).shuffle(pool)
    pool = [name for name in pool if name not in old_names]
    slot = ordinal // 2 + (0 if role == "tutor" else len(EXPANSION_SUBJECTS) * EXPANSION_PER_SUBJECT // 2)
    return pool[slot]


def expanded_postings(role, ordinal, subject, level):
    """First two postings guarantee online and in-person results for the persona's grade."""
    grades = tuple(SUBJECT_GRADES[subject])
    for j in range(3 if role == "tutor" else 2):
        post_level = grades[(grades.index(level) + 1) % len(grades)] if j == 2 else level
        mode = ("ONLINE", "OFFLINE", "ONLINE" if ordinal % 2 else "OFFLINE")[j]
        yield j, post_level, mode, topic_for_grade(subject, post_level)


def application_message(i, j, level, subject):
    topic = topic_for_grade(subject, level)
    return APPLICATION_MESSAGES[(i*3+j) % len(APPLICATION_MESSAGES)].format(
        level=level, subject=subject, topic=topic)


def offer_message(i, j, level, subject):
    topic = topic_for_grade(subject, level)
    return OFFER_MESSAGES[(i*3+j) % len(OFFER_MESSAGES)].format(
        level=level, subject=subject, topic=topic)


def schedule_text(days, start, end):
    names = {1: "Thứ 2", 2: "Thứ 3", 3: "Thứ 4", 4: "Thứ 5",
             5: "Thứ 6", 6: "Thứ 7", 7: "Chủ nhật"}
    return f"{names[days[0]]} và {names[days[1]]}, {start}–{end}"


def teaching_title(i, j, subject, level, focus, mode, district_name):
    place = "trực tuyến" if mode == "ONLINE" else f"tại {district_name}"
    return TEACHING_TITLES[(i*7+j*3)%len(TEACHING_TITLES)].format(
        subject=subject, grade=level, focus=focus, place=place)


def studying_title(i, j, subject, level, focus, mode, district_name):
    place = "trực tuyến" if mode == "ONLINE" else f"tại {district_name}"
    return STUDYING_TITLES[(i*5+j*3)%len(STUDYING_TITLES)].format(
        subject=subject, grade=level, focus=focus, place=place)


def topic_for_grade(subject, level):
    """Pick a curriculum topic from the nearest grade, never a distant grade."""
    return min(TOPICS[subject], key=lambda row: (abs(row[0] - level), row[0]))[1]


def bank_key():
    encoded=os.environ.get("BANK_ACCOUNT_ENCRYPTION_KEY") or os.environ.get("CITIZEN_ID_ENCRYPTION_KEY")
    if not encoded:
        env_file=Path(__file__).resolve().parents[2]/".env"
        if env_file.exists():
            settings={line.split("=",1)[0]:line.split("=",1)[1].strip().strip('"').strip("'")
                      for line in env_file.read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.lstrip().startswith("#")}
            encoded=settings.get("BANK_ACCOUNT_ENCRYPTION_KEY") or settings.get("CITIZEN_ID_ENCRYPTION_KEY")
    if not encoded:
        raise ValueError("--finance requires BANK_ACCOUNT_ENCRYPTION_KEY (or CITIZEN_ID_ENCRYPTION_KEY)")
    key=base64.b64decode(encoded)
    if len(key)!=32:
        raise ValueError("bank account encryption key must decode to 32 bytes")
    return hashlib.sha256(b"bank-account-number\0"+key).digest()


def encrypted_account(account, key):
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    iv=os.urandom(12)
    return base64.urlsafe_b64encode(iv+AESGCM(key).encrypt(iv,account.encode("utf-8"),None)).rstrip(b"=").decode("ascii")


def bank_holder(name):
    return "".join(c for c in unicodedata.normalize("NFD",name.replace("Đ","D")) if unicodedata.category(c)!="Mn").upper()


def generate(include_ai=False, include_chatbot=False, finance_key=None):
    rng = random.Random(27011)
    out = ["BEGIN;", "SET LOCAL client_encoding = 'UTF8';"]
    refreshes = []
    changed_embedding_posts = []
    seeded_users = []
    corpus_path = Path(__file__).with_name("narratives.json")
    narratives = json.loads(corpus_path.read_text(encoding="utf-8"))
    if len(narratives) != EXPECTED_NARRATIVES:
        raise ValueError(f"narratives.json must contain all {EXPECTED_NARRATIVES} authored records before loading")
    for name in TOPICS:
        out.append(insert("subjects",["id","name","status"],[sql_id("subject",name),q(name),q("ACTIVE")]))
    for level in range(1,13):
        out.append(insert("grades",["id","name","level","status"],[sql_id("grade",level),q(f"Lớp {level}"),level,q("ACTIVE")]))
    for index,(surname,given,function) in enumerate((("Trần Thu","Hà","CUSTOMER_SERVICE"),("Nguyễn Minh","Quân","ACCOUNTANT"))):
        key=f"staff-{index}"
        staff_user = uid("user", key)
        seeded_users.append(staff_user)
        staff_email = ("hotro@ptutor.local", "ketoan@ptutor.local")[index]
        out.append(insert("users",["id","citizen_id","email","password","first_name","last_name","status"],
            [q(staff_user),q(f"079900000{index:03d}"),q(staff_email),q(HASH),q(surname),q(given),q("ACTIVE")]))
        refreshes.append(refresh("users", staff_user, email=staff_email))
        out.append(insert("employees",["id","user_id","role","job_function"],
            [sql_id("employee",key),sql_id("user",key),2,q(function)]))
    tutors, students, teaching, studying, applications, offers, tutor_names = [], [], [], [], [], [], []
    contract_scenarios = []
    for role, count in (("tutor", 84), ("student", 64)):
        for i in range(count):
            gender = "MALE" if (i % 2 == 0) else "FEMALE"
            name_index = i if role == "tutor" else i + 85
            given = (MALE if gender == "MALE" else FEMALE)[(name_index * 7 + name_index // 12) % 12]
            middle = (MIDDLE_MALE if gender == "MALE" else MIDDLE_FEMALE)[(name_index // 3) % 6]
            surname = FAMILIES[(name_index * 5 + name_index // 12) % 12]
            home = DISTRICTS[(i * 7) % len(DISTRICTS)]
            key = f"{role}-{i:03d}"
            user = uid("user", key)
            seeded_users.append(user)
            level = 6 + (i * 5) % 7 if role == "student" else None
            birth_year = 2026 - (level + 6) if level is not None else 1983 + i % 19
            birth_date = f"{birth_year}-{1+i%12:02d}-{5+(i*7)%23:02d}"
            full_name = surname + " " + middle + " " + given
            email = seed_email(full_name, birth_year)
            status="BLOCKED" if i==count-1 else "INACTIVE" if i==count-2 else "ACTIVE"
            account_status=status
            columns=["id","district_id","citizen_id","email","password","first_name","last_name","gender","status","date_of_birth","created_at"]
            citizen_number=(100000000 if role=="tutor" else 200000000)+i
            values=[q(user),district(home),q(f"079{citizen_number:09d}"),q(email),q(HASH),q(surname+' '+middle),q(given),q(gender),q(status),q(birth_date),f"CURRENT_TIMESTAMP - INTERVAL '{100+i%200} days'"]
            if status=="BLOCKED":
                columns += ["suspension_type","suspension_reason","suspended_at","suspended_until","suspended_by_user_id","suspension_count"]
                values += [q("TEMPORARY"),q("Tài khoản tạm khóa để xác minh phản ánh về lịch học."),"CURRENT_TIMESTAMP - INTERVAL '2 days'","CURRENT_TIMESTAMP + INTERVAL '12 days'","(SELECT user_id FROM employees WHERE role=1 ORDER BY id LIMIT 1)",1]
            out.append(insert("users",columns,values))
            refreshes.append(refresh("users", user, email=email, first_name=surname+" "+middle,
                                     last_name=given, date_of_birth=birth_date))
            if role == "tutor":
                subject = LEGACY_SUBJECTS[i % len(LEGACY_SUBJECTS)]
                level, topic = LEGACY_TOPICS[subject][(i // len(LEGACY_SUBJECTS)) % len(LEGACY_TOPICS[subject])]
                method = METHODS[i % len(METHODS)]
                exp = 1 + (i * 7) % 13
                profile = uid("tutor", i)
                education = ["Đại học Sư phạm Hà Nội", "Đại học Khoa học Tự nhiên", "Đại học Ngoại ngữ", "Đại học Bách khoa Hà Nội"][i % 4]
                intro = f"Tôi dạy {subject} lớp {max(6,level-1)}–{min(12,level+1)} được {exp} năm, chú trọng {topic}. {method}"
                profile_text = narratives.get(profile, {})
                intro = profile_text.get("introduction", intro)
                method = profile_text.get("teaching_methodology", method)
                reviewer="(SELECT id FROM employees WHERE role=1 ORDER BY id LIMIT 1)"
                out.append(insert("tutors", ["id","user_id","introduction","experience_years","education","teaching_methodology","strength_subjects","target_student_type","profile_status","profile_reviewed_by","profile_reviewed_at"],
                    [q(profile),q(user),q(intro),exp,q(education),q(method),q(subject),q("Học sinh cần củng cố nền tảng" if i%3 else "Học sinh muốn luyện thi"),q("PENDING" if i%17==0 else "VERIFIED"),"NULL" if i%17==0 else reviewer,"NULL" if i%17==0 else "CURRENT_TIMESTAMP - INTERVAL '73 days'"]))
                refreshes.append(refresh("tutors", profile, introduction=intro, teaching_methodology=method))
                tutors.append((profile, user, subject, level, home))
                tutor_names.append(surname+" "+middle+" "+given)
                if i%5 == 0:
                    cert_status="REJECTED" if i%15==0 else "PENDING" if i%10==0 else "VERIFIED"
                    out.append(insert("certificates",["id","tutor_id","name","issuing_organization","issue_date","status","rejection_reason","reviewed_by","reviewed_at"],
                        [sql_id("cert",i),q(profile),q("Bằng cử nhân ngành liên quan"),q(education),q("2021-06-30"),q(cert_status),q("Ảnh chứng chỉ chưa đọc rõ thông tin, vui lòng tải lại") if cert_status=="REJECTED" else "NULL","NULL" if cert_status=="PENDING" else "(SELECT id FROM employees WHERE role=1 ORDER BY id LIMIT 1)","NULL" if cert_status=="PENDING" else "CURRENT_TIMESTAMP - INTERVAL '5 days'"]))
                for j in range(2):
                    tlevel, ttopic = LEGACY_TOPICS[subject][(i+j)%len(LEGACY_TOPICS[subject])]
                    posting = uid("teaching",i,j)
                    mode = "ONLINE" if (i+j)%4==0 else "OFFLINE"
                    price = 140000+((i+j)%8)*20000
                    desc = f"Tập trung {ttopic}. {method} Có thể điều chỉnh tốc độ theo tình hình của học sinh."
                    posting_text = narratives.get(posting, {})
                    title = teaching_title(i, j, subject, tlevel, ttopic, mode, home)
                    title = posting_text.get("title", title)
                    if "title" in posting_text:
                        changed_embedding_posts.append(posting)
                    desc = posting_text.get("description", desc)
                    days = (2+(i+j)%4, 6+(i+j)%2)
                    start, end = ("18:00", "20:00") if i%2 else ("18:30", "20:30")
                    preferred = schedule_text(days, start, end)
                    status = "CLOSED" if account_status!="ACTIVE" else "PENDING_REVIEW" if i%17==0 or (i%19==0 and j==1) else "REJECTED" if i%23==0 and j==1 else "OPEN" if i%11 else "CLOSED"
                    out.append(insert("teaching_requests",["id","tutor_id","subject_id","title","description","quantity","expected_price","teaching_mode","preferred_schedule","status","reviewed_at","created_at"],
                        [q(posting),q(profile),catalog("subjects",subject),q(title),q(desc),1+i%3,price,q(mode),q(preferred),q(status),"NULL" if status=="PENDING_REVIEW" else "CURRENT_TIMESTAMP - INTERVAL '72 days'",f"CURRENT_TIMESTAMP - INTERVAL '{75+i%75} days'"]))
                    refreshes.append(refresh("teaching_requests", posting, title=title, description=desc,
                                             preferred_schedule=preferred))
                    out.append(insert("grade_teaching_requests",["id","grade_id","teaching_request_id"],[sql_id("tr-grade",i,j),grade(tlevel),q(posting)]))
                    if mode=="OFFLINE":
                        out.append(insert("teaching_request_districts",["id","teaching_request_id","district_id"],[sql_id("tr-district",i,j),q(posting),district(home)]))
                    for k, day in enumerate(days):
                        slot_id = uid("tr-slot", i, j, k)
                        out.append(insert("teaching_request_availabilities",["id","teaching_request_id","day_of_week","start_time","end_time"],[q(slot_id),q(posting),day,q(start),q(end)]))
                        refreshes.append(refresh("teaching_request_availabilities", slot_id,
                                                 day_of_week=day, start_time=start, end_time=end))
                    teaching.append((posting, profile, subject, tlevel, price, mode, status))
            else:
                profile = uid("student",i)
                need = NEEDS[i%len(NEEDS)]
                subject = LEGACY_SUBJECTS[i % len(LEGACY_SUBJECTS)]
                focus = topic_for_grade(subject, level)
                profile_text = narratives.get(profile, {})
                student_intro = profile_text.get("introduction", f"Học sinh lớp {level} cần gia sư {subject}. {need}")
                weak_points = profile_text.get("weak_points", f"Con cần ôn lại {focus}.")
                goals = profile_text.get("goals_description", f"Nắm chắc {subject} và cải thiện kết quả học tập.")
                out.append(insert("students",["id","user_id","introduction","current_level","weak_points","goals_description"],
                    [q(profile),q(user),q(student_intro),q(f"Lớp {level}"),q(weak_points),q(goals)]))
                refreshes.append(refresh("students", profile, introduction=student_intro,
                                         weak_points=weak_points, goals_description=goals))
                students.append((profile,user,level,home))
                for j in range(1+(i%3==0)):
                    subject = LEGACY_SUBJECTS[(i+j*3)%len(LEGACY_SUBJECTS)]
                    posting = uid("studying",i,j)
                    mode = "ONLINE" if (i+j)%4==0 else "OFFLINE"
                    need = NEEDS[(i+j)%len(NEEDS)]
                    focus = topic_for_grade(subject, level)
                    posting_text = narratives.get(posting, {})
                    title = studying_title(i, j, subject, level, focus, mode, home)
                    desc = posting_text.get("description", need)
                    learning_goals = posting_text.get("learning_goals", f"Cải thiện {subject} trong học kỳ này")
                    days = (2+(i+j)%4, 6+(i+j)%2)
                    start, end = ("18:00", "20:00") if i%2 else ("18:30", "20:30")
                    preferred = schedule_text(days, start, end)
                    status = "CLOSED" if account_status!="ACTIVE" else "PENDING_REVIEW" if i%21==0 and j==0 else "REJECTED" if i%25==0 and j==0 else "OPEN" if i%13 else "CLOSED"
                    out.append(insert("studying_requests",["id","district_id","student_id","subject_id","grade_id","title","description","learning_goals","max_price","learning_mode","preferred_schedule","status","created_at"],
                        [q(posting),district(home) if mode=="OFFLINE" else "NULL",q(profile),catalog("subjects",subject),grade(level),q(title),q(desc),q(learning_goals),180000+(i%7)*20000,q(mode),q(preferred),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{60+i%55} days'"]))
                    refreshes.append(refresh("studying_requests", posting, title=title, description=desc,
                                             learning_goals=learning_goals, preferred_schedule=preferred))
                    for k, day in enumerate(days):
                        slot_id = uid("sr-slot", i, j, k)
                        out.append(insert("studying_request_availabilities",["id","studying_request_id","day_of_week","start_time","end_time"],[q(slot_id),q(posting),day,q(start),q(end)]))
                        refreshes.append(refresh("studying_request_availabilities", slot_id,
                                                 day_of_week=day, start_time=start, end_time=end))
                    studying.append((posting,profile,subject,level,mode,status))
    # Search catalog expansion is intentionally separate from scripted learning events.
    # These people and postings cover genuine filter combinations; no fabricated clicks follow.
    for role, ordinal, subject, level, (province, home) in (
        list(expanded_personas("tutor")) + list(expanded_personas("student"))
    ):
        name = expanded_name(role, ordinal)
        given = name.rsplit(" ", 1)[-1]
        first_name = name[:-(len(given) + 1)]
        birth_year = (1987 + ordinal % 17) if role == "tutor" else (
            1984 + ordinal % 15 if level <= 5 else 2026 - (level + 6))
        birth_date = f"{birth_year}-{1+ordinal%12:02d}-{5+(ordinal*7)%23:02d}"
        user = uid("user", f"expanded-{role}-{ordinal}")
        seeded_users.append(user)
        profile = uid(f"expanded-{role}", ordinal)
        email = seed_email(name, birth_year)
        user_columns = ["id", "district_id", "citizen_id", "email", "password", "first_name",
                        "last_name", "gender", "status", "date_of_birth", "created_at"]
        citizen = f"079{(300000000 if role == 'tutor' else 400000000) + ordinal:09d}"
        user_values = [q(user), district(home, province), q(citizen), q(email), q(HASH),
                       q(first_name), q(given), q("MALE" if ordinal % 2 == 0 else "FEMALE"),
                       q("ACTIVE"), q(birth_date), f"CURRENT_TIMESTAMP - INTERVAL '{20 + ordinal % 180} days'"]
        out.append(insert("users", user_columns, user_values))
        refreshes.append(refresh("users", user, email=email, first_name=first_name,
                                 last_name=given, date_of_birth=birth_date))
        if role == "tutor":
            experience = 1 + ordinal % 10
            prose = narratives[profile]
            out.append(insert("tutors", ["id", "user_id", "introduction", "experience_years", "education",
                                         "teaching_methodology", "strength_subjects", "target_student_type",
                                         "profile_status", "profile_reviewed_by", "profile_reviewed_at"],
                              [q(profile), q(user), q(prose["introduction"]), experience,
                               q(("Đại học Sư phạm Hà Nội", "Đại học Sài Gòn", "Đại học Đà Nẵng",
                                  "Đại học Cần Thơ", "Đại học Huế", "Đại học Khoa học Tự nhiên",
                                  "Đại học Ngoại ngữ", "Đại học Sư phạm Thành phố Hồ Chí Minh",
                                  "Đại học Bách khoa Hà Nội", "Đại học Quy Nhơn")[ordinal % 10]),
                               q(prose["teaching_methodology"]), q(subject),
                               q("Học sinh cần củng cố kiến thức" if ordinal % 3 else "Học sinh ôn thi"),
                               q("VERIFIED"), "(SELECT id FROM employees WHERE role=1 ORDER BY id LIMIT 1)",
                               "CURRENT_TIMESTAMP - INTERVAL '12 days'"]))
            refreshes.append(refresh("tutors", profile, introduction=prose["introduction"],
                                     teaching_methodology=prose["teaching_methodology"]))
        else:
            prose = narratives[profile]
            out.append(insert("students", ["id", "user_id", "introduction", "current_level",
                                           "weak_points", "goals_description"],
                              [q(profile), q(user), q(prose["introduction"]), q(f"Lớp {level}"),
                               q(prose["weak_points"]), q(prose["goals_description"])]))
            refreshes.append(refresh("students", profile, introduction=prose["introduction"],
                                     weak_points=prose["weak_points"], goals_description=prose["goals_description"]))
        for j, post_level, mode, focus in expanded_postings(role, ordinal, subject, level):
            kind = "teaching" if role == "tutor" else "studying"
            posting = uid(f"expanded-{kind}", ordinal, j)
            prose = narratives[posting]
            days = (1 + (ordinal + j) % 5, 6 + (ordinal + j) % 2)
            start = ("16:30", "18:00", "19:00", "17:30")[(ordinal + j) % 4]
            end = ("18:00", "19:30", "20:30", "19:00")[(ordinal + j) % 4]
            schedule = schedule_text(days, start, end)
            if role == "tutor":
                price = 120000 + ((ordinal * 3 + j * 5) % 12) * 20000
                out.append(insert("teaching_requests", ["id", "tutor_id", "subject_id", "title",
                                                        "description", "quantity", "expected_price", "teaching_mode",
                                                        "preferred_schedule", "status", "reviewed_at", "created_at"],
                                  [q(posting), q(profile), catalog("subjects", subject), q(prose["title"]),
                                   q(prose["description"]), 1 + ordinal % 3, price, q(mode), q(schedule),
                                   q("OPEN"), "CURRENT_TIMESTAMP - INTERVAL '10 days'",
                                   f"CURRENT_TIMESTAMP - INTERVAL '{12 + ordinal % 70} days'"]))
                refreshes.append(refresh("teaching_requests", posting, title=prose["title"],
                                         description=prose["description"], preferred_schedule=schedule))
                out.append(insert("grade_teaching_requests", ["id", "grade_id", "teaching_request_id"],
                                  [sql_id("expanded-tr-grade", ordinal, j), grade(post_level), q(posting)]))
                if mode == "OFFLINE":
                    out.append(insert("teaching_request_districts", ["id", "teaching_request_id", "district_id"],
                                      [sql_id("expanded-tr-district", ordinal, j), q(posting), district(home, province)]))
                slot_table, post_column = "teaching_request_availabilities", "teaching_request_id"
            else:
                price = 130000 + ((ordinal * 5 + j * 3) % 11) * 20000
                out.append(insert("studying_requests", ["id", "district_id", "student_id", "subject_id",
                                                        "grade_id", "title", "description", "learning_goals",
                                                        "max_price", "learning_mode", "preferred_schedule",
                                                        "status", "created_at"],
                                  [q(posting), district(home, province) if mode == "OFFLINE" else "NULL",
                                   q(profile), catalog("subjects", subject), grade(post_level), q(prose["title"]),
                                   q(prose["description"]), q(prose["learning_goals"]), price, q(mode),
                                   q(schedule), q("OPEN"), f"CURRENT_TIMESTAMP - INTERVAL '{8 + ordinal % 65} days'"]))
                refreshes.append(refresh("studying_requests", posting, title=prose["title"],
                                         description=prose["description"],
                                         learning_goals=prose["learning_goals"], preferred_schedule=schedule))
                slot_table, post_column = "studying_request_availabilities", "studying_request_id"
            for k, day in enumerate(days):
                out.append(insert(slot_table, ["id", post_column, "day_of_week", "start_time", "end_time"],
                                  [sql_id(f"expanded-{kind}-slot", ordinal, j, k), q(posting), day,
                                   q(start), q(end)]))
    # Unsigned contracts remain editable/signable; a rejected or creator-cancelled draft has
    # CANCELLED status and no signature, lessons, installments or tuition payment.
    for ordinal in range(100, 112):
        _, _, subject, level, _ = next(row for row in expanded_personas("tutor") if row[1] == ordinal)
        student = uid("expanded-student", ordinal)
        tutor = uid("expanded-tutor", ordinal)
        student_user = uid("user", f"expanded-student-{ordinal}")
        tutor_user = uid("user", f"expanded-tutor-{ordinal}")
        posting = uid("expanded-teaching", ordinal, 0)
        application = uid("contract-lifecycle-application", ordinal)
        contract = uid("contract-lifecycle", ordinal)
        tutor_price = 120000 + (ordinal * 3 % 12) * 20000
        student_budget = 130000 + (ordinal * 5 % 11) * 20000
        price = min(tutor_price, student_budget)
        cancelled = ordinal >= 108
        days = (1 + ordinal % 5, 6 + ordinal % 2)
        start = ("16:30", "18:00", "19:00", "17:30")[ordinal % 4]
        end = ("18:00", "19:30", "20:30", "19:00")[ordinal % 4]
        out.append(insert("student_tutor_requests", ["id", "student_id", "grade_id",
                                                     "teaching_request_id", "proposed_price", "learning_mode",
                                                     "message", "status", "created_at", "responded_at"],
                          [q(application), q(student), grade(level), q(posting), price,
                           q("ONLINE"), q(f"Em muốn học {subject} lớp {level} trực tuyến theo lịch của gia sư."),
                           q("ACCEPTED"), "CURRENT_TIMESTAMP - INTERVAL '8 days'",
                           "CURRENT_TIMESTAMP - INTERVAL '7 days'"]))
        out.append(insert("contracts", ["id", "student_id", "tutor_id", "subject_id", "grade_id",
                                        "teaching_mode", "price", "payment_period", "total_lession",
                                        "start_date", "end_date", "status", "created_by_user_id",
                                        "student_tutor_request_id", "preferred_schedule", "cancelled_at",
                                        "created_at"],
                          [q(contract), q(student), q(tutor), catalog("subjects", subject), grade(level),
                           q("ONLINE"), price, q("PER_LESSON"), 8,
                           "CURRENT_DATE + 7", "CURRENT_DATE + 90",
                           q("CANCELLED" if cancelled else "PENDING"), q(tutor_user), q(application),
                           q(schedule_text(days, start, end)),
                           "CURRENT_TIMESTAMP - INTERVAL '2 days'" if cancelled else "NULL",
                           "CURRENT_TIMESTAMP - INTERVAL '6 days'" if cancelled
                           else "CURRENT_TIMESTAMP - INTERVAL '2 days'"]))
        for slot, day in enumerate(days):
            out.append(insert("contract_schedules", ["id", "contract_id", "day_of_week",
                                                     "start_time", "end_time"],
                              [sql_id("contract-lifecycle-schedule", ordinal, slot), q(contract),
                               day, q(start), q(end)]))
    open_teaching = [x for x in teaching if x[-1]=="OPEN"]
    open_studying = [x for x in studying if x[-1]=="OPEN"]
    for i,(student,user,level,home) in enumerate(students):
        if i>=len(students)-2:
            continue
        options = [x for x in open_teaching if x[3]==level]
        for j in range(min(7,len(options))):
            row=options[(i+j)%len(options)]
            posting,tutor,subject,_,price,mode,_ = row
            tutor_user=next(t[1] for t in tutors if t[0]==tutor)
            status = "ACCEPTED" if j==0 or (j==6 and i%2==0) else "REJECTED" if j==1 or j==6 else "PENDING"
            age=5 if j==6 else 70 if j==0 and i%9==0 else 24+j*3
            req = uid("application",i,j)
            applications.append((i,j,req,age))
            application_columns=["id","student_id","grade_id","teaching_request_id","proposed_price","learning_mode","message","status","created_at"]
            message = application_message(i, j, level, subject)
            application_values=[q(req),q(student),grade(level),q(posting),price,q(mode),q(message),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                application_columns.append("responded_at")
                application_values.append("NULL" if status=="PENDING" else f"CURRENT_TIMESTAMP - INTERVAL '{age-2} days'")
            out.append(insert("student_tutor_requests",application_columns,application_values))
            refreshes.append(refresh("student_tutor_requests", req, message=message))
            if j==0:
                out.append(insert("favorite_tutors",["id","student_id","tutor_id","teaching_request_id"],
                                  [sql_id("favorite",i),q(student),q(tutor),q(posting)]))
                contract = uid("contract",i)
                completed=i%3==0  # Reviews are only valid after a completed contract.
                contract_scenarios.append((i, contract, user, tutor_user, price, completed))
                contract_days = (2 + i % 3, 5 + i % 3)
                contract_schedule = schedule_text(contract_days, "18:30", "20:00")
                out.append(insert("contracts",["id","student_id","tutor_id","subject_id","grade_id","teaching_mode","price","payment_period","total_lession","start_date","end_date","status","created_by_user_id","signed_by_user_id","student_tutor_request_id","preferred_schedule","signed_at"],
                    [q(contract),q(student),q(tutor),catalog("subjects",subject),grade(level),q(mode),price,q("PER_LESSON"),3 if completed else 12,"CURRENT_DATE - 60" if completed else "CURRENT_DATE - 20","CURRENT_DATE - 1" if completed else "CURRENT_DATE + 70",q("COMPLETED" if completed else "ACTIVE"),q(tutor_user),q(user),q(req),q(contract_schedule),"CURRENT_TIMESTAMP - INTERVAL '67 days'" if completed else "CURRENT_TIMESTAMP - INTERVAL '21 days'"]))
                for slot, day in enumerate(contract_days):
                    out.append(insert("contract_schedules", ["id", "contract_id", "day_of_week", "start_time", "end_time"],
                                      [sql_id("contract-schedule", i, slot), q(contract), day, q("18:30"), q("20:00")]))
                if completed:
                    refreshes.append(f"UPDATE contracts SET status='COMPLETED', end_date=CURRENT_DATE - 1, total_lession=3 WHERE id={q(contract)};")
                for k in range(3):
                    lesson_focus = topic_for_grade(subject, level)
                    lesson_title = f"Buổi {k+1}: {lesson_focus}"
                    lesson_note = LESSON_NOTES[(i+k*3)%len(LESSON_NOTES)].format(
                        topic=lesson_focus)
                    lesson_id = uid("lesson", i, k)
                    disputed = (i % 15 == 5 or i == 10) and k == 0
                    settled = completed or k == 1 or (i % 4 == 0 and not disputed)
                    lesson_status = ("PENDING_CONFIRMATION" if disputed or
                                     (finance_key is not None and k < 2 and not settled)
                                     else "COMPLETED" if completed or k < 2 else "SCHEDULED")
                    out.append(insert("lessons",["id","contract_id","title","date","start_time","end_time","teaching_mode","status","note"],
                        [q(lesson_id),q(contract),q(lesson_title),f"CURRENT_DATE - {25-k*7}" if completed else (f"CURRENT_DATE - {18-k*7}" if k<2 else "CURRENT_DATE + 3"),q("18:30"),q("20:00"),q(mode),q(lesson_status),q(lesson_note)]))
                    refreshes.append(refresh("lessons", lesson_id, title=lesson_title, note=lesson_note))
                    if completed:
                        refreshes.append(f"UPDATE lessons SET status='COMPLETED', date=CURRENT_DATE - {25-k*7} WHERE id={q(lesson_id)};")
                    out.append(insert("contract_payment_installments",["id","contract_id","lesson_id","sequence_number","payment_period","amount","due_date","status"],
                        [sql_id("installment",i,k),q(contract),sql_id("lesson",i,k),k+1,q("PER_LESSON"),price,f"CURRENT_DATE - {18-k*7}" if completed else f"CURRENT_DATE + {k*7}",q("PAID" if completed or k<2 else "PENDING")]))
                    if finance_key is not None and (completed or k < 2):
                        paid_at = (f"CURRENT_TIMESTAMP - INTERVAL '{35-k} days'" if completed
                                   else f"CURRENT_TIMESTAMP - INTERVAL '{19-7*k} days'")
                        out.append(insert("payments", ["id", "user_id", "amount", "payment_method",
                                                       "payment_type", "status", "transaction_code",
                                                       "payment_installment_id", "reference_type", "reference_id",
                                                       "paid_at", "created_at"],
                                          [sql_id("tuition-payment", i, k), q(user), price, q("VNPAY"),
                                           q("TUITION_PAYMENT"), q("PAID"), q(f"LOCAL-TUITION-{i:03d}-{k+1}"),
                                           sql_id("installment", i, k), q("CONTRACT"), q(contract),
                                           paid_at, f"{paid_at} - INTERVAL '1 hour'"]))
                    if completed:
                        refreshes.append(f"UPDATE contract_payment_installments SET status='PAID', due_date=CURRENT_DATE - {18-k*7} WHERE id={sql_id('installment',i,k)};")
                if i%3==0:
                    review_comment = REVIEW_TEXT[i%len(REVIEW_TEXT)] + " " + REVIEW_DETAILS[(i//3)%len(REVIEW_DETAILS)]
                    columns=["id","student_id","tutor_id","rating","comment"]
                    values=[sql_id("review",i),q(student),q(tutor),4+i%2,q(review_comment)]
                    if include_ai:
                        columns.append("contract_id")
                        values.append(q(contract))
                    out.append(insert("reviews",columns,values))
                    refreshes.append(refresh("reviews", uid("review", i), comment=review_comment))
                if i%16==0:
                    out.append(insert("complaints",["id","user_id","contract_id","title","content","status","category"],[sql_id("complaint",i),q(user),q(contract),q("Cần trao đổi lại lịch buổi học"),q("Buổi học gần đây dời lịch sát giờ. Gia đình muốn được hỗ trợ thống nhất lịch học tiếp theo."),q("PENDING"),q("OTHER")]))
                if i%17==0 and not completed:
                    out.append(insert("cancel_contract_requests",["id","contract_id","user_id","reason","status"],
                        [sql_id("cancel-request",i),q(contract),q(user),q("Gia đình thay đổi lịch học trong học kỳ mới, muốn dừng hợp đồng sau buổi đã hẹn."),q("PENDING")]))
                if i%15==5 or i==10:
                    refund=uid("refund",i)
                    refund_created = f"CURRENT_TIMESTAMP - INTERVAL '{1 + i % 4} days'"
                    out.append(insert("refund_requests",["id","contract_id","student_id","description","status","tutor_response_deadline","unit_price","created_at"],
                        [q(refund),q(contract),q(student),q("Gia sư vắng mặt ở buổi học đã thu học phí, gia đình đề nghị hoàn đủ tiền buổi này." if i==10 else "Buổi học kết thúc sớm khoảng 30 phút, gia đình đề nghị kiểm tra phần học phí tương ứng."),q("PENDING"),"CURRENT_TIMESTAMP + INTERVAL '3 days'",price,refund_created]))
                    out.append(insert("refund_request_lessons",["id","refund_request_id","lesson_id","claimed_outcome","missed_minutes","created_at"],
                        [sql_id("refund-lesson",i),q(refund),sql_id("lesson",i,0),q("TUTOR_ABSENT" if i==10 else "TUTOR_LATE_OR_SHORT"),"NULL" if i==10 else 30,refund_created]))
        out.append(insert("notifications",["id","user_id","title","content","type","is_read"],[sql_id("notification",i),q(user),q("Cập nhật yêu cầu học"),q("Yêu cầu học của bạn đã được ghi nhận. Bạn có thể xem tiến độ trong tài khoản."),q("SYSTEM"),"true" if i%3 else "false"]))
        if i%4==0:
            wallet=uid("wallet",i)
            out.append(insert("wallets",["id","user_id","balance","pending_balance"],[q(wallet),q(user),500000,0]))
            out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount","balance_after","pending_balance_after","status","idempotency_key","description","reference_type","reference_id","created_at"],
                [sql_id("wallet-tx",i),q(wallet),q("CREDIT"),q("TOP_UP"),500000,500000,0,q("COMPLETED"),q(f"realistic-data-topup-{i}"),q("Nạp tiền vào ví học viên"),q("PAYMENT"),sql_id("payment",i),"CURRENT_TIMESTAMP - INTERVAL '15 days'"]))
            out.append(insert("payments",["id","user_id","amount","payment_method","payment_type","status","transaction_code","reference_type","reference_id","paid_at","created_at"],
                [sql_id("payment",i),q(user),500000,q("VNPAY"),q("WALLET_TOP_UP"),q("PAID"),q(f"LOCAL-TOPUP-{i:03d}"),q("PAYMENT"),sql_id("payment",i),"CURRENT_TIMESTAMP - INTERVAL '15 days'","CURRENT_TIMESTAMP - INTERVAL '15 days 1 hour'"]))
    # Saved searches are user-owned criteria, used by both recommendation directions.
    # Store real catalog IDs so rerunning the search remains valid on a migrated DB.
    if include_ai:
        for i, (_, user, level, _) in enumerate(students[:16]):
            subject = LEGACY_SUBJECTS[i % len(LEGACY_SUBJECTS)]
            filters = ("jsonb_build_object('subjectId', " + catalog("subjects", subject)
                       + ", 'gradeId', " + grade(level)
                       + ", 'learningMode', 'ANY')")
            context = "jsonb_build_object('filters', " + filters + ", 'semanticText', NULL)"
            out.append(insert("recommendation_saved_searches",
                              ["id", "user_id", "direction", "name", "query_context", "notify_enabled"],
                              [sql_id("saved-student", i), q(user), q("STUDENT_TO_TUTOR"),
                               q(f"Gia sư {subject} lớp {level}"), context, "true" if i % 4 else "false"]))
        for i, (_, user, subject, level, _) in enumerate(tutors[:16]):
            filters = ("jsonb_build_object('subjectId', " + catalog("subjects", subject)
                       + ", 'gradeIds', jsonb_build_array(" + grade(level)
                       + "), 'learningMode', 'ANY')")
            context = "jsonb_build_object('filters', " + filters + ", 'semanticText', NULL)"
            out.append(insert("recommendation_saved_searches",
                              ["id", "user_id", "direction", "name", "query_context", "notify_enabled"],
                              [sql_id("saved-tutor", i), q(user), q("TUTOR_TO_STUDENT"),
                               q(f"Học viên {subject} lớp {level}"), context, "true" if i % 4 else "false"]))
    for i,(tutor,_,subject,_,_) in enumerate(tutors):
        if i>=len(tutors)-2 or i%17==0:
            continue
        candidates=[x for x in open_studying if x[2]==subject]
        for j in range(min(5,len(candidates))):
            sr,_,_,level,mode,_=candidates[(i+j)%len(candidates)]
            status="ACCEPTED" if j==0 or (j==4 and i%2==0) else "REJECTED" if j==1 or j==4 else "PENDING"
            age=5 if j==4 else 30+j*3
            offer=uid("offer",i,j)
            offers.append((i,j,offer,age))
            columns=["id","tutor_id","grade_id","studying_request_id","proposed_price","teaching_mode","message","status","created_at"]
            message = offer_message(i, j, level, subject)
            values=[q(offer),q(tutor),grade(level),q(sr),200000,q(mode),q(message),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                columns.append("responded_at")
                values.append("NULL" if status=="PENDING" else f"CURRENT_TIMESTAMP - INTERVAL '{age-2} days'")
            out.append(insert("tutor_student_requests",columns,values))
            refreshes.append(refresh("tutor_student_requests", offer, message=message))
    # Search events are linked to actual postings, so preference and exposure jobs can use them.
    for i,(student,_,level,_) in enumerate(students):
        if i>=len(students)-2:
            continue
        candidates=[x for x in open_teaching if x[3]==level]
        if not candidates:
            continue
        for turn in range(10):
            choices=[candidates[(i*3+turn*4+k)%len(candidates)] for k in range(min(6,len(candidates)))]
            log=uid("search-student",i,turn)
            age=20+turn+i%6 if turn<8 else 9+(turn-8)
            returned=[{"candidateId":row[0],"rank":rank+1,"features":{
                "sem":round(0.82-rank*0.07+(i%3)*0.02,2),"need":round(0.78-rank*0.06,2),
                "price":round(max(0.15,1-abs(row[4]-(180000+i%7*20000))/230000),2),
                "style":round(0.48+(i+rank)%5*0.1,2),"sched":round(0.45+(i+rank)%4*0.12,2),
                "area":0.9 if row[5]=="ONLINE" else round(0.55+(i+rank)%3*0.12,2),
                "segment":round(0.52+(i+rank)%4*0.1,2)}} for rank,row in enumerate(choices)]
            columns=["id","searcher_student_id","query_context","returned_candidate_ids","created_at"]
            values=[q(log),q(student),q(json.dumps({"grade":level,"source":"student-search","turn":turn}))+"::jsonb",q(json.dumps(returned))+"::jsonb",f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                columns += ["direction","algorithm_version","query_text","latency_ms"]
                values += [q("STUDENT_TO_TUTOR"),q("local-seed-v1"),q(STUDENT_QUERIES[turn].format(subject=choices[0][2],level=level)),120+i%180]
            out.append(insert("recommendation_logs",columns,values))
            for rank,row in enumerate(choices[:3]):
                action=("FAVORITED" if rank==0 and i%4==0 else "CLICKED" if rank<2 else "DISMISSED") if include_ai else ("CLICKED" if rank<2 else "VIEWED")
                columns=["id","recommendation_log_id","clicked_tutor_id","rank","action","created_at"]
                values=[sql_id("search-event-student",i,turn,rank),q(log),q(row[1]),rank+1,q(action),f"CURRENT_TIMESTAMP - INTERVAL '{age-1} days'"]
                if include_ai:
                    columns.append("candidate_teaching_request_id")
                    values.append(q(row[0]))
                out.append(insert("recommendation_click_logs",columns,values))
    for i,(tutor,_,subject,level,_) in enumerate(tutors):
        if i>=len(tutors)-2 or i%17==0:
            continue
        candidates=[x for x in open_studying if x[2]==subject]
        if not candidates:
            continue
        for turn in range(10):
            choices=[candidates[(i+turn*3+k)%len(candidates)] for k in range(min(5,len(candidates)))]
            log=uid("search-tutor",i,turn)
            age=20+turn+i%6 if turn<8 else 9+(turn-8)
            returned=[{"candidateId":row[0],"rank":rank+1,"features":{
                "sem":round(0.8-rank*0.08,2),"price":round(0.55+(i+rank)%4*0.1,2),
                "need":round(0.7-rank*0.05,2),"segment":round(0.5+(i+rank)%4*0.1,2),
                "sched":round(0.45+(i+rank)%5*0.1,2),"area":0.9 if row[4]=="ONLINE" else 0.6}}
                for rank,row in enumerate(choices)]
            columns=["id","searcher_tutor_id","query_context","returned_candidate_ids","created_at"]
            values=[q(log),q(tutor),q(json.dumps({"grade":level,"subject":subject,"turn":turn}))+"::jsonb",q(json.dumps(returned))+"::jsonb",f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                columns += ["direction","algorithm_version","query_text","latency_ms"]
                values += [q("TUTOR_TO_STUDENT"),q("local-seed-v1"),q(TUTOR_QUERIES[turn].format(subject=subject,level=level)),100+i%120]
            out.append(insert("recommendation_logs",columns,values))
            for rank,row in enumerate(choices[:2]):
                columns=["id","recommendation_log_id","clicked_student_id","rank","action","created_at"]
                values=[sql_id("search-event-tutor",i,turn,rank),q(log),q(row[1]),rank+1,q("CONTACTED" if rank==0 and i%5==0 else "CLICKED"),f"CURRENT_TIMESTAMP - INTERVAL '{age-1} days'"]
                if include_ai:
                    columns.append("candidate_studying_request_id")
                    values.append(q(row[0]))
                out.append(insert("recommendation_click_logs",columns,values))
    for index,(title,content,category,audience,status) in enumerate((
        ("Cập nhật lịch bảo trì hệ thống","Một số trang có thể truy cập chậm trong khung 23–24 giờ tối Chủ nhật.","MAINTENANCE","ALL","DRAFT"),
        ("Lưu ý xác nhận buổi học","Gia sư vui lòng cập nhật trạng thái buổi học đúng thời hạn để học viên tiện theo dõi.","SERVICE_UPDATE","TUTOR","SCHEDULED"))):
        out.append(insert("notifications",["id","title","content","type","record_type","category","audience","campaign_status","created_by_user_id","scheduled_at"],
            [sql_id("campaign",index),q(title),q(content),q("SYSTEM"),q("BROADCAST_MASTER"),q(category),q(audience),q(status),"(SELECT user_id FROM employees WHERE role=1 ORDER BY id LIMIT 1)","NULL" if status=="DRAFT" else "CURRENT_TIMESTAMP + INTERVAL '2 days'"]))
    if include_ai:
        # The integrated recommendation branch has this table; dev does not yet.
        # Values model fit at decision time, with varied positive and negative outcomes.
        for i,j,req,age in applications:
            fit=round(0.82 if j==0 or (j==6 and i%2==0) else 0.38 if j==1 or j==6 else 0.55,2)
            common={"hist":round(0.45+(i%9)*0.05,2),"cap":round(0.5+(i%4)*0.1,2),"pending":j,"reachedOut":False,"p0":0.53}
            for model,kind,features in (
                ("B_S2T","DECISION",{"fitSegment":fit,"fitSched":round(0.4+(i%5)*0.1,2),"fitPrice":round(0.5+(i%4)*0.1,2)}),
                ("B_T2S","INITIATIVE",{"fitQuality":fit,"fitStyle":round(0.5+(i%4)*0.1,2),"fitPrice":round(0.5+(i%4)*0.1,2)}),
            ):
                payload={**common,**features}
                out.append(insert("recommendation_decision_snapshots",["model_key","sample_kind","request_type","request_id","features","created_at"],
                    [q(model),q(kind),q("STUDENT_TUTOR_REQUEST"),q(req),q(json.dumps(payload))+"::jsonb",f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]))
        for i,j,req,age in offers:
            fit=round(0.8 if j==0 or (j==4 and i%2==0) else 0.35 if j==1 or j==4 else 0.53,2)
            common={"hist":round(0.42+(i%8)*0.06,2),"cap":round(0.5+(i%4)*0.1,2),"pending":j,"reachedOut":False,"p0":0.53}
            for model,kind,features in (
                ("B_T2S","DECISION",{"fitQuality":fit,"fitStyle":round(0.5+(i%4)*0.1,2),"fitPrice":round(0.5+(i%4)*0.1,2)}),
                ("B_S2T","INITIATIVE",{"fitSegment":fit,"fitSched":round(0.4+(i%5)*0.1,2),"fitPrice":round(0.5+(i%4)*0.1,2)}),
            ):
                payload={**common,**features}
                out.append(insert("recommendation_decision_snapshots",["model_key","sample_kind","request_type","request_id","features","created_at"],
                    [q(model),q(kind),q("TUTOR_STUDENT_REQUEST"),q(req),q(json.dumps(payload))+"::jsonb",f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]))
        # SQL fixtures bypass the application event listener. Queue the real profile worker for
        # people with interaction logs; it derives preference vectors from those recorded signals.
        out.append("""
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts,
                 next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'PROFILE_UPDATE', person.entity_type, person.entity_id,
                   'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM (
                SELECT DISTINCT 'STUDENT' AS entity_type, l.searcher_student_id AS entity_id
                FROM recommendation_click_logs cl JOIN recommendation_logs l
                  ON l.id = cl.recommendation_log_id
                WHERE l.searcher_student_id IS NOT NULL
                UNION ALL
                SELECT DISTINCT 'TUTOR', l.searcher_tutor_id
                FROM recommendation_click_logs cl JOIN recommendation_logs l
                  ON l.id = cl.recommendation_log_id
                WHERE l.searcher_tutor_id IS NOT NULL
            ) person
            WHERE NOT EXISTS (
                SELECT 1 FROM recommendation_user_profiles p
                JOIN students s ON s.user_id = p.user_id
                WHERE person.entity_type = 'STUDENT' AND s.id = person.entity_id
                UNION ALL
                SELECT 1 FROM recommendation_user_profiles p
                JOIN tutors t ON t.user_id = p.user_id
                WHERE person.entity_type = 'TUTOR' AND t.id = person.entity_id
            )
            ON CONFLICT (job_type, entity_type, entity_id)
                WHERE status IN ('PENDING', 'PROCESSING') DO NOTHING;
        """)
        # The nightly exposure job uses the same sources. Materialize them now so exploration
        # works immediately in a freshly restored demo database.
        out.append("""
            INSERT INTO recommendation_exposure_stats
                (candidate_type, candidate_id, impressions_7d, clicks_7d,
                 impressions_total, clicks_total, updated_at)
            SELECT candidate_type, candidate_id,
                   SUM(impression_7d), SUM(click_7d), SUM(impression), SUM(click), CURRENT_TIMESTAMP
            FROM (
                SELECT CASE WHEN l.direction = 'STUDENT_TO_TUTOR' THEN 'TEACHING_REQUEST'
                            ELSE 'STUDYING_REQUEST' END AS candidate_type,
                       (candidate ->> 'candidateId')::uuid AS candidate_id,
                       CASE WHEN l.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                            THEN 1 ELSE 0 END AS impression_7d,
                       0 AS click_7d, 1 AS impression, 0 AS click
                FROM recommendation_logs l
                CROSS JOIN LATERAL jsonb_array_elements(l.returned_candidate_ids) candidate
                WHERE l.deleted_at IS NULL AND l.direction IS NOT NULL
                  AND jsonb_typeof(l.returned_candidate_ids) = 'array'
                  AND (candidate ->> 'rank')::integer <= 20
                UNION ALL
                SELECT CASE WHEN cl.candidate_teaching_request_id IS NOT NULL
                            THEN 'TEACHING_REQUEST' ELSE 'STUDYING_REQUEST' END,
                       COALESCE(cl.candidate_teaching_request_id,
                                cl.candidate_studying_request_id),
                       0,
                       CASE WHEN MAX(cl.created_at) >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                            THEN 1 ELSE 0 END,
                       0, 1
                FROM recommendation_click_logs cl
                WHERE cl.deleted_at IS NULL AND cl.action <> 'DISMISSED'
                  AND COALESCE(cl.candidate_teaching_request_id,
                               cl.candidate_studying_request_id) IS NOT NULL
                GROUP BY cl.recommendation_log_id, 1, 2
            ) counted
            GROUP BY candidate_type, candidate_id
            ON CONFLICT (candidate_type, candidate_id) DO UPDATE SET
                impressions_7d = EXCLUDED.impressions_7d,
                clicks_7d = EXCLUDED.clicks_7d,
                impressions_total = EXCLUDED.impressions_total,
                clicks_total = EXCLUDED.clicks_total,
                updated_at = EXCLUDED.updated_at;
        """)
    if include_chatbot:
        # A real support queue is more useful than fabricated assistant answers or citations.
        for i in range(40):
            role=SUPPORT_ROLES[i%len(SUPPORT_ROLES)]
            user=students[i%len(students)][1] if role=="STUDENT" else tutors[i%len(tutors)][1]
            status=("OPEN","IN_PROGRESS","ANSWERED","CLOSED","CANCELLED")[i%5]
            answer=SUPPORT_ANSWERS[i%len(SUPPORT_ANSWERS)] if status in ("ANSWERED","CLOSED") else None
            support_id = uid("support", i)
            question = narratives.get(support_id, {}).get("question", SUPPORT_QUESTIONS[i%len(SUPPORT_QUESTIONS)])
            out.append(insert("support_requests",["id","code","user_id","user_role","question","source","page_path","status","answer","assigned_employee_id","assigned_at","created_at","updated_at","answered_at","closed_at"],
                [q(support_id),q(f"HT-LOCAL-{i+1:04d}"),q(user),q(role),q(question),q("MENU" if i%5==0 else "ASSISTANT_ABSTAIN"),q("/my-support-requests"),q(status),q(answer),"NULL" if status in ("OPEN","CANCELLED") else sql_id("employee","staff-0"),"NULL" if status in ("OPEN","CANCELLED") else "CURRENT_TIMESTAMP - INTERVAL '1 day'",f"CURRENT_TIMESTAMP - INTERVAL '{2+i%18} days'",f"CURRENT_TIMESTAMP - INTERVAL '{i%2} days'","NULL" if answer is None else "CURRENT_TIMESTAMP - INTERVAL '1 day'","CURRENT_TIMESTAMP" if status in ("CLOSED","CANCELLED") else "NULL"]))
            refreshes.append(refresh("support_requests", support_id, question=question,
                                     user_role=role, user_id=user))
    if finance_key is not None:
        for i,(_,user,_,_,_) in enumerate(tutors[:24]):
            holder=bank_holder(tutor_names[i])
            account=f"97000000000{i+1000:04d}"
            encrypted=encrypted_account(account,finance_key)
            out.append(insert("bank_accounts",["id","user_id","bank_code","bank_name","encrypted_account_number","account_number_last_four","account_holder_name"],
                [sql_id("bank",i),q(user),q("VCB" if i%2 else "TCB"),q("Vietcombank" if i%2 else "Techcombank"),q(encrypted),q(account[-4:]),q(holder)]))
            wallet=uid("tutor-wallet",i)
            pending=i<12 and i%3==0
            completed=i<12 and i%3==2
            balance=900000 if pending or completed else 1200000
            out.append(insert("wallets",["id","user_id","balance","pending_balance"],[q(wallet),q(user),balance,300000 if pending else 0]))
            opening_payment=uid("tutor-opening-topup",i)
            out.append(insert("payments",["id","user_id","amount","payment_method","payment_type",
                                          "status","transaction_code","reference_type","reference_id",
                                          "paid_at","created_at"],
                [q(opening_payment),q(user),1200000,q("VNPAY"),q("WALLET_TOP_UP"),q("PAID"),
                 q(f"LOCAL-TUTOR-TOPUP-{i:03d}"),q("PAYMENT"),q(opening_payment),
                 "CURRENT_TIMESTAMP - INTERVAL '100 days'",
                 "CURRENT_TIMESTAMP - INTERVAL '100 days 1 hour'"]))
            out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount",
                                                      "balance_after","pending_balance_after","status",
                                                      "idempotency_key","description","reference_type",
                                                      "reference_id","created_at"],
                [sql_id("tutor-earning",i),q(wallet),q("CREDIT"),q("TOP_UP"),1200000,1200000,0,
                 q("COMPLETED"),q(f"local-tutor-earning-{i}"),q("Nạp tiền vào ví"),
                 q("PAYMENT"),q(opening_payment),"CURRENT_TIMESTAMP - INTERVAL '100 days'"]))
            if pending or completed:
                out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount","balance_after","pending_balance_after","status","idempotency_key","description"],
                    [sql_id("withdrawal-tx",i),q(wallet),q("DEBIT"),q("WITHDRAWAL"),300000,900000,300000 if pending else 0,q("PENDING" if pending else "COMPLETED"),q(f"local-withdrawal-{i}"),q("Rút thu nhập gia sư")]))
            if i<12:
                status=("PENDING","REJECTED","COMPLETED")[i%3]
                out.append(insert("withdrawal_requests",["id","wallet_id","amount","bank_code","bank_name","encrypted_account_number","account_number_last_four","account_holder_name","idempotency_key","status","wallet_transaction_id","reviewed_by_user_id","reviewed_at","rejection_reason","completed_at","transfer_reference"],
                    [sql_id("withdrawal",i),q(wallet),300000,q("VCB" if i%2 else "TCB"),q("Vietcombank" if i%2 else "Techcombank"),q(encrypted),q(account[-4:]),q(holder),q(f"local-withdrawal-request-{i}"),q(status),sql_id("withdrawal-tx",i) if pending or completed else "NULL","NULL" if pending else "(SELECT user_id FROM employees WHERE role=1 ORDER BY id LIMIT 1)","NULL" if pending else "CURRENT_TIMESTAMP - INTERVAL '2 days'",q("Thông tin người thụ hưởng chưa khớp") if status=="REJECTED" else "NULL","CURRENT_TIMESTAMP - INTERVAL '1 day'" if completed else "NULL",q(f"LOCAL-TRANSFER-{i:03d}") if completed else "NULL"]))
    # Registration normally creates a wallet. Direct SQL fixtures must do the same for every
    # account; keep existing funded wallets and their transactions untouched on reruns.
    for user in seeded_users:
        out.append(insert("wallets", ["id", "user_id", "balance", "pending_balance"],
                          [sql_id("wallet-default", user), q(user), 0, 0]))
    # Old refund fixtures disputed already-completed lessons. Keep only the four active-contract
    # cases above and restore their actual awaiting-confirmation state on repeat loads.
    for old_index in (0, 40, 60):
        out.append(f"DELETE FROM refund_request_lessons WHERE refund_request_id={sql_id('refund', old_index)};")
        out.append(f"DELETE FROM refund_requests WHERE id={sql_id('refund', old_index)};")
    for refund_index in (5, 10, 20, 35, 50):
        out.append(f"UPDATE lessons SET status='PENDING_CONFIRMATION' "
                   f"WHERE id={sql_id('lesson', refund_index, 0)};")
    if finance_key is not None:
        # Repair dates/references of deterministic rows created by earlier seed versions.
        for old_index in range(0, 64, 4):
            out.append(f"UPDATE payments SET created_at=CURRENT_TIMESTAMP - INTERVAL '15 days 1 hour', "
                       "paid_at=CURRENT_TIMESTAMP - INTERVAL '15 days', reference_type='PAYMENT', "
                       f"reference_id={sql_id('payment', old_index)} WHERE id={sql_id('payment', old_index)};")
            out.append(f"UPDATE wallet_transactions SET created_at=CURRENT_TIMESTAMP - INTERVAL '15 days', "
                       f"reference_type='PAYMENT', reference_id={sql_id('payment', old_index)} "
                       f"WHERE id={sql_id('wallet-tx', old_index)};")
        for old_index in range(24):
            out.append(f"UPDATE wallet_transactions SET created_at=CURRENT_TIMESTAMP - INTERVAL '100 days', "
                       "purpose='TOP_UP', reference_type='PAYMENT', "
                       f"reference_id={sql_id('tutor-opening-topup', old_index)}, "
                       f"description={q('Nạp tiền vào ví')} "
                       f"WHERE id={sql_id('tutor-earning', old_index)};")
        # A paid installment is a VNPAY payment tied to that installment and contract.
        # Some completed lessons are settled; unsettled lessons remain visible as held tuition.
        for index, contract, student_user, tutor_user, price, completed in contract_scenarios:
            for lesson_index in range(3 if completed else 2):
                disputed = (index % 15 == 5 or index == 10) and lesson_index == 0
                settled = completed or lesson_index == 1 or (index % 4 == 0 and not disputed)
                if not settled or disputed:
                    continue
                lesson_id = uid("lesson", index, lesson_index)
                occurred_at = ("CURRENT_TIMESTAMP - INTERVAL '2 days' + "
                               f"INTERVAL '{index * 3 + lesson_index} minutes'")
                out.append(wallet_credit(tutor_user, price, uid("lesson-payout", index, lesson_index),
                                         "TUTOR_EARNING", "LESSON", lesson_id,
                                         "Thu nhập từ buổi học đã hoàn tất", occurred_at))

        # Fees belong to approved/open postings; payment dates follow each posting's creation.
        for index, (posting, tutor, _, _, _, _, status) in enumerate(teaching):
            if status != "OPEN" or index % 4 != 0:
                continue
            tutor_user = next(row[1] for row in tutors if row[0] == tutor)
            created_at = f"(SELECT created_at + INTERVAL '1 day' FROM teaching_requests WHERE id={q(posting)}::uuid)"
            out.append(insert("payments", ["id", "user_id", "amount", "payment_method", "payment_type",
                                           "status", "transaction_code", "reference_type", "reference_id",
                                           "paid_at", "created_at"],
                              [sql_id("teaching-fee", index), q(tutor_user), 100000, q("VNPAY"),
                               q("TEACHING_REQUEST_FEE"), q("PAID"), q(f"LOCAL-TEACHING-FEE-{index:03d}"),
                               q("TEACHING_REQUEST"), q(posting),
                               f"{created_at} + INTERVAL '1 hour'", created_at]))
        for index, (posting, student, _, _, _, status) in enumerate(studying):
            if status != "OPEN" or index % 3 != 0:
                continue
            student_user = next(row[1] for row in students if row[0] == student)
            created_at = f"(SELECT created_at + INTERVAL '1 day' FROM studying_requests WHERE id={q(posting)}::uuid)"
            out.append(insert("payments", ["id", "user_id", "amount", "payment_method", "payment_type",
                                           "status", "transaction_code", "reference_type", "reference_id",
                                           "paid_at", "created_at"],
                              [sql_id("studying-fee", index), q(student_user), 100000, q("VNPAY"),
                               q("STUDYING_REQUEST_FEE"), q("PAID"), q(f"LOCAL-STUDYING-FEE-{index:03d}"),
                               q("STUDYING_REQUEST"), q(posting),
                               f"{created_at} + INTERVAL '1 hour'", created_at]))

        # A complete, inspectable story for the published demo logins: the grade-1 music
        # student hires the matching offline tutor, prepays two lessons, and the tutor has
        # received one lesson payout while the second payment remains held.
        demo_student = uid("expanded-student", 80)
        demo_student_user = uid("user", "expanded-student-80")
        demo_tutor = uid("expanded-tutor", 80)
        demo_tutor_user = uid("user", "expanded-tutor-80")
        demo_posting = uid("expanded-teaching", 80, 1)
        demo_studying = uid("expanded-studying", 80, 0)
        demo_application = uid("demo-finance-application")
        demo_contract = uid("demo-finance-contract")
        demo_price = 220000
        out.append(insert("student_tutor_requests", ["id", "student_id", "grade_id",
                                                     "teaching_request_id", "proposed_price", "learning_mode",
                                                     "message", "status", "created_at", "responded_at"],
                          [q(demo_application), q(demo_student), grade(1), q(demo_posting), demo_price,
                           q("OFFLINE"), q("Gia đình muốn học Âm nhạc trực tiếp hai buổi mỗi tuần."),
                           q("ACCEPTED"), "CURRENT_TIMESTAMP - INTERVAL '20 days'",
                           "CURRENT_TIMESTAMP - INTERVAL '19 days'"]))
        out.append(insert("contracts", ["id", "student_id", "tutor_id", "subject_id", "grade_id",
                                        "teaching_mode", "price", "payment_period", "total_lession",
                                        "start_date", "end_date", "status", "created_by_user_id",
                                        "signed_by_user_id", "student_tutor_request_id", "preferred_schedule",
                                        "signed_at"],
                          [q(demo_contract), q(demo_student), q(demo_tutor), catalog("subjects", "Âm nhạc"),
                           grade(1), q("OFFLINE"), demo_price, q("PER_LESSON"), 3,
                           "CURRENT_DATE - 14", "CURRENT_DATE + 60", q("ACTIVE"), q(demo_tutor_user),
                           q(demo_student_user), q(demo_application), q("Thứ 3 và thứ 7, 17:30–19:00"),
                           "CURRENT_TIMESTAMP - INTERVAL '18 days'"]))
        for slot, day in enumerate((2, 6)):
            out.append(insert("contract_schedules", ["id", "contract_id", "day_of_week",
                                                     "start_time", "end_time"],
                              [sql_id("demo-finance-schedule", slot), q(demo_contract), day,
                               q("17:30"), q("19:00")]))
        for lesson_index in range(3):
            lesson_id = uid("demo-finance-lesson", lesson_index)
            installment_id = uid("demo-finance-installment", lesson_index)
            lesson_date = ("CURRENT_DATE - 12", "CURRENT_DATE - 5", "CURRENT_DATE + 2")[lesson_index]
            paid = lesson_index < 2
            out.append(insert("lessons", ["id", "contract_id", "title", "date", "start_time",
                                          "end_time", "teaching_mode", "status", "note"],
                              [q(lesson_id), q(demo_contract), q(f"Buổi {lesson_index + 1}: Âm nhạc lớp 1"),
                               lesson_date, q("17:30"), q("19:00"), q("OFFLINE"),
                               q("COMPLETED" if lesson_index == 0 else
                                 "PENDING_CONFIRMATION" if paid else "SCHEDULED"),
                               q("Luyện nghe nhịp và hát theo giai điệu đơn giản.")]))
            out.append(insert("contract_payment_installments", ["id", "contract_id", "lesson_id",
                                                              "sequence_number", "payment_period", "amount",
                                                              "due_date", "status"],
                              [q(installment_id), q(demo_contract), q(lesson_id), lesson_index + 1,
                               q("PER_LESSON"), demo_price, lesson_date,
                               q("PAID" if paid else "PENDING")]))
            if paid:
                paid_at = f"CURRENT_TIMESTAMP - INTERVAL '{13 - lesson_index * 7} days'"
                out.append(insert("payments", ["id", "user_id", "amount", "payment_method",
                                               "payment_type", "status", "transaction_code",
                                               "payment_installment_id", "reference_type", "reference_id",
                                               "paid_at", "created_at"],
                                  [sql_id("demo-finance-tuition", lesson_index), q(demo_student_user),
                                   demo_price, q("VNPAY"), q("TUITION_PAYMENT"), q("PAID"),
                                   q(f"LOCAL-DEMO-TUITION-{lesson_index + 1}"), q(installment_id),
                                   q("CONTRACT"), q(demo_contract), paid_at,
                                   f"{paid_at} - INTERVAL '1 hour'"]))
        out.append(wallet_credit(demo_tutor_user, demo_price, uid("demo-finance-payout"),
                                 "TUTOR_EARNING", "LESSON", uid("demo-finance-lesson", 0),
                                 "Thu nhập buổi Âm nhạc lớp 1 đã xác nhận",
                                 "CURRENT_TIMESTAMP - INTERVAL '3 days'"))
        for kind, owner, posting in (("TEACHING", demo_tutor_user, demo_posting),
                                     ("STUDYING", demo_student_user, demo_studying)):
            out.append(insert("payments", ["id", "user_id", "amount", "payment_method", "payment_type",
                                           "status", "transaction_code", "reference_type", "reference_id",
                                           "paid_at", "created_at"],
                              [sql_id("demo-finance-posting-fee", kind), q(owner), 100000, q("VNPAY"),
                               q(f"{kind}_REQUEST_FEE"), q("PAID"), q(f"LOCAL-DEMO-{kind}-FEE"),
                               q(f"{kind}_REQUEST"), q(posting),
                               "CURRENT_TIMESTAMP - INTERVAL '20 days'",
                               "CURRENT_TIMESTAMP - INTERVAL '20 days 1 hour'"]))
        demo_holder = bank_holder(expanded_name("tutor", 80))
        demo_account = "970000009980"
        demo_encrypted = encrypted_account(demo_account, finance_key)
        out.append(insert("bank_accounts", ["id", "user_id", "bank_code", "bank_name",
                                            "encrypted_account_number", "account_number_last_four",
                                            "account_holder_name"],
                          [sql_id("demo-finance-bank"), q(demo_tutor_user), q("VCB"), q("Vietcombank"),
                           q(demo_encrypted), q(demo_account[-4:]), q(demo_holder)]))
        demo_withdrawal = uid("demo-finance-withdrawal")
        demo_withdrawal_tx = uid("demo-finance-withdrawal-tx")
        out.append(f"""
            WITH reserved AS (
                INSERT INTO wallet_transactions
                    (id, wallet_id, transaction_type, purpose, amount, balance_after,
                     pending_balance_after, reference_type, reference_id, description,
                     idempotency_key, status, created_at)
                SELECT {q(demo_withdrawal_tx)}::uuid, w.id, 'DEBIT', 'WITHDRAWAL', 100000,
                       w.balance - 100000, w.pending_balance + 100000,
                       'WITHDRAWAL_REQUEST', {q(demo_withdrawal)}::uuid,
                       'Yêu cầu rút thu nhập gia sư', 'demo-finance-withdrawal',
                       'PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day'
                FROM wallets w WHERE w.user_id={q(demo_tutor_user)}::uuid AND w.balance >= 100000
                ON CONFLICT DO NOTHING RETURNING wallet_id, amount
            )
            UPDATE wallets w SET balance=w.balance-reserved.amount,
                                 pending_balance=w.pending_balance+reserved.amount,
                                 updated_at=CURRENT_TIMESTAMP
            FROM reserved WHERE w.id=reserved.wallet_id;
        """)
        out.append(insert("withdrawal_requests", ["id", "wallet_id", "amount", "bank_code",
                                                  "bank_name", "encrypted_account_number",
                                                  "account_number_last_four", "account_holder_name",
                                                  "idempotency_key", "status", "wallet_transaction_id",
                                                  "created_at"],
                          [q(demo_withdrawal),
                           f"(SELECT id FROM wallets WHERE user_id={q(demo_tutor_user)}::uuid)",
                           100000, q("VCB"), q("Vietcombank"), q(demo_encrypted),
                           q(demo_account[-4:]), q(demo_holder), q("demo-finance-withdrawal"),
                           q("PENDING"), q(demo_withdrawal_tx),
                           "CURRENT_TIMESTAMP - INTERVAL '1 day'"]))

        # Top-ups create both a paid payment and the matching wallet credit. A mix of funded
        # and unused accounts is natural; always fund the published demo student account.
        finance_students = [row[1] for row in students]
        finance_students += [uid("user", f"expanded-student-{ordinal}") for ordinal in range(320)]
        for index, student_user in enumerate(finance_students):
            if index >= 62 and index < 64:
                continue  # inactive / blocked legacy accounts
            if index < 64 and index % 4 == 0:
                continue  # existing top-up already has a matching wallet credit
            if index % 3 != 0 and index != 64 + 80:
                continue
            amount = 450000 + index % 7 * 150000
            payment_id = uid("finance-topup", index)
            paid_at = f"CURRENT_TIMESTAMP - INTERVAL '{5 + index % 70} days'"
            out.append(insert("payments", ["id", "user_id", "amount", "payment_method", "payment_type",
                                           "status", "transaction_code", "reference_type", "reference_id",
                                           "paid_at", "created_at"],
                              [q(payment_id), q(student_user), amount, q("VNPAY"), q("WALLET_TOP_UP"),
                               q("PAID"), q(f"LOCAL-FINANCE-TOPUP-{index:03d}"), q("PAYMENT"),
                               q(payment_id), paid_at, f"{paid_at} - INTERVAL '1 hour'"]))
            out.append(wallet_credit(student_user, amount, uid("finance-topup-credit", index),
                                     "TOP_UP", "PAYMENT", payment_id, "Nạp tiền vào ví học viên", paid_at))

        # Refund outcomes: pending, under review, partial and full refund, and rejected.
        # The approved case (i=35) missed 30 of 90 minutes, so refund=floor(price/3);
        # the tutor receives only the taught portion of that disputed lesson.
        for refund_index in (5, 10, 20, 35, 50):
            scenario = next(row for row in contract_scenarios if row[0] == refund_index)
            _, _, student_user, tutor_user, price, _ = scenario
            refund_id = uid("refund", refund_index)
            out.append(f"UPDATE refund_requests SET created_at=CURRENT_TIMESTAMP - INTERVAL '{1 + refund_index % 4} days' "
                       f"WHERE id={q(refund_id)}::uuid;")
            out.append(f"UPDATE refund_request_lessons SET created_at=CURRENT_TIMESTAMP - INTERVAL '{1 + refund_index % 4} days' "
                       f"WHERE refund_request_id={q(refund_id)}::uuid;")
            if refund_index == 20:
                out.append(f"UPDATE refund_requests SET status='IN_REVIEW', "
                           f"employee_id={sql_id('employee', 'staff-0')}, "
                           "tutor_response_deadline=CURRENT_TIMESTAMP - INTERVAL '1 day' "
                           f"WHERE id={q(refund_id)}::uuid;")
            elif refund_index == 50:
                out.append(f"UPDATE refund_requests SET status='REJECTED', "
                           f"employee_id={sql_id('employee', 'staff-0')}, "
                           "tutor_response_deadline=CURRENT_TIMESTAMP - INTERVAL '3 days', "
                           "reviewed_at=CURRENT_TIMESTAMP - INTERVAL '1 day', "
                           f"rejection_reason={q('Đối chiếu lịch và xác nhận buổi học cho thấy gia sư dạy đủ thời lượng.')} "
                           f"WHERE id={q(refund_id)}::uuid;")
            elif refund_index == 10:
                refund_transaction = uid("finance-refund-credit", refund_index)
                out.append(f"UPDATE payments SET status='REFUNDED', refunded_amount={price} "
                           f"WHERE id={sql_id('tuition-payment', refund_index, 0)};")
                out.append(wallet_credit(student_user, price, refund_transaction,
                                         "CONTRACT_REFUND", "REFUND_REQUEST", refund_id,
                                         "Hoàn toàn bộ học phí buổi gia sư vắng mặt",
                                         "CURRENT_TIMESTAMP - INTERVAL '1 day'"))
                lesson_id = uid("lesson", refund_index, 0)
                out.append(f"UPDATE refund_request_lessons SET decision='TUTOR_ABSENT' "
                           f"WHERE refund_request_id={q(refund_id)}::uuid;")
                out.append(f"UPDATE lessons SET status='CANCELLED' WHERE id={q(lesson_id)}::uuid;")
                out.append(f"UPDATE refund_requests SET status='REFUNDED', "
                           f"employee_id={sql_id('employee', 'staff-0')}, "
                           "tutor_response_deadline=CURRENT_TIMESTAMP - INTERVAL '2 days', "
                           "reviewed_at=CURRENT_TIMESTAMP - INTERVAL '1 day', "
                           f"refund_amount={price}, tutor_share_amount=0, "
                           f"wallet_transaction_id={q(refund_transaction)}::uuid "
                           f"WHERE id={q(refund_id)}::uuid;")
            elif refund_index == 35:
                refund_amount = price // 3
                refund_transaction = uid("finance-refund-credit", refund_index)
                out.append(f"UPDATE payments SET refunded_amount={refund_amount} "
                           f"WHERE id={sql_id('tuition-payment', refund_index, 1)};")
                out.append(wallet_credit(student_user, refund_amount, refund_transaction,
                                         "CONTRACT_REFUND", "REFUND_REQUEST", refund_id,
                                         "Hoàn phần thời lượng buổi học chưa được dạy",
                                         "CURRENT_TIMESTAMP - INTERVAL '1 day'"))
                lesson_id = uid("lesson", refund_index, 0)
                out.append(wallet_credit(tutor_user, price - refund_amount,
                                         uid("lesson-payout", refund_index, 0),
                                         "TUTOR_EARNING", "LESSON", lesson_id,
                                         "Thu nhập phần buổi học đã dạy sau khi xử lý hoàn tiền",
                                         "CURRENT_TIMESTAMP - INTERVAL '1 day'"))
                out.append(f"UPDATE refund_request_lessons SET decision='TUTOR_LATE_OR_SHORT' "
                           f"WHERE refund_request_id={q(refund_id)}::uuid;")
                out.append(f"UPDATE lessons SET status='COMPLETED' WHERE id={q(lesson_id)}::uuid;")
                out.append(f"UPDATE refund_requests SET status='REFUNDED', "
                           f"employee_id={sql_id('employee', 'staff-0')}, "
                           "tutor_response_deadline=CURRENT_TIMESTAMP - INTERVAL '3 days', "
                           "reviewed_at=CURRENT_TIMESTAMP - INTERVAL '1 day', "
                           f"refund_amount={refund_amount}, tutor_share_amount=0, "
                           f"wallet_transaction_id={q(refund_transaction)}::uuid "
                           f"WHERE id={q(refund_id)}::uuid;")
    out.extend(refreshes)
    if finance_key is not None:
        for index, _, _, _, _, completed in contract_scenarios:
            if not completed and index % 4 != 0 and index % 15 != 5 and index != 10:
                out.append(f"UPDATE lessons SET status='PENDING_CONFIRMATION' "
                           f"WHERE id={sql_id('lesson', index, 0)} AND status='COMPLETED';")
        out.append(f"UPDATE lessons SET status='PENDING_CONFIRMATION' "
                   f"WHERE id={sql_id('demo-finance-lesson', 1)} AND status='COMPLETED';")
    # Lifecycle states that do not arise from the active matching/finance stories above.
    # Connected applications point to signed contracts; cancelled applications have no contract.
    for index in range(62):
        out.append(f"UPDATE student_tutor_requests SET status='CONNECTED' "
                   f"WHERE id={sql_id('application', index, 0)} AND EXISTS "
                   f"(SELECT 1 FROM contracts WHERE student_tutor_request_id={sql_id('application', index, 0)} "
                   "AND status IN ('ACTIVE','COMPLETED'));")
    if finance_key is not None:
        out.append(f"UPDATE student_tutor_requests SET status='CONNECTED' "
                   f"WHERE id={sql_id('demo-finance-application')};")
    # A tutor-originated offer also reaches CONNECTED through a signed contract. It has an
    # upcoming lesson and a pending installment, so no tuition or payout exists yet.
    offer_id = uid("offer", 2, 0)
    offer_contract = uid("offer-origin-contract")
    offer_lesson = uid("offer-origin-lesson")
    out.append(f"""
        INSERT INTO contracts (id, student_id, tutor_id, subject_id, grade_id, teaching_mode,
                               price, payment_period, total_lession, start_date, end_date,
                               status, created_by_user_id, signed_by_user_id,
                               tutor_student_request_id, preferred_schedule, signed_at, created_at)
        SELECT {q(offer_contract)}::uuid, sr.student_id, r.tutor_id, sr.subject_id,
               r.grade_id, r.teaching_mode, r.proposed_price, 'PER_LESSON', 8,
               CURRENT_DATE + 3, CURRENT_DATE + 90, 'ACTIVE', t.user_id, s.user_id,
               r.id, sr.preferred_schedule, CURRENT_TIMESTAMP - INTERVAL '1 day',
               CURRENT_TIMESTAMP - INTERVAL '2 days'
        FROM tutor_student_requests r
        JOIN studying_requests sr ON sr.id=r.studying_request_id
        JOIN tutors t ON t.id=r.tutor_id JOIN students s ON s.id=sr.student_id
        WHERE r.id={q(offer_id)}::uuid AND r.proposed_price <= sr.max_price
        ON CONFLICT DO NOTHING;
    """)
    for slot in range(2):
        schedule_id = sql_id('offer-origin-schedule') if slot == 0 else sql_id('offer-origin-schedule', slot)
        out.append(f"""
            INSERT INTO contract_schedules (id, contract_id, day_of_week, start_time, end_time)
            SELECT {schedule_id}::uuid, {q(offer_contract)}::uuid,
                   a.day_of_week, a.start_time, a.end_time
            FROM tutor_student_requests r
            JOIN studying_request_availabilities a ON a.studying_request_id=r.studying_request_id
            WHERE r.id={q(offer_id)}::uuid
            ORDER BY a.day_of_week, a.start_time LIMIT 1 OFFSET {slot}
            ON CONFLICT DO NOTHING;
        """)
    out.append(insert("lessons", ["id", "contract_id", "title", "date", "start_time", "end_time",
                                  "teaching_mode", "status"],
                      [q(offer_lesson), q(offer_contract), q("Buổi đầu tiên theo hợp đồng"),
                       "CURRENT_DATE + 7", q("18:00"), q("19:30"), q("ONLINE"), q("SCHEDULED")]))
    out.append(insert("contract_payment_installments", ["id", "contract_id", "lesson_id",
                                                      "sequence_number", "payment_period", "amount",
                                                      "due_date", "status"],
                      [sql_id("offer-origin-installment"), q(offer_contract), q(offer_lesson), 1,
                       q("PER_LESSON"), 200000, "CURRENT_DATE + 7", q("PENDING")]))
    out.append(f"UPDATE tutor_student_requests SET status='CONNECTED' "
               f"WHERE id={q(offer_id)}::uuid AND EXISTS "
               f"(SELECT 1 FROM contracts WHERE id={q(offer_contract)}::uuid);")
    out.append(f"UPDATE studying_requests SET status='MATCHED' WHERE id=("
               f"SELECT studying_request_id FROM tutor_student_requests WHERE id={q(offer_id)}::uuid);")
    out.append(f"UPDATE teaching_requests SET status='MATCHED' WHERE id=("
               f"SELECT teaching_request_id FROM student_tutor_requests "
               f"WHERE id={sql_id('application', 2, 0)});")
    out.append(f"UPDATE teaching_requests SET status='CANCELLED' "
               f"WHERE id={sql_id('teaching', 22, 0)} AND status='CLOSED';")
    out.append(f"UPDATE studying_requests SET status='CANCELLED' "
               f"WHERE id={sql_id('studying', 26, 0)} AND status='CLOSED';")
    out.append(f"UPDATE student_tutor_requests SET status='CANCELLED' "
               f"WHERE id={sql_id('application', 3, 2)} AND status='PENDING';")
    out.append(f"UPDATE tutor_student_requests SET status='CANCELLED' "
               f"WHERE id={sql_id('offer', 4, 2)} AND status='PENDING';")
    out.append(f"UPDATE tutors SET profile_status='REJECTED', "
               f"profile_reviewed_by={sql_id('employee', 'staff-0')}, "
               "profile_reviewed_at=CURRENT_TIMESTAMP - INTERVAL '3 days', "
               f"profile_rejection_reason={q('Hồ sơ đào tạo chưa đủ thông tin để xác minh.')} "
               f"WHERE id={sql_id('tutor', 17)};")
    out.append(f"UPDATE teaching_requests SET status='REJECTED', "
               "reviewed_at=CURRENT_TIMESTAMP - INTERVAL '3 days', "
               f"rejection_reason={q('Hồ sơ gia sư chưa được duyệt.')} "
               f"WHERE tutor_id={sql_id('tutor', 17)} AND status='PENDING_REVIEW';")
    out.append(f"UPDATE teaching_requests SET status='DRAFT' "
               f"WHERE id={sql_id('teaching', 11, 0)} AND status='CLOSED';")
    out.append(f"UPDATE studying_requests SET status='DRAFT' "
               f"WHERE id={sql_id('studying', 13, 0)} AND status='CLOSED';")
    out.append(insert("certificates", ["id", "tutor_id", "name", "issuing_organization",
                                       "issue_date", "expiry_date", "status"],
                      [sql_id("expired-certificate"), sql_id("tutor", 2),
                       q("Chứng chỉ bồi dưỡng nghiệp vụ giảng dạy"), q("Trung tâm bồi dưỡng giáo viên"),
                       q("2019-04-01"), q("2024-04-01"), q("EXPIRED")]))
    for index, status, resolution in (
        (0, "RESOLVED", "Hai bên thống nhất lịch bù và gia sư đã xác nhận lại bằng văn bản."),
        (16, "IN_REVIEW", None),
        (32, "AWAITING_EVIDENCE", None),
        (48, "REJECTED", "Đối chiếu lịch và xác nhận của hai bên, chưa đủ căn cứ cho khiếu nại.")
    ):
        assignments = [f"status={q(status)}", f"employee_id={sql_id('employee', 'staff-0')}"]
        if resolution:
            assignments += [f"resolution={q(resolution)}",
                            "resolved_at=CURRENT_TIMESTAMP - INTERVAL '1 day'"]
        out.append(f"UPDATE complaints SET {', '.join(assignments)} WHERE id={sql_id('complaint', index)};")
    for index, status in ((8, "CANCELLED"), (9, "PENDING")):
        out.append(insert("complaints", ["id", "user_id", "contract_id", "title", "content",
                                         "status", "category", "created_at"],
                          [sql_id("complaint-extra", index), q(students[index][1]),
                           sql_id("contract", index), q("Trao đổi về lịch học"),
                           q("Gia đình cần xác nhận lại lịch buổi học và đã liên hệ bộ phận hỗ trợ."),
                           q(status), q("OTHER"), f"CURRENT_TIMESTAMP - INTERVAL '{index % 4 + 2} days'"]))
    out.append(f"UPDATE lessons SET status='CONFIRMED' WHERE id={sql_id('lesson', 7, 1)};")
    out.append(f"UPDATE lessons SET status='CANCELLED' WHERE id={sql_id('lesson', 11, 2)};")
    out.append(f"UPDATE contract_payment_installments "
               "SET status='PENDING', lesson_id=NULL, due_date=NULL "
               f"WHERE id={sql_id('installment', 11, 2)};")
    if finance_key is not None:
        for offset, status in enumerate(("PENDING", "FAILED", "CANCELLED")):
            user = uid("user", f"expanded-student-{200 + offset}")
            out.append(insert("payments", ["id", "user_id", "amount", "payment_method",
                                           "payment_type", "status", "transaction_code", "created_at",
                                           "expires_at"],
                              [sql_id("topup-attempt", offset), q(user), 750000 + offset * 100000,
                               q("VNPAY"), q("WALLET_TOP_UP"), q(status),
                               q(f"LOCAL-TOPUP-ATTEMPT-{offset}"),
                               f"CURRENT_TIMESTAMP - INTERVAL '{offset + 1} days'",
                               "CURRENT_TIMESTAMP + INTERVAL '1 day'" if status == "PENDING" else
                               f"CURRENT_TIMESTAMP - INTERVAL '{offset} days'"]))
        out.append(f"UPDATE withdrawal_requests SET status='PROCESSING' "
                   f"WHERE id={sql_id('withdrawal', 3)} AND status='PENDING';")
        out.append(f"""
            WITH released AS (
                UPDATE wallet_transactions SET status='CANCELLED', updated_at=CURRENT_TIMESTAMP
                WHERE id={sql_id('withdrawal-tx', 6)} AND status='PENDING'
                RETURNING wallet_id, amount
            )
            UPDATE wallets w SET balance=w.balance+released.amount,
                                 pending_balance=w.pending_balance-released.amount,
                                 updated_at=CURRENT_TIMESTAMP
            FROM released WHERE w.id=released.wallet_id;
        """)
        out.append(f"UPDATE withdrawal_requests SET status='CANCELLED' "
                   f"WHERE id={sql_id('withdrawal', 6)} AND status='PENDING';")
        # The backend derives a PER_LESSON unit price from the complete set of
        # installments. Keep future installments present but unpaid so a newly
        # confirmed lesson uses the agreed contract price.
        out.append("""
            INSERT INTO contract_payment_installments
                (id, contract_id, sequence_number, payment_period, amount, status)
            SELECT md5('fixture-future-installment:' || c.id::text || ':' || sequence.value)::uuid,
                   c.id, sequence.value, 'PER_LESSON', c.price, 'PENDING'
            FROM contracts c
            CROSS JOIN LATERAL generate_series(1, c.total_lession) AS sequence(value)
            WHERE c.deleted_at IS NULL AND c.status IN ('ACTIVE','COMPLETED')
              AND c.payment_period='PER_LESSON'
              AND NOT EXISTS (
                  SELECT 1 FROM contract_payment_installments installment
                  WHERE installment.contract_id=c.id
                    AND installment.sequence_number=sequence.value
                    AND installment.deleted_at IS NULL
              )
            ON CONFLICT (contract_id, sequence_number) DO NOTHING;
        """)
        # Publication payment precedes review, so every non-draft posting has
        # exactly one paid fee even when it was later rejected, closed or cancelled.
        # Resolve statuses after the lifecycle updates above and avoid duplicating
        # fees that were already inserted for the original and demo stories.
        out.append("""
            WITH postings AS (
                SELECT tr.id, t.user_id, tr.created_at, 'TEACHING'::text AS kind,
                       tr.quantity
                FROM teaching_requests tr JOIN tutors t ON t.id=tr.tutor_id
                WHERE tr.deleted_at IS NULL AND tr.status <> 'DRAFT'
                UNION ALL
                SELECT sr.id, s.user_id, sr.created_at, 'STUDYING'::text AS kind,
                       1 AS quantity
                FROM studying_requests sr JOIN students s ON s.id=sr.student_id
                WHERE sr.deleted_at IS NULL AND sr.status <> 'DRAFT'
            )
            INSERT INTO payments (id, user_id, amount, payment_method, payment_type,
                                  status, transaction_code, reference_type, reference_id,
                                  paid_at, created_at)
            SELECT md5('fixture-posting-fee:' || p.kind || ':' || p.id::text)::uuid,
                   p.user_id, 100000 * p.quantity, 'VNPAY', p.kind || '_REQUEST_FEE',
                   'PAID', 'LOCAL-POST-FEE-' || upper(replace(p.id::text, '-', '')),
                   p.kind || '_REQUEST', p.id,
                   p.created_at + INTERVAL '1 day 1 hour',
                   p.created_at + INTERVAL '1 day'
            FROM postings p
            WHERE NOT EXISTS (
                SELECT 1 FROM payments paid
                WHERE paid.deleted_at IS NULL AND paid.reference_id=p.id
                  AND paid.reference_type=p.kind || '_REQUEST'
                  AND paid.payment_type=p.kind || '_REQUEST_FEE'
                  AND paid.status IN ('PAID','REFUNDED')
            )
            ON CONFLICT (id) DO NOTHING;
        """)
        out.append("""
            UPDATE payments p SET amount=100000 * tr.quantity
            FROM teaching_requests tr
            WHERE p.reference_type='TEACHING_REQUEST' AND p.reference_id=tr.id
              AND p.payment_type='TEACHING_REQUEST_FEE' AND p.status IN ('PAID','REFUNDED')
              AND p.transaction_code LIKE 'LOCAL-%'
              AND p.amount<>100000 * tr.quantity;
        """)
        out.append("""
            DO $$
            DECLARE
                item RECORD;
                added RECORD;
            BEGIN
                FOR item IN
                    WITH rejected AS (
                        SELECT id, 'TEACHING_REQUEST'::text AS kind FROM teaching_requests
                        WHERE deleted_at IS NULL AND status='REJECTED'
                        UNION ALL
                        SELECT id, 'STUDYING_REQUEST'::text AS kind FROM studying_requests
                        WHERE deleted_at IS NULL AND status='REJECTED'
                    )
                    SELECT p.id payment_id, p.user_id, p.amount, p.reference_id,
                           p.reference_type, rejected.kind
                    FROM payments p JOIN rejected ON rejected.id=p.reference_id
                        AND rejected.kind=p.reference_type
                    WHERE p.deleted_at IS NULL AND p.status IN ('PAID','REFUNDED')
                      AND p.payment_type=rejected.kind || '_FEE'
                    ORDER BY p.id
                LOOP
                    INSERT INTO wallet_transactions
                        (id, wallet_id, transaction_type, purpose, amount,
                         balance_after, pending_balance_after, reference_type,
                         reference_id, description, idempotency_key, status, created_at)
                    SELECT md5('fixture-posting-refund:' || item.payment_id::text)::uuid,
                           w.id, 'CREDIT', item.kind || '_REFUND', item.amount,
                           w.balance + item.amount, w.pending_balance, item.kind,
                           item.reference_id, 'Refund for rejected posting',
                           'seed-posting-refund-' || item.payment_id::text,
                           'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1 day'
                    FROM wallets w WHERE w.user_id=item.user_id
                    ON CONFLICT DO NOTHING RETURNING wallet_id, amount INTO added;
                    IF FOUND THEN
                        UPDATE wallets SET balance=balance+added.amount,
                                           updated_at=CURRENT_TIMESTAMP
                        WHERE id=added.wallet_id;
                    END IF;
                    UPDATE payments SET status='REFUNDED' WHERE id=item.payment_id;
                END LOOP;
            END $$;
        """)
    if include_ai:
        for posting in changed_embedding_posts:
            out.append("""INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
                SELECT gen_random_uuid(), 'EMBED', 'TEACHING_REQUEST', """ + q(posting) + """::uuid,
                       'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM ai_enrichment_jobs WHERE job_type='EMBED'
                    AND entity_type='TEACHING_REQUEST' AND entity_id=""" + q(posting) + """::uuid
                    AND status IN ('PENDING','PROCESSING'));""")
    out.append("COMMIT;")
    return "\n".join(out)+"\n"


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--load",action="store_true",help="load into an isolated local database")
    parser.add_argument("--database",default="ptutor_realistic_data")
    parser.add_argument("--container",default="ptutor-postgres")
    parser.add_argument("--ai",action="store_true",help="include decision snapshots after the recommendation branch migration")
    parser.add_argument("--chatbot",action="store_true",help="include support requests after the chatbot branch migration")
    parser.add_argument("--finance",action="store_true",help="include encrypted bank accounts and withdrawal scenarios")
    args=parser.parse_args()
    sql=generate(args.ai,args.chatbot,bank_key() if args.finance else None)
    if not args.load:
        sys.stdout.buffer.write(sql.encode("utf-8"))
        return
    if args.database in ("ptutor","postgres"):
        parser.error("choose a dedicated local database, not ptutor or postgres")
    result=subprocess.run(["docker","exec","-i",args.container,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-d",args.database],input=sql.encode("utf-8"),capture_output=True)
    if result.returncode:
        sys.stderr.write(result.stderr.decode("utf-8",errors="replace"))
        raise SystemExit(result.returncode)
    print(f"Loaded local fixture into {args.database}.")


if __name__=="__main__":
    main()
