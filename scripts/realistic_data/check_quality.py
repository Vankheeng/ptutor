"""Check the seeded personas and prose before loading them into PostgreSQL."""

import collections
import json
import re
from pathlib import Path

import generate_narratives as corpus_spec
import seed


def main():
    data = json.loads(Path(__file__).with_name("narratives.json").read_text(encoding="utf-8"))
    records = corpus_spec.contexts()
    assert set(data) == {record["id"] for record in records}, "narrative coverage is incomplete"
    by_kind = collections.defaultdict(list)
    for record in records:
        kind = record["kind"]
        row = data[record["id"]]
        assert set(row) == set(corpus_spec.FIELDS[kind]), (kind, record["id"])
        for field, text in row.items():
            assert isinstance(text, str) and text.strip(), (kind, record["id"], field)
            limit = 255 if (kind, field) in {
                ("tutor", "teaching_methodology"), ("student", "weak_points"),
                ("studying", "description"), ("studying", "learning_goals"),
            } else 1000
            assert len(text) <= limit, (kind, record["id"], field, len(text))
        if kind == "teaching":
            assert not any(phrase in row["description"].lower()
                           for phrase in ("tìm gia sư", "cần gia sư")), record["id"]
        if kind in ("teaching", "studying"):
            description = row["description"].lower()
            mode = record["context"]["mode"]
            opposite = ("trực tiếp", "tại nhà") if mode == "trực tuyến" else ("trực tuyến", "online")
            assert not any(phrase in description for phrase in opposite), (kind, record["id"], "mode")
        if kind in ("student", "studying"):
            text = re.sub(r"vào lớp\s+\d{1,2}", "", " ".join(row.values()).lower())
            mentioned_grades = {int(value) for value in re.findall(r"lớp\s+(\d{1,2})", text)}
            assert not mentioned_grades or mentioned_grades == {record["context"]["grade"]}, (kind, record["id"], "grade")
        by_kind[kind].append(row)
    for kind, rows in by_kind.items():
        for field in corpus_spec.FIELDS[kind]:
            unique = len({row[field].strip().casefold() for row in rows})
            assert unique >= len(rows) * 0.95, (kind, field, unique, len(rows))
            print(f"{kind}.{field}: {unique}/{len(rows)} unique")

    emails = set()
    names = set()
    for role, count in (("tutor", 84), ("student", 64)):
        for i in range(count):
            index = i if role == "tutor" else i + 85
            male = i % 2 == 0
            given = (seed.MALE if male else seed.FEMALE)[(index * 7 + index // 12) % 12]
            middle = (seed.MIDDLE_MALE if male else seed.MIDDLE_FEMALE)[(index // 3) % 6]
            surname = seed.FAMILIES[(index * 5 + index // 12) % 12]
            name = f"{surname} {middle} {given}"
            assert name not in names, name
            names.add(name)
            grade = 6 + (i * 5) % 7
            year = 1983 + i % 19 if role == "tutor" else 2026 - (grade + 6)
            email = seed.seed_email(name, year)
            assert re.fullmatch(r"[a-z0-9]+@gmail\.com", email), email
            assert email not in emails, email
            emails.add(email)
    print(f"personas: {len(names)} unique names and {len(emails)} valid emails")

    teaching_titles = []
    for i in range(84):
        subject = list(seed.TOPICS)[i % len(seed.TOPICS)]
        district = seed.DISTRICTS[(i * 7) % len(seed.DISTRICTS)]
        for j in range(2):
            grade, focus = seed.TOPICS[subject][(i+j) % len(seed.TOPICS[subject])]
            mode = "ONLINE" if (i+j) % 4 == 0 else "OFFLINE"
            teaching_titles.append(seed.teaching_title(i, j, subject, grade, focus, mode, district))
    assert len(set(teaching_titles)) >= 150
    print(f"teaching titles: {len(set(teaching_titles))}/{len(teaching_titles)} unique")

    studying_titles = []
    for i in range(64):
        grade = 6 + (i * 5) % 7
        district = seed.DISTRICTS[(i * 7) % len(seed.DISTRICTS)]
        for j in range(1 + (i % 3 == 0)):
            subject = list(seed.TOPICS)[(i + j*3) % len(seed.TOPICS)]
            focus = seed.TOPICS[subject][(i+j) % len(seed.TOPICS[subject])][1]
            mode = "ONLINE" if (i+j) % 4 == 0 else "OFFLINE"
            studying_titles.append(seed.studying_title(i, j, subject, grade, focus, mode, district))
    assert len(set(studying_titles)) == len(studying_titles)
    print(f"studying titles: {len(set(studying_titles))}/{len(studying_titles)} unique")


if __name__ == "__main__":
    main()
