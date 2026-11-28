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
        fields = set(corpus_spec.FIELDS[kind])
        assert set(row) == fields or (kind == "teaching" and set(row) == fields | {"title"}), (kind, record["id"])
        for field, text in row.items():
            assert isinstance(text, str) and text.strip(), (kind, record["id"], field)
            limit = 255 if (kind, field) in {
                ("tutor", "teaching_methodology"), ("student", "weak_points"),
                ("studying", "description"), ("studying", "learning_goals"),
                ("studying_expanded", "description"), ("studying_expanded", "learning_goals"),
                ("teaching_expanded", "title"), ("studying_expanded", "title"),
            } else 1000
            assert len(text) <= limit, (kind, record["id"], field, len(text))
            if kind.endswith("_expanded") and field == "title":
                assert len(text) <= 130, (kind, record["id"], field, len(text))
            if kind == "teaching_expanded" and field == "description":
                assert len(text) <= 350, (kind, record["id"], field, len(text))
        if kind.startswith("teaching"):
            assert not any(phrase in row["description"].lower()
                           for phrase in ("tìm gia sư", "cần gia sư")), record["id"]
        if kind == "teaching_expanded":
            description = row["description"].lower()
            assert "tôi" in description or "mình" in description, record["id"]
            assert not any(phrase in description for phrase in
                           ("con tôi", "gia đình tôi", "gia đình mong", "bạn đang tìm")), record["id"]
        if kind.startswith(("teaching", "studying")):
            description = " ".join(row.values()).lower()
            mode = record["context"]["mode"]
            opposite = ("trực tiếp", "tại nhà") if mode == "trực tuyến" else ("trực tuyến", "online")
            assert not any(phrase in description for phrase in opposite), (kind, record["id"], "mode")
        if kind in ("student", "studying", "studying_expanded", "teaching_expanded"):
            text = re.sub(r"vào lớp\s+\d{1,2}", "", " ".join(row.values()).lower())
            mentioned_grades = {int(value) for value in re.findall(r"lớp\s+(\d{1,2})", text)}
            assert not mentioned_grades or mentioned_grades == {record["context"]["grade"]}, (kind, record["id"], "grade")
        by_kind[kind].append(row)
    for kind, rows in by_kind.items():
        for field in corpus_spec.FIELDS[kind]:
            unique = len({row[field].strip().casefold() for row in rows})
            target = 0.99 if kind.endswith("_expanded") else 0.95
            assert unique >= len(rows) * target, (kind, field, unique, len(rows))
            print(f"{kind}.{field}: {unique}/{len(rows)} unique")
        if kind.endswith("_expanded") or kind in ("tutor", "student"):
            field = "description" if kind.endswith("_expanded") else "introduction"
            openings = collections.Counter(" ".join(row[field].casefold().split()[:5]) for row in rows)
            assert openings.most_common(1)[0][1] <= max(4, len(rows) * 0.05), (kind, openings.most_common(3))
        if kind.endswith("_expanded"):
            patterns = collections.Counter()
            for row in rows:
                title = row["title"].casefold()
                for subject in sorted(seed.SUBJECT_GRADES, key=len, reverse=True):
                    title = title.replace(subject.casefold(), "{môn}")
                title = re.sub(r"\d+", "#", title)
                patterns[title] += 1
            assert patterns.most_common(1)[0][1] <= len(rows) * 0.05, (kind, patterns.most_common(3))

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
    for role in ("tutor", "student"):
        for _, ordinal, _, grade, _ in seed.expanded_personas(role):
            name = seed.expanded_name(role, ordinal)
            year = (1987 + ordinal % 17) if role == "tutor" else (
                1984 + ordinal % 15 if grade <= 5 else 2026 - (grade + 6))
            email = seed.seed_email(name, year)
            assert name not in names and email not in emails, (name, email)
            assert re.fullmatch(r"[a-z0-9]+@gmail\.com", email), email
            names.add(name)
            emails.add(email)
    print(f"personas: {len(names)} unique names and {len(emails)} valid emails")

    teaching_titles = []
    corpus = data
    for i in range(84):
        subject = seed.LEGACY_SUBJECTS[i % len(seed.LEGACY_SUBJECTS)]
        district = seed.DISTRICTS[(i * 7) % len(seed.DISTRICTS)]
        for j in range(2):
            grade, focus = seed.LEGACY_TOPICS[subject][(i+j) % len(seed.LEGACY_TOPICS[subject])]
            mode = "ONLINE" if (i+j) % 4 == 0 else "OFFLINE"
            key = seed.uid("teaching", i, j)
            teaching_titles.append(corpus[key].get("title") or seed.teaching_title(i, j, subject, grade, focus, mode, district))
    assert len(set(teaching_titles)) == len(teaching_titles)
    print(f"teaching titles: {len(set(teaching_titles))}/{len(teaching_titles)} unique")

    studying_titles = []
    for i in range(64):
        grade = 6 + (i * 5) % 7
        district = seed.DISTRICTS[(i * 7) % len(seed.DISTRICTS)]
        for j in range(1 + (i % 3 == 0)):
            subject = seed.LEGACY_SUBJECTS[(i + j*3) % len(seed.LEGACY_SUBJECTS)]
            focus = seed.topic_for_grade(subject, grade)
            mode = "ONLINE" if (i+j) % 4 == 0 else "OFFLINE"
            studying_titles.append(seed.studying_title(i, j, subject, grade, focus, mode, district))
    assert len(set(studying_titles)) == len(studying_titles)
    print(f"studying titles: {len(set(studying_titles))}/{len(studying_titles)} unique")

    for role in ("tutor", "student"):
        covered = set()
        for _, ordinal, subject, grade, _ in seed.expanded_personas(role):
            for j, post_grade, mode, _ in seed.expanded_postings(role, ordinal, subject, grade):
                covered.add((subject, post_grade, mode))
        required = {(subject, grade, mode) for subject, grades in seed.SUBJECT_GRADES.items()
                    for grade in grades for mode in ("ONLINE", "OFFLINE")}
        assert required <= covered, (role, required - covered)
        print(f"{role} search combinations: {len(required)}/{len(required)} covered")


if __name__ == "__main__":
    main()
