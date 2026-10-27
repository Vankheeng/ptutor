# Độ chính xác LLM — A1 (gắn tag, trích lịch) và A2 (tách câu tìm kiếm)

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Sinh bằng `scripts/recommendation_eval/llm_eval.py` (gọi AI service
> thật, model của `.env`). Lý do và quyết định: [13 mục 13.6](../../13-tinh-chinh-sau-danh-gia.md); bảng quy ước nhãn
> L1–L10 / T1–T10: 13.6 và 05 mục 5.4.

**Chạy gì.** Hai bộ câu, cùng danh mục `ptutor`:

- **Bộ độc lập** (`llm_eval_independent.json`): 13 câu lịch, 23 câu tìm kiếm; câu do ChatGPT sinh, nhóm sửa và duyệt
  nhãn. **Chỉ dùng để đo** — không xem từng câu sai của bộ này khi sửa prompt. Đây là số chính.
- **Bộ nháp**: 196 câu do Claude soạn nhãn; nhóm đã duyệt mẫu 40 / 196. Báo kèm.

| File | Nội dung |
|---|---|
| `tom-tat.csv`, `chi-tiet.json` | Lượt đầu 22/10/2026 (bộ nháp, chưa duyệt nhãn) |
| `truoc-tom-tat.csv`, `truoc-chi-tiet.json` | Prompt cũ, bộ nháp (`--as truoc`) |
| `sau-tom-tat.csv`, `sau-chi-tiet.json` | Prompt mới, bộ nháp (`--as sau`) |
| `truoc-doc-lap-*`, `sau-doc-lap-*` | Như trên, bộ độc lập (`--group independent`) |

Mỗi lượt 236 lời gọi. Sau khi sửa nhãn (T10 ở bộ độc lập; #23, #30, #35 ở bộ nháp) cả hai lượt được chấm lại từ câu
trả lời đã lưu (`--rescore`, không gọi LLM).

## Bảng chính

| Chỉ số | Độc lập trước | Độc lập sau | Nháp trước | Nháp sau |
|---|---|---|---|---|
| Lịch: đúng toàn bộ ngày | 76,9 % | **100 %** | 78,3 % | **100 %** |
| Lịch: recall / precision theo ngày | 0,85 / 1,00 | 1,00 / 1,00 | 0,76 / 0,96 | 1,00 / 1,00 |
| A2: câu đúng toàn bộ | 39,1 % (9/23) | **91,3 %** (21/23) | 88,7 % | **96,3 %** |
| A2: ngày (F1) | 0,14 | 1,00 | 1,00 | 1,00 |
| A1 tag (F1) | — | — | 0,973 | 0,974 |

## Đọc kết quả

- Lỗi lớn nhất của prompt cũ: "thứ 2, 4, 6" đọc thành ngày ISO 2, 4, 6; giờ bắt đầu không có giờ kết thúc bị bỏ trống.
  Ghi rõ quy ước thì lịch đúng hết ở cả hai bộ.
- A2 độc lập 91,3 %: một câu "đúng thêm" là do sửa nhãn cho thống nhất (T10), trước khi sửa nhãn là 87,0 %. Hai câu còn
  sai không đem ra chỉnh prompt.
- Tag không giảm khi đổi prompt (0,973 → 0,974).
- Ngoài prompt, AI service tự sửa hai trường hợp: một giá min = max → 0,8X–1,2X; `mode = ONLINE` → bỏ quận.
