"""Generate repeatable, Vietnamese local test data for the dev schema.

The SQL is printed by default. Pass --load only for an isolated local database.
No network calls, external identities, or real payment credentials are used.
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
METHODS = [
    "Mỗi buổi kiểm tra phần cũ, giải thích bằng ví dụ rồi mới cho con tự làm bài.",
    "Chia kiến thức thành từng chủ đề nhỏ; cuối tuần có bài kiểm tra và nhận xét cụ thể.",
    "Ưu tiên tìm chỗ con chưa hiểu, giảng lại nền tảng trước khi luyện đề.",
    "Dùng sơ đồ và bài tập gần với chương trình ở trường, không thúc ép học quá nhanh.",
    "Cho học sinh trình bày cách nghĩ, sửa lỗi ngay trong lúc làm và giao bài vừa sức.",
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


def district(name):
    return f"(SELECT d.id FROM districts d JOIN provinces p ON p.id=d.province_id WHERE p.name='Hà Nội' AND d.name={q(name)} ORDER BY d.id LIMIT 1)"


def insert(table, columns, values):
    return f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({', '.join(map(str, values))}) ON CONFLICT DO NOTHING;"


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
    for name in TOPICS:
        out.append(insert("subjects",["id","name","status"],[sql_id("subject",name),q(name),q("ACTIVE")]))
    for level in range(6,13):
        out.append(insert("grades",["id","name","level","status"],[sql_id("grade",level),q(f"Lớp {level}"),level,q("ACTIVE")]))
    for index,(surname,given,function) in enumerate((("Trần Thu","Hà","CUSTOMER_SERVICE"),("Nguyễn Minh","Quân","ACCOUNTANT"))):
        key=f"staff-{index}"
        out.append(insert("users",["id","citizen_id","email","password","first_name","last_name","status"],
            [sql_id("user",key),q(f"079900000{index:03d}"),q(f"staff.{function.lower()}@ptutor.local"),q(HASH),q(surname),q(given),q("ACTIVE")]))
        out.append(insert("employees",["id","user_id","role","job_function"],
            [sql_id("employee",key),sql_id("user",key),2,q(function)]))
    tutors, students, teaching, studying, applications, offers, tutor_names = [], [], [], [], [], [], []
    for role, count in (("tutor", 84), ("student", 64)):
        for i in range(count):
            gender = "MALE" if (i % 2 == 0) else "FEMALE"
            given = (MALE if gender == "MALE" else FEMALE)[(i * 7 + i // 12) % 12]
            middle = (MIDDLE_MALE if gender == "MALE" else MIDDLE_FEMALE)[(i // 3) % 6]
            surname = FAMILIES[(i * 5 + i // 12) % 12]
            home = DISTRICTS[(i * 7) % len(DISTRICTS)]
            key = f"{role}-{i:03d}"
            user = uid("user", key)
            email = f"{role}.{i+1:03d}@ptutor.local"
            status="BLOCKED" if i==count-1 else "INACTIVE" if i==count-2 else "ACTIVE"
            account_status=status
            columns=["id","district_id","citizen_id","email","password","first_name","last_name","gender","status","date_of_birth","created_at"]
            citizen_number=(100000000 if role=="tutor" else 200000000)+i
            values=[q(user),district(home),q(f"079{citizen_number:09d}"),q(email),q(HASH),q(surname+' '+middle),q(given),q(gender),q(status),q(f"{1983+i%19}-0{1+i%9}-15"),f"CURRENT_TIMESTAMP - INTERVAL '{100+i%200} days'"]
            if status=="BLOCKED":
                columns += ["suspension_type","suspension_reason","suspended_at","suspended_until","suspended_by_user_id","suspension_count"]
                values += [q("TEMPORARY"),q("Tài khoản tạm khóa để xác minh phản ánh về lịch học."),"CURRENT_TIMESTAMP - INTERVAL '2 days'","CURRENT_TIMESTAMP + INTERVAL '12 days'","(SELECT user_id FROM employees WHERE role=1 ORDER BY id LIMIT 1)",1]
            out.append(insert("users",columns,values))
            if role == "tutor":
                subject = list(TOPICS)[i % len(TOPICS)]
                level, topic = TOPICS[subject][(i // len(TOPICS)) % len(TOPICS[subject])]
                method = METHODS[i % len(METHODS)]
                exp = 1 + (i * 7) % 13
                profile = uid("tutor", i)
                education = ["Đại học Sư phạm Hà Nội", "Đại học Khoa học Tự nhiên", "Đại học Ngoại ngữ", "Đại học Bách khoa Hà Nội"][i % 4]
                intro = f"Tôi dạy {subject} lớp {max(6,level-1)}–{min(12,level+1)} được {exp} năm, chú trọng {topic}. {method}"
                reviewer="(SELECT id FROM employees WHERE role=1 ORDER BY id LIMIT 1)"
                out.append(insert("tutors", ["id","user_id","introduction","experience_years","education","teaching_methodology","strength_subjects","target_student_type","profile_status","profile_reviewed_by","profile_reviewed_at"],
                    [q(profile),q(user),q(intro),exp,q(education),q(method),q(subject),q("Học sinh cần củng cố nền tảng" if i%3 else "Học sinh muốn luyện thi"),q("PENDING" if i%17==0 else "VERIFIED"),"NULL" if i%17==0 else reviewer,"NULL" if i%17==0 else "CURRENT_TIMESTAMP - INTERVAL '73 days'"]))
                tutors.append((profile, user, subject, level, home))
                tutor_names.append(surname+" "+middle+" "+given)
                if i%5 == 0:
                    cert_status="REJECTED" if i%15==0 else "PENDING" if i%10==0 else "VERIFIED"
                    out.append(insert("certificates",["id","tutor_id","name","issuing_organization","issue_date","status","rejection_reason","reviewed_by","reviewed_at"],
                        [sql_id("cert",i),q(profile),q("Bằng cử nhân ngành liên quan"),q(education),q("2021-06-30"),q(cert_status),q("Ảnh chứng chỉ chưa đọc rõ thông tin, vui lòng tải lại") if cert_status=="REJECTED" else "NULL","NULL" if cert_status=="PENDING" else "(SELECT id FROM employees WHERE role=1 ORDER BY id LIMIT 1)","NULL" if cert_status=="PENDING" else "CURRENT_TIMESTAMP - INTERVAL '5 days'"]))
                for j in range(2):
                    tlevel, ttopic = TOPICS[subject][(i+j)%len(TOPICS[subject])]
                    posting = uid("teaching",i,j)
                    mode = "ONLINE" if (i+j)%4==0 else "OFFLINE"
                    price = 140000+((i+j)%8)*20000
                    title = [f"Nhận kèm {subject} lớp {tlevel}",f"{subject} lớp {tlevel}: {ttopic}",f"Ôn tập {subject} cho học sinh lớp {tlevel}"][j+i%3 if j+i%3<3 else 2]
                    desc = f"Tập trung {ttopic}. {method} Có thể điều chỉnh tốc độ theo tình hình của học sinh."
                    status = "CLOSED" if account_status!="ACTIVE" else "PENDING_REVIEW" if i%17==0 or (i%19==0 and j==1) else "REJECTED" if i%23==0 and j==1 else "OPEN" if i%11 else "CLOSED"
                    out.append(insert("teaching_requests",["id","tutor_id","subject_id","title","description","quantity","expected_price","teaching_mode","preferred_schedule","status","reviewed_at","created_at"],
                        [q(posting),q(profile),catalog("subjects",subject),q(title),q(desc),1+i%3,price,q(mode),q("Thứ 3 và thứ 7, sau 18 giờ" if i%2 else "Thứ 2 và thứ 5, 19–21 giờ"),q(status),"NULL" if status=="PENDING_REVIEW" else "CURRENT_TIMESTAMP - INTERVAL '72 days'",f"CURRENT_TIMESTAMP - INTERVAL '{75+i%75} days'"]))
                    out.append(insert("grade_teaching_requests",["id","grade_id","teaching_request_id"],[sql_id("tr-grade",i,j),grade(tlevel),q(posting)]))
                    if mode=="OFFLINE":
                        out.append(insert("teaching_request_districts",["id","teaching_request_id","district_id"],[sql_id("tr-district",i,j),q(posting),district(home)]))
                    for k, day in enumerate((2+i%3, 6)):
                        out.append(insert("teaching_request_availabilities",["id","teaching_request_id","day_of_week","start_time","end_time"],[sql_id("tr-slot",i,j,k),q(posting),day,q("18:00"),q("20:00")]))
                    teaching.append((posting, profile, subject, tlevel, price, mode, status))
            else:
                profile = uid("student",i)
                level = 6+(i*5)%7
                need = NEEDS[i%len(NEEDS)]
                out.append(insert("students",["id","user_id","introduction","current_level","weak_points","goals_description"],
                    [q(profile),q(user),q(need),q(f"Lớp {level}"),q(need),q("Nắm chắc kiến thức và cải thiện kết quả học tập")]))
                students.append((profile,user,level,home))
                for j in range(1+(i%3==0)):
                    subject = list(TOPICS)[(i+j*3)%len(TOPICS)]
                    posting = uid("studying",i,j)
                    mode = "ONLINE" if (i+j)%4==0 else "OFFLINE"
                    need = NEEDS[(i+j)%len(NEEDS)]
                    status = "CLOSED" if account_status!="ACTIVE" else "PENDING_REVIEW" if i%21==0 and j==0 else "REJECTED" if i%25==0 and j==0 else "OPEN" if i%13 else "CLOSED"
                    out.append(insert("studying_requests",["id","district_id","student_id","subject_id","grade_id","title","description","learning_goals","max_price","learning_mode","preferred_schedule","status","created_at"],
                        [q(posting),district(home) if mode=="OFFLINE" else "NULL",q(profile),catalog("subjects",subject),grade(level),q(f"Tìm gia sư {subject} lớp {level}"),q(need),q(f"Cải thiện {subject} trong học kỳ này"),180000+(i%7)*20000,q(mode),q("Hai buổi tối trong tuần"),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{60+i%55} days'"]))
                    for k, day in enumerate((3,7)):
                        out.append(insert("studying_request_availabilities",["id","studying_request_id","day_of_week","start_time","end_time"],[sql_id("sr-slot",i,j,k),q(posting),day,q("18:30"),q("20:00")]))
                    studying.append((posting,profile,subject,level,mode,status))
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
            application_values=[q(req),q(student),grade(level),q(posting),price,q(mode),q(f"Em đang học lớp {level}, muốn được kèm phần {subject} vào buổi tối. Gia đình có thể trao đổi lịch cụ thể."),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                application_columns.append("responded_at")
                application_values.append("NULL" if status=="PENDING" else f"CURRENT_TIMESTAMP - INTERVAL '{age-2} days'")
            out.append(insert("student_tutor_requests",application_columns,application_values))
            if j==0:
                out.append(insert("favorite_tutors",["id","student_id","tutor_id"],[sql_id("favorite",i),q(student),q(tutor)]))
                contract = uid("contract",i)
                completed=i%9==0
                out.append(insert("contracts",["id","student_id","tutor_id","subject_id","grade_id","teaching_mode","price","payment_period","total_lession","start_date","end_date","status","created_by_user_id","signed_by_user_id","student_tutor_request_id","preferred_schedule","signed_at"],
                    [q(contract),q(student),q(tutor),catalog("subjects",subject),grade(level),q(mode),price,q("PER_LESSON"),3 if completed else 12,"CURRENT_DATE - 60" if completed else "CURRENT_DATE - 20","CURRENT_DATE - 1" if completed else "CURRENT_DATE + 70",q("COMPLETED" if completed else "ACTIVE"),q(tutor_user),q(user),q(req),q("Hai buổi tối mỗi tuần"),"CURRENT_TIMESTAMP - INTERVAL '67 days'" if completed else "CURRENT_TIMESTAMP - INTERVAL '21 days'"]))
                for k in range(3):
                    out.append(insert("lessons",["id","contract_id","title","date","start_time","end_time","teaching_mode","status","note"],
                        [sql_id("lesson",i,k),q(contract),q(f"Buổi {k+1}: ôn {subject} lớp {level}"),f"CURRENT_DATE - {25-k*7}" if completed else (f"CURRENT_DATE - {18-k*7}" if k<2 else "CURRENT_DATE + 3"),q("18:30"),q("20:00"),q(mode),q("COMPLETED" if completed or k<2 else "SCHEDULED"),q("Ôn phần còn yếu và luyện bài tập theo chương trình")]))
                    out.append(insert("contract_payment_installments",["id","contract_id","lesson_id","sequence_number","payment_period","amount","due_date","status"],
                        [sql_id("installment",i,k),q(contract),sql_id("lesson",i,k),k+1,q("PER_LESSON"),price,f"CURRENT_DATE - {18-k*7}" if completed else f"CURRENT_DATE + {k*7}",q("PAID" if completed or k<2 else "PENDING")]))
                if i%3==0:
                    columns=["id","student_id","tutor_id","rating","comment"]
                    values=[sql_id("review",i),q(student),q(tutor),4+i%2,q(REVIEW_TEXT[i%len(REVIEW_TEXT)])]
                    if include_ai:
                        columns.append("contract_id")
                        values.append(q(contract))
                    out.append(insert("reviews",columns,values))
                if i%16==0:
                    out.append(insert("complaints",["id","user_id","contract_id","title","content","status","category"],[sql_id("complaint",i),q(user),q(contract),q("Cần trao đổi lại lịch buổi học"),q("Buổi học gần đây dời lịch sát giờ. Gia đình muốn được hỗ trợ thống nhất lịch học tiếp theo."),q("PENDING"),q("OTHER")]))
                if i%17==0 and not completed:
                    out.append(insert("cancel_contract_requests",["id","contract_id","user_id","reason","status"],
                        [sql_id("cancel-request",i),q(contract),q(user),q("Gia đình thay đổi lịch học trong học kỳ mới, muốn dừng hợp đồng sau buổi đã hẹn."),q("PENDING")]))
                if i%20==0:
                    refund=uid("refund",i)
                    out.append(insert("refund_requests",["id","contract_id","student_id","description","status","tutor_response_deadline","unit_price"],
                        [q(refund),q(contract),q(student),q("Buổi học kết thúc sớm khoảng 30 phút, gia đình đề nghị kiểm tra phần học phí tương ứng."),q("PENDING"),"CURRENT_TIMESTAMP + INTERVAL '3 days'",price]))
                    out.append(insert("refund_request_lessons",["id","refund_request_id","lesson_id","claimed_outcome","missed_minutes"],
                        [sql_id("refund-lesson",i),q(refund),sql_id("lesson",i,0),q("TUTOR_LATE_OR_SHORT"),30]))
        out.append(insert("notifications",["id","user_id","title","content","type","is_read"],[sql_id("notification",i),q(user),q("Cập nhật yêu cầu học"),q("Yêu cầu học của bạn đã được ghi nhận. Bạn có thể xem tiến độ trong tài khoản."),q("SYSTEM"),"true" if i%3 else "false"]))
        if i%4==0:
            wallet=uid("wallet",i)
            out.append(insert("wallets",["id","user_id","balance","pending_balance"],[q(wallet),q(user),500000,0]))
            out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount","balance_after","pending_balance_after","status","idempotency_key","description"],
                [sql_id("wallet-tx",i),q(wallet),q("CREDIT"),q("TOP_UP"),500000,500000,0,q("COMPLETED"),q(f"realistic-data-topup-{i}"),q("Nạp tiền vào ví học viên")]))
            out.append(insert("payments",["id","user_id","amount","payment_method","payment_type","status","transaction_code","paid_at"],
                [sql_id("payment",i),q(user),500000,q("VNPAY"),q("WALLET_TOP_UP"),q("PAID"),q(f"LOCAL-TOPUP-{i:03d}"),"CURRENT_TIMESTAMP - INTERVAL '15 days'"]))
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
            values=[q(offer),q(tutor),grade(level),q(sr),200000,q(mode),q(f"Tôi có kinh nghiệm dạy {subject} lớp {level} và có thể trao đổi lịch học với gia đình."),q(status),f"CURRENT_TIMESTAMP - INTERVAL '{age} days'"]
            if include_ai:
                columns.append("responded_at")
                values.append("NULL" if status=="PENDING" else f"CURRENT_TIMESTAMP - INTERVAL '{age-2} days'")
            out.append(insert("tutor_student_requests",columns,values))
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
    if include_chatbot:
        # A real support queue is more useful than fabricated assistant answers or citations.
        for i in range(36):
            role="STUDENT" if i%3 else "TUTOR"
            user=students[i%len(students)][1] if role=="STUDENT" else tutors[i%len(tutors)][1]
            status=("OPEN","IN_PROGRESS","ANSWERED","CLOSED")[i%4]
            answer=SUPPORT_ANSWERS[i%len(SUPPORT_ANSWERS)] if status in ("ANSWERED","CLOSED") else None
            out.append(insert("support_requests",["id","code","user_id","user_role","question","source","page_path","status","answer","assigned_employee_id","assigned_at","created_at","updated_at","answered_at","closed_at"],
                [sql_id("support",i),q(f"HT-LOCAL-{i+1:04d}"),q(user),q(role),q(SUPPORT_QUESTIONS[i%len(SUPPORT_QUESTIONS)]),q("MENU" if i%5==0 else "ASSISTANT_ABSTAIN"),q("/my-support-requests"),q(status),q(answer),"NULL" if status=="OPEN" else sql_id("employee","staff-0"),"NULL" if status=="OPEN" else "CURRENT_TIMESTAMP - INTERVAL '1 day'",f"CURRENT_TIMESTAMP - INTERVAL '{2+i%18} days'",f"CURRENT_TIMESTAMP - INTERVAL '{i%2} days'","NULL" if answer is None else "CURRENT_TIMESTAMP - INTERVAL '1 day'","CURRENT_TIMESTAMP" if status=="CLOSED" else "NULL"]))
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
            out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount","balance_after","pending_balance_after","status","idempotency_key","description"],
                [sql_id("tutor-earning",i),q(wallet),q("CREDIT"),q("TUTOR_EARNING"),1200000,1200000,0,q("COMPLETED"),q(f"local-tutor-earning-{i}"),q("Thu nhập từ các buổi học đã xác nhận")]))
            if pending or completed:
                out.append(insert("wallet_transactions",["id","wallet_id","transaction_type","purpose","amount","balance_after","pending_balance_after","status","idempotency_key","description"],
                    [sql_id("withdrawal-tx",i),q(wallet),q("DEBIT"),q("WITHDRAWAL"),300000,900000,300000 if pending else 0,q("PENDING" if pending else "COMPLETED"),q(f"local-withdrawal-{i}"),q("Rút thu nhập gia sư")]))
            if i<12:
                status=("PENDING","REJECTED","COMPLETED")[i%3]
                out.append(insert("withdrawal_requests",["id","wallet_id","amount","bank_code","bank_name","encrypted_account_number","account_number_last_four","account_holder_name","idempotency_key","status","wallet_transaction_id","reviewed_by_user_id","reviewed_at","rejection_reason","completed_at","transfer_reference"],
                    [sql_id("withdrawal",i),q(wallet),300000,q("VCB" if i%2 else "TCB"),q("Vietcombank" if i%2 else "Techcombank"),q(encrypted),q(account[-4:]),q(holder),q(f"local-withdrawal-request-{i}"),q(status),sql_id("withdrawal-tx",i) if pending or completed else "NULL","NULL" if pending else "(SELECT user_id FROM employees WHERE role=1 ORDER BY id LIMIT 1)","NULL" if pending else "CURRENT_TIMESTAMP - INTERVAL '2 days'",q("Thông tin người thụ hưởng chưa khớp") if status=="REJECTED" else "NULL","CURRENT_TIMESTAMP - INTERVAL '1 day'" if completed else "NULL",q(f"LOCAL-TRANSFER-{i:03d}") if completed else "NULL"]))
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
