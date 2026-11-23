# 7. API

> 💡 **File này nói về gì:** các đường gọi (API) giữa giao diện, backend và `assistant-service`: gọi vào đâu, gửi gì, nhận gì, lỗi thế nào. Dùng khi code.

Hai lớp API:

- **API công khai của backend** — frontend gọi, bọc trong `ApiResponse<T>` như các API khác (`success`, `code`,
  `message`, `data`, `errors`, `timestamp`, `path`).
- **API nội bộ của `assistant-service`** (`/v1/**`) — chỉ backend gọi, header `X-Internal-Token`.

## 7.1. Phân quyền ở backend

> 💡 **Nói đơn giản:** Ai được gọi API nào: học viên / gia sư gọi API trợ lý; chỉ admin gọi API quản trị kho tri thức.

Thêm vào `SecurityConstants` / `SecurityConfig`:

```java
public static final String[] ASSISTANT_API = {
        "/api/v1/assistant",
        "/api/v1/assistant/**"
};
public static final String[] ADMIN_ASSISTANT_API = {
        "/api/v1/admin/assistant",
        "/api/v1/admin/assistant/**"
};
// .requestMatchers(ADMIN_ASSISTANT_API).hasRole("ADMIN")
// .requestMatchers(ASSISTANT_API).hasAnyRole("STUDENT", "TUTOR")
```

## 7.2. API trợ lý (học viên, gia sư)

> 💡 **Nói đơn giản:** Các API khung chat dùng: gửi câu hỏi, xem lại cuộc trò chuyện, kết thúc phiên, đánh giá, lấy câu hỏi gợi ý. Có ví dụ dữ liệu gửi và nhận.

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| `POST` | `/api/v1/assistant/messages` | Gửi câu hỏi, nhận câu trả lời; không có `conversationId` → tạo hội thoại mới |
| `GET` | `/api/v1/assistant/conversations` | Hội thoại của tôi (phân trang, mới nhất trước) |
| `GET` | `/api/v1/assistant/conversations/{conversationId}/messages` | Tin nhắn của một hội thoại |
| `POST` | `/api/v1/assistant/conversations/{conversationId}/end` | Kết thúc phiên (đăng xuất, "Cuộc trò chuyện mới") — đặt `ended_at` |
| `DELETE` | `/api/v1/assistant/conversations/{conversationId}` | Xóa (mềm) hội thoại |
| `POST` | `/api/v1/assistant/messages/{messageId}/feedback` | Đánh giá 👍/👎 |
| `GET` | `/api/v1/assistant/suggestions?pagePath=...` | Câu hỏi gợi ý theo vai trò và trang |

### `POST /api/v1/assistant/messages`

Request:

```json
{
  "conversationId": "7d1c…",
  "message": "Kỳ học phí tiếp theo của hợp đồng Toán khi nào?",
  "pagePath": "/student/contracts"
}
```

Response `data`:

```json
{
  "conversationId": "7d1c…",
  "messageId": "a90f…",
  "answer": "Kỳ học phí tiếp theo của hợp đồng **Toán 9** là ngày 20/10/2026, số tiền 1.200.000 đ. …[1]",
  "citations": [
    {"index": 1, "documentTitle": "Thanh toán học phí theo kỳ", "section": "Hạn thanh toán",
     "docKey": "thanh-toan-ky-hoc-phi", "anchor": "han-thanh-toan"}
  ],
  "actions": [{"label": "Xem hợp đồng", "route": "/student/contracts/3b2e…"}],
  "suggestions": ["Thanh toán bằng ví thế nào?"],
  "intent": "LOOKUP",
  "grounded": "FULL",
  "createdAt": "2026-10-13T08:15:02Z"
}
```

| HTTP | `code` | Khi |
|---|---|---|
| 400 | `INVALID_MESSAGE` | Rỗng hoặc quá 1.000 ký tự |
| 404 | `CONVERSATION_NOT_FOUND` | Không tồn tại hoặc không thuộc người dùng |
| 409 | `CONVERSATION_ENDED` | Phiên đã kết thúc → widget bắt đầu phiên mới và gửi lại |
| 409 | `CONVERSATION_BUSY` | Phiên đang xử lý một lượt khác (hai tab dùng chung phiên) |
| 429 | `ASSISTANT_RATE_LIMITED` | Quá giới hạn (khởi đầu: 20 tin / 5 phút / người) |
| 503 | `ASSISTANT_UNAVAILABLE` | `assistant-service` hoặc `ai-service` tắt, lỗi, quá thời gian |

### `POST /api/v1/assistant/messages/{messageId}/feedback`

```json
{ "rating": -1, "reason": "WRONG", "comment": "Phí đăng tin đã đổi" }
```

Gửi lại → ghi đè.

## 7.3. API quản trị kho tri thức (ADMIN)

> 💡 **Nói đơn giản:** Các API trang quản trị dùng: tạo / sửa bài nháp, công bố, lưu trữ, khôi phục phiên bản, xem câu hỏi chưa trả lời được, thống kê.

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| `GET` | `/api/v1/admin/assistant/documents` | Danh sách tài liệu: lọc theo `status`, `type`, `features`, `audience`, từ khóa; có cờ "có bản nháp", "còn todo" |
| `POST` | `/api/v1/admin/assistant/documents` | Tạo tài liệu mới (bản nháp) |
| `GET` | `/api/v1/admin/assistant/documents/{id}` | Chi tiết: bản công bố, bản nháp, lịch sử phiên bản |
| `PUT` | `/api/v1/admin/assistant/documents/{id}/draft` | Lưu bản nháp (tạo nháp từ bản công bố nếu chưa có) |
| `POST` | `/api/v1/admin/assistant/documents/{id}/draft/validate` | Kiểm tra hợp lệ + xem trước cách chia đoạn |
| `POST` | `/api/v1/admin/assistant/documents/{id}/publish` | Công bố bản nháp |
| `POST` | `/api/v1/admin/assistant/documents/{id}/archive` | Lưu trữ |
| `DELETE` | `/api/v1/admin/assistant/documents/{id}/draft` | Bỏ bản nháp |
| `GET` | `/api/v1/admin/assistant/documents/{id}/versions/{version}` | Xem một phiên bản cũ |
| `POST` | `/api/v1/admin/assistant/documents/{id}/versions/{version}/restore` | Khôi phục: công bố lại nội dung phiên bản cũ thành phiên bản mới |
| `GET` | `/api/v1/admin/assistant/unanswered` | Câu hỏi chưa trả lời được / bị 👎 (đã che, không có `user_id`) |
| `GET` | `/api/v1/admin/assistant/stats?from=&to=` | Số lượt, tỉ lệ grounded / abstain, 👍/👎, độ trễ p50/p95, token |
| `GET` / `PUT` | `/api/v1/admin/assistant/settings/{key}` | Route cho phép, câu hỏi gợi ý |

## 7.4. Backend → `assistant-service`

> 💡 **Nói đơn giản:** Backend chuyển câu hỏi vào trong kèm "thẻ ra vào" nội bộ và thông tin người hỏi. Quyền đăng nhập của người hỏi được chuyển theo chỉ để dùng cho tra cứu.

`AssistantProxyService` thêm header:

| Header | Giá trị |
|---|---|
| `X-Internal-Token` | `app.assistant-service.internal-token` |
| `X-User-Id` | Claim `sub` |
| `X-User-Role` | Claim `role` |
| `Authorization` | Chuyển tiếp nguyên header của người dùng — chỉ `ToolGateMiddleware` dùng, cho tool tra cứu |
| `X-Request-Id` | ID truy vết, ghi log hai phía và `turn_trace.request_id` |

`assistant-service` tin `X-User-*` vì chỉ backend gọi được (token nội bộ + cổng bind `127.0.0.1`). Tool tra cứu
không dựa vào `X-User-Id` mà dùng `Authorization`, nên kể cả khi header bị giả, backend vẫn chỉ trả dữ liệu của
chủ token. API quản trị chỉ được gọi từ `AdminAssistantController` (đã kiểm `ROLE_ADMIN`); `assistant-service` kiểm
lại `X-User-Role = ADMIN` cho mọi `/v1/admin/**`.

## 7.5. API nội bộ của `assistant-service`

> 💡 **Nói đơn giản:** Các API chỉ backend gọi được (không mở ra ngoài).

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| `GET` | `/health` | `{"status": "ready" \| "degraded" \| "error", "db", "llm", "embedding", "rerank", "kb": {"published": 24, "chunks": 180}}` — không cần token |
| `POST` | `/v1/chat` | Một lượt hội thoại |
| `GET` | `/v1/conversations`, `/v1/conversations/{id}/messages` | Kiểm tra `userId` sở hữu |
| `DELETE` | `/v1/conversations/{id}` | |
| `POST` | `/v1/messages/{id}/feedback` | |
| `GET` | `/v1/suggestions` | `?role=&pagePath=` |
| | `/v1/admin/**` | Tương ứng 7.3 |

Câu hỏi gợi ý lưu ở `kb_setting` (`suggestions`), ví dụ:

```json
{
  "STUDENT": {
    "default": ["Làm sao đăng tin tìm gia sư?", "Phí đăng tin là bao nhiêu?", "Hợp đồng của tôi thế nào rồi?"],
    "/student/wallet": ["Nạp ví tối thiểu bao nhiêu?", "Thanh toán VNPay bị lỗi thì sao?"],
    "/student/contracts": ["Hủy hợp đồng thế nào?", "Kỳ học phí tiếp theo khi nào?"]
  },
  "TUTOR": {
    "default": ["Hồ sơ của tôi đã được duyệt chưa?", "Làm sao nộp chứng chỉ?"],
    "/tutor/certificates": ["Chứng chỉ bị từ chối thì làm gì?"]
  }
}
```

## 7.6. API yêu cầu hỗ trợ

API cho người dùng, nhân viên và admin, cùng endpoint nội bộ `/v1/conversations/{id}/excerpt`:
[14](./14-yeu-cau-ho-tro.md) mục 14.5.
