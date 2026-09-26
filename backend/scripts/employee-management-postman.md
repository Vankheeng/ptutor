# Kiểm thử API quản lý nhân viên bằng Postman

## 1. Chuẩn bị dữ liệu

Khởi động PostgreSQL và backend ít nhất một lần để Flyway áp dụng migration `V19`:

```powershell
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

Dừng backend, quay về thư mục gốc repository rồi chạy seed:

```powershell
Get-Content backend/scripts/seed-employee-management-test.sql |
  docker exec -i ptutor-postgres psql -U postgres -d ptutor
```

Nếu `.env` sử dụng tên container, database hoặc database user khác, thay `ptutor-postgres`, `ptutor` và `postgres` tương ứng. Script có thể chạy lại; mỗi lần chạy sẽ đưa các tài khoản test về trạng thái ban đầu.

Khởi động lại backend sau khi seed. Bước này bắt buộc để hệ thống mã hóa các CCCD test và tạo hash:

```powershell
cd backend
mvn spring-boot:run
```

Tài khoản có sẵn:

| Tài khoản | Mật khẩu | Trạng thái/chức năng | Employee ID |
| --- | --- | --- | --- |
| `admin@ptutor.local` | `admin` | Admin | — |
| `employee.complaint.test@ptutor.local` | `Test@123` | Active / `COMPLAINT_HANDLER` | `42000000-0000-0000-0000-000000000001` |
| `employee.accounting.test@ptutor.local` | `Test@123` | Active / `ACCOUNTANT` | `42000000-0000-0000-0000-000000000002` |
| `employee.reviewer.test@ptutor.local` | `Test@123` | Active / `CONTENT_REVIEWER` | `42000000-0000-0000-0000-000000000003` |
| `employee.support.test@ptutor.local` | `Test@123` | Active / `USER_SUPPORT` | `42000000-0000-0000-0000-000000000004` |
| `employee.inactive.test@ptutor.local` | `Test@123` | Inactive / `GENERAL_OPERATIONS` | `42000000-0000-0000-0000-000000000005` |

## 2. Tạo Postman Environment

Tạo environment với các biến:

| Variable | Initial value |
| --- | --- |
| `baseUrl` | `http://localhost:8080/api/v1` |
| `adminToken` | để trống |
| `employeeToken` | để trống |
| `employeeId` | `42000000-0000-0000-0000-000000000001` |
| `provinceId` | để trống |
| `districtId` | để trống |

Các request có bảo vệ dùng header:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

## 3. Đăng nhập Admin

```http
POST {{baseUrl}}/auth/login
Content-Type: application/json
```

```json
{
  "email": "admin@ptutor.local",
  "password": "admin"
}
```

Thêm script trong tab **Tests** để tự lưu token:

```javascript
const response = pm.response.json();
pm.environment.set("adminToken", response.data.accessToken);
pm.test("Admin login succeeds", () => pm.response.to.have.status(200));
```

## 4. Xem và lọc danh sách

```http
GET {{baseUrl}}/admin/employees?page=0&size=20
Authorization: Bearer {{adminToken}}
```

Các ví dụ lọc:

```http
GET {{baseUrl}}/admin/employees?jobFunction=COMPLAINT_HANDLER
GET {{baseUrl}}/admin/employees?status=INACTIVE
GET {{baseUrl}}/admin/employees?keyword=accounting
```

Kỳ vọng `200 OK`; danh sách không chứa tài khoản Admin.

## 5. Lấy provinceId và districtId

```http
GET {{baseUrl}}/districts
Authorization: Bearer {{adminToken}}
```

Chọn một phần tử trong `data`, sau đó lưu:

- `districtId` bằng `data[n].id`.
- `provinceId` bằng `data[n].provinceId`.

Hai ID phải thuộc cùng một phần tử district.

## 6. Tạo nhân viên

```http
POST {{baseUrl}}/admin/employees
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "email": "employee.created.postman@ptutor.local",
  "initialPassword": "Temporary@123",
  "firstName": "Postman",
  "lastName": "Employee",
  "phone": "0920000001",
  "dateOfBirth": "1996-06-15",
  "gender": "MALE",
  "citizenId": "079200000001",
  "provinceId": "{{provinceId}}",
  "districtId": "{{districtId}}",
  "detailAddress": "123 Postman Street",
  "jobFunction": "COMPLAINT_HANDLER"
}
```

Kỳ vọng `201 Created`. Tab **Tests**:

```javascript
const response = pm.response.json();
pm.environment.set("employeeId", response.data.employeeId);
pm.test("Employee is active", () => {
  pm.expect(response.data.status).to.eql("ACTIVE");
});
```

Nếu chạy lại request, đổi email và CCCD vì hai trường này là duy nhất.

## 7. Xem chi tiết

```http
GET {{baseUrl}}/admin/employees/{{employeeId}}
Authorization: Bearer {{adminToken}}
```

Kỳ vọng `200 OK`. `maskedCitizenId` phải có dạng `********0001`; response không được chứa password, ciphertext hoặc hash CCCD.

## 8. Cập nhật thông tin và chức năng

`citizenId` có thể bỏ qua để giữ nguyên CCCD hiện tại.

```http
PUT {{baseUrl}}/admin/employees/{{employeeId}}
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "email": "employee.created.postman@ptutor.local",
  "firstName": "Updated",
  "lastName": "Employee",
  "phone": "0920000099",
  "dateOfBirth": "1996-06-15",
  "gender": "MALE",
  "provinceId": "{{provinceId}}",
  "districtId": "{{districtId}}",
  "detailAddress": "456 Updated Street",
  "jobFunction": "USER_SUPPORT"
}
```

Kỳ vọng `200 OK`, `jobFunction = USER_SUPPORT`.

## 9. Thu hồi và cấp lại quyền

Thu hồi:

```http
PATCH {{baseUrl}}/admin/employees/{{employeeId}}/deactivate
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "reason": "Nhân viên tạm thời ngừng làm việc để kiểm thử."
}
```

Kỳ vọng `200 OK`, `status = INACTIVE`. Đăng nhập bằng tài khoản này sau đó phải nhận `423 Locked` với code `ACCOUNT_INACTIVE`.

Cấp lại quyền:

```http
PATCH {{baseUrl}}/admin/employees/{{employeeId}}/reactivate
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "reason": "Nhân viên đã quay lại làm việc sau kiểm thử."
}
```

Kỳ vọng `200 OK`, `status = ACTIVE` và có thể đăng nhập lại.

## 10. Kiểm tra chỉ Admin được quản lý nhân viên

Đăng nhập bằng tài khoản nhân viên khiếu nại:

```http
POST {{baseUrl}}/auth/login
Content-Type: application/json
```

```json
{
  "email": "employee.complaint.test@ptutor.local",
  "password": "Test@123"
}
```

Lưu `data.accessToken` vào `employeeToken`, rồi gọi:

```http
GET {{baseUrl}}/admin/employees
Authorization: Bearer {{employeeToken}}
```

Kỳ vọng `403 Forbidden`. Nhân viên này vẫn gọi được `GET {{baseUrl}}/admin/complaints` vì có `COMPLAINT_HANDLER`, nhưng gọi `GET {{baseUrl}}/admin/users` phải nhận `403` với code `EMPLOYEE_FUNCTION_FORBIDDEN`.

## 11. Một số trường hợp lỗi nên kiểm tra

| Trường hợp | Kỳ vọng |
| --- | --- |
| Email hoặc CCCD trùng | `409` |
| Mật khẩu ban đầu ngắn hơn 8 ký tự | `400` |
| Province và district không khớp | `400 DISTRICT_PROVINCE_MISMATCH` |
| Deactivate hai lần | `409 EMPLOYEE_ALREADY_INACTIVE` |
| Reactivate tài khoản đang active | `409 EMPLOYEE_ALREADY_ACTIVE` |
| Đổi chức năng/deactivate người còn complaint mở | `409 EMPLOYEE_HAS_OPEN_ASSIGNMENTS` |
| Employee gọi API quản lý nhân viên | `403 FORBIDDEN` |

