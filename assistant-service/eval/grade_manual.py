"""Lệnh chấm tay (thiết kế 13 mục 13.5):

    # 1) Xuất phiếu chấm mù từ một hay nhiều lần chạy (ví dụ baseline và e2_no_verifier trên tập test)
    .venv/Scripts/python -m eval.grade_manual export eval/reports/<run_a> eval/reports/<run_b> --out eval/reports/manual
    # 2) Người chấm điền cột kp1..kpN, contradiction, unsupported_claims trong manual/sheet.csv (KHÔNG mở key.json)
    # 3) Tính kết quả theo cấu hình
    .venv/Scripts/python -m eval.grade_manual score --out eval/reports/manual
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.cli import use_utf8_output
from eval.graders.manual_grading import export_sheet, score_sheet


def main() -> None:
    use_utf8_output()
    parser = argparse.ArgumentParser(description="Chấm tay theo rubric")
    parser.add_argument("action", choices=["export", "score"])
    parser.add_argument("runs", nargs="*", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    arguments = parser.parse_args()
    sheet, key = arguments.out / "sheet.csv", arguments.out / "key.json"
    if arguments.action == "export":
        if not arguments.runs:
            raise SystemExit("Cần ít nhất một thư mục lần chạy")
        count = export_sheet(arguments.runs, sheet, key)
        print(f"{count} câu trả lời cần chấm → {sheet} (khoá giải mã: {key} — người chấm không mở)")
    else:
        print(json.dumps(score_sheet(sheet, key), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
