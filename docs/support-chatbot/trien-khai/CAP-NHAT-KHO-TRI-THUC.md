# Cập nhật kho tri thức khi trả lời các câu todo

> Kho hiện còn **95 câu todo ở 50 tài liệu**. Đó là những câu người soạn ghi là chưa có câu trả lời chính thức.
> Trả lời xong các câu này **không cần sửa code**, chỉ cần sửa dữ liệu rồi chạy lại các lệnh dưới đây.
> Các lệnh `python` chạy trong `assistant-service/`.

## 1. Sửa tài liệu (`knowledge-base/**/*.md`)

- [ ] Viết câu trả lời vào **nội dung** tài liệu, đúng mục liên quan, theo quy ước viết trong
      [03-kho-tri-thuc.md](../03-kho-tri-thuc.md).
- [ ] **Xoá câu đó khỏi danh sách `todo:`** trong front matter. Nếu câu đó còn trong danh sách:
  - tài liệu không công bố được nếu không có `--allow-todo`;
  - trợ lý vẫn coi câu đó là câu hỏi mở và chỉ trả lời "một phần".
- [ ] Kiểm tra tài liệu: `python -m app.kb.import --path ../knowledge-base`. Lệnh chỉ lưu nháp vào DB, chưa công bố,
      và in lỗi định dạng nếu có.
- [ ] Kiểm tra danh mục route: `python scripts/check_catalog.py`.

## 2. Nạp lại vào DB

```bash
# Docker (demo)
docker compose -p ptutor-demo --profile assistant run --rm assistant-service kb-import --publish
# Hoặc chạy trực tiếp
python -m app.kb.import --path ../knowledge-base --publish
```

- Không có `--allow-todo` thì tài liệu nào còn todo sẽ **không** được công bố, và lệnh báo ra những tài liệu đó.
  `--allow-todo` chỉ dùng cho demo / eval.
- Lệnh tự chia đoạn và embed lại. Nếu dùng bge-m3 (`ASSISTANT_EMBEDDING_PROVIDER=ai-service`), ai-service phải đang chạy.

## 3. Sửa nhãn bộ câu hỏi eval (dễ quên nhất)

File `knowledge-base/_eval/cau-hoi-mau.csv`, cột `loai`:

- [ ] Câu nào **giờ đã có đáp án**:
  - đổi `NO_ANSWER` thành `POLICY`, `HOWTO`, `FAQ` hoặc `ERROR`;
  - viết lại cột `y_bat_buoc` thành các ý chính của câu trả lời. Con số (tiền, ngày, %) trong cột này sẽ thành từ khoá
    bắt buộc khi chấm.

  Nếu không đổi, eval vẫn đòi trợ lý trả lời "chưa có thông tin", nên chấm **trượt** khi nó trả lời đúng.
- [ ] Câu nào vẫn chưa có đáp án: giữ `NO_ANSWER`.
- [ ] Thêm tài liệu mới: không cần chia lại dev / test. Tài liệu mới tự vào một phía theo băm `doc_id`, tài liệu cũ giữ
      nguyên phía.

Sau đó dựng lại dữ liệu eval:

```bash
python -m eval.build_datasets          # bộ golden từ cau-hoi-mau.csv
python -m eval.build_verifier_claims   # bộ khẳng định cho verifier (sinh từ nội dung tài liệu)
python -m eval.make_splits             # chỉ cần khi có tài liệu mới
```

## 4. Chạy lại eval

Chạy theo thứ tự này, và xem kết quả từng bước trước khi chạy bước sau:

| Bước | Lệnh | Chi phí |
|---|---|---|
| Kiểm tra không hỏng gì (Actor giả lập) | `python -m eval.run --mode quick --split dev` | 0 $ |
| Hiệu chỉnh lớp chặn phạm vi (nếu đổi nhiều câu hỏi) | `python -m eval.scope_eval` (cần ai-service) | 0 $ |
| Model thật, dev | `eval.run --config eval/configs/real_bge.yaml --mode quick --split dev --yes …` | ≈ 0,4 $ |
| Model thật, test (để báo cáo) | `eval.run --config eval/configs/real_bge.yaml --mode gate --split test --yes …` | ≈ 0,9 $ |

- Lệnh chạy model thật cần `OPENAI_API_KEY`, `AI_SERVICE_URL`, `AI_INTERNAL_TOKEN` trong môi trường.
- Luôn thêm `--max-cost-usd` và `--budget-ledger eval/reports/t3/ledger.json`: hệ thống dừng trước khi vượt trần.
- Chỉ sửa prompt hay ngưỡng dựa trên **dev**. Tập **test** chỉ để báo cáo
  ([13-thiet-ke-eval.md](../13-thiet-ke-eval.md) mục 13.9).

## 5. Ghi vào báo cáo

- [ ] Ghi rõ "đo trên kho tri thức phiên bản mới (ngày …, còn N câu todo)". Số cũ (test 86,4%, NO_ANSWER 65,5%) đo
      trên bộ nhãn cũ, nên **không so trực tiếp** được.
- [ ] Cập nhật số câu todo còn lại trong [BAO-CAO-DANH-GIA.md](./BAO-CAO-DANH-GIA.md) mục 0.

## Không cần làm

- Không sửa code. Phần đưa câu hỏi todo vào ngữ cảnh model (`open_questions`) tự không làm gì khi tài liệu hết todo,
  nên cứ để nguyên.
- Không đổi `SPLITS.yaml` bằng tay.
