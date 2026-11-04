# Baseline cho cổng hồi quy

- `baseline.json`: lần chạy `baseline`, tập **test**, chế độ `gate` (k = 3), Actor trích xuất + embedding giả lập,
  kho tri thức trong bộ nhớ dựng từ `knowledge-base/` (66 tài liệu). Tạo bằng `bash eval/run_experiments.sh stage8`.
- So một lần chạy mới với baseline:

```
.venv/Scripts/python -m eval.compare eval/baselines/baseline.json eval/reports/<run_id>
```

- Cập nhật baseline khi đã chấp nhận thay đổi: chạy lại `baseline` trên test ở chế độ `gate` rồi chép `run.json` vào đây,
  ghi lý do trong commit.
