"""2.6: accuracy of the LLM parts on hand-labelled sets (design 09, 9.5).

    python scripts/recommendation_eval/llm_eval.py --export   # llm_eval_cases.py -> llm_eval_labels.json
    python scripts/recommendation_eval/llm_eval.py            # calls the AI service, writes ket-qua/llm/

A1, tags: micro precision / recall / F1 over (group, code). A1, schedule: per sentence the set of days (P/R/F1 and
exact match) and, on the days found in both, the IoU of the time ranges. A2: P/R/F1 per filter field and the share of
sentences whose fields are all right.

Calls the AI service directly (http://127.0.0.1:8000) with the internal token read from the .env at the root of the
repository; the token is never printed. About 200 LLM calls. The vocabulary and the catalog come from the database
named by EVAL_DB (run it with EVAL_DB=ptutor to read the dev catalog).
"""

import csv
import json
import sys
from pathlib import Path
from urllib import request

from common import REPO, ROOT, query_json, read_json, write_json

LABELS = ROOT / "llm_eval_labels.json"
OUTPUT = REPO / "docs" / "recommendation" / "ket-qua" / "llm"
AI = "http://127.0.0.1:8000"
GROUPS = ("TOPIC", "STYLE", "SEGMENT", "GOAL")


def export() -> None:
    import llm_eval_cases as cases

    data = {
        "note": "Nhãn nháp do Claude soạn 22/10/2026 theo 09 mục 9.5 — nhóm cần đọc và sửa trước khi đưa số vào báo cáo.",
        "a1_tags": [{"entityType": e, "subject": s, "grade": g, "text": t, "expected": x} for e, s, g, t, x in cases.A1_TAGS],
        "a1_schedule": [{"text": t, "expected": [list(slot) for slot in x]} for t, x in cases.A1_SCHEDULE],
        "a2": [{"direction": "STUDENT_TO_TUTOR" if d == "S" else "TUTOR_TO_STUDENT", "text": t, "expected": x}
               for d, t, x in cases.A2_QUERIES],
    }
    LABELS.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"wrote {LABELS}")


def token() -> str:
    for line in (REPO / ".env").read_text(encoding="utf-8").splitlines():
        if line.startswith("AI_INTERNAL_TOKEN="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("AI_INTERNAL_TOKEN is not in .env")


def call(path: str, body: dict, secret: str) -> dict:
    req = request.Request(AI + path, data=json.dumps(body).encode("utf-8"), method="POST",
                          headers={"Content-Type": "application/json", "X-Internal-Token": secret})
    with request.urlopen(req, timeout=120) as response:
        return json.loads(response.read())


def vocabulary(subject: str | None) -> dict:
    rows = query_json("""
        SELECT t.tag_group AS g, t.code, t.name, s.name AS subject FROM recommendation_tags t
        LEFT JOIN subjects s ON s.id = t.subject_id WHERE t.status = 'ACTIVE'""")
    groups = {group: [] for group in GROUPS}
    for row in rows:
        if row["g"] == "TOPIC" and row["subject"] != subject:
            continue
        groups[row["g"]].append({"code": row["code"], "name": row["name"]})
    return groups


def prf(tp: int, fp: int, fn: int) -> dict:
    p = tp / (tp + fp) if tp + fp else 1.0
    r = tp / (tp + fn) if tp + fn else 1.0
    return {"precision": p, "recall": r, "f1": 2 * p * r / (p + r) if p + r else 0.0, "tp": tp, "fp": fp, "fn": fn}


def minutes(clock: str) -> int:
    hour, minute = clock.split(":")
    return int(hour) * 60 + int(minute)


def iou(left, right) -> float:
    a, b = (minutes(left[0]), minutes(left[1])), (minutes(right[0]), minutes(right[1]))
    inter = max(0, min(a[1], b[1]) - max(a[0], b[0]))
    union = max(a[1], b[1]) - min(a[0], b[0])
    return inter / union if union else 0.0


def evaluate_tags(cases: list, secret: str) -> tuple[dict, list]:
    tp = fp = fn = 0
    rows = []
    vocab_cache = {}
    for case in cases:
        vocab = vocab_cache.setdefault(case["subject"], vocabulary(case["subject"]))
        answer = call("/enrich", {"entityType": case["entityType"], "subject": case["subject"],
                                  "grades": [f"Lớp {case['grade']}"], "fields": {"description": case["text"]},
                                  "vocabulary": vocab, "extract": ["tags"]}, secret)
        got = {f"{t['group']}:{t['code']}" for t in answer.get("tags", [])}
        want = set(case["expected"])
        tp, fp, fn = tp + len(got & want), fp + len(got - want), fn + len(want - got)
        rows.append({"text": case["text"], "expected": sorted(want), "got": sorted(got)})
    return prf(tp, fp, fn), rows


def evaluate_schedule(cases: list, secret: str) -> tuple[dict, list]:
    tp = fp = fn = exact = 0
    ious, rows = [], []
    for case in cases:
        answer = call("/enrich", {"entityType": "STUDYING_REQUEST", "subject": "Toán", "grades": [],
                                  "fields": {"preferred_schedule": case["text"]}, "extract": ["schedule"]}, secret)
        got = {slot["dayOfWeek"]: (slot["start"], slot["end"]) for slot in answer.get("schedule", [])}
        want = {day: (start, end) for day, start, end in case["expected"]}
        tp, fp, fn = tp + len(got.keys() & want.keys()), fp + len(got.keys() - want.keys()), fn + len(want.keys() - got.keys())
        exact += got.keys() == want.keys()
        ious += [iou(got[day], want[day]) for day in got.keys() & want.keys()]
        rows.append({"text": case["text"], "expected": case["expected"], "got": [[d, *got[d]] for d in sorted(got)]})
    summary = prf(tp, fp, fn)
    summary.update(exact_days=exact / len(cases), mean_iou=sum(ious) / len(ious) if ious else None)
    return summary, rows


def normalise_a2(answer: dict) -> dict:
    """The parsed sentence in the shape of the labels."""
    def grade_number(name: str):
        digits = "".join(ch for ch in name if ch.isdigit())
        return int(digits) if digits else None

    price = answer.get("price") or {}
    area = answer.get("area") or {}
    return {
        "subject": answer.get("subject"),
        "grades": sorted(g for g in (grade_number(n) for n in answer.get("grades") or []) if g),
        "mode": answer.get("mode"),
        "scope": area.get("scope") if area.get("scope") in ("PROVINCE", "ANY") else None,
        "districts": sorted(area.get("districts") or []),
        "price_min": price.get("min"),
        "price_max": price.get("max"),
        "days": sorted({slot["dayOfWeek"] for slot in answer.get("schedule") or []}),
        "styleTags": sorted(answer.get("styleTags") or []),
        "goalTags": sorted(answer.get("goalTags") or []),
        "segmentTags": sorted(answer.get("segmentTags") or []),
    }


A2_FIELDS = ["subject", "grades", "mode", "scope", "districts", "price_min", "price_max", "days", "styleTags",
             "goalTags", "segmentTags"]


def as_set(value) -> set:
    if value is None:
        return set()
    return set(value) if isinstance(value, list) else {value}


def evaluate_a2(cases: list, secret: str) -> tuple[dict, list]:
    catalog = {
        "subjects": [r["name"] for r in query_json("SELECT name FROM subjects WHERE status = 'ACTIVE' ORDER BY name")],
        "grades": [r["name"] for r in query_json("SELECT name FROM grades ORDER BY level")],
        "districtsInUserProvince": [r["name"] for r in query_json(
            "SELECT d.name FROM districts d JOIN provinces p ON p.id = d.province_id WHERE p.name = 'Hà Nội' ORDER BY 1")],
    }
    counts = {field: [0, 0, 0] for field in A2_FIELDS}
    all_right, rows = 0, []
    for case in cases:
        expected = {field: case["expected"].get(field) for field in A2_FIELDS}
        for field in ("grades", "districts", "days", "styleTags", "goalTags", "segmentTags"):
            expected[field] = sorted(expected[field] or [])
        got = normalise_a2(call("/parse-query", {"text": case["text"], "direction": case["direction"],
                                                 "catalog": catalog, "vocabulary": vocabulary(None)}, secret))
        right = True
        for field in A2_FIELDS:
            want, have = as_set(expected[field]), as_set(got[field])
            counts[field][0] += len(want & have)
            counts[field][1] += len(have - want)
            counts[field][2] += len(want - have)
            right &= want == have
        all_right += right
        rows.append({"text": case["text"], "expected": expected, "got": got, "all_right": right})
    summary = {field: prf(*values) for field, values in counts.items()}
    summary["all_fields_right"] = all_right / len(cases)
    return summary, rows


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    if "--export" in sys.argv:
        export()
        return 0
    labels = read_json(LABELS)
    secret = token()
    tags, tag_rows = evaluate_tags(labels["a1_tags"], secret)
    schedule, schedule_rows = evaluate_schedule(labels["a1_schedule"], secret)
    a2, a2_rows = evaluate_a2(labels["a2"], secret)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_json(OUTPUT / "chi-tiet.json", {"a1_tags": tag_rows, "a1_schedule": schedule_rows, "a2": a2_rows})
    with (OUTPUT / "tom-tat.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["phan", "chi_so", "gia_tri"])
        for key in ("precision", "recall", "f1"):
            writer.writerow(["A1 tag", key, f"{tags[key]:.4f}"])
        for key in ("precision", "recall", "f1", "exact_days"):
            writer.writerow(["A1 lịch (theo ngày)", key, f"{schedule[key]:.4f}"])
        writer.writerow(["A1 lịch", "IoU khung giờ", f"{schedule['mean_iou']:.4f}" if schedule["mean_iou"] else ""])
        for field in A2_FIELDS:
            writer.writerow([f"A2 {field}", "f1", f"{a2[field]['f1']:.4f}"])
        writer.writerow(["A2", "câu đúng toàn bộ", f"{a2['all_fields_right']:.4f}"])
    print("A1 tags", {k: round(v, 3) for k, v in tags.items() if isinstance(v, float)})
    print("A1 schedule", {k: round(v, 3) if isinstance(v, float) else v for k, v in schedule.items()})
    print("A2", {f: round(a2[f]["f1"], 3) for f in A2_FIELDS}, "all right", round(a2["all_fields_right"], 3))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
