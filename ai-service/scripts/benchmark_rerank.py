"""Measure reranking latency on this machine and check that scores make sense in Vietnamese.

    python scripts/benchmark_rerank.py
    python scripts/benchmark_rerank.py --documents 20 --repeat 5 --batch-sizes 1 4 20

Reports p50 / max for one search (one query against N postings) with short and with long postings.
The backend's rerank timeout (app.recommendation.rerank.timeout-ms) should be chosen from these numbers.
"""

import argparse
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

QUERY = (
    "Môn Toán, Lớp 9. Cần gia sư ôn thi vào 10, con học lực trung bình, yếu hình học, "
    "học buổi tối tại nhà, mong thầy cô kiên nhẫn."
)
SHORT = "Gia sư Toán THCS, luyện thi vào lớp 10, dạy buổi tối."
LONG = (
    "Gia sư Toán THCS 5 năm kinh nghiệm, chuyên luyện thi vào lớp 10. Phương pháp: hệ thống lại "
    "kiến thức nền, luyện đề theo chuyên đề, sửa lỗi trình bày. Kiên nhẫn với học sinh mất gốc, "
    "dạy trực tuyến hoặc tại nhà các buổi tối trong tuần. Đã chuẩn hoá: Hình học phẳng, Hệ phương trình, "
    "Thi vào lớp 10, Kiên nhẫn, Chậm mà chắc. Học sinh mục tiêu: trung bình, khá. Có giáo án riêng "
    "cho từng học sinh, kiểm tra định kỳ hai tuần một lần và báo cáo kết quả cho phụ huynh. "
) * 2
SANITY = [
    ("Cần gia sư Toán lớp 9 ôn thi vào 10, yếu hình học",
     "Nhận dạy Toán THCS, luyện thi vào lớp 10, mạnh hình học",
     "Dạy tiếng Anh giao tiếp cho người đi làm"),
    ("Muốn học IELTS mục tiêu 6.5",
     "Luyện thi IELTS cam kết đầu ra 6.5+",
     "Gia sư Hoá học lớp 11, ôn thi học kỳ"),
    ("Con mất gốc Toán, cần thầy cô kiên nhẫn dạy chậm",
     "Gia sư Toán kiên nhẫn, chuyên học sinh mất gốc, đi từ căn bản",
     "Luyện Toán nâng cao cho học sinh giỏi thi chuyên"),
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--documents", type=int, default=20)
    parser.add_argument("--repeat", type=int, default=5)
    parser.add_argument("--batch-sizes", type=int, nargs="+", default=[1, 4, 20])
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    from app.config import load_settings
    from app.rerank import Reranker

    settings = load_settings()
    for batch_size in args.batch_sizes:
        started = time.perf_counter()
        reranker = Reranker(settings.rerank_dir, settings.rerank_max_tokens, settings.threads, batch_size)
        print(f"batch size {batch_size}: model loaded in {time.perf_counter() - started:.1f} s")
        reranker.score(QUERY, [SHORT])  # warm-up
        for label, document in (("short", SHORT), ("long", LONG)):
            documents = [f"{document} ({index})" for index in range(args.documents)]
            timings = []
            for _ in range(args.repeat):
                started = time.perf_counter()
                reranker.score(QUERY, documents)
                timings.append((time.perf_counter() - started) * 1000)
            print(f"  {args.documents} {label:5} postings: p50 {statistics.median(timings):7.0f} ms, "
                  f"max {max(timings):7.0f} ms, {statistics.median(timings) / args.documents:5.0f} ms per pair")

    print("sanity (relevant should score clearly higher than unrelated):")
    for query, relevant, unrelated in SANITY:
        good, bad = reranker.score(query, [relevant, unrelated])
        print(f"  {'ok ' if good > bad + 0.2 else 'BAD'} relevant {good:.3f}  unrelated {bad:.3f}  | {query}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
