"""2.3: needs that live only in free text (plan of 21/10/2026), for the "text-only" scenario of world.py.

Six needs a student may have (0 or 1) and a tutor may be experienced in (0 to 2), written only in prose: no tag of
the scenario expresses them (the overlapping tags are switched off in its database, EXCLUDED_TAGS, so A1 cannot pick
them up) and no search filter carries them. A tutor may also say something that goes against a need (CONTRARY).
Nothing about health: the needs are about how a student learns.
"""

NEEDS = {
    "focus": {
        "student": ["con hay mất tập trung, cần người kiên nhẫn, chia nhỏ bài",
                    "bé ngồi học không lâu được, mong thầy cô chia bài thành từng phần ngắn",
                    "con dễ xao nhãng, cần người nhẹ nhàng kéo con vào bài",
                    "học được 15 phút là con bắt đầu lơ đãng, cần cách dạy từng chút một",
                    "cháu khó tập trung lâu, cần gia sư biết chia nhỏ nội dung",
                    "con hay mất tập trung giữa buổi, nhờ thầy cô kiên trì"],
        "tutor": ["quen kèm các bạn khó tập trung, chia bài thành từng phần nhỏ",
                  "có kinh nghiệm với học sinh hay xao nhãng, mỗi buổi chia nhiều chặng ngắn",
                  "kiên trì với các bạn ngồi không yên, dạy theo từng đoạn ngắn có nghỉ",
                  "giữ sự chú ý của học sinh bằng các bài nhỏ nối tiếp nhau",
                  "từng kèm nhiều bé mất tập trung, biết kéo các bé quay lại bài",
                  "dạy chia nhỏ, đổi hoạt động liên tục để học sinh không chán"],
    },
    "specialist": {
        "student": ["cần luyện đề thi chuyên", "con muốn thi vào lớp chuyên, cần người luyện đề chuyên",
                    "chuẩn bị thi trường chuyên, cần nhiều đề nâng cao", "định thi chuyên nên cần ôn dạng đề chuyên",
                    "nhà muốn cho con thử sức vào chuyên, cần gia sư có đề chuyên các năm",
                    "luyện đề chuyên là chính"],
        "tutor": ["nhiều năm luyện đề thi chuyên", "có bộ đề chuyên các tỉnh, từng có học sinh đỗ chuyên",
                  "chuyên ôn cho học sinh thi vào trường chuyên", "luyện dạng bài khó của đề chuyên",
                  "đã đưa nhiều bạn vào lớp chuyên", "dạy đề chuyên các năm và đề dự đoán"],
    },
    "restart": {
        "student": ["mất gốc từ lớp dưới, cần dạy lại từ đầu", "con hổng kiến thức từ mấy năm trước, phải học lại từ căn bản",
                    "kiến thức nền của con gần như không có, cần người dạy lại từ đầu",
                    "con quên hết kiến thức lớp dưới, muốn ôn lại từ gốc",
                    "học lại từ chương trình lớp dưới vì con không theo kịp",
                    "bị hổng từ lâu, cần bắt đầu lại từ những bài đầu tiên"],
        "tutor": ["chuyên dạy lại từ đầu cho học sinh hổng kiến thức lớp dưới",
                  "quen lấy lại nền cho các bạn quên hết chương trình cũ",
                  "dạy lại từ chương trình các lớp dưới khi cần", "bắt đầu từ bài cơ bản nhất cho các bạn mất nền",
                  "từng giúp nhiều bạn học lại từ con số không", "xây lại kiến thức nền từng bước một"],
    },
    "speaking": {
        "student": ["cần luyện nói, phát âm", "con đọc sai nhiều, muốn luyện phát âm và nói",
                    "con ngại mở miệng nói tiếng Anh, cần người luyện nói", "muốn con nói tự nhiên hơn, sửa giọng đọc",
                    "cần luyện speaking và sửa cách phát âm", "con viết được nhưng nói rất kém"],
        "tutor": ["luyện nói và sửa phát âm từng âm", "có nhiều buổi hội thoại để học sinh mạnh dạn nói",
                  "sửa giọng đọc, luyện ngữ điệu", "giúp các bạn hết ngại nói tiếng Anh",
                  "tập trung speaking, phát âm chuẩn", "học sinh nói nhiều hơn thầy trong mỗi buổi"],
    },
    "examples": {
        "student": ["học chậm, cần nhiều ví dụ thực tế", "con hiểu chậm, cần giảng bằng ví dụ đời thường",
                    "con cần thấy bài toán gắn với thực tế mới hiểu", "mong gia sư lấy nhiều ví dụ gần gũi",
                    "con tiếp thu chậm, phải có ví dụ cụ thể", "học qua tình huống thực tế thì con nhớ lâu hơn"],
        "tutor": ["giảng bằng nhiều ví dụ thực tế", "gắn kiến thức với tình huống đời sống",
                  "dùng ví dụ gần gũi cho các bạn tiếp thu chậm", "minh hoạ bằng đồ vật, câu chuyện hằng ngày",
                  "kiên trì với các bạn học chậm, giải thích bằng ví dụ", "mỗi khái niệm đều có ví dụ thực tế đi kèm"],
    },
    "olympiad": {
        "student": ["chuẩn bị thi học sinh giỏi", "con được chọn vào đội tuyển, cần ôn thi HSG",
                    "muốn con đi thi học sinh giỏi cấp quận", "cần bồi dưỡng để thi giỏi",
                    "con khá rồi, giờ muốn thi học sinh giỏi", "ôn thi đội tuyển của trường"],
        "tutor": ["bồi dưỡng đội tuyển học sinh giỏi", "có học sinh đạt giải học sinh giỏi cấp quận, thành phố",
                  "dạy chuyên đề nâng cao cho đội tuyển", "luyện thi học sinh giỏi nhiều năm",
                  "chuyên các bài khó dành cho thi giỏi", "từng phụ trách đội tuyển của trường"],
    },
}

# Something a tutor says that goes against a need ("lệch hẳn" in the plan): -1 to the hidden relevance.
CONTRARY = {
    "fast": {"against": {"focus", "examples", "restart"},
             "tutor": ["dạy nhanh, nhiều bài khó mỗi buổi", "tốc độ cao, phù hợp các bạn tự học tốt",
                       "không dạy lại kiến thức cũ, chỉ đi tiếp chương trình"]},
    "basics_only": {"against": {"specialist", "olympiad"},
                    "tutor": ["chỉ kèm kiến thức cơ bản trên lớp", "không nhận luyện thi nâng cao",
                              "tập trung bài tập sách giáo khoa"]},
    "grammar_only": {"against": {"speaking"},
                     "tutor": ["chủ yếu ngữ pháp và bài tập viết, ít luyện nói", "dạy theo sách ngữ pháp",
                              "tập trung làm đề trắc nghiệm ngữ pháp"]},
    "advanced_only": {"against": {"restart", "examples"},
                      "tutor": ["chỉ nhận học sinh khá giỏi", "không nhận các bạn mất gốc",
                                "dạy nâng cao cho học sinh đã vững"]},
}

# Tags that express the same ideas: switched off in the text-only database before A1 runs.
EXCLUDED_TAGS = ["kien_nhan", "cham_chac", "giai_thich_ky", "giao_tiep", "mat_goc", "can_kem_cap", "thi_chuyen",
                 "hoc_sinh_gioi", "noi", "phat_am"]


def adjust(value: int, student: dict, tutor: dict) -> int:
    """Hidden relevance with the free-text need: one level up on a match, one down against it (0 stays 0)."""
    need = student.get("need")
    if value == 0 or not need:
        return value
    if need in tutor.get("specialties", []):
        value += 1
    elif tutor.get("contrary") and need in CONTRARY[tutor["contrary"]]["against"]:
        value -= 1
    return max(1, min(3, value))
