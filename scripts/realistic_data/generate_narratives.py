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
    "support": ["question"],
}
KIND_RULES = {
    "tutor": "Đây là hồ sơ do GIA SƯ tự viết ở ngôi tôi. Nêu kinh nghiệm và cách dạy cụ thể, không viết giọng quảng cáo, không mở đầu bằng câu hỏi hay 'Nếu bạn'.",
    "student": "Đây là hồ sơ do HỌC VIÊN hoặc phụ huynh viết. Mô tả khó khăn thật và mục tiêu hợp với lớp học; không tự bịa điểm số cụ thể.",
    "teaching": "Đây là TIN NHẬN DẠY do GIA SƯ đăng. Gia sư đang đề nghị dạy học sinh, tuyệt đối không viết 'tìm gia sư' hoặc 'cần gia sư'. Mô tả viết ở ngôi tôi hoặc trung tính, dưới 230 ký tự.",
    "studying": "Đây là TIN TÌM GIA SƯ do HỌC VIÊN hoặc phụ huynh đăng. Mô tả dưới 220 ký tự, mục tiêu học tập dưới 160 ký tự.",
    "support": "Đây là câu hỏi HỖ TRỢ của người dùng. Giữ đúng vấn đề đã nêu, đổi cách diễn đạt tự nhiên, không thêm giao dịch hay sự kiện không có trong ngữ cảnh.",
}


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
                subject = list(seed.TOPICS)[i % len(seed.TOPICS)]
                level, topic = seed.TOPICS[subject][(i // len(seed.TOPICS)) % len(seed.TOPICS[subject])]
                experience = 1 + (i * 7) % 13
                records.append({"kind": "tutor", "id": seed.uid("tutor", i), "context": {
                    "name": name, "subject": subject, "grade_range": f"{max(6, level-1)}–{min(12, level+1)}",
                    "topic": topic, "experience_years": experience,
                    "teaching_approach": seed.METHODS[i % len(seed.METHODS)],
                }})
                for j in range(2):
                    grade, focus = seed.TOPICS[subject][(i + j) % len(seed.TOPICS[subject])]
                    mode = "trực tuyến" if (i+j) % 4 == 0 else "trực tiếp"
                    records.append({"kind": "teaching", "id": seed.uid("teaching", i, j), "context": {
                        "subject": subject, "grade": grade, "focus": focus, "mode": mode,
                        "district": district if mode == "trực tiếp" else None,
                        "price_vnd_per_lesson": 140000 + ((i+j) % 8) * 20000,
                        "teaching_approach": seed.METHODS[i % len(seed.METHODS)],
                    }})
            else:
                grade = 6 + (i * 5) % 7
                subject = list(seed.TOPICS)[i % len(seed.TOPICS)]
                topic = seed.topic_for_grade(subject, grade)
                need = seed.NEEDS[i % len(seed.NEEDS)]
                records.append({"kind": "student", "id": seed.uid("student", i), "context": {
                    "grade": grade, "subject": subject, "focus": topic, "difficulty": need,
                }})
                for j in range(1 + (i % 3 == 0)):
                    subject = list(seed.TOPICS)[(i + j * 3) % len(seed.TOPICS)]
                    topic = seed.topic_for_grade(subject, grade)
                    mode = "trực tuyến" if (i+j) % 4 == 0 else "trực tiếp"
                    records.append({"kind": "studying", "id": seed.uid("studying", i, j), "context": {
                        "grade": grade, "subject": subject, "focus": topic, "mode": mode,
                        "district": district if mode == "trực tiếp" else None,
                        "budget_vnd_per_lesson": 180000 + (i % 7) * 20000,
                        "difficulty": seed.NEEDS[(i+j) % len(seed.NEEDS)],
                    }})
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
        "Chỉ trả JSON. " + KIND_RULES[kind] + "\n" + json.dumps(batch, ensure_ascii=False)
    )
    response = client.chat.completions.create(
        model=model, temperature=0.8, max_completion_tokens=3000,
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": prompt}], timeout=45,
    )
    items = json.loads(response.choices[0].message.content)["items"]
    expected = {record["id"] for record in batch}
    if {item["id"] for item in items} != expected:
        raise ValueError("model returned missing, duplicate, or extra IDs")
    for item in items:
        data = item["fields"]
        if any(not isinstance(data.get(field), str) or not data[field].strip() for field in fields):
            raise ValueError(f"missing field for {item['id']}")
        if kind == "teaching" and any(word in data["description"].lower()
                                      for word in ("tìm gia sư", "cần gia sư")):
            raise ValueError("teaching request is written from the student perspective")
        if kind == "studying" and len(data["description"]) > 255:
            raise ValueError("studying description exceeds database limit")
    return {item["id"]: item["fields"] for item in items}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="gpt-4o-mini")
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=6)
    args = parser.parse_args()
    from openai import OpenAI
    client = OpenAI(api_key=key_from_env(), max_retries=0)
    saved = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.exists() else {}
    records = [record for record in contexts() if record["id"] not in saved]
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
                print(f"batch failed ({futures[future][0]['kind']}): {type(error).__name__}", flush=True)
                continue
            saved.update(generated)
            OUTPUT.write_text(json.dumps(saved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"generated {len(saved)}/{len(contexts())}", flush=True)


if __name__ == "__main__":
    main()
