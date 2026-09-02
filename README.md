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

Khi backend khởi động, Flyway sẽ tự động tạo schema, dữ liệu tỉnh thành, tài khoản Admin và schema teaching request/certificate từ các migration `V1` đến `V4`.

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

### 6.4. API quản lý certificate của gia sư

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
