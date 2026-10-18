# 8. Giao diện

## 8.1. Widget chat (học viên, gia sư)

### Vị trí

- Nút nổi góc dưới phải, gắn trong `StudentLayout` và `TutorLayout` (`frontend/src/pages/student|tutor/*Layout.tsx`)
  để có mặt trên mọi trang sau đăng nhập. Không hiển thị ở `/login`, `/register`, `/forgot-password`.
- Mở ra thành khung chat (desktop ~380 × 560 px; điện thoại: toàn màn hình).

### Thành phần

```
src/components/assistant/
├── AssistantWidget.tsx       # nút nổi + trạng thái mở/đóng
├── AssistantPanel.tsx        # khung chat: header, danh sách tin, ô nhập
├── MessageBubble.tsx         # Markdown an toàn (không render HTML), chip trích dẫn
├── CitationChip.tsx          # [1] → tooltip: tên tài liệu > mục
├── ActionButtons.tsx         # nút điều hướng (react-router navigate)
├── SuggestionChips.tsx       # câu hỏi gợi ý
├── FeedbackButtons.tsx       # 👍 / 👎 + lý do
├── SupportRequestDialog.tsx  # gửi câu hỏi cho nhân viên (14)
└── useAssistant.ts           # gọi API, giữ conversationId, trạng thái đang trả lời
src/services/assistantApi.ts  # dùng httpClient sẵn có (tự gắn token, tự làm mới token khi 401)
```

### Hành vi

| Tình huống | Hiển thị |
|---|---|
| Mở lần đầu | Lời chào theo vai trò + 3–4 câu hỏi gợi ý theo trang đang mở |
| Đang chờ trả lời | Bong bóng "đang soạn…", khóa nút gửi; quá 35 giây (lớn hơn timeout 30 giây của backend) → báo lỗi, cho gửi lại |
| Có trích dẫn | Chip `[1]`; bấm → tooltip tên tài liệu và mục |
| Có `actions` | Nút dưới câu trả lời; bấm → điều hướng trong ứng dụng, giữ khung chat mở |
| `grounded = PARTIAL` | Câu trả lời bình thường + dòng nhỏ "Một phần câu hỏi chưa có thông tin chính thức" |
| `grounded = NONE` (abstain) | Viền nhạt khác màu + nút "Gửi câu hỏi cho nhân viên" ([14](./14-yeu-cau-ho-tro.md)) |
| `grounded = PARTIAL` | Như trên, thêm nút "Gửi câu hỏi cho nhân viên" |
| `OUT_OF_SCOPE` | Câu từ chối mẫu + chip câu hỏi gợi ý; **không** có nút gửi nhân viên |
| 👎 | Hỏi lý do (Sai / Không hữu ích / Đã cũ / Khác) + ô ghi chú tùy chọn; sau đó gợi ý "Gửi câu hỏi cho nhân viên" |
| `429` | "Bạn gửi hơi nhanh, thử lại sau ít phút" |
| `503` | "Trợ lý tạm thời không khả dụng" — không ảnh hưởng phần còn lại của trang |
| Hội thoại mới | Nút "Cuộc trò chuyện mới" ở header; hội thoại vượt 20 lượt → chỉ gợi ý bắt đầu cuộc trò chuyện mới |
| Menu header | "Hỏi nhân viên hỗ trợ" (mở hộp thoại gửi), "Yêu cầu hỗ trợ của tôi" (mở trang danh sách) |
| `409 CONVERSATION_ENDED` | Tự bắt đầu phiên mới, gửi lại câu hỏi, hiện dòng nhỏ "Đã bắt đầu cuộc trò chuyện mới" |

- Gửi kèm `pagePath = location.pathname` để trợ lý ưu tiên tài liệu và tool của trang hiện tại.
- `conversationId` giữ trong state của widget và `sessionStorage` (bọc try/catch) — sống theo **tab**: không mất khi
  chuyển trang hay tải lại, mất khi đóng tab. Đăng xuất xóa `conversationId` và gọi `/end` (vòng đời phiên:
  [04](./04-luong-hoi-dap.md) mục 4.10). Không hết hạn theo thời gian không hoạt động.
- Dòng chú thích dưới ô nhập: "Trợ lý có thể sai. Thông tin chính thức xem tại trang tương ứng."

## 8.2. Trang quản trị kho tri thức (ADMIN)

`dev` chưa có khu vực quản trị trên frontend; các trang dưới đây đặt trong layout quản trị chung (route gợi ý
`/admin/assistant/...`), dùng chung với các chức năng `Admin*` khác khi được xây dựng.

```
src/pages/admin/assistant/
├── KbDocumentListPage.tsx      # danh sách tài liệu + bộ lọc + cờ "có nháp", "còn todo"
├── KbDocumentEditorPage.tsx    # form metadata + trình soạn Markdown + xem trước
├── KbVersionHistory.tsx        # lịch sử phiên bản, xem bản cũ, khôi phục
├── UnansweredQuestionsPage.tsx # câu hỏi chưa trả lời được / bị 👎
└── AssistantStatsPage.tsx      # số liệu tổng quan
```

### Trang soạn tài liệu

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Hủy hợp đồng                    [Bản nháp v4] · đang công bố v3   [Lưu] [Công bố] │
├──────────────────────────────┬───────────────────────────────────────────────┤
│ Loại: policy ▾               │ Xem trước                                     │
│ Đối tượng: ☑ Học viên ☑ Gia sư│  # Hủy hợp đồng                               │
│ Chức năng: contract ×        │  ## Khi nào được hủy …                        │
│ Trang liên quan: /student/…  │                                               │
│ Từ khóa: hủy hợp đồng, …     │ Đoạn sau khi chia: 4 đoạn (≤ 400 token) ✓     │
│ Căn cứ: ContractController…  │ Đoạn 1: Hủy hợp đồng > Khi nào được hủy (180) │
│ ⚠ Còn 1 câu hỏi cần trả lời  │ Đoạn 2: … > Các bước hủy (học viên)    (240)  │
│ ─────────────────────────────│ …                                             │
│ # Hủy hợp đồng               │                                               │
│ ## Khi nào được hủy          │ Lịch sử phiên bản: v3 (đang dùng) · v2 · v1   │
│ …  (soạn Markdown)           │                          [Khôi phục v2]       │
└──────────────────────────────┴───────────────────────────────────────────────┘
```

- **Công bố**: chạy kiểm tra hợp lệ → hộp thoại xác nhận → công bố. Nội dung sai sau khi công bố → "Khôi phục"
  phiên bản trước ([03](./03-kho-tri-thuc.md) mục 3.2).
- **Còn `todo`** → nút Công bố bị khóa, hiện danh sách câu hỏi cần trả lời.
- **Câu hỏi chưa trả lời được**: mỗi dòng có nút "Tạo tài liệu" (điền sẵn câu hỏi vào bản nháp mới) hoặc
  "Mở tài liệu liên quan" (theo đoạn được truy xuất gần nhất).

## 8.3. Trang yêu cầu hỗ trợ

Trang của học viên / gia sư và trang xử lý của nhân viên: [14](./14-yeu-cau-ho-tro.md) mục 14.6.

## 8.4. Mở rộng sau

- **Streaming** (SSE) để hiện chữ dần. Lưu ý: khi có bước kiểm chứng, chỉ stream được sau khi verifier chấp nhận
  (hoặc stream rồi thay thế khi bị bác) — giai đoạn đầu trả một lần.
- Lịch sử hội thoại dạng danh sách ở cột trái của khung chat.
