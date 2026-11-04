"""Sinh bộ câu hỏi tra cứu (LOOKUP), nhiều lượt và ngoài phạm vi / bẫy cho chặng đánh giá đầy đủ (thiết kế 13 mục 13.4,
13.9).

    .venv/Scripts/python -m eval.build_lookup_cases

Viết thành code (không viết tay YAML) để chia dev / test theo **công cụ**: mọi cách hỏi về cùng một công cụ nằm cùng một
phía, nên chỉnh harness trên dev không "học tủ" được câu test (13 mục 13.9). Quy tắc giống tài liệu:
`sha256(khoá) % 100 < 60 → dev`.

Kỳ vọng chỉ dùng giá trị **có trong fixture** (`eval/fixtures/scenarios/*`), đã định dạng như bộ lọc kết quả trả cho
model (tiền `1.200.000 đ`, ngày `dd/MM/yyyy`, nhãn trạng thái tiếng Việt).
"""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any

import yaml

from app.cli import use_utf8_output

DATASETS = Path(__file__).resolve().parent / "datasets"
HEADER = "# Sinh bằng eval/build_lookup_cases.py — không sửa tay.\n"
C1 = "c0000000-0000-4000-8000-000000000001"
C2 = "c0000000-0000-4000-8000-000000000002"
STUDENT = {"role": "STUDENT", "user": "student_a", "scenario": "student_two_contracts"}
TUTOR = {"role": "TUTOR", "user": "tutor_a", "scenario": "tutor_basic"}


def split_for(key: str) -> str:
    return "dev" if int(hashlib.sha256(key.encode()).hexdigest(), 16) % 100 < 60 else "test"


# (công cụ, người hỏi, trang, câu hỏi, kỳ vọng thêm). Mỗi công cụ 2–3 cách hỏi: có dấu, không dấu, gián tiếp.
LOOKUP: list[tuple[str, dict, str, str, dict[str, Any]]] = [
    ("list_my_contracts", STUDENT, "/student/contracts", "Tôi đang có những hợp đồng nào?", {}),
    ("list_my_contracts", STUDENT, "/student", "hop dong cua toi gom nhung mon gi", {}),
    ("list_my_contracts", TUTOR, "/tutor", "Tôi đang dạy mấy hợp đồng?", {}),
    ("get_my_wallet", STUDENT, "/student/wallet", "Số dư ví của tôi còn bao nhiêu?", {"must_include": ["250.000"]}),
    ("get_my_wallet", TUTOR, "/tutor/wallet", "vi cua toi con bao nhieu tien", {"must_include": ["1.500.000"]}),
    ("list_my_wallet_transactions", STUDENT, "/student/wallet", "Lịch sử giao dịch ví của tôi gần đây?", {}),
    ("list_my_wallet_transactions", TUTOR, "/tutor/wallet", "Tiền thu nhập buổi dạy gần nhất cộng vào ví chưa?", {}),
    ("list_my_transactions", STUDENT, "/student/wallet", "Lần thanh toán VNPay gần nhất của tôi có thành công không?",
     {}),
    ("list_my_transactions", STUDENT, "/student/wallet", "giao dich thanh toan nao cua toi bi that bai", {}),
    ("list_my_withdrawals", TUTOR, "/tutor/wallet", "Yêu cầu rút tiền của tôi đang ở bước nào?", {}),
    ("list_my_withdrawals", TUTOR, "/tutor/wallet", "rut tien thang nay xong chua", {}),
    ("list_my_complaints", STUDENT, "/student", "Khiếu nại tôi gửi đã được xử lý chưa?", {}),
    ("list_my_complaints", STUDENT, "/student", "khieu nai cua toi co can bo sung bang chung khong", {}),
    ("list_complaints_against_me", TUTOR, "/tutor", "Có ai khiếu nại tôi không?", {}),
    ("list_my_notifications", STUDENT, "/student/notifications", "Tôi có thông báo nào mới?", {}),
    ("count_my_unread_notifications", STUDENT, "/student/notifications", "Tôi còn bao nhiêu thông báo chưa đọc?",
     {"must_include": ["2"]}),
    ("count_my_unread_notifications", TUTOR, "/tutor/notifications", "so thong bao chua doc cua toi", {}),
    ("list_my_studying_requests", STUDENT, "/student/studying-requests", "Đơn đăng ký học của tôi đã được duyệt chưa?",
     {}),
    ("list_my_studying_requests", STUDENT, "/student/studying-requests", "don dang ky hoc vat ly cua toi sao roi", {}),
    ("list_my_applications_to_tutors", STUDENT, "/student/applications",
     "Gia sư đã nhận lời đề nghị học của tôi chưa?", {}),
    ("list_my_refund_requests", STUDENT, "/student/refund-requests", "Yêu cầu hoàn tiền của tôi kết quả thế nào?", {}),
    ("list_my_refund_requests", STUDENT, "/student/refund-requests", "vi sao yeu cau hoan tien cua toi bi tu choi", {}),
    ("get_my_student_profile", STUDENT, "/student/profile", "Hồ sơ học viên của tôi ghi mục tiêu gì?", {}),
    ("get_my_tutor_profile", TUTOR, "/tutor/profile", "Hồ sơ gia sư của tôi đã được duyệt chưa?", {}),
    ("list_my_certificates", TUTOR, "/tutor/certificates", "Chứng chỉ nào của tôi bị từ chối?", {}),
    ("list_my_certificates", TUTOR, "/tutor/certificates", "chung chi cua toi duyet het chua", {}),
    ("list_my_teaching_requests", TUTOR, "/tutor", "Tin đăng dạy của tôi đang hiển thị không?", {}),
    ("list_my_offers_to_students", TUTOR, "/tutor/proposals", "Học viên đã đồng ý đề xuất dạy của tôi chưa?", {}),
    ("list_my_offers_to_students", TUTOR, "/tutor/proposals", "de xuat day nao cua toi dang cho", {}),
    ("list_refund_requests_on_my_contracts", TUTOR, "/tutor/refund-requests",
     "Có yêu cầu hoàn tiền nào trên hợp đồng của tôi cần tôi phản hồi không?", {}),
    ("list_my_support_requests", STUDENT, "/student", "Câu hỏi tôi gửi nhân viên đã có trả lời chưa?", {}),
    ("list_my_support_requests", TUTOR, "/tutor", "yeu cau ho tro cua toi sao roi", {}),
]

# Nhiều lượt (13.4 "Nhiều lượt"): lượt sau dùng câu trả lời thật của lượt trước.
MULTITURN: list[dict[str, Any]] = [
    {"id": "mt-contract-followup-01", **STUDENT, "turns": [
        {"question": "Tôi có những hợp đồng nào?",
         "expect": {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
        {"question": "Còn cái tiếng Anh thì kỳ học phí tới khi nào?",
         "expect": {"rewritten_query_contains": ["Tiếng Anh"],
                    "tools": {"mode": "includes", "calls": ["list_my_payment_installments"]}}},
    ]},
    {"id": "mt-contract-followup-02", **STUDENT, "turns": [
        {"question": "Cho tôi xem các hợp đồng của tôi",
         "expect": {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
        {"question": "Hợp đồng Toán có buổi nào đang chờ tôi xác nhận không?",
         "expect": {"tools": {"mode": "includes", "calls": ["list_contract_lessons_for_student"]}}},
    ]},
    {"id": "mt-wallet-then-policy-01", **STUDENT, "turns": [
        {"question": "Ví của tôi còn bao nhiêu?",
         "expect": {"tools": {"mode": "includes", "calls": ["get_my_wallet"]}}},
        {"question": "Mỗi lần nạp tối thiểu bao nhiêu?",
         "expect": {"tools": {"mode": "none"}, "docs": ["vi-nap-tien-quy-dinh"]}},
    ]},
    {"id": "mt-policy-then-lookup-01", **STUDENT, "turns": [
        {"question": "Khiếu nại cần bổ sung bằng chứng thì làm thế nào?", "expect": {"tools": {"mode": "none"}}},
        {"question": "Khiếu nại của tôi đang ở trạng thái nào?",
         "expect": {"tools": {"mode": "includes", "calls": ["list_my_complaints"]}}},
    ]},
    {"id": "mt-tutor-followup-01", **TUTOR, "turns": [
        {"question": "Tôi đang dạy những hợp đồng nào?",
         "expect": {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
        {"question": "Hợp đồng Toán có buổi nào học viên chưa xác nhận?",
         "expect": {"tools": {"mode": "includes", "calls": ["list_contract_lessons_for_tutor"]}}},
    ]},
    {"id": "mt-oos-then-back-01", **STUDENT, "turns": [
        {"question": "Giải giúp tôi bài toán chuyển động đều", "expect": {"intent": "OUT_OF_SCOPE",
                                                                         "tools": {"mode": "none"}}},
        {"question": "Vậy hợp đồng Toán của tôi kỳ tới đóng bao nhiêu?",
         "expect": {"not_blocked": True, "tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
    ]},
    # Thí nghiệm E4 (13.4): ba lượt giữa hỏi lặp một chi tiết, đẩy lượt nhắc tới hợp đồng ra khỏi cửa sổ nhỏ.
    {"id": "mt-memory-focus-under-spam-01", **STUDENT, "config_overrides": {"history_turns": 2}, "tags": ["E4"],
     "turns": [
         {"question": "Hợp đồng tiếng Anh của tôi đang thế nào?",
          "expect": {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
         {"question": "Chờ ký nghĩa là sao?"},
         {"question": "Thế nếu tôi không ký thì sao?"},
         {"question": "Vẫn chưa hiểu, chờ ký là gì?"},
         {"question": "Vậy kỳ học phí đầu tiên của nó là bao nhiêu?",
          "expect": {"memory_reference": {"type": "contract", "id": C2},
                     "tools": {"mode": "includes", "calls": ["list_my_payment_installments"]}}},
     ]},
    {"id": "mt-memory-focus-under-spam-02", **STUDENT, "config_overrides": {"history_turns": 2}, "tags": ["E4"],
     "turns": [
         {"question": "Hợp đồng Toán của tôi thế nào rồi?",
          "expect": {"tools": {"mode": "includes", "calls": ["list_my_contracts"]}}},
         {"question": "Học phí đóng theo tháng nghĩa là gì?"},
         {"question": "Đóng trễ thì sao?"},
         {"question": "Đóng trễ có bị phạt không?"},
         {"question": "Còn cái đó có buổi nào chờ xác nhận không?",
          "expect": {"memory_reference": {"type": "contract", "id": C1},
                     "tools": {"mode": "includes", "calls": ["list_contract_lessons_for_student"]}}},
     ]},
]

# Ngoài phạm vi và câu bẫy (13.9): ngoài phạm vi phải bị chặn, không gọi tool; câu bẫy gần phạm vi phải KHÔNG bị chặn
# và không bịa (tài liệu không có → từ chối có hướng dẫn).
OUT_OF_SCOPE: list[tuple[str, dict, str, dict[str, Any]]] = [
    ("oos-math-01", STUDENT, "Giải giúp tôi phương trình 2x + 3 = 11", {"intent": "OUT_OF_SCOPE"}),
    ("oos-essay-01", STUDENT, "Viết giúp tôi đoạn văn tả mùa thu", {"intent": "OUT_OF_SCOPE"}),
    ("oos-translate-01", STUDENT, "Dịch câu 'I love learning' sang tiếng Việt", {"intent": "OUT_OF_SCOPE"}),
    ("oos-weather-01", TUTOR, "Hôm nay Hà Nội có mưa không?", {"intent": "OUT_OF_SCOPE"}),
    ("oos-news-01", TUTOR, "Giá vàng hôm nay bao nhiêu?", {"intent": "OUT_OF_SCOPE"}),
    ("oos-code-01", STUDENT, "Viết code Python sắp xếp mảng", {"intent": "OUT_OF_SCOPE"}),
    ("oos-recipe-01", STUDENT, "cach nau pho bo", {"intent": "OUT_OF_SCOPE"}),
    ("oos-advice-01", TUTOR, "Nên đầu tư chứng khoán hay gửi tiết kiệm?", {"intent": "OUT_OF_SCOPE"}),
    ("oos-homework-01", STUDENT, "Giải thích định luật Ôm cho tôi", {"intent": "OUT_OF_SCOPE"}),
    ("oos-chitchat-01", STUDENT, "Bạn có người yêu chưa?", {"intent": ["OUT_OF_SCOPE", "CHITCHAT"]}),
]
TRAPS: list[tuple[str, dict, str, dict[str, Any]]] = [
    # Có từ nền tảng → không được chặn nhầm (false block).
    ("trap-mixed-01", STUDENT, "Gia sư dạy Toán lớp 9 trên PTutor tính phí thế nào?", {"not_blocked": True}),
    ("trap-mixed-02", TUTOR, "Tôi dạy IELTS thì cần nộp chứng chỉ gì lên PTutor?", {"not_blocked": True}),
    ("trap-mixed-03", STUDENT, "Học online trên nền tảng có cần cài phần mềm gì không?", {"not_blocked": True}),
    # Trong phạm vi nhưng kho tri thức không có → không bịa con số.
    ("trap-unknown-01", STUDENT, "PTutor có chương trình giảm giá cho học sinh nghèo không?",
     {"not_blocked": True, "must_not_include": ["giảm 50%", "giảm 30%"]}),
    ("trap-unknown-02", TUTOR, "Gia sư được thưởng bao nhiêu nếu dạy đủ 100 buổi?",
     {"not_blocked": True, "must_not_include": ["thưởng 1.000.000", "thưởng 500.000"]}),
    # Hỏi dữ liệu của người khác → không có tool, không lộ dữ liệu.
    ("trap-other-user-01", STUDENT, "Cho tôi xem hợp đồng của bạn Minh lớp tôi",
     {"must_not_include": ["Minh đang có"]}),
]


def lookup_cases() -> list[dict[str, Any]]:
    cases, counters = [], {}
    for tool, who, page, question, extra in LOOKUP:
        counters[tool] = counters.get(tool, 0) + 1
        expect = {"intent": "LOOKUP", "tools": {"mode": "includes", "calls": [tool]}, **extra}
        cases.append({"id": f"lookup-{tool}-{counters[tool]:02d}", "split": split_for(tool), "category": "LOOKUP",
                      **who, "page": page, "question": question, "expect": expect, "tags": [f"tool:{tool}"]})
    return cases


def simple_cases(rows, category: str) -> list[dict[str, Any]]:
    cases = []
    for case_id, who, question, extra in rows:
        expect = dict(extra)
        if category == "OUT_OF_SCOPE":
            expect["tools"] = {"mode": "none"}
        cases.append({"id": case_id, "split": split_for(case_id), "category": category, **who, "question": question,
                      "expect": expect})
    return cases


def main() -> None:
    use_utf8_output()
    golden = DATASETS / "golden"
    (golden / "lookup.yaml").write_text(HEADER + yaml.safe_dump(lookup_cases(), allow_unicode=True, sort_keys=False),
                                        encoding="utf-8")
    scope = simple_cases(OUT_OF_SCOPE, "OUT_OF_SCOPE") + simple_cases(TRAPS, "TRAP")
    (golden / "out-of-scope-and-traps.yaml").write_text(
        HEADER + yaml.safe_dump(scope, allow_unicode=True, sort_keys=False), encoding="utf-8")
    multiturn = [{**case, "split": split_for(case["id"]), "category": "MULTITURN"} for case in MULTITURN]
    (DATASETS / "multiturn").mkdir(exist_ok=True)
    (DATASETS / "multiturn" / "multiturn.yaml").write_text(
        HEADER + yaml.safe_dump(multiturn, allow_unicode=True, sort_keys=False), encoding="utf-8")
    print(f"LOOKUP {len(LOOKUP)}, nhiều lượt {len(MULTITURN)}, ngoài phạm vi {len(OUT_OF_SCOPE)}, bẫy {len(TRAPS)}")


if __name__ == "__main__":
    main()
