#!/usr/bin/env bash
# Chạy toàn bộ thí nghiệm cho báo cáo đánh giá (thiết kế 10 mục 10.5, 13 mục 13.11) với Actor trích xuất (T0–T2,
# không tốn tiền). Kết quả ở eval/reports/<nhãn>/ (không commit); số liệu chép vào BAO-CAO-DANH-GIA.md.
#
#   bash eval/run_experiments.sh stage8
#
# - baseline: dev (quick) để chỉnh; test (gate, k = 3) để báo cáo.
# - E1: vector / hybrid (= baseline) / hybrid + rerank (chỉ chạy khi có ai-service: EVAL_WITH_RERANK=1).
# - E2: không verifier / verifier không thử lại / baseline.
# - E4: không viết lại câu hỏi / baseline — trên bộ nhiều lượt.
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL="${1:-experiments}"
OUT="eval/reports/$LABEL"
PY=.venv/Scripts/python
[ -x "$PY" ] || PY=.venv/bin/python
run() { "$PY" -m eval.run --config "eval/configs/$1.yaml" --mode "$2" --split "$3" --suites "$4" --out "$OUT/$1-$3" | tail -1; }

run baseline quick dev golden,multiturn,safety
run baseline gate test golden,multiturn,safety
run e1_vector gate test golden
run e2_no_verifier gate test golden,multiturn,safety
run e2_no_retry gate test golden,multiturn,safety
run e4_no_rewrite gate all multiturn
run baseline gate all multiturn
if [ "${EVAL_WITH_RERANK:-0}" = "1" ]; then
  run e1_rerank gate test golden
fi
echo "Xong. Thư mục: $OUT"
