"""26a: the simulated market with hidden attributes (design 09, 9.1-9.3).

    python scripts/recommendation_eval/world.py        # writes out/world.json

Two layers. The hidden attributes (needs, budget, schedule, what each tutor really accepts, reliability) are
drawn with a fixed seed. The visible text (profiles, postings) is written from them with templates and
variations: synonyms, abbreviations, typos and some sketchy profiles. The ranking only ever sees the text and
the criteria typed into a search; the answer key (rel, P(accept)) is computed from the hidden layer.
"""

import math
import random

from common import OUT, SCENARIO, SEED, uid, write_json
from textonly import CONTRARY, EXCLUDED_TAGS, NEEDS, adjust

SUBJECTS = ["Toán", "Tiếng Anh", "Vật lý"]
CITIES = {
    "Hà Nội": ["Cầu Giấy", "Đống Đa", "Ba Đình", "Hà Đông", "Hai Bà Trưng", "Hoàng Mai"],
    "Hồ Chí Minh": ["Bến Thành", "Bình Thạnh", "Bình Tân", "Bàn Cờ", "An Đông", "Bình Tây"],
}
LOW_RELIABILITY_SHARE = 0.12
UNFOUNDED_COMPLAINT_SHARE = 0.08

# What changes per scenario (plan of 21/10/2026, 2.1). "base" must keep drawing exactly the same world.
SCENARIOS = {
    "base": {"subjects": SUBJECTS, "subject_weights": [0.45, 0.35, 0.2], "cities": CITIES, "tutors": 120,
             "students": 60, "famous": 0.0, "new": 0.0, "t2s_tutors": None,
             "tutor_modes": [0.35, 0.3, 0.35], "student_modes": [0.35, 0.25, 0.4]},
    # Many candidates per search and competition for places (2.2): 2 subjects, 1 city, 4 districts; 15% of the
    # tutors are popular with only 1-2 places, 20% are new (no history, no review).
    "crowded": {"subjects": ["Toán", "Tiếng Anh"], "subject_weights": [0.55, 0.45],
                "cities": {"Hà Nội": ["Cầu Giấy", "Đống Đa", "Ba Đình", "Hai Bà Trưng"]}, "tutors": 600,
                "students": 300, "famous": 0.15, "new": 0.20, "t2s_tutors": 150,
                # Mostly in person in the same 4 districts, so that both sides compete for the same people.
                "tutor_modes": [0.7, 0.05, 0.25], "student_modes": [0.6, 0.05, 0.35]},
    # What a student needs is only in the prose (2.3); enough candidates per search for the order to matter.
    "text-only": {"subjects": ["Toán", "Tiếng Anh"], "subject_weights": [0.5, 0.5],
                  "cities": {"Hà Nội": ["Cầu Giấy", "Đống Đa", "Ba Đình", "Hai Bà Trưng"]}, "tutors": 300,
                  "students": 120, "famous": 0.0, "new": 0.0, "t2s_tutors": 0,
                  "tutor_modes": [0.7, 0.05, 0.25], "student_modes": [0.6, 0.05, 0.35]},
}
TEXT_ONLY = SCENARIO == "text-only"
S = SCENARIOS.get(SCENARIO, SCENARIOS["base"])

GOALS_BY_GRADE = {
    6: ["cai_thien_diem", "hoc_vuot", "hoc_sinh_gioi"],
    7: ["cai_thien_diem", "hoc_vuot", "hoc_sinh_gioi"],
    8: ["cai_thien_diem", "hoc_sinh_gioi", "hoc_vuot"],
    9: ["thi_vao_10", "thi_chuyen", "cai_thien_diem"],
    10: ["cai_thien_diem", "hoc_sinh_gioi", "hoc_vuot"],
    11: ["cai_thien_diem", "thi_dai_hoc", "hoc_sinh_gioi"],
    12: ["thi_dai_hoc", "thi_tot_nghiep_thpt", "cai_thien_diem"],
}
TOPICS = {
    "Toán": ["dai_so", "hinh_hoc_phang", "phuong_trinh_he_phuong_trinh", "ham_so_do_thi", "chung_minh",
             "giai_tich", "hinh_hoc_khong_gian", "xac_suat_thong_ke"],
    "Tiếng Anh": ["ngu_phap", "tu_vung", "doc_hieu", "nghe", "noi", "viet", "phat_am"],
    "Vật lý": ["co_hoc", "dien_hoc", "quang_hoc", "nhiet_hoc", "dao_dong_song", "vat_ly_hat_nhan"],
}
STYLES = ["cham_chac", "giai_thich_ky", "ke_hoach_ro", "kien_nhan", "luyen_tap_nhieu", "nghiem_khac", "tu_duy",
          "tuong_tac_cong_nghe", "vui_ve"]
SEGMENTS = ["mat_goc", "trung_binh", "kha", "gioi", "can_kem_cap"]

# Visible wording of each code: several ways people write it, so that A1 and the embedding have work to do.
PHRASES = {
    "cai_thien_diem": ["cải thiện điểm trên lớp", "nâng điểm kiểm tra", "lấy lại điểm số", "tăng điểm tbm"],
    "hoc_vuot": ["học vượt chương trình", "học trước kiến thức lớp trên", "học nhanh hơn chương trình"],
    "hoc_sinh_gioi": ["ôn thi học sinh giỏi", "luyện thi HSG", "bồi dưỡng đội tuyển"],
    "thi_vao_10": ["ôn thi vào lớp 10", "luyện thi vào 10 công lập", "ôn thi chuyển cấp"],
    "thi_chuyen": ["thi vào trường chuyên", "luyện thi chuyên", "ôn thi lớp 10 chuyên"],
    "thi_dai_hoc": ["luyện thi đại học", "ôn thi ĐH", "thi đánh giá năng lực và đại học"],
    "thi_tot_nghiep_thpt": ["ôn thi tốt nghiệp THPT", "ôn thi tốt nghiệp", "thi TN THPT"],
    "dai_so": ["đại số", "biến đổi biểu thức", "đại số cơ bản"],
    "hinh_hoc_phang": ["hình học phẳng", "hình học", "bài hình"],
    "phuong_trinh_he_phuong_trinh": ["phương trình và hệ phương trình", "giải phương trình", "hệ pt"],
    "ham_so_do_thi": ["hàm số, đồ thị", "khảo sát hàm số", "đồ thị hàm số"],
    "chung_minh": ["bài chứng minh", "chứng minh hình", "các bài chứng minh khó"],
    "giai_tich": ["giải tích", "đạo hàm, tích phân", "giải tích 12"],
    "hinh_hoc_khong_gian": ["hình học không gian", "hình không gian", "hhkg"],
    "xac_suat_thong_ke": ["xác suất thống kê", "tổ hợp xác suất", "xác suất"],
    "ngu_phap": ["ngữ pháp", "grammar", "ngữ pháp căn bản"],
    "tu_vung": ["từ vựng", "vốn từ", "vocab"],
    "doc_hieu": ["đọc hiểu", "reading", "bài đọc hiểu"],
    "nghe": ["kỹ năng nghe", "listening", "luyện nghe"],
    "noi": ["kỹ năng nói", "speaking", "giao tiếp nói"],
    "viet": ["kỹ năng viết", "writing", "viết đoạn văn tiếng Anh"],
    "phat_am": ["phát âm", "pronunciation", "phát âm chuẩn"],
    "co_hoc": ["cơ học", "chuyển động và lực", "động lực học"],
    "dien_hoc": ["điện học", "mạch điện", "điện từ"],
    "quang_hoc": ["quang học", "thấu kính", "khúc xạ ánh sáng"],
    "nhiet_hoc": ["nhiệt học", "nhiệt lượng", "nhiệt"],
    "dao_dong_song": ["dao động và sóng", "dao động cơ", "sóng cơ"],
    "vat_ly_hat_nhan": ["vật lý hạt nhân", "hạt nhân nguyên tử", "phóng xạ"],
    "cham_chac": ["dạy chậm mà chắc", "đi chậm, chắc nền tảng", "học chắc từ gốc"],
    "giai_thich_ky": ["giải thích kỹ bản chất", "giảng kỹ để hiểu bản chất", "không học vẹt, hiểu bản chất"],
    "ke_hoach_ro": ["có lộ trình rõ ràng, kiểm tra định kỳ", "kế hoạch học theo tuần", "lộ trình và bài kiểm tra hàng tháng"],
    "kien_nhan": ["kiên nhẫn, nhẹ nhàng", "rất kiên nhẫn", "nhẹ nhàng, không quát mắng"],
    "luyen_tap_nhieu": ["luyện đề nhiều", "nhiều bài tập về nhà", "luyện tập thật nhiều dạng bài"],
    "nghiem_khac": ["nghiêm khắc, kỷ luật", "kỷ luật cao", "quản lý chặt bài tập"],
    "tu_duy": ["rèn tư duy, giải bài khó", "phát triển tư duy", "bài nâng cao, tư duy"],
    "tuong_tac_cong_nghe": ["dùng bảng điện tử và tài liệu số", "học online có công cụ tương tác", "tài liệu số, app luyện tập"],
    "vui_ve": ["vui vẻ, tạo hứng thú", "học mà chơi, vui vẻ", "tạo hứng thú cho con"],
    "mat_goc": ["học sinh mất gốc", "các bạn bị hổng kiến thức", "mất căn bản"],
    "trung_binh": ["học sinh trung bình", "học lực trung bình", "các bạn học tb"],
    "kha": ["học sinh khá", "học lực khá", "các bạn khá muốn lên giỏi"],
    "gioi": ["học sinh giỏi", "các bạn giỏi", "học sinh khá giỏi muốn nâng cao"],
    "can_kem_cap": ["cần kèm cặp sát sao", "cần người kèm sát", "cần theo dõi từng buổi"],
}
TYPOS = {"học": "hoc", "sinh": "sih", "kiến thức": "kien thuc", "luyện": "luyen", "không": "ko", "được": "dc",
         "với": "vs", "những": "nhữg", "trên": "tren"}

WEEKDAY_EVENING = [(day, "18:00", "20:00") for day in range(1, 6)] + [(day, "19:30", "21:30") for day in range(1, 6)]
WEEKEND = [(day, start, end) for day in (6, 7) for start, end in (("08:00", "10:00"), ("14:00", "16:00"),
                                                                  ("19:00", "21:00"))]


def sigmoid(value: float) -> float:
    return 1 / (1 + math.exp(-value))


def minutes(clock: str) -> int:
    hour, minute = clock.split(":")
    return int(hour) * 60 + int(minute)


def overlap_minutes(left, right) -> int:
    total = 0
    for day, start, end in left:
        for other_day, other_start, other_end in right:
            if day == other_day:
                total += max(0, min(minutes(end), minutes(other_end)) - max(minutes(start), minutes(other_start)))
    return total


def schedule_overlap(student_slots, tutor_slots) -> float:
    """Share of the student's free time the tutor can also teach."""
    wanted = sum(minutes(end) - minutes(start) for _, start, end in student_slots)
    return 0.0 if wanted == 0 else min(1.0, overlap_minutes(student_slots, tutor_slots) / wanted)


class Writer:
    """Turns codes into sentences with random wording, abbreviations and typos."""

    def __init__(self, rng: random.Random):
        self.rng = rng

    def phrase(self, code: str) -> str:
        return self.rng.choice(PHRASES[code])

    def roughen(self, text: str, rate: float = 0.25) -> str:
        if self.rng.random() > rate:
            return text
        for word, typo in TYPOS.items():
            if word in text and self.rng.random() < 0.5:
                text = text.replace(word, typo, 1)
        return text if self.rng.random() < 0.7 else text.lower()

    def schedule(self, slots) -> str:
        names = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
        return ", ".join(f"{names[day - 1]} {start}-{end}" for day, start, end in slots)


def make_tutor(index: int, rng: random.Random, writer: Writer) -> dict:
    cities = S["cities"]
    city = rng.choice(list(cities))
    home = rng.choice(cities[city])
    subjects = rng.sample(S["subjects"], 2 if rng.random() < 0.35 else 1)
    # Most tutors teach Toán or Tiếng Anh, like on the real platform.
    level = rng.choice(["thcs", "thpt", "both"])
    teach_lo, teach_hi = {"thcs": (6, 9), "thpt": (10, 12), "both": (6, 12)}[level]
    # Grades the tutor really wants: a narrower window inside what the postings offer.
    pref_lo = rng.randint(teach_lo, max(teach_lo, teach_hi - 2))
    pref_hi = min(teach_hi, pref_lo + rng.randint(2, 4))
    experience = rng.choice([1, 2, 3, 4, 5, 6, 8, 10, 12])
    min_price = 90_000 + experience * 12_000 + rng.choice([0, 10_000, 20_000, 30_000])
    goals = sorted({goal for grade in range(pref_lo, pref_hi + 1) for goal in GOALS_BY_GRADE[grade][:2]})
    goals = rng.sample(goals, min(len(goals), rng.randint(1, 3)))
    slots = rng.sample(WEEKDAY_EVENING, rng.randint(4, 7)) + rng.sample(WEEKEND, rng.randint(1, 3))
    tutor = {
        "key": f"t{index:03d}",
        "city": city,
        "home": home,
        "subjects": subjects,
        "teach_grades": [teach_lo, teach_hi],
        "pref_grades": [pref_lo, pref_hi],
        "experience": experience,
        "min_price": min_price,
        "goals": goals,
        "topics": {subject: rng.sample(TOPICS[subject], rng.randint(2, 4)) for subject in subjects},
        "styles": rng.sample(STYLES, rng.randint(2, 3)),
        "segments": rng.sample(SEGMENTS, rng.randint(1, 2)),
        "mode": rng.choices(["OFFLINE", "ONLINE", "BOTH"], S["tutor_modes"])[0],
        "districts": sorted({home, *rng.sample(cities[city], min(len(cities[city]), rng.randint(2, 4)))}),
        "slots": sorted(slots),
        "capacity": rng.randint(1, 3),
        "reliable": rng.random() >= LOW_RELIABILITY_SHARE,
        "quality": round(min(1.0, 0.45 + experience * 0.04 + rng.uniform(-0.1, 0.15)), 2),
        "sketchy": rng.random() < 0.15,
    }
    if S["famous"] or S["new"]:
        kind = rng.random()
        if kind < S["famous"]:
            # Popular: everybody wants them, but they only have one or two places.
            tutor.update(famous=True, experience=rng.randint(10, 15), quality=round(rng.uniform(0.9, 1.0), 2),
                         capacity=rng.randint(1, 2), styles=rng.sample(STYLES, 3), sketchy=False, reliable=True)
        elif kind < S["famous"] + S["new"]:
            # New on the platform: no history, no review.
            tutor.update(new=True, experience=rng.randint(0, 2))
    if TEXT_ONLY:
        strip_excluded(tutor, rng)
        possible = [need for need in NEEDS if need != "speaking" or "Tiếng Anh" in subjects]
        tutor["specialties"] = rng.sample(possible, rng.choice([0, 1, 1, 2]))
        if rng.random() < 0.25:
            options = [code for code, item in CONTRARY.items() if not item["against"] & set(tutor["specialties"])]
            tutor["contrary"] = rng.choice(options)
    tutor["profile"] = tutor_profile_text(tutor, writer)
    tutor["postings"] = [make_teaching_posting(tutor, number, subject, rng, writer)
                         for number, subject in enumerate(subjects)]
    if len(subjects) == 1 and rng.random() < 0.4:
        tutor["postings"].append(make_teaching_posting(tutor, 1, subjects[0], rng, writer))
    if TEXT_ONLY:
        # The experience is only told in the introduction and in the postings, in varied words.
        said = [rng.choice(NEEDS[need]["tutor"]) for need in tutor["specialties"]]
        if tutor.get("contrary"):
            said.append(rng.choice(CONTRARY[tutor["contrary"]]["tutor"]))
        if said:
            tutor["profile"]["introduction"] += " " + writer.roughen(". ".join(said).capitalize() + ".")
            for posting in tutor["postings"]:
                posting["description"] += " " + writer.roughen(
                    ". ".join(rng.choice(NEEDS[need]["tutor"]) for need in tutor["specialties"])
                    + (". " + rng.choice(CONTRARY[tutor["contrary"]]["tutor"]) if tutor.get("contrary") else "")
                ).strip(". ").capitalize() + "."
    return tutor


def strip_excluded(entity: dict, rng: random.Random) -> None:
    """No structured attribute may carry a concept of the free-text needs (text-only scenario)."""
    def keep(codes, pool):
        kept = [code for code in codes if code not in EXCLUDED_TAGS]
        return kept or [rng.choice([code for code in pool if code not in EXCLUDED_TAGS])]

    if "goals" in entity:
        entity["goals"] = keep(entity["goals"], ["cai_thien_diem", "hoc_vuot", "thi_vao_10", "thi_dai_hoc"])
    if "styles" in entity:
        entity["styles"] = keep(entity["styles"], STYLES)
    if "segments" in entity:
        entity["segments"] = keep(entity["segments"], SEGMENTS)
    if "topics" in entity and isinstance(entity["topics"], dict):
        entity["topics"] = {subject: keep(codes, TOPICS[subject]) for subject, codes in entity["topics"].items()}


def tutor_profile_text(tutor: dict, writer: Writer) -> dict:
    subjects = " và ".join(tutor["subjects"])
    intro = f"Gia sư {subjects}, {tutor['experience']} năm kinh nghiệm dạy kèm."
    if tutor["sketchy"]:
        return {"introduction": writer.roughen(intro, 0.6), "methodology": "", "target": "", "education": ""}
    styles = ", ".join(writer.phrase(code) for code in tutor["styles"])
    method = writer.roughen(f"Phong cách: {styles}. Mỗi buổi có bài tập về nhà và nhận xét cho phụ huynh.")
    target = ", ".join(writer.phrase(code) for code in tutor["segments"])
    education = writer.rng.choice(["Sinh viên Sư phạm", "Cử nhân Sư phạm", "Thạc sĩ", "Giáo viên THCS",
                                   "Giáo viên THPT", "Sinh viên Bách khoa"])
    return {"introduction": writer.roughen(intro), "methodology": method, "target": target, "education": education}


def make_teaching_posting(tutor: dict, number: int, subject: str, rng: random.Random, writer: Writer) -> dict:
    lo, hi = tutor["teach_grades"]
    if number > 0 and hi - lo > 3:
        lo, hi = (lo, lo + 3) if rng.random() < 0.5 else (hi - 3, hi)
    grades = list(range(lo, hi + 1))
    mode = tutor["mode"] if tutor["mode"] != "BOTH" else rng.choice(["OFFLINE", "ONLINE"])
    price = tutor["min_price"] + rng.choice([0, 20_000, 40_000, 60_000])
    topics = tutor["topics"][subject]
    goal_text = ", ".join(writer.phrase(goal) for goal in tutor["goals"])
    topic_text = ", ".join(writer.phrase(topic) for topic in topics)
    title = rng.choice([f"Gia sư {subject} lớp {lo}-{hi}", f"Dạy kèm {subject} {lo}–{hi}",
                        f"Nhận dạy {subject} cấp {'2' if hi <= 9 else '3' if lo >= 10 else '2, 3'}"])
    if tutor["sketchy"] and rng.random() < 0.6:
        description = writer.roughen(f"Nhận dạy {subject} lớp {lo} đến {hi}. Liên hệ để biết thêm.", 0.6)
    else:
        description = writer.roughen(
            f"Dạy {subject} lớp {lo} đến {hi}, chuyên {topic_text}. Hướng tới {goal_text}. "
            f"Phù hợp {', '.join(writer.phrase(code) for code in tutor['segments'])}. "
            f"Cách dạy: {', '.join(writer.phrase(code) for code in tutor['styles'])}.")
    return {
        "key": f"{tutor['key']}-p{number}",
        "subject": subject,
        "grades": grades,
        "mode": mode,
        "price": price,
        "districts": tutor["districts"] if mode == "OFFLINE" else [],
        "slots": tutor["slots"],
        "title": title,
        "description": description,
        "schedule_text": writer.schedule(tutor["slots"]),
        "quantity": tutor["capacity"],
    }


def make_student(index: int, rng: random.Random, writer: Writer) -> dict:
    cities = S["cities"]
    city = rng.choice(list(cities))
    grade = rng.randint(6, 12)
    subject = rng.choices(S["subjects"], S["subject_weights"])[0]
    stated = rng.sample(STYLES, rng.randint(1, 2))
    taste = rng.choice([style for style in STYLES if style not in stated])
    segment = rng.choices(SEGMENTS, [0.25, 0.3, 0.25, 0.1, 0.1])[0]
    slots = sorted(rng.sample(WEEKDAY_EVENING, rng.randint(2, 4)) + rng.sample(WEEKEND, rng.randint(1, 2)))
    student = {
        "key": f"s{index:02d}",
        "city": city,
        "home": rng.choice(cities[city]),
        "grade": grade,
        "subject": subject,
        "goal": rng.choice(GOALS_BY_GRADE[grade]),
        "topics": rng.sample(TOPICS[subject], rng.randint(1, 2)),
        "segment": segment,
        "styles": stated,
        "taste": taste,
        "budget": rng.choice([120_000, 150_000, 180_000, 200_000, 250_000, 300_000]),
        "mode": rng.choices(["OFFLINE", "ONLINE", "ANY"], S["student_modes"])[0],
        "slots": slots,
        "has_posting": (index % 4 != 0 and rng.random() < 0.75) if SCENARIO == "base" else index % 10 != 0,
    }
    if TEXT_ONLY:
        # Structured attributes stay clear of the free-text needs; the need itself is only written in words.
        if student["segment"] in EXCLUDED_TAGS:
            student["segment"] = segment = rng.choice(["trung_binh", "kha", "gioi"])
        if student["goal"] in EXCLUDED_TAGS:
            student["goal"] = rng.choice([g for g in GOALS_BY_GRADE[grade] if g not in EXCLUDED_TAGS])
        student["topics"] = [code for code in student["topics"] if code not in EXCLUDED_TAGS] or [
            rng.choice([code for code in TOPICS[subject] if code not in EXCLUDED_TAGS])]
        student["styles"] = stated = [code for code in stated if code not in EXCLUDED_TAGS] or [
            rng.choice([code for code in STYLES if code not in EXCLUDED_TAGS])]
        if student["taste"] in EXCLUDED_TAGS:
            student["taste"] = rng.choice([code for code in STYLES if code not in EXCLUDED_TAGS + stated])
        possible = [need for need in NEEDS if need != "speaking" or subject == "Tiếng Anh"]
        student["need"] = rng.choice(possible) if rng.random() < 0.75 else None
    student["profile"] = {
        "current_level": writer.phrase(segment),
        "weak_points": writer.roughen(", ".join(writer.phrase(topic) for topic in student["topics"])),
        "goals": writer.roughen(f"Muốn {writer.phrase(student['goal'])}."),
    }
    if student["has_posting"]:
        topics = ", ".join(writer.phrase(topic) for topic in student["topics"])
        student["posting"] = {
            "key": f"{student['key']}-r0",
            "title": rng.choice([f"Tìm gia sư {subject} lớp {grade}", f"Cần gia sư {subject} {grade}",
                                 f"Tìm thầy/cô dạy {subject} lớp {grade}"]),
            "description": writer.roughen(
                f"Con {writer.phrase(segment)}, yếu {topics}. Mong gia sư {writer.phrase(stated[0])}"
                + (f", {writer.phrase(stated[1])}" if len(stated) > 1 else "") + "."),
            "learning_goals": writer.roughen(writer.phrase(student["goal"])),
            "schedule_text": writer.schedule(slots),
        }
    if TEXT_ONLY and student["need"]:
        student["profile"]["weak_points"] += ". " + writer.roughen(rng.choice(NEEDS[student["need"]]["student"]))
        if student["has_posting"]:
            student["posting"]["description"] += " " + writer.roughen(
                rng.choice(NEEDS[student["need"]]["student"]).capitalize() + ".")
    return student


def rel(student: dict, posting: dict, tutor: dict) -> int:
    """Hidden relevance 0-3 of a teaching posting for a student (design 09, 9.3)."""
    if posting["subject"] != student["subject"] or student["grade"] not in posting["grades"]:
        return 0
    if overlap_minutes(student["slots"], posting["slots"]) == 0:
        return 0
    if posting["price"] > student["budget"] * 1.3:
        return 0
    if student["mode"] != "ANY" and posting["mode"] != student["mode"]:
        return 0
    if posting["mode"] == "OFFLINE" and (tutor["city"] != student["city"]
                                         or student["home"] not in posting["districts"]):
        return 0
    styles = {*student["styles"], student["taste"]}
    need = (0.35 * (student["goal"] in tutor["goals"])
            + 0.25 * len(set(student["topics"]) & set(tutor["topics"][posting["subject"]])) / len(student["topics"])
            + 0.20 * (student["segment"] in tutor["segments"])
            + 0.20 * len(styles & set(tutor["styles"])) / len(styles))
    value = 1 + (need >= 0.35) + (need >= 0.6)
    if posting["price"] > student["budget"]:
        value = max(1, value - 1)
    return adjust(int(value), student, tutor)


BETA = {"b0": -1.0, "grade": 1.6, "price": 1.2, "segment": 1.0, "schedule": 1.2}


def p_accept(student: dict, posting: dict, tutor: dict) -> float:
    """Hidden probability that the tutor accepts the student's application (design 09, 9.3)."""
    lo, hi = tutor["pref_grades"]
    return sigmoid(BETA["b0"] + BETA["grade"] * (lo <= student["grade"] <= hi)
                   + BETA["price"] * (student["budget"] >= tutor["min_price"])
                   + BETA["segment"] * (student["segment"] in tutor["segments"])
                   + BETA["schedule"] * schedule_overlap(student["slots"], posting["slots"]))


def p_student_accepts(student: dict, posting: dict, tutor: dict) -> float:
    """Hidden probability that a student accepts a tutor's offer (tutor-side reciprocity)."""
    styles = {*student["styles"], student["taste"]}
    return sigmoid(-0.8 + 1.5 * (posting["price"] <= student["budget"])
                   + 1.0 * len(styles & set(tutor["styles"])) / len(styles) + 0.8 * tutor["quality"])


def make_queries(students, tutors, rng: random.Random) -> list:
    """2-3 searches per student, 1-2 per tutor; half of them calibrate, half test (design 09, 9.2)."""
    queries = []
    for student in students:
        kinds = ["full", "subject_grade"] + (["posting"] if student["has_posting"] else [])
        if not student["has_posting"] and rng.random() < 0.5:
            kinds.append("semantic")
        for kind in kinds:
            queries.append({"id": f"{student['key']}-{kind}", "side": "S2T", "owner": student["key"], "kind": kind})
    searching = tutors if S["t2s_tutors"] is None else rng.sample(tutors, S["t2s_tutors"])
    for tutor in searching:
        postings = tutor["postings"][: 1 if rng.random() < 0.5 else 2]
        for posting in postings:
            queries.append({"id": f"{posting['key']}-q", "side": "T2S", "owner": tutor["key"],
                            "kind": "posting", "posting": posting["key"]})
    order = list(range(len(queries)))
    rng.shuffle(order)
    for position, index in enumerate(order):
        queries[index]["split"] = "calibration" if position % 2 == 0 else "test"
    return queries


def build() -> dict:
    rng = random.Random(SEED)
    writer = Writer(rng)
    tutors = [make_tutor(index, rng, writer) for index in range(1, S["tutors"] + 1)]
    students = [make_student(index, rng, writer) for index in range(1, S["students"] + 1)]
    reliable = [tutor for tutor in tutors if tutor["reliable"] and not tutor.get("new")]
    for tutor in rng.sample(reliable, int(len(tutors) * UNFOUNDED_COMPLAINT_SHARE)):
        tutor["unfounded_complaint"] = True
    for entity in tutors + students:
        entity["id"] = uid("eval", entity["key"])
    return {"seed": SEED, "scenario": SCENARIO, "tutors": tutors, "students": students,
            "queries": make_queries(students, tutors, rng), "beta": BETA}


def main() -> int:
    world = build()
    write_json(OUT / "world.json", world)
    postings = sum(len(tutor["postings"]) for tutor in world["tutors"])
    studying = sum(student["has_posting"] for student in world["students"])
    print(f"{len(world['tutors'])} tutors, {postings} teaching postings, {len(world['students'])} students, "
          f"{studying} studying postings, {len(world['queries'])} queries -> {OUT / 'world.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
