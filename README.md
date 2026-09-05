# PTutor

Dự án kết nối học viên và gia sư.

## Công nghệ

### Frontend

- React
- Vite
- JavaScript
- ESLint
- Prettier

### Backend

- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- PostgreSQL
- Checkstyle
- Spotless

### Development Tools

- Docker
- Git
- Husky
- Commitlint
- Conventional Commits

## Cài đặt

### 1. Clone project

```bash
git clone <repository-url>
cd ptutor
git fetch origin
git switch --track -c dev origin/dev
```

### 2. Cài đặt Git Hooks

Root `package.json` chỉ phục vụ Git workflow (Husky + Commitlint), không chứa dependency frontend/backend.

```bash
npm install
```

Lệnh trên tự chạy script `prepare` để kích hoạt Husky — không cần chạy thêm lệnh nào khác.

### 3. Chuẩn bị các file cấu hình local

Sau khi checkout hoặc pull code từ nhánh `dev`, tạo các file cấu hình local một lần. Các file này đã được Git ignore nên sẽ được giữ lại khi chuyển sang các nhánh khác.

Windows PowerShell:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
if (-not (Test-Path backend/src/main/resources/application.yml)) { Copy-Item backend/src/main/resources/application-prod.yaml backend/src/main/resources/application.yml }
notepad .env
notepad backend/src/main/resources/application.yml
```

Linux/macOS:

```bash
test -f .env || cp .env.example .env
test -f backend/src/main/resources/application.yml || cp backend/src/main/resources/application-prod.yaml backend/src/main/resources/application.yml
nano .env
nano backend/src/main/resources/application.yml
```

Điền các giá trị cấu hình tương ứng trong `.env` và `backend/src/main/resources/application.yml`. Không cần tạo lại hai file này khi chuyển sang các nhánh khác.

Docker Compose tự động đọc `.env` ở thư mục gốc project. Spring Boot sử dụng cấu hình trong `backend/src/main/resources/application.yml`.

### 4. Khởi động PostgreSQL

```bash
docker compose up -d
```

Kiểm tra container đang chạy:

```bash
docker ps
```

Kiểm tra riêng service PostgreSQL và trạng thái sẵn sàng của database:

```powershell
docker compose ps
docker compose exec postgres pg_isready -U postgres -d ptutor
```

Kiểm tra extension `vector`, cần thiết cho các cột embedding của `Student` và `Tutor`:

```powershell
docker compose exec postgres psql -U postgres -d ptutor -c "SELECT extname FROM pg_extension WHERE extname = 'vector';"
```

Kết quả phải có extension `vector`. Nếu bạn thay đổi `POSTGRES_USER` hoặc `POSTGRES_DB` trong `.env`, hãy thay `postgres` và `ptutor` trong các lệnh trên bằng giá trị tương ứng.

Cấu hình database mặc định:

```text
Database: ptutor
Username: postgres
Port: 5432
```

### 5. Khởi động Backend

```bash
cd backend
```

Khi backend khởi động, Flyway sẽ tự động tạo schema, dữ liệu tỉnh thành, tài khoản Admin và các thay đổi schema từ migration `V1` đến `V8`.

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Backend mặc định chạy tại `http://localhost:8080`.

### 6. Mở Swagger API Documentation

Sau khi backend khởi động thành công, mở Swagger UI tại:

```text
http://localhost:8080/swagger-ui.html
```

> **Mẹo:** Thay vì cấu hình request thủ công trên Postman, hãy mở Swagger UI, chọn API cần kiểm thử, nhấn `Try it out` rồi `Execute`. Sau đó copy đoạn cURL được hiển thị và dán vào Postman để tạo request nhanh hơn.

### 6.1. API danh mục môn học

API yêu cầu access token và trả về các môn học đang hoạt động. Dữ liệu môn học mặc định được tạo tự động khi backend khởi động.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/subjects` | Lấy danh sách môn học `ACTIVE`, sắp xếp theo tên |

Ví dụ response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": "1f8f8a2a-29e4-4f65-a5d5-2a2c9fdc6c3c",
      "name": "Toán",
      "description": null,
      "status": "ACTIVE",
      "createdAt": "2026-09-02T08:00:00Z",
      "updatedAt": "2026-09-02T08:00:00Z"
    }
  ],
  "errors": {},
  "timestamp": "2026-09-02T08:00:00Z",
  "path": "/api/v1/subjects"
}
```

### 6.2. API danh mục lớp học

API yêu cầu access token và trả về các lớp đang hoạt động, sắp xếp từ lớp 1 đến lớp 12. Sử dụng `id` trong response làm `gradeId` khi tạo hoặc cập nhật teaching request.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/grades` | Lấy danh sách grade `ACTIVE`, sắp xếp theo `level` |

Ví dụ response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": "4e5f8a08-41fb-4f14-85f7-01c9cfb5a2b0",
      "name": "Lớp 1",
      "level": 1,
      "status": "ACTIVE",
      "createdAt": "2026-09-02T08:00:00Z",
      "updatedAt": "2026-09-02T08:00:00Z"
    }
  ],
  "errors": {},
  "timestamp": "2026-09-02T08:00:00Z",
  "path": "/api/v1/grades"
}
```

### 6.3. API danh mục quận huyện

API yêu cầu access token và trả về danh sách district đã có trong hệ thống. Có thể truyền `provinceId` để chỉ lấy các district thuộc một tỉnh/thành phố. Sử dụng `id` trong response làm `districtId` khi tạo hoặc cập nhật teaching request.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/districts` | Lấy tất cả district, sắp xếp theo tỉnh và tên district |
| `GET` | `/api/v1/districts?provinceId={provinceId}` | Lấy district thuộc một tỉnh/thành phố |

Ví dụ response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": "5c2f2f3e-1e2f-4f3b-8a5b-1d3a8e0d6f10",
      "name": "Ba Đình",
      "provinceId": "11111111-1111-1111-1111-111111111111",
      "provinceName": "Hà Nội",
      "createdAt": "2026-09-02T08:00:00Z",
      "updatedAt": "2026-09-02T08:00:00Z"
    }
  ],
  "errors": {},
  "timestamp": "2026-09-02T08:00:00Z",
  "path": "/api/v1/districts"
}
```

### 6.4. API đăng yêu cầu tìm học viên

Các API này dành cho tài khoản `TUTOR` và sử dụng tutor từ access token. Request mới tạo luôn ở trạng thái `DRAFT` vì gia sư chưa hoàn tất thanh toán, nên chưa hiển thị cho student hoặc tutor khác.

Sau khi payment flow xác nhận thanh toán thành công, service nội bộ sẽ kích hoạt request:

- Subject có sẵn: `DRAFT` → `OPEN`.
- Subject tự nhập: `DRAFT` → `PENDING_REVIEW` để admin/employee duyệt.

Gia sư có thể cập nhật request ở `DRAFT`, `OPEN` hoặc `PENDING_REVIEW`. Request `DRAFT` vẫn giữ `DRAFT` sau khi cập nhật. Gia sư có thể hủy request ở `DRAFT`, `OPEN`, `CLOSED` hoặc `PENDING_REVIEW`.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/tutors/me/teaching-requests` | Tạo request ở trạng thái `DRAFT` |
| `GET` | `/api/v1/tutors/me/teaching-requests` | Xem các request của mình |
| `GET` | `/api/v1/tutors/me/teaching-requests/{requestId}` | Xem chi tiết request |
| `PUT` | `/api/v1/tutors/me/teaching-requests/{requestId}` | Cập nhật request |
| `PATCH` | `/api/v1/tutors/me/teaching-requests/{requestId}/status` | Chuyển `OPEN` ↔ `CLOSED` |
| `POST` | `/api/v1/tutors/me/teaching-requests/{requestId}/cancel` | Hủy request |

### 6.5. API quản lý certificate của gia sư

Các API quản lý certificate của chính mình dành cho tài khoản có role `TUTOR`. Hệ thống lấy tutor từ user trong access token, vì vậy client không truyền `tutorId` khi quản lý certificate của mình. Student có thể xem certificate đã được duyệt của một tutor qua API đọc riêng.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/tutors/me/certificates` | Tạo certificate mới ở trạng thái `PENDING` |
| `GET` | `/api/v1/tutors/me/certificates` | Xem tất cả certificate của mình |
| `GET` | `/api/v1/tutors/me/certificates?status=VERIFIED` | Lọc certificate của mình theo trạng thái |
| `GET` | `/api/v1/tutors/me/certificates/{certificateId}` | Xem chi tiết một certificate |
| `PUT` | `/api/v1/tutors/me/certificates/{certificateId}` | Cập nhật certificate và đưa về `PENDING` |
| `DELETE` | `/api/v1/tutors/me/certificates/{certificateId}` | Xóa mềm certificate |

Tutor tự xem hồ sơ cá nhân:

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/tutors/me` | Xem hồ sơ cá nhân, thông tin liên hệ và địa chỉ của mình |
| `PATCH` | `/api/v1/tutors/me` | Cập nhật từng phần thông tin cá nhân, địa chỉ và hồ sơ nghề nghiệp |

Các field truyền với giá trị `null` sẽ được giữ nguyên. Email, password, citizen ID, trạng thái tài khoản, rating và các thống kê của tutor không thể cập nhật. `provinceId` và `districtId` phải được truyền cùng nhau; district phải thuộc province đã chọn.

Student xem hồ sơ và certificate đã duyệt của tutor:

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/tutors/{tutorId}` | Xem thông tin hồ sơ công khai của tutor |
| `GET` | `/api/v1/tutors/{tutorId}/certificates` | Chỉ trả certificate có trạng thái `VERIFIED` |

API `/api/v1/tutors/me` chỉ dành cho role `TUTOR` và trả thêm email, số điện thoại, ngày sinh, giới tính và địa chỉ của chính tutor. API hồ sơ/certificate theo `tutorId` chỉ dành cho role `STUDENT`; response hồ sơ công khai không trả email, số điện thoại, mật khẩu hoặc citizen ID.

Request tạo/cập nhật:

```json
{
  "name": "IELTS 8.0",
  "issuingOrganization": "British Council",
  "description": "English language certificate",
  "issueDate": "2025-05-20",
  "expiryDate": "2027-05-20",
  "certificateUrl": "https://cdn.example.com/certificates/ielts.pdf"
}
```

`name` là bắt buộc. Nếu truyền cả hai ngày, `expiryDate` không được trước `issueDate`. `certificateUrl` là đường dẫn tới file minh chứng; API hiện lưu đường dẫn, chưa trực tiếp upload file lên storage.

Certificate mới luôn có trạng thái `PENDING`. Certificate `VERIFIED` không được phép chỉnh sửa; gia sư cần tạo certificate mới nếu thông tin đã xác minh thay đổi. Các response thành công đều sử dụng format `ApiResponse` chung của backend.

### 6.6. API xác thực tài khoản

Các API đăng ký, đăng nhập và password reset là API public. API `refresh` và `logout` không yêu cầu access token JWT, nhưng refresh token trong request body vẫn được kiểm tra tại service. Các response thành công sử dụng format `ApiResponse` chung của backend.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Đăng ký tài khoản `STUDENT` hoặc `TUTOR` |
| `POST` | `/api/v1/auth/login` | Đăng nhập cho `STUDENT`, `TUTOR`, `EMPLOYEE` hoặc `ADMIN` |
| `POST` | `/api/v1/auth/refresh` | Cấp access token và refresh token mới từ refresh token hợp lệ |
| `POST` | `/api/v1/auth/logout` | Vô hiệu hóa refresh token hiện tại |
| `POST` | `/api/v1/auth/password-reset/otp` | Gửi OTP 6 chữ số đến email đã đăng ký |
| `POST` | `/api/v1/auth/password-reset/verify` | Kiểm tra OTP còn hiệu lực |
| `POST` | `/api/v1/auth/password-reset/reset` | Xác thực OTP và đặt lại mật khẩu |

Register không cho phép tự tạo tài khoản `EMPLOYEE` hoặc `ADMIN`. OTP password reset có hiệu lực trong 5 phút và chỉ được sử dụng một lần khi reset mật khẩu.

### 6.7. API hồ sơ Student và tài khoản

Các API dưới đây yêu cầu access token JWT. API hồ sơ chỉ dành cho role `STUDENT`; API đổi mật khẩu dùng chung cho `STUDENT`, `TUTOR`, `EMPLOYEE` và `ADMIN`. Hệ thống lấy người dùng hiện tại từ JWT, client không truyền `userId`.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/students/me` | Xem hồ sơ và địa chỉ của Student đang đăng nhập |
| `PATCH` | `/api/v1/students/me` | Cập nhật một phần thông tin hồ sơ Student |
| `PUT` | `/api/v1/users/me/password` | Đổi mật khẩu của tài khoản đang đăng nhập |

Response hồ sơ không chứa password, CCCD hoặc thông tin nhạy cảm không cần thiết. Khi cập nhật hồ sơ, các field không gửi lên sẽ được giữ nguyên. Đổi mật khẩu yêu cầu mật khẩu hiện tại, mật khẩu mới và xác nhận mật khẩu mới.

### 6.8. API Studying Request của Student

Các API dưới đây yêu cầu access token JWT với role `STUDENT`. Mỗi request chỉ được truy cập hoặc chỉnh sửa bởi Student sở hữu request đó.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/students/me/studying-requests` | Tạo studying request ở trạng thái `DRAFT` |
| `GET` | `/api/v1/students/me/studying-requests` | Lấy danh sách studying request của mình |
| `GET` | `/api/v1/students/me/studying-requests/{requestId}` | Xem chi tiết một studying request |
| `PATCH` | `/api/v1/students/me/studying-requests/{requestId}` | Cập nhật một phần thông tin studying request |
| `PATCH` | `/api/v1/students/me/studying-requests/{requestId}/status` | Cập nhật status theo luồng `OPEN` ↔ `CLOSED` |
| `POST` | `/api/v1/students/me/studying-requests/{requestId}/cancel` | Hủy studying request |

API danh sách hỗ trợ các query parameter `page`, `size` và `status`:

```text
GET /api/v1/students/me/studying-requests?page=0&size=20&status=OPEN
```

`page` bắt đầu từ `0`, `size` tối đa là `100`. Request chỉ được cập nhật thông tin khi đang ở trạng thái `DRAFT` hoặc `OPEN`. API cập nhật status chỉ cho phép chuyển giữa `OPEN` và `CLOSED`; việc chuyển từ `DRAFT` sang `OPEN` được thực hiện qua payment flow sau này.

Khi gọi các API cần xác thực, thêm header:

```text
Authorization: Bearer <access-token>
```

Ví dụ tạo studying request:

```json
{
  "subjectId": "5f68e2cf-d21f-1c69-3fbd-1404b89f26ff",
  "gradeId": "2f2dd357-1839-4330-2f8a-16c4b36cfd3a",
  "quantity": 1,
  "title": "Cần gia sư Toán lớp 10",
  "description": "Cần hỗ trợ ôn thi học kỳ.",
  "districtId": "2f2dd357-1839-4330-2f8a-16c4b36cfd3a",
  "minPrice": 100000,
  "maxPrice": 200000,
  "learningMode": "ONLINE",
  "preferredSchedule": "Buổi tối các ngày trong tuần"
}
```

Ví dụ cập nhật thông tin:

```json
{
  "title": "Cần gia sư Toán lớp 10 - cập nhật",
  "maxPrice": 250000
}
```

Ví dụ cập nhật status:

```json
{
  "status": "CLOSED"
}
```

`requestId` được lấy từ trường `id` trong response khi tạo request hoặc trong danh sách request. Khi gọi API cập nhật, xem chi tiết, cập nhật status hoặc hủy, phải truyền UUID cụ thể trong URL; không sử dụng URL collection không có `requestId`.

### 6.9. API Tutor Student Request của Student

Các API dưới đây yêu cầu access token JWT với role `STUDENT`. Student chỉ có thể xem hoặc xử lý các đề nghị Tutor được gửi tới `studying_request` thuộc về chính mình.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests` | Lấy danh sách đề nghị Tutor, hỗ trợ phân trang và lọc status |
| `GET` | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}` | Xem chi tiết một đề nghị Tutor |
| `PATCH` | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}/accept` | Chấp nhận đề nghị đang `PENDING` |
| `PATCH` | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}/reject` | Từ chối đề nghị đang `PENDING` |

API danh sách hỗ trợ các query parameter:

```text
GET /api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests?page=0&size=20&status=PENDING
```

Các status của Tutor Request gồm `PENDING`, `ACCEPTED`, `REJECTED` và `CANCELLED`. Hai API `accept` và `reject` không cần request body. Khi số Tutor được chấp nhận đạt `quantity` của studying request, studying request sẽ chuyển sang `MATCHED`. Các đề nghị `PENDING` còn lại được giữ nguyên.

Khi gọi API, thêm header:

```text
Authorization: Bearer <access-token>
```

`studyingRequestId` lấy từ trường `id` của Studying Request. `tutorRequestId` lấy từ trường `id` trong response của API danh sách hoặc API chi tiết. Nếu đề nghị đã được xử lý hoặc studying request không thuộc Student hiện tại, API sẽ trả lỗi phù hợp.
### 6.10. API Student gửi đề nghị đăng ký học (Student Tutor Request)

Các API dưới đây yêu cầu access token JWT với role `STUDENT`. Student chỉ có thể tạo, xem danh sách và hủy các request do chính mình tạo.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/students/me/teaching-requests/{teachingRequestId}/student-tutor-requests` | Gửi đề nghị đăng ký học tới một teaching request đang `OPEN` |
| `GET` | `/api/v1/students/me/student-tutor-requests` | Xem danh sách đề nghị đã gửi; hỗ trợ `page`, `size`, `status` |
| `PATCH` | `/api/v1/students/me/student-tutor-requests/{requestId}/status` | Hủy đề nghị đang `PENDING` bằng status `CANCELLED` |

Ví dụ tạo đề nghị:

```json
{
  "gradeId": "2f2dd357-1839-4330-2f8a-16c4b36cfd3a",
  "proposedPrice": 150000,
  "learningMode": "ONLINE",
  "preferredSchedule": "Tối thứ 2 và thứ 4",
  "message": "Tôi muốn đăng ký học."
}
```

API danh sách có thể gọi như sau:

```text
GET /api/v1/students/me/student-tutor-requests?page=0&size=20&status=PENDING
```

Request mới có status `PENDING`. Chỉ request `PENDING` bị xem là trùng; request đã `REJECTED` hoặc `CANCELLED` được phép đăng ký lại. Khi hủy, dùng UUID cụ thể lấy từ trường `id` của response:

```http
PATCH /api/v1/students/me/student-tutor-requests/{requestId}/status
```

```json
{
  "status": "CANCELLED"
}
```

Header xác thực:

```text
Authorization: Bearer <access-token>
```

### 6.10.1. API gia sư duyệt đề nghị học từ học viên (Student Tutor Request)

Các API này dành cho role `TUTOR`. Gia sư chỉ xem và xử lý các đề nghị gửi đến teaching request do chính mình sở hữu.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests` | Lấy các đề nghị học của một teaching request; có thể lọc `status` |
| `GET` | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests/{requestId}` | Xem chi tiết một đề nghị học |
| `PATCH` | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests/{requestId}/status` | Chấp nhận hoặc từ chối đề nghị |

Chỉ đề nghị `PENDING` được chuyển sang `ACCEPTED` hoặc `REJECTED`:

```json
{
  "status": "ACCEPTED"
}
```

Mỗi đề nghị được chấp nhận sẽ được tính vào `quantity` của teaching request. Teaching request chỉ chuyển sang `MATCHED` khi số đề nghị `ACCEPTED` đạt đủ `quantity`; trước đó request vẫn có thể tiếp tục nhận học viên. API danh sách hiện hỗ trợ lọc trạng thái, chưa phân trang.

### 6.11. API gia sư gửi đề nghị dạy (Tutor Student Request)

Các API này dành cho role `TUTOR`. Gia sư gửi đề nghị dạy đến studying request đang `OPEN` của học viên. Hệ thống tạo đề nghị ở `PENDING` và không cho tạo trùng một đề nghị `PENDING` trên cùng studying request.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/tutors/me/studying-requests/{studyingRequestId}/tutor-student-requests` | Gửi đề nghị dạy, gồm `gradeId`, `proposedPrice`, `teachingMode`, lịch và lời nhắn |
| `GET` | `/api/v1/tutors/me/tutor-student-requests` | Lấy các đề nghị dạy của tôi; hỗ trợ `status`, `page`, `size` |
| `POST` | `/api/v1/tutors/me/tutor-student-requests/{requestId}/cancel` | Hủy đề nghị dạy đang chờ |

Chỉ đề nghị `PENDING` mới được hủy và khi hủy sẽ chuyển sang `CANCELLED`. Ví dụ tạo đề nghị:

```json
{
  "gradeId": "3878ce4c-8136-4468-a47a-0bd7b803e719",
  "proposedPrice": 150000,
  "teachingMode": "ONLINE",
  "preferredSchedule": "Tối thứ 2 và thứ 4",
  "message": "Tôi có kinh nghiệm dạy môn này."
}
```

### 6.12. API khiếu nại (Complaint)

API dùng chung cho `STUDENT` và `TUTOR`. Người gửi phải là một trong hai bên tham gia hợp đồng (`contractId`) được khiếu nại. Mỗi người chỉ xem, cập nhật hoặc hủy khiếu nại do chính mình tạo.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/users/me/complaints` | Tạo khiếu nại `PENDING`, có thể kèm evidence URL |
| `GET` | `/api/v1/users/me/complaints` | Lấy khiếu nại của tôi; hỗ trợ `status`, `page`, `size` |
| `GET` | `/api/v1/users/me/complaints/{complaintId}` | Xem trạng thái, resolution và evidence |
| `PUT` | `/api/v1/users/me/complaints/{complaintId}` | Cập nhật title, content và evidence của khiếu nại `PENDING` |
| `POST` | `/api/v1/users/me/complaints/{complaintId}/cancel` | Hủy khiếu nại `PENDING` |

Các trạng thái gồm `PENDING`, `IN_REVIEW`, `RESOLVED`, `REJECTED`, `CANCELLED`. Khi cập nhật complaint, không thể đổi `contractId`; bỏ field `evidences` để giữ evidence cũ, gửi `[]` để xóa toàn bộ evidence, hoặc gửi danh sách mới để thay thế. Complaint chỉ được cập nhật/hủy khi còn `PENDING`.

### 6.14. API buổi học của hợp đồng (Lesson)

Các API này dành cho role `TUTOR`. Gia sư chỉ quản lý buổi học thuộc contract của chính mình. Tạo hoặc sửa lịch chỉ thực hiện được khi contract `ACTIVE`; ngày học phải nằm trong thời hạn contract và `startTime` phải trước `endTime`.

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| `POST` | `/api/v1/tutors/me/contracts/{contractId}/lessons` | Tạo buổi học mới cho contract |
| `GET` | `/api/v1/tutors/me/contracts/{contractId}/lessons` | Lấy danh sách buổi học của contract; hỗ trợ `status`, `page`, `size` |
| `GET` | `/api/v1/tutors/me/lessons/{lessonId}` | Xem chi tiết một buổi học |
| `PUT` | `/api/v1/tutors/me/lessons/{lessonId}` | Cập nhật tiêu đề, lịch và ghi chú của buổi `SCHEDULED` |
| `PATCH` | `/api/v1/tutors/me/lessons/{lessonId}/status` | Cập nhật trạng thái buổi học |
| `POST` | `/api/v1/students/me/lessons/{lessonId}/confirm` | Học viên xác nhận buổi học `PENDING_CONFIRMATION` |

Ví dụ tạo buổi học:

```json
{
  "title": "Buổi 1: Ôn tập đại số",
  "date": "2026-09-12",
  "startTime": "18:00:00",
  "endTime": "20:00:00",
  "note": "Chuẩn bị bài tập chương 1"
}
```

Khi tạo, hệ thống tự gán `SCHEDULED` nếu `date + endTime` chưa qua; nếu buổi học đã kết thúc thì gán `PENDING_CONFIRMATION`. API danh sách sắp xếp theo `date`, rồi `startTime`.

#### Ma trận chuyển trạng thái Lesson

| Chủ thể | Chuyển trạng thái đã implement | Điều kiện |
| --- | --- | --- |
| System khi tạo lesson | `→ SCHEDULED` | `date + endTime` lớn hơn thời điểm tạo. |
| System khi tạo lesson | `→ PENDING_CONFIRMATION` | `date + endTime` đã qua tại thời điểm tạo. |
| Tutor | `SCHEDULED → PENDING_CONFIRMATION` | Tutor sở hữu contract; buổi học đã kết thúc (`date + endTime ≤ now`). |
| Tutor | `SCHEDULED → CANCELLED` | Tutor sở hữu contract. |
| Tutor | `PENDING_CONFIRMATION → CANCELLED` | Tutor sở hữu contract. |
| Student | `PENDING_CONFIRMATION → CONFIRMED` | Student thuộc contract và contract không có complaint `PENDING` hoặc `IN_REVIEW`. |
| System (job tự động) | `PENDING_CONFIRMATION → COMPLETED` | Buổi học đã kết thúc ít nhất 3 ngày, student chưa xác nhận (vẫn `PENDING_CONFIRMATION`) và contract không có complaint `PENDING` hoặc `IN_REVIEW`. |
| Admin | Chưa có | Chưa có API/service để admin đổi `LessonStatus`. |

`CONFIRMED`, `COMPLETED` và `CANCELLED` là trạng thái kết thúc trong luồng Lesson hiện tại, không có transition đi tiếp. Job tự động chạy theo chu kỳ cấu hình (`app.lesson.auto-completion-interval-ms`, mặc định 1 giờ); mốc chờ xác nhận cấu hình bằng `app.lesson.auto-completion-grace-days`, mặc định 3 ngày.

Complaint đang xử lý sẽ giữ lesson ở `PENDING_CONFIRMATION`; admin xử lý complaint và quyết toán ở luồng Complaint/Payment riêng, không đổi trực tiếp trạng thái Lesson.

### 7. Khởi động Frontend

Mở terminal mới:

```bash
cd frontend
npm install
npm run dev
```

Frontend mặc định chạy tại `http://localhost:5173`.

---

## Các lệnh thường dùng

### Frontend

Chạy trong thư mục `frontend/`:

| Lệnh                   | Chức năng                              |
| ---------------------- | -------------------------------------- |
| `npm run dev`          | Chạy frontend ở môi trường development |
| `npm run lint`         | Kiểm tra code bằng ESLint              |
| `npm run lint:fix`     | Tự sửa lỗi ESLint có thể fix           |
| `npm run format`       | Format code bằng Prettier              |
| `npm run format:check` | Kiểm tra format mà không sửa code      |
| `npm test`             | Chạy unit test                         |

### Backend

Chạy trong thư mục `backend/`:

| Lệnh                      | Chức năng                    |
| ------------------------- | ---------------------------- |
| `./mvnw spring-boot:run`  | Chạy Spring Boot             |
| `./mvnw test`             | Chạy unit test               |
| `./mvnw checkstyle:check` | Kiểm tra style code          |
| `./mvnw spotless:apply`   | Tự động format code Java     |
| `./mvnw spotless:check`   | Kiểm tra format mà không sửa |
| `./mvnw clean package`    | Build project                |

---

## Git Hooks tự động (chạy local, chưa có CI/CD)

Dự án hiện **chưa dùng GitHub Actions hay bất kỳ CI/CD nào** — toàn bộ kiểm tra chất lượng code chạy **ngay tại máy** thông qua Husky, tại thời điểm `git commit`. Vì vậy các bước dưới đây là **tuyến chặn duy nhất** hiện có; không có tầng kiểm tra nào khác trên server.

Sau khi chạy `npm install` ở bước 2, 2 hook sau tự động kích hoạt:

### pre-commit

Tự động phát hiện phần nào (frontend/backend) đang có thay đổi trong lần commit và chỉ chạy kiểm tra tương ứng:

```text
git commit
    │
    ├─ Có file thay đổi trong frontend/?
    │     └─ ESLint + Prettier (qua lint-staged) → npm test
    │
    └─ Có file .java thay đổi trong backend/?
          └─ Spotless format → Checkstyle check → mvn test
```

- **Frontend**: chỉ lint/format các file đang staged (nhanh), sau đó chạy `npm test`.
- **Backend**: chạy `spotless:apply` (tự format), `checkstyle:check` (kiểm tra style), rồi `mvn test` — quét toàn bộ module `backend/` (Maven không lọc theo file staged như `lint-staged`).
- Nếu bất kỳ bước nào fail, commit bị huỷ.

### commit-msg

Kiểm tra commit message đúng chuẩn Conventional Commits bằng Commitlint.

Ví dụ hợp lệ:

```text
feat: add tutor registration
fix: fix login validation
docs: update README
refactor: improve tutor service
test: add user service tests
chore: update dependencies
```

Ví dụ không hợp lệ (bị chặn):

```text
add tutor
fix bug
update
```

> **Lưu ý:** vì chưa có CI/CD, cả 2 hook trên đều chỉ chạy ở máy từng người và có thể bị bỏ qua bằng `git commit --no-verify`. Không có tầng kiểm tra bắt buộc trên GitHub — team cần tự giác không dùng `--no-verify` trừ trường hợp thật sự cần thiết.

---

## Quy ước nhánh & commit

Xem chi tiết đầy đủ trong [`CONTRIBUTING.md`](./CONTRIBUTING.md), bao gồm:

- Sơ đồ nhánh `main` → `dev` → `feature/fix/...`
- Quy ước đặt tên nhánh
- Quy ước Conventional Commits
- Quy trình tạo Pull Request và review
- Quy định khi nào rebase, khi nào merge

Ví dụ luồng làm việc:

```bash
git checkout dev
git pull

git checkout -b feat/tutor-registration

git add .
git commit -m "feat: add tutor registration"

git push -u origin feat/tutor-registration
```

---

## Cấu trúc project

```text
ptutor/
├── backend/
│   ├── pom.xml
│   └── src/
│
├── frontend/
│   ├── package.json
│   ├── eslint.config.js
│   ├── .prettierrc.json
│   ├── .prettierignore
│   └── src/
│
├── .github/
│   └── pull_request_template.md
│
├── .husky/
│   ├── pre-commit
│   └── commit-msg
│
├── package.json
├── package-lock.json
├── commitlint.config.js
├── docker-compose.yml
├── CONTRIBUTING.md
└── README.md
```

---

## Cấu trúc file cấu hình

| File                                             | Vai trò                                                           |
| ------------------------------------------------ | ----------------------------------------------------------------- |
| `frontend/eslint.config.js`                      | Rule kiểm tra JavaScript/React                                    |
| `frontend/.prettierrc.json`                      | Rule format frontend                                              |
| `backend/pom.xml` (plugin Checkstyle + Spotless) | Rule kiểm tra & format backend                                    |
| `commitlint.config.js`                           | Rule kiểm tra commit message                                      |
| `package.json` (root)                            | Quản lý Husky + Commitlint                                        |
| `docker-compose.yml`                             | Cấu hình PostgreSQL development                                   |
| `.github/pull_request_template.md`               | Checklist hiện khi tạo PR trên GitHub (không cần CI để hoạt động) |
| `CONTRIBUTING.md`                                | Quy định Git workflow của project                                 |

---

## Development Flow

```text
Clone project
      │
npm install (root — kích hoạt Husky)
      │
docker compose up -d (PostgreSQL)
      │
   ┌──┴──┐
Backend  Frontend
      │
   Develop
      │
  git commit
      │
   Husky (local)
 ┌────┴────┐
 │         │
pre-commit commit-msg
(lint/     (commitlint)
format/
test)
      │
   Commit thành công
      │
   git push
      │
Pull Request → dev
      │
Code Review (thủ công — chưa có CI tự động check)
      │
   Merge
```

## Lưu ý

- Không commit password, API key hoặc secret vào repository.
- Không xoá Docker volume (`docker compose down -v`) nếu không muốn mất dữ liệu PostgreSQL.
- Backend dùng Java 21.
- Frontend và backend quản lý dependency độc lập (`frontend/package.json` và `backend/pom.xml`).
- Root `package.json` chỉ phục vụ Husky và Commitlint, không cài dependency của frontend/backend.
- Dự án **hiện chưa có CI/CD** — mọi kiểm tra chất lượng code (lint, format, test, commit message) chỉ chạy tại máy qua Husky. Khi triển khai GitHub Actions sau này, cần cập nhật lại phần "Git Hooks tự động" ở trên cho khớp thực tế.
