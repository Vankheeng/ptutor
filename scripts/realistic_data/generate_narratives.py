"""One-off authoring aid: generate a reviewed, deterministic text corpus for seed.py.

The seed never calls an LLM. Run this only when refreshing narratives, then review
the JSON and commit it alongside the seed. Only synthetic profile context is sent.
"""

import argparse
import json
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import seed

HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "narratives.json"
FIELDS = {
    "tutor": ["introduction", "teaching_methodology"],
    "student": ["introduction", "weak_points", "goals_description"],
    "teaching": ["description"],
    "studying": ["description", "learning_goals"],
    "teaching_expanded": ["title", "description"],
    "studying_expanded": ["title", "description", "learning_goals"],
    "support": ["question"],
}
KIND_RULES = {
    "tutor": "Đây là hồ sơ do GIA SƯ tự viết ở ngôi tôi. Nêu kinh nghiệm và cách dạy cụ thể, không viết giọng quảng cáo, không mở đầu bằng câu hỏi hay 'Nếu bạn'.",
    "student": "Đây là hồ sơ do HỌC VIÊN hoặc phụ huynh viết. Mô tả khó khăn thật và mục tiêu hợp với lớp học; không tự bịa điểm số cụ thể. Tránh mở đầu bằng 'Con tôi đang học lớp', 'Tôi đang tìm gia sư'; hãy dùng hướng viết riêng trong context.",
    "teaching": "Đây là TIN NHẬN DẠY do GIA SƯ đăng. Gia sư đang đề nghị dạy học sinh, tuyệt đối không viết 'tìm gia sư' hoặc 'cần gia sư'. Mô tả viết ở ngôi tôi hoặc trung tính, dưới 230 ký tự.",
    "studying": "Đây là TIN TÌM GIA SƯ do HỌC VIÊN hoặc phụ huynh đăng. Mô tả dưới 220 ký tự, mục tiêu học tập dưới 160 ký tự.",
    "teaching_expanded": "Đây là TIN NHẬN DẠY do GIA SƯ tự đăng, không phải phụ huynh. Tiêu đề dưới 130 ký tự, mô tả dưới 230 ký tự. Trong mô tả, gia sư chủ động nói sẽ dạy/kèm/ôn nội dung nào và bằng cách nào; viết ở ngôi 'tôi' hoặc 'mình' ít nhất một lần. Không nói 'con tôi', 'gia đình tôi', 'đang tìm gia sư', không mô tả học sinh cụ thể như một người đã gặp, không dùng câu hỏi tu từ hay lời quảng cáo. Với lớp 1–5, tránh luyện đề thi và kiến thức cấp lớn. Mỗi tin có cách mở đầu khác nhau.",
    "studying_expanded": "Đây là TIN TÌM GIA SƯ do HỌC VIÊN hoặc phụ huynh tự đăng. Tiêu đề dưới 130 ký tự, mô tả dưới 220 ký tự, mục tiêu dưới 160 ký tự. Nói một khó khăn cụ thể và mong muốn học; tránh lời quảng cáo và tránh cùng một câu mở đầu. Với lớp 1–5, phụ huynh viết về con.",
    "support": "Đây là câu hỏi HỖ TRỢ của người dùng. Giữ đúng vấn đề đã nêu, đổi cách diễn đạt tự nhiên, không thêm giao dịch hay sự kiện không có trong ngữ cảnh.",
}
WRITING_STYLES = (
    "Mở bằng một tình huống cụ thể, rồi nói cách xử lý.",
    "Vào thẳng mục tiêu học và lịch phù hợp, câu ngắn tự nhiên.",
    "Nêu điều học sinh hay vướng trước, sau đó nói cách kèm.",
    "Viết như một lời nhắn súc tích, không dùng khẩu hiệu.",
    "Nhắc bài tập hoặc kỹ năng đang cần ôn, rồi nêu nhịp học.",
    "Nêu một chi tiết về cách chuẩn bị bài học, tránh liệt kê.",
    "Viết theo giọng phụ huynh hoặc gia sư gần gũi, không trang trọng.",
    "Bắt đầu bằng điều muốn cải thiện trong vài tuần tới.",
    "Nói về lịch học trước rồi tới nội dung học, không lặp cú pháp.",
    "Dùng hai câu với độ dài khác nhau, từ ngữ đời thường.",
    "Mở bằng môn và lớp nhưng không dùng khung 'Gia sư X lớp Y'.",
    "Nêu một trở ngại cụ thể và một kỳ vọng vừa phải.",
)
TUTOR_POST_STYLES = (
    "Mở bằng 'Tôi nhận kèm' rồi nói một cách chữa bài cụ thể.",
    "Mở bằng nội dung muốn dạy, sau đó viết 'mình sẽ...' về cách học.",
    "Mở bằng lịch còn trống, rồi nói phần sẽ ôn cùng học sinh.",
    "Bắt đầu bằng một bước trong buổi học, rồi nói mục tiêu vừa phải.",
    "Viết ngắn như gia sư đăng tin: môn/lớp, cách hướng dẫn, lịch.",
    "Mở bằng 'Mình thường...' mô tả cách kiểm tra kiến thức nền.",
    "Nêu phần kiến thức có thể kèm, rồi nói cách giao bài tập.",
    "Mở bằng 'Nhận dạy' nhưng ở câu sau dùng 'tôi' để nói cách kèm.",
    "Bắt đầu bằng cách theo dõi tiến độ học, rồi nêu môn/lớp.",
    "Nêu lịch dạy mong muốn trước, sau đó nói bài sẽ chuẩn bị.",
    "Mở bằng điểm mạnh chuyên môn cụ thể, không tự nhận thành tích.",
    "Nêu cách sửa lỗi cho học sinh ở buổi đầu, rồi lịch học.",
)


def contexts():
    records = []
    for role, count in (("tutor", 84), ("student", 64)):
        for i in range(count):
            name_index = i if role == "tutor" else i + 85
            male = i % 2 == 0
            given = (seed.MALE if male else seed.FEMALE)[(name_index * 7 + name_index // 12) % 12]
            middle = (seed.MIDDLE_MALE if male else seed.MIDDLE_FEMALE)[(name_index // 3) % 6]
            surname = seed.FAMILIES[(name_index * 5 + name_index // 12) % 12]
            name = f"{surname} {middle} {given}"
            district = seed.DISTRICTS[(i * 7) % len(seed.DISTRICTS)]
            if role == "tutor":
                subject = seed.LEGACY_SUBJECTS[i % len(seed.LEGACY_SUBJECTS)]
                level, topic = seed.LEGACY_TOPICS[subject][(i // len(seed.LEGACY_SUBJECTS)) % len(seed.LEGACY_TOPICS[subject])]
                experience = 1 + (i * 7) % 13
                records.append({"kind": "tutor", "id": seed.uid("tutor", i), "context": {
                    "name": name, "subject": subject, "grade_range": f"{max(6, level-1)}–{min(12, level+1)}",
                    "topic": topic, "experience_years": experience,
                    "teaching_approach": seed.METHODS[i % len(seed.METHODS)],
                }})
                for j in range(2):
                    grade, focus = seed.LEGACY_TOPICS[subject][(i + j) % len(seed.LEGACY_TOPICS[subject])]
                    mode = "trực tuyến" if (i+j) % 4 == 0 else "trực tiếp"
                    records.append({"kind": "teaching", "id": seed.uid("teaching", i, j), "context": {
                        "subject": subject, "grade": grade, "focus": focus, "mode": mode,
                        "district": district if mode == "trực tiếp" else None,
                        "price_vnd_per_lesson": 140000 + ((i+j) % 8) * 20000,
                        "teaching_approach": seed.METHODS[i % len(seed.METHODS)],
                    }})
            else:
                grade = 6 + (i * 5) % 7
                subject = seed.LEGACY_SUBJECTS[i % len(seed.LEGACY_SUBJECTS)]
                topic = seed.topic_for_grade(subject, grade)
                need = seed.NEEDS[i % len(seed.NEEDS)]
                records.append({"kind": "student", "id": seed.uid("student", i), "context": {
                    "grade": grade, "subject": subject, "focus": topic, "difficulty": need,
                }})
                for j in range(1 + (i % 3 == 0)):
                    subject = seed.LEGACY_SUBJECTS[(i + j * 3) % len(seed.LEGACY_SUBJECTS)]
                    topic = seed.topic_for_grade(subject, grade)
                    mode = "trực tuyến" if (i+j) % 4 == 0 else "trực tiếp"
                    records.append({"kind": "studying", "id": seed.uid("studying", i, j), "context": {
                        "grade": grade, "subject": subject, "focus": topic, "mode": mode,
                        "district": district if mode == "trực tiếp" else None,
                        "budget_vnd_per_lesson": 180000 + (i % 7) * 20000,
                        "difficulty": seed.NEEDS[(i+j) % len(seed.NEEDS)],
                    }})
    for role in ("tutor", "student"):
        for _, ordinal, subject, grade, (province, district) in seed.expanded_personas(role):
            profile = seed.uid(f"expanded-{role}", ordinal)
            name = seed.expanded_name(role, ordinal)
            context = {"subject": subject, "grade": grade, "focus": seed.topic_for_grade(subject, grade),
                       "province": province, "district": district}
            if role == "tutor":
                records.append({"kind": "tutor", "id": profile, "context": {
                    **context, "name": name, "experience_years": 1 + ordinal % 10,
                    "teaching_approach": seed.METHODS[ordinal % len(seed.METHODS)],
                }})
            else:
                records.append({"kind": "student", "id": profile, "context": {
                    **context, "role": "phụ huynh" if grade <= 5 else "học viên hoặc phụ huynh",
                    "difficulty": seed.NEEDS[ordinal % len(seed.NEEDS)],
                    "writing_style": WRITING_STYLES[(ordinal * 7) % len(WRITING_STYLES)],
                }})
            for j, post_grade, mode, focus in seed.expanded_postings(role, ordinal, subject, grade):
                kind = "teaching" if role == "tutor" else "studying"
                price = (120000 + ((ordinal * 3 + j * 5) % 12) * 20000) if role == "tutor" else (
                    130000 + ((ordinal * 5 + j * 3) % 11) * 20000)
                post_context = {**context, "grade": post_grade, "focus": focus,
                                "mode": "trực tuyến" if mode == "ONLINE" else "trực tiếp",
                                "province": province if mode == "OFFLINE" else None,
                                "district": district if mode == "OFFLINE" else None,
                                "price_vnd_per_lesson": price}
                if role == "tutor":
                    post_context.update({"author_role": "gia sư", "teaching_approach": seed.METHODS[ordinal % len(seed.METHODS)],
                                         "writing_style": TUTOR_POST_STYLES[(ordinal * 7 + j * 5) % len(TUTOR_POST_STYLES)]})
                else:
                    post_context.update({"author_role": "phụ huynh" if grade <= 5 else "học sinh hoặc phụ huynh",
                                         "difficulty": seed.NEEDS[(ordinal + j) % len(seed.NEEDS)],
                                         "writing_style": WRITING_STYLES[(ordinal * 7 + j * 5) % len(WRITING_STYLES)]})
                records.append({"kind": f"{kind}_expanded", "id": seed.uid(f"expanded-{kind}", ordinal, j),
                                "context": post_context})
    for i in range(36):
        records.append({"kind": "support", "id": seed.uid("support", i), "context": {
            "role": "gia sư" if seed.SUPPORT_ROLES[i % len(seed.SUPPORT_ROLES)] == "TUTOR" else "học viên",
            "issue": seed.SUPPORT_QUESTIONS[i % len(seed.SUPPORT_QUESTIONS)],
        }})
    return records


def key_from_env():
    if os.environ.get("OPENAI_API_KEY"):
        return os.environ["OPENAI_API_KEY"]
    env_path = HERE.parents[1] / ".env"
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("OPENAI_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise RuntimeError("OPENAI_API_KEY is not configured")


def make_batch(client, model, batch):
    kind = batch[0]["kind"]
    fields = FIELDS[kind]
    prompt = (
        "Viết dữ liệu tiếng Việt cho một nền tảng kết nối gia sư. Trả JSON dạng "
        "{\"items\":[{\"id\":\"...\",\"fields\":{...}}]}. Mỗi item phải có đúng ID và đủ các trường: "
        + ", ".join(fields) + ". Viết như người thật, mỗi item có giọng điệu, thứ tự ý và cách mở đầu khác nhau; "
        "đừng dùng một khuôn chung, đừng lặp nguyên câu giữa các item. Giữ chính xác môn, lớp, hình thức, "
        "khu vực, mức tiền và số năm nếu nhắc đến. Không tự bịa thành tích, chứng chỉ, giao dịch hay cam kết. "
        "Mô tả tin đăng gồm 2–3 câu; hồ sơ 2–3 câu, các trường khác 1–2 câu. "
        "Không ghi tên người hoặc thông tin cá nhân trong mô tả tin. "
        "Với yêu cầu hỗ trợ, viết lại vấn đề từ ngữ cảnh thành một câu hỏi khác, rõ và tự nhiên. "
        "Chỉ trả JSON, trong fields tuyệt đối không chép lại các khóa context như mode, grade hoặc price. "
        + KIND_RULES[kind] + "\n" + json.dumps(batch, ensure_ascii=False)
    )
    response = client.chat.completions.create(
        model=model, temperature=0.8, max_completion_tokens=3000,
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": prompt}], timeout=45,
    )
    items = json.loads(response.choices[0].message.content)["items"]
    expected = {record["id"] for record in batch}
    accepted = {}
    for item in items:
        if item.get("id") not in expected or item["id"] in accepted:
            continue
        data = {field: item["fields"].get(field) for field in fields}
        if any(not isinstance(data.get(field), str) or not data[field].strip() for field in fields):
            continue
        if kind.startswith("teaching") and any(word in data["description"].lower()
                                      for word in ("tìm gia sư", "cần gia sư", "bạn đang tìm",
                                                   "tìm kiếm một gia sư", "tôi cung cấp", "gia đình mong",
                                                   "con tôi", "gia đình tôi")):
            continue
        if kind == "teaching_expanded" and not any(word in data["description"].lower()
                                                    for word in ("tôi", "mình")):
            continue
        if kind.startswith("studying") and len(data["description"]) > 255:
            continue
        if kind.endswith("_expanded") and len(data["title"]) > 130:
            continue
        accepted[item["id"]] = data
    return accepted


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="gpt-4o-mini")
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=6)
    args = parser.parse_args()
    from openai import OpenAI
    client = OpenAI(api_key=key_from_env(), max_retries=0)
    saved = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.exists() else {}
    all_records = contexts()
    records = [record for record in all_records if record["id"] not in saved]
    batches = []
    for kind in FIELDS:
        group = [record for record in records if record["kind"] == kind]
        batches.extend(group[i:i+args.batch_size] for i in range(0, len(group), args.batch_size))
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(make_batch, client, args.model, batch): batch for batch in batches}
        for future in as_completed(futures):
            try:
                generated = future.result()
            except Exception as error:
                print(f"batch failed ({futures[future][0]['kind']}): {type(error).__name__}: {error}", flush=True)
                continue
            saved.update(generated)
            OUTPUT.write_text(json.dumps(saved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"generated {len(saved)}/{len(all_records)}", flush=True)


if __name__ == "__main__":
    main()
