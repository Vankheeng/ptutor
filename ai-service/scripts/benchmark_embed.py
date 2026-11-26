"""Measure embedding latency on this machine.

    python scripts/benchmark_embed.py                          # in-process, loads the model directly
    python scripts/benchmark_embed.py --url http://127.0.0.1:8000 --token <AI_INTERNAL_TOKEN>

Reports p50 / p95 for a single short query (the search path, budget 150 ms) and for batches
(the background EmbeddingWorker path), plus a small Vietnamese similarity sanity check.
"""

import argparse
import json
import statistics
import sys
import time
import http.client
import urllib.parse
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

QUERY = "Cần gia sư Toán lớp 9 ôn thi vào 10, học buổi tối, kiên nhẫn với học sinh mất gốc"
POSTING = (
    "Gia sư Toán THCS 5 năm kinh nghiệm, chuyên luyện thi vào lớp 10. Phương pháp: hệ thống lại "
    "kiến thức nền, luyện đề theo chuyên đề, sửa lỗi trình bày. Kiên nhẫn với học sinh mất gốc, "
    "dạy trực tuyến hoặc tại nhà các buổi tối trong tuần."
)
SANITY = [
    ("Cần gia sư Toán lớp 9 ôn thi vào 10", "Nhận dạy Toán THCS, luyện thi vào lớp 10", True),
    ("Cần gia sư Toán lớp 9 ôn thi vào 10", "Dạy tiếng Anh giao tiếp cho người đi làm", False),
    ("Muốn học IELTS mục tiêu 6.5", "Luyện thi IELTS cam kết đầu ra 6.5+", True),
    ("Muốn học IELTS mục tiêu 6.5", "Gia sư Hoá học lớp 11, ôn thi học kỳ", False),
]


def local_embedder():
    from app.config import load_settings
    from app.embed import Embedder

    settings = load_settings()
    embedder = Embedder(settings.embed_dir, settings.embed_max_tokens, settings.threads)
    return lambda texts: embedder.embed(texts)


def remote_embedder(url: str, token: str):
    # One keep-alive connection, like the backend's HTTP client; urllib would add a proxy
    # lookup to every call on Windows and distort the numbers.
    parsed = urllib.parse.urlsplit(url)
    connection = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=60)
    headers = {"Content-Type": "application/json", "X-Internal-Token": token}

    def embed(texts):
        connection.request("POST", "/embed", json.dumps({"texts": texts}).encode("utf-8"), headers)
        response = connection.getresponse()
        payload = response.read()
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}: {payload[:200]!r}")
        return np.array(json.loads(payload)["embeddings"], dtype=np.float32)

    return embed


def measure(embed, texts, runs):
    samples = []
    for _ in range(runs):
        started = time.perf_counter()
        embed(texts)
        samples.append((time.perf_counter() - started) * 1000)
    samples.sort()
    p95 = samples[min(len(samples) - 1, int(round(0.95 * (len(samples) - 1))))]
    return statistics.median(samples), p95


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", help="benchmark a running service instead of loading the model here")
    parser.add_argument("--token", default="", help="X-Internal-Token for --url")
    parser.add_argument("--runs", type=int, default=30)
    args = parser.parse_args()
    # Windows consoles default to a legacy code page that cannot print Vietnamese.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    started = time.perf_counter()
    embed = remote_embedder(args.url, args.token) if args.url else local_embedder()
    embed([QUERY])  # warm-up
    print(f"Ready after {time.perf_counter() - started:.1f} s ({'remote' if args.url else 'in-process'})")

    print(f"{'case':<34}{'p50 ms':>10}{'p95 ms':>10}{'ms/text':>10}")
    for label, texts in (
        ("1 short query", [QUERY]),
        ("1 posting (~80 tokens)", [POSTING]),
        ("batch of 8 postings", [POSTING] * 8),
        ("batch of 32 postings", [POSTING] * 32),
    ):
        runs = args.runs if len(texts) == 1 else max(5, args.runs // 6)
        p50, p95 = measure(embed, texts, runs)
        print(f"{label:<34}{p50:>10.1f}{p95:>10.1f}{p50 / len(texts):>10.1f}")

    print("\nSimilarity sanity check (cosine; related pairs must score higher than unrelated ones)")
    ok = True
    scores = {}
    for left, right, related in SANITY:
        vectors = embed([left, right])
        scores[(left, related)] = float(vectors[0] @ vectors[1])
        print(f"  {scores[(left, related)]:.3f}  {'related  ' if related else 'unrelated'}  {left}  |  {right}")
    for left in {pair[0] for pair in SANITY}:
        ok = ok and scores[(left, True)] > scores[(left, False)]
    print("Sanity check:", "PASSED" if ok else "FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
