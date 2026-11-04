#!/usr/bin/env bash
# T3: chạy thí nghiệm với model thật (gpt-4.1-mini). Khoá OPENAI_API_KEY phải có sẵn trong môi trường.
# Mọi lần chạy dùng chung sổ chi tiêu eval/reports/t3/ledger.json: tổng không vượt BUDGET_USD (mặc định 10 $).
#
#   OPENAI_API_KEY=... bash eval/run_experiments_real.sh t3          # cả chuỗi
#   OPENAI_API_KEY=... bash eval/run_experiments_real.sh t3 rest     # chỉ phần còn thiếu sau lần hết credit 2026-11-04
# Nhà cung cấp từ chối (hết credit, khoá sai) → eval.run trả mã 2 và chuỗi dừng ngay (set -e).
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL="${1:-t3}"
OUT="eval/reports/$LABEL"
LEDGER="$OUT/ledger.json"
BUDGET="${BUDGET_USD:-10}"
PY=.venv/Scripts/python
[ -x "$PY" ] || PY=.venv/bin/python
run() {  # run <config> <mode> <split> <suites> <trần tiền của lần chạy>
  "$PY" -m eval.run --config "eval/configs/$1.yaml" --mode "$2" --split "$3" --suites "$4" --yes \
    --max-cost-usd "$5" --budget-ledger "$LEDGER" --budget-usd "$BUDGET" --out "$OUT/$1-$3" | tail -1
}

PART="${2:-all}"
if [ "$PART" = all ]; then
  run real_llm gate test golden,multiturn,safety 2.5
  run real_e1_vector gate test golden 2
  run real_e2_no_verifier gate test golden,multiturn,safety 2
else  # lần trước 6 case safety cuối của no_verifier gặp lỗi hết credit → chạy lại riêng bộ safety
  run real_e2_no_verifier gate test safety 0.5
fi
run real_e2_no_retry gate test golden,multiturn,safety 2.5
run real_llm gate all multiturn 0.5
run real_e4_no_rewrite gate all multiturn 0.5
"$PY" -m eval.verifier --config eval/configs/real_llm.yaml --k 3 --yes --max-cost-usd 0.5 \
  --budget-ledger "$LEDGER" --budget-usd "$BUDGET" --out "$OUT/verifier" | tail -2
echo "Xong. Sổ chi tiêu: $LEDGER"
