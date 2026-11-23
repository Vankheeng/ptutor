---
id: tin-tim-gia-su-sua-dong-huy
title: Cách sửa, tạm đóng, hủy tin tìm gia sư
type: guide
audience: [STUDENT]
features: [studying-request]
routes: [/student/studying-requests, /student/studying-requests/:requestId, /student/studying-requests/:requestId/edit]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: components/student/StudentStudyingRequestMenu.tsx (Edit information khi DRAFT/OPEN, Pay to publish khi DRAFT, Close request khi OPEN, Reopen request khi CLOSED, Cancel request khi DRAFT/OPEN/MATCHED/CLOSED)"
  - "frontend: pages/student/StudentStudyingRequestEditPage.tsx (Edit studying request, Save changes)"
  - "backend: StudyingRequestController PATCH /{requestId}, PATCH /{requestId}/status, POST /{requestId}/cancel"
error_codes: [INVALID_STUDYING_REQUEST_STATUS_TRANSITION, EMPTY_UPDATE_REQUEST]
keywords: [sửa tin, đóng tin, mở lại tin, hủy tin, sua tin, dong tin, mo lai tin, huy tin, close request, cancel request]
todo: []
---

# Cách sửa, tạm đóng, hủy tin tìm gia sư

Mọi thao tác dưới đây nằm trong menu thao tác của từng tin ở trang **My Studying Requests**. Menu chỉ hiện những thao tác hợp lệ với trạng thái hiện tại của tin.

## Sửa tin tìm gia sư

1. Vào **My Studying Requests**, mở menu thao tác của tin.
2. Chọn **Edit information**. Trang **Edit studying request** mở ra.
3. Sửa thông tin cần thay đổi.
4. Bấm **Save changes**.

Nút **Edit information** chỉ hiện khi tin đang là bản nháp hoặc đang mở.

## Tạm đóng tin tìm gia sư

1. Mở menu thao tác của tin đang mở.
2. Chọn **Close request**.

Tin tạm đóng không nhận đề xuất mới.

## Mở lại tin tìm gia sư

1. Mở menu thao tác của tin đang tạm đóng.
2. Chọn **Reopen request**.

Tin trở về trạng thái đang mở và gia sư lại thấy tin.

## Hủy tin tìm gia sư

1. Mở menu thao tác của tin.
2. Chọn **Cancel request**.

Khi hủy thành công, trang báo "Studying request cancelled." (đã hủy tin). Nút **Cancel request** hiện với tin bản nháp, đang mở, đã ghép hoặc tạm đóng. Tin đã hủy không khôi phục được, và các đề xuất đang chờ cho tin đó bị hủy theo.
