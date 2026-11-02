# Tổng hợp quy định của nền tảng PTutor, rút ra từ code backend

> **Nguồn:** nhánh `dev` (commit `27f6eeb`, 31/10/2026), thư mục `backend/src/main/java/com/ptutor/backend`, gồm service, scheduler, security filter, DTO validation, `application-prod.yaml` và migration.
> **Cách đọc:** mỗi quy định ghi kèm **[file › hàm]** để tra lại và **mã lỗi** (`MA_LOI`) mà hệ thống trả về khi vi phạm.
> Giá trị có dạng `app.xxx` là **cấu hình** chạy lúc deploy. Số trong ngoặc là giá trị mặc định, nên khi vận hành có thể khác.
> Mục **⚠️ Khoảng trống** ở cuối liệt kê những chỗ code **chưa có** quy định. Chatbot không được bịa ra quy định cho các chỗ này.

---

## Mục lục

1. [Vai trò và phân quyền](#1-vai-trò-và-phân-quyền)
2. [Tài khoản: đăng ký, đăng nhập, mật khẩu, hồ sơ](#2-tài-khoản-đăng-ký-đăng-nhập-mật-khẩu-hồ-sơ)
3. [Đình chỉ và mở lại tài khoản](#3-đình-chỉ-và-mở-lại-tài-khoản)
4. [Bài đăng: tìm gia sư (học viên) và nhận dạy (gia sư)](#4-bài-đăng-tìm-gia-sư-học-viên-và-nhận-dạy-gia-sư)
5. [Đề nghị dạy và đơn đăng ký học](#5-đề-nghị-dạy-và-đơn-đăng-ký-học)
6. [Hợp đồng](#6-hợp-đồng)
7. [Kỳ thanh toán và đóng học phí](#7-kỳ-thanh-toán-và-đóng-học-phí)
8. [Buổi học](#8-buổi-học)
9. [Yêu cầu hoàn tiền](#9-yêu-cầu-hoàn-tiền)
10. [Khiếu nại](#10-khiếu-nại)
11. [Ví, nạp tiền, rút tiền, tài khoản ngân hàng](#11-ví-nạp-tiền-rút-tiền-tài-khoản-ngân-hàng)
12. [Hồ sơ gia sư và chứng chỉ](#12-hồ-sơ-gia-sư-và-chứng-chỉ)
13. [Đánh giá](#13-đánh-giá)
14. [Điểm đề xuất gia sư](#14-điểm-đề-xuất-gia-sư)
15. [Thông báo](#15-thông-báo)
16. [Quản lý nhân viên](#16-quản-lý-nhân-viên)
17. [Các tác vụ tự động](#17-các-tác-vụ-tự-động)
18. [⚠️ Khoảng trống: những gì code chưa quy định](#18-️-khoảng-trống-những-gì-code-chưa-quy-định)
19. [Ghi chú cho kho tri thức của chatbot](#19-ghi-chú-cho-kho-tri-thức-của-chatbot)

---

## 1. Vai trò và phân quyền

💡 *Hiểu nhanh:* có 4 vai trò: học viên, gia sư, nhân viên và admin. Nhân viên còn có thêm "chức năng", và chức năng quyết định họ được vào trang quản trị nào.

| Quy định | Chi tiết | Nguồn |
|---|---|---|
| Vai trò | `STUDENT`, `TUTOR`, `EMPLOYEE`, `ADMIN`. Chỉ STUDENT và TUTOR được **tự đăng ký** (`INVALID_REGISTRATION_ROLE`). | AuthService › register |
| Chức năng nhân viên | **Chỉ có 2 chức năng**: `CUSTOMER_SERVICE` (chăm sóc khách hàng) và `ACCOUNTANT` (kế toán). Code trên `dev` còn 5 chức năng cũ; nhánh `feat/employee-two-job-functions` gộp lại còn 2. | enums/EmployeeJobFunction |
| Admin vào được mọi trang quản trị | Bộ lọc theo chức năng bỏ qua admin. Trang quản lý nhân viên **chỉ admin** được vào. | EmployeeFunctionAuthorizationFilter, SecurityConfig |
| `CUSTOMER_SERVICE` | Khiếu nại; thông báo hàng loạt; duyệt chứng chỉ, hồ sơ gia sư, bài nhận dạy, bài tìm gia sư; danh mục môn học và khối lớp; tài khoản học viên và gia sư (xem, đình chỉ, mở lại) | như trên |
| `ACCOUNTANT` | Yêu cầu hoàn tiền | như trên |
| Đổi chức năng hoặc khoá nhân viên | Phải chuyển hết khiếu nại và yêu cầu hoàn tiền đang mở của người đó sang người khác trước. Admin xem được các việc đang mở của từng nhân viên. | AdminEmployeeService |
| Sai chức năng | `EMPLOYEE_FUNCTION_FORBIDDEN` (403) | như trên |
| Tài khoản không ACTIVE | Mọi request đều bị chặn: `ACCOUNT_SUSPENDED` nếu bị đình chỉ, `ACCOUNT_INACTIVE` nếu ngừng hoạt động | AccountStatusFilter |

> **Lưu ý:** khi đọc code `dev` trước lúc nhánh trên được merge, sẽ còn thấy `GENERAL_OPERATIONS`, `COMPLAINT_HANDLER`, `CONTENT_REVIEWER`, `USER_SUPPORT`. Các tên này **sắp bỏ**, không đưa vào kho tri thức.

## 2. Tài khoản: đăng ký, đăng nhập, mật khẩu, hồ sơ

| Quy định | Giá trị | Nguồn / mã lỗi |
|---|---|---|
| Email | Bắt buộc, đúng định dạng, ≤255 ký tự, **không trùng** | RegisterRequest; `EMAIL_ALREADY_EXISTS` |
| Mật khẩu | 8–72 ký tự | RegisterRequest, ChangePasswordRequest |
| Họ, tên | Bắt buộc, mỗi trường ≤100 ký tự | RegisterRequest |
| Số điện thoại | Bắt buộc, ≤20 ký tự | RegisterRequest |
| CCCD | Đúng **12 chữ số**, **không trùng**, lưu ở dạng mã hoá AES-GCM | RegisterRequest; `CITIZEN_ID_ALREADY_EXISTS`; CitizenIdCryptoService |
| Địa chỉ | Quận/huyện phải thuộc tỉnh/thành đã chọn | `DISTRICT_PROVINCE_MISMATCH` |
| Khi đăng ký | Hệ thống tự tạo **ví**. Hồ sơ gia sư mới ở trạng thái **PENDING** (chờ duyệt). | AuthService › register |
| Đăng nhập sai | `INVALID_CREDENTIALS`. Code **không khoá tài khoản** sau nhiều lần sai. | AuthService |
| Đăng nhập khi bị đình chỉ | Trả 423. Đình chỉ tạm thời: báo "đến <thời điểm> UTC" kèm lý do. Đình chỉ vĩnh viễn: báo vĩnh viễn kèm lý do. | AuthService › accountUnavailable |
| Phiên đăng nhập | Access token sống `app.security.jwt.access-token-ttl` (mặc định 3600 giây, **prod đặt 36000 giây = 10 giờ**). Refresh token sống 30 ngày (2592000 giây). | JwtService, RefreshTokenService, application-prod.yaml |
| Dùng lại refresh token đã thu hồi | **Thu hồi toàn bộ** phiên của người dùng (chống đánh cắp token) | RefreshTokenService › refresh |
| Bị đình chỉ | Mọi refresh token bị thu hồi ngay | AccountSuspensionService › suspend |
| Đổi mật khẩu | Phải nhập đúng mật khẩu cũ, xác nhận khớp, mật khẩu mới **khác** mật khẩu cũ | AccountService; `CURRENT_PASSWORD_INCORRECT`, `PASSWORD_CONFIRMATION_MISMATCH`, `NEW_PASSWORD_MUST_BE_DIFFERENT` |
| Quên mật khẩu | OTP **6 chữ số**, gửi qua email, sống **5 phút**, **dùng một lần**. Email chưa đăng ký trả `EMAIL_NOT_FOUND`. | ForgotPasswordService; `INVALID_OR_EXPIRED_OTP` |
| Sửa hồ sơ | Phải gửi ít nhất 1 trường. Tỉnh và quận/huyện phải gửi **cùng nhau**. | Student/TutorProfileService; `EMPTY_UPDATE_REQUEST`, `ADDRESS_IDS_REQUIRED_TOGETHER` |
| Gia sư sửa nội dung hồ sơ cần duyệt | Hồ sơ **quay về PENDING** và xoá kết quả duyệt cũ | TutorProfileService › update |

## 3. Đình chỉ và mở lại tài khoản

💡 *Hiểu nhanh:* nhân viên hoặc admin có thể khoá tài khoản học viên hoặc gia sư, tạm thời hoặc vĩnh viễn. Khoá tạm thời sẽ tự mở khi hết hạn. Mỗi lần bị khoá được đếm và trừ vào điểm đề xuất của gia sư (mục 14).

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Đối tượng | Chỉ tài khoản có **đúng một** hồ sơ học viên hoặc gia sư | `ACCOUNT_TARGET_NOT_SUPPORTED` |
| Không tự khoá mình | | `SELF_SUSPENSION_NOT_ALLOWED` |
| Chỉ khoá tài khoản đang ACTIVE | | `ACCOUNT_ALREADY_SUSPENDED`, `ACCOUNT_NOT_ACTIVE` |
| Lý do | 10–1000 ký tự | SuspendAccountRequest |
| Tạm thời (`TEMPORARY`) | **Bắt buộc** có thời điểm kết thúc, và thời điểm đó phải ở tương lai | `INVALID_SUSPENSION_PERIOD` |
| Vĩnh viễn (`PERMANENT`) | **Không được** có thời điểm kết thúc | `INVALID_SUSPENSION_PERIOD` |
| Hệ quả | Trạng thái chuyển `BLOCKED`, `suspensionCount` +1, thu hồi mọi phiên đăng nhập, gửi thông báo | AccountSuspensionService › suspend |
| Tự mở khoá | Mỗi 60 giây (`app.account-suspension.expiration-interval-ms`), và cả lúc người dùng gửi request, hệ thống kiểm tra khoá tạm thời đã hết hạn chưa. Hết hạn thì mở lại với lý do `TEMPORARY_SUSPENSION_EXPIRED`. | AccountSuspensionScheduler, UserAccessService |
| Mở khoá thủ công | Lý do 10–1000 ký tự. Chỉ mở được tài khoản đang bị khoá. | `INVALID_REACTIVATION_REASON`, `ACCOUNT_NOT_SUSPENDED` |
| Bị khoá thì sao | Không đăng nhập được, mọi API trả `ACCOUNT_SUSPENDED`, không rút tiền được (`USER_INACTIVE`) | AccountStatusFilter, WithdrawalService |

## 4. Bài đăng: tìm gia sư (học viên) và nhận dạy (gia sư)

💡 *Hiểu nhanh:* bài mới tạo ở dạng **nháp**. Người đăng **trả phí đăng** thì bài mới được đưa lên. Bài có môn học "tự nhập" (không có trong danh mục) phải chờ nhân viên duyệt trước. Nếu bài bị từ chối, phí đăng được **hoàn vào ví**.

**Trạng thái bài đăng:** `DRAFT` → (trả phí) → `PENDING_REVIEW` hoặc `OPEN` → `MATCHED` / `CLOSED` / `CANCELLED` / `REJECTED`.

| Quy định | Bài tìm gia sư (học viên) | Bài nhận dạy (gia sư) | Nguồn |
|---|---|---|---|
| Phí đăng | `app.payment.fees.studying-request`, **không có mặc định**, đặt bằng biến môi trường `PAYMENT_STUDYING_REQUEST_FEE`, số VND nguyên > 0 | `app.payment.fees.teaching-request` (`PAYMENT_TEACHING_REQUEST_FEE`) | PaymentService |
| Cách trả phí | VNPay hoặc ví, chỉ khi bài đang `DRAFT` | như bên trái | PaymentService; `INVALID_*_PAYMENT_STATUS` |
| Khi nào phải chờ duyệt | Không chọn môn trong danh mục **và** có ghi chú (môn tự nhập phải kèm ghi chú) | Có ghi chú **hoặc** dùng môn tự nhập | StudyingRequestService/TeachingRequestService › requiresReview |
| Ai duyệt | Nhân viên `CUSTOMER_SERVICE` hoặc admin | như bên trái | mục 1 |
| Duyệt bài có môn tự nhập | Nhân viên phải chọn môn có sẵn hoặc tạo môn mới (tên ≤100 ký tự, mô tả ≤500 ký tự, không trùng môn đã có) | như bên trái | Admin*RequestService; `SUBJECT_RESOLUTION_REQUIRED`, `SUBJECT_ALREADY_EXISTS` |
| Từ chối | Lý do bắt buộc, ≤500 ký tự. **Hoàn 100% phí đăng vào ví.** | như bên trái | `INVALID_REJECTION_REASON`; credit `*_REQUEST_REFUND` |
| Sửa bài | Khi bài đang `DRAFT` hoặc `OPEN` | Khi bài đang `DRAFT`, `OPEN` hoặc `PENDING_REVIEW`. Sửa bài đã đăng sẽ **xét lại** việc có cần duyệt không. | |
| Tạm đóng / mở lại | Chỉ chuyển qua lại `OPEN` ↔ `CLOSED` | như bên trái | |
| Huỷ | Khi bài ở `DRAFT`, `OPEN`, `MATCHED` hoặc `CLOSED`. Các đề nghị dạy đang chờ hoặc đã nhận cũng bị huỷ theo. | Khi bài ở `DRAFT`, `OPEN`, `CLOSED` hoặc `PENDING_REVIEW` | |
| Giá | Giá min và max ≥ 0, min ≤ max | Giá mong muốn ≥ 0; số học viên nhận (`quantity`) > 0 | `INVALID_PRICE_RANGE` |
| Lịch rảnh | Thứ 1–7, giờ kết thúc sau giờ bắt đầu | như bên trái | `INVALID_AVAILABILITY` |
| Môn học | Phải đang hoạt động | Chọn **đúng một** trong hai: môn có sẵn hoặc môn tự nhập | `SUBJECT_SOURCE_REQUIRED` |
| Ai xem được | Gia sư chỉ thấy bài `OPEN` | Người dùng thấy bài `OPEN`. Trang công khai trả 20 bài (tối đa 50). Nhân viên thấy tất cả. | |
| Giới hạn độ dài | Tiêu đề, địa chỉ ≤255 ký tự; mô tả ≤10000; ghi chú ≤500 | Tên môn tự nhập ≤100; ghi chú ≤500; mô tả ≤10000 | DTO |

## 5. Đề nghị dạy và đơn đăng ký học

💡 *Hiểu nhanh:* có hai chiều ghép đôi:
- (a) gia sư gửi **đề nghị dạy** vào bài tìm gia sư của học viên;
- (b) học viên gửi **đơn đăng ký học** vào bài nhận dạy của gia sư.

Trạng thái chung: `PENDING` → `ACCEPTED` / `REJECTED` / `CANCELLED`.

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| (a) Gửi đề nghị | Chỉ gửi vào bài `OPEN`. Mỗi gia sư chỉ có **1 đề nghị đang chờ** cho mỗi bài. Dạy offline thì phải chọn tỉnh và quận/huyện khớp nhau. | TutorStudentRequestService; `STUDYING_REQUEST_NOT_OPEN`, `DUPLICATE_TUTOR_STUDENT_REQUEST`, `INVALID_PREFERRED_LOCATION` |
| (a) Sửa / rút đề nghị | Chỉ khi đề nghị còn `PENDING` | `INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION` |
| (a) Học viên nhận đề nghị | Bài phải `OPEN`. Mỗi bài chỉ nhận **1 gia sư**. Nhận xong, bài chuyển sang `MATCHED`. | `STUDYING_REQUEST_ALREADY_MATCHED` |
| (b) Gửi đơn | Bài nhận dạy phải `OPEN`. Khối lớp phải thuộc bài đó. Mỗi học viên chỉ có **1 đơn đang chờ** cho mỗi bài. | StudentTutorRequestService; `INVALID_TEACHING_REQUEST_STATUS`, `INVALID_GRADE`, `DUPLICATE_STUDENT_TUTOR_REQUEST` |
| (b) Gia sư duyệt đơn | Đơn phải `PENDING` và bài phải `OPEN`. Khi số đơn đã nhận đạt `quantity`, bài chuyển sang `MATCHED`. | StudentTutorRequestService › updateStatus |
| (b) Học viên huỷ đơn | Chỉ khi đơn còn `PENDING` | |

## 6. Hợp đồng

💡 *Hiểu nhanh:* sau khi một đề nghị hoặc đơn được **nhận**, một bên soạn hợp đồng và bên kia **ký** thì hợp đồng có hiệu lực. Hợp đồng tự kết thúc khi hết ngày kết thúc. Trạng thái: `PENDING` → `ACTIVE` → `COMPLETED`, hoặc `CANCELLED`.

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Ai soạn | (a) **Học viên** soạn từ đề nghị dạy đã nhận. (b) **Gia sư** soạn từ đơn đăng ký đã nhận. | ContractService › createFrom* |
| Điều kiện soạn | Bài gốc phải `OPEN` hoặc `MATCHED`, đề nghị/đơn phải `ACCEPTED`. Bài nhận dạy phải có môn đã được chuẩn hoá. Bài tìm gia sư phải có hình thức học. | `INVALID_CONTRACT_SOURCE_STATUS`, `INVALID_CONTRACT_APPLICATION_STATUS`, `CONTRACT_SUBJECT_REQUIRED`, `CONTRACT_TEACHING_MODE_REQUIRED` |
| Một nguồn chỉ một hợp đồng | Mỗi đề nghị/đơn chỉ có **1 hợp đồng chưa huỷ** (có unique index trong DB) | `DUPLICATE_CONTRACT` |
| Điều khoản | Giá ≥ 0; số buổi > 0; ngày kết thúc **sau** ngày bắt đầu; có ít nhất 1 lịch học (thứ 1–7, giờ kết thúc sau giờ bắt đầu). Nếu nhập lịch theo thứ, **số buổi tự tính** từ lịch và khoảng ngày. | ContractService › normalizeTerms; `INVALID_CONTRACT_*` |
| Kỳ thanh toán | `PER_LESSON` (theo buổi), `WEEKLY` (theo tuần), `MONTHLY` (theo tháng), `PACKAGE` (trọn gói) | enums/PaymentPeriod |
| Sửa hợp đồng | Chỉ **người soạn**, chỉ khi `PENDING`. Học viên soạn thì chỉ sửa được giá và lịch. Gia sư soạn thì sửa được giá, lịch và khối lớp. **Không** sửa được kỳ thanh toán, số buổi hay ngày (trừ hợp đồng gia hạn). | `CONTRACT_CREATOR_REQUIRED`, `CONTRACT_FIELD_NOT_EDITABLE` |
| Ký / từ chối | Chỉ **bên còn lại** (không phải người soạn), khi hợp đồng `PENDING` và **chưa tới ngày kết thúc**. Ký thì hợp đồng chuyển `ACTIVE`. Từ chối thì chuyển `CANCELLED`. | `CONTRACT_COUNTERPARTY_REQUIRED`, `CONTRACT_EXPIRED` |
| Huỷ | Chỉ **người soạn**, và chỉ khi hợp đồng còn `PENDING` | ContractService › cancel |
| Gia hạn | Một trong hai bên đề xuất khi hợp đồng đang `ACTIVE`. Ngày bắt đầu hợp đồng mới **không sớm hơn** ngày kết thúc hợp đồng cũ. Mỗi hợp đồng chỉ có **1 bản gia hạn chưa huỷ**. Bên kia ký như hợp đồng thường. | `DUPLICATE_CONTRACT_RENEWAL`, `INVALID_CONTRACT_DATE_RANGE` |
| Tự kết thúc | Mỗi ngày lúc 00:00 giờ VN (`app.contract.lifecycle.expiration-cron`): hợp đồng `ACTIVE` đã tới ngày kết thúc chuyển `COMPLETED`; hợp đồng `PENDING` đã tới ngày kết thúc chuyển `CANCELLED`. | ContractLifecycleService |

## 7. Kỳ thanh toán và đóng học phí

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Tạo các kỳ | Tạo khi hợp đồng `ACTIVE` và giá > 0. `PACKAGE`: 1 kỳ, hạn vào ngày bắt đầu. `PER_LESSON`: số kỳ bằng số buổi, mỗi kỳ gắn với một buổi học khi gia sư tạo buổi đó, hạn là ngày học. `WEEKLY`/`MONTHLY`: mỗi tuần/tháng một kỳ, tính từ ngày bắt đầu đến trước ngày kết thúc. | ContractPaymentInstallmentService |
| Số tiền mỗi kỳ | Bằng **giá hợp đồng** (giá được hiểu là giá mỗi kỳ) | newInstallment |
| Đóng học phí | Chỉ học viên của hợp đồng, khi hợp đồng `ACTIVE`, kỳ còn `PENDING`. Với `PER_LESSON`, kỳ chỉ đóng được khi đã gắn buổi học. | PaymentService; `CONTRACT_NOT_ACTIVE`, `PAYMENT_INSTALLMENT_NOT_PAYABLE`, `PAYMENT_INSTALLMENT_NOT_READY` |
| Cách đóng | VNPay hoặc ví. Đóng bằng ví sẽ **huỷ** link VNPay đang chờ của cùng kỳ. | PaymentService › completeWalletPayment |
| Link VNPay | Hết hạn sau 15 phút (`app.payment.vnpay.expiry-minutes`). Bấm lại khi link cũ còn hạn thì dùng lại giao dịch cũ; hết hạn thì giao dịch cũ chuyển `FAILED`. Hệ thống kiểm tra chữ ký và số tiền khớp. | PaymentService › initiate, handleIpn |
| Nhắc đóng tiền | Mỗi ngày lúc 9:00 giờ VN, nhắc học viên về các kỳ **đến hạn trong 3 ngày tới** (mỗi kỳ nhắc 1 lần) | PaymentInstallmentReminderService |

## 8. Buổi học

💡 *Hiểu nhanh:* gia sư tạo buổi học trong hợp đồng. Dạy xong, gia sư bấm "đã dạy" và học viên xác nhận. Sau **3 ngày** học viên không phản hồi, buổi học tự hoàn tất, **trừ khi** đang có khiếu nại hoặc yêu cầu hoàn tiền liên quan.

Trạng thái: `SCHEDULED` → `PENDING_CONFIRMATION` → `CONFIRMED` / `COMPLETED`, hoặc `CANCELLED`.

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Tạo / sửa buổi | Chỉ gia sư của hợp đồng, khi hợp đồng `ACTIVE`. Ngày học nằm trong thời hạn hợp đồng, giờ bắt đầu trước giờ kết thúc. Số buổi không vượt tổng số buổi (với `PER_LESSON`). Chỉ sửa được buổi `SCHEDULED`. | LessonService; `CONTRACT_NOT_ACTIVE`, `LESSON_OUTSIDE_CONTRACT_PERIOD`, `CONTRACT_LESSON_LIMIT_REACHED` |
| Tạo buổi đã diễn ra | Buổi có giờ kết thúc ở quá khứ được tạo thẳng ở trạng thái `PENDING_CONFIRMATION` và học viên được báo | LessonService › initialStatus |
| Đánh dấu "đã dạy" | Chỉ sau khi buổi học **kết thúc** | `LESSON_NOT_FINISHED` |
| Gia sư huỷ buổi | Khi buổi ở `SCHEDULED` hoặc `PENDING_CONFIRMATION` | LessonService › updateStatus |
| Học viên xác nhận | Khi buổi `PENDING_CONFIRMATION`. **Bị chặn** nếu hợp đồng có khiếu nại đang mở (`PENDING`, `IN_REVIEW`, `AWAITING_EVIDENCE`) hoặc buổi đang nằm trong yêu cầu hoàn tiền đang mở. | `LESSON_COMPLETION_ON_HOLD` |
| Tự hoàn tất | Mỗi giờ (`app.lesson.auto-completion-interval-ms`), buổi đã đánh dấu "đã dạy" quá **3 ngày** (`app.lesson.auto-completion-grace-days`) chuyển `COMPLETED`, trừ các buổi bị giữ như trên | LessonAutoCompletionScheduler |

## 9. Yêu cầu hoàn tiền

💡 *Hiểu nhanh:* học viên khai những buổi **không diễn ra**. Gia sư có **48 giờ** để phản hồi. Sau đó kế toán quyết định từng buổi.
- Gia sư vắng: học viên được hoàn **100%** giá một buổi.
- Học viên vắng: học viên được hoàn **80%**, gia sư nhận **20%** còn lại.

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Ai gửi | Chỉ học viên. Hợp đồng phải **đã ký** (không còn `PENDING`) và **đã đóng học phí**. | RefundRequestService; `CONTRACT_NOT_REFUNDABLE` |
| Một hợp đồng một yêu cầu mở | Không gửi được khi hợp đồng đã có yêu cầu `PENDING` hoặc `IN_REVIEW` (có unique index) | `REFUND_REQUEST_ALREADY_OPEN` |
| Buổi được khai | Thuộc hợp đồng, đang `PENDING_CONFIRMATION`. Mỗi buổi chỉ khai 1 lần. Kết quả khai: `TUTOR_ABSENT` (gia sư vắng) hoặc `STUDENT_ABSENT` (học viên vắng). Tối đa **50 buổi** mỗi yêu cầu. | `LESSON_NOT_DISPUTABLE`, `INVALID_CLAIMED_OUTCOME`, `DUPLICATE_DISPUTED_LESSON` |
| Nội dung | Mô tả bắt buộc, ≤2000 ký tự; tối đa 5 bằng chứng | RefundRequestCreateRequest |
| Thông báo | Mọi kế toán đang hoạt động và gia sư (kèm hạn phản hồi) | RefundRequestService › create |
| Gia sư phản hồi | Trong **48 giờ** (`app.refund.tutor-response-hours`). Chỉ phản hồi 1 lần. Phải nêu ý kiến cho **từng** buổi bị khai. Nội dung ≤2000 ký tự, tối đa 5 bằng chứng. | `TUTOR_RESPONSE_NOT_ALLOWED`, `TUTOR_RESPONSE_ALREADY_SUBMITTED`, `LESSON_POSITION_REQUIRED` |
| Học viên rút yêu cầu | Chỉ khi yêu cầu còn `PENDING` (chưa có kế toán nhận) | |
| Kế toán nhận xử lý | Yêu cầu `PENDING` chưa ai nhận. Nhận xong, yêu cầu chuyển `IN_REVIEW` và thuộc về người nhận. | AdminRefundRequestService › startReview |
| Điều kiện duyệt | Gia sư đã phản hồi **hoặc** đã hết 48 giờ; hợp đồng **không có khiếu nại đang mở**; có quyết định cho **từng** buổi đang chờ; kết quả tính ra phải có tiền để trả. Số tiền nhân viên nhìn thấy phải khớp với số tính lại lúc duyệt. | `TUTOR_RESPONSE_WINDOW_OPEN`, `CONTRACT_HAS_OPEN_COMPLAINT`, `LESSON_DECISION_REQUIRED`, `NOTHING_TO_REFUND`, `REFUND_AMOUNT_CHANGED` |
| Công thức | Đơn giá = tổng các kỳ chưa huỷ ÷ tổng số buổi. Hoàn cho học viên = 100% đơn giá × số buổi gia sư vắng + 80% đơn giá × số buổi học viên vắng, **không vượt** số học phí đã đóng trừ số đã hoàn trước đó. Gia sư nhận = (đơn giá − 80% đơn giá) × số buổi học viên vắng. | RefundCalculator, RefundPolicy (`app.refund.rates.*`) |
| Ví dụ | Đơn giá 200.000đ. 1 buổi gia sư vắng: học viên nhận 200.000đ. 1 buổi học viên vắng: học viên nhận 160.000đ, gia sư nhận 40.000đ. | |
| Kết quả | Tiền vào **ví** (học viên: `CONTRACT_REFUND`; gia sư: `TUTOR_EARNING`). Buổi có quyết định "đã dạy" chuyển `COMPLETED`; buổi có quyết định "vắng" chuyển `CANCELLED`. Yêu cầu chuyển `REFUNDED`. | approve, settleDisputedLessons |
| Từ chối | Lý do ≤500 ký tự, yêu cầu chuyển `REJECTED` | |
| Chuyển người xử lý | Chỉ **admin**, sang một kế toán đang hoạt động khác | `REFUND_REQUEST_REASSIGN_FORBIDDEN` |

## 10. Khiếu nại

💡 *Hiểu nhanh:* một bên trong hợp đồng khiếu nại bên kia. Nhân viên nhận xử lý, có thể đòi thêm bằng chứng, rồi kết luận **chấp nhận** (bắt buộc chỉ rõ bên có lỗi) hoặc **bác bỏ**. Trên `dev`, việc ghi nhận bên có lỗi **chưa kéo theo hình phạt tự động** nào.

Trạng thái: `PENDING` → `IN_REVIEW` ↔ `AWAITING_EVIDENCE` → `RESOLVED` / `REJECTED`, hoặc `CANCELLED`.

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Ai gửi | Học viên hoặc gia sư **thuộc hợp đồng**. Code không giới hạn trạng thái hợp đồng hay thời hạn gửi. | ComplaintService › create |
| Loại khiếu nại | `ABSENCE` (vắng mặt), `LATE_OR_SHORT` (đến muộn hoặc dạy thiếu giờ), `TEACHING_QUALITY` (chất lượng dạy), `MISCONDUCT` (hành vi không phù hợp), `SERIOUS_MISCONDUCT` (vi phạm nghiêm trọng), `OTHER` (khác) | enums/ComplaintCategory |
| Nội dung | Tiêu đề ≤255 ký tự, nội dung ≤10000 ký tự, bằng chứng là link (≤2048 ký tự) kèm loại tệp | ComplaintCreateRequest |
| Sửa / huỷ | Chỉ người gửi, khi khiếu nại còn `PENDING` | ComplaintService › ensurePending |
| Bên bị khiếu nại | Thấy khiếu nại khi nó đã sang `IN_REVIEW` trở đi. Được **phản hồi 1 lần** khi khiếu nại đang `IN_REVIEW` hoặc `AWAITING_EVIDENCE` (≤5000 ký tự, ≤5 bằng chứng). Kết luận chỉ hiện với họ khi khiếu nại đã đóng. | `RESPONDENT_RESPONSE_NOT_ALLOWED`, `RESPONDENT_RESPONSE_ALREADY_SUBMITTED` |
| Nhận xử lý | Nhân viên `CUSTOMER_SERVICE` hoặc admin nhận khiếu nại `PENDING` chưa ai nhận | AdminComplaintService › startReview |
| Đòi bằng chứng | Khiếu nại chuyển `AWAITING_EVIDENCE`. Người gửi nộp bằng chứng thì khiếu nại về lại `IN_REVIEW`. | request-evidence, submitEvidence |
| Kết luận | Chỉ **người đang xử lý**, khi khiếu nại `IN_REVIEW`. Kết luận ≤5000 ký tự. **Chấp nhận thì bắt buộc chọn bên có lỗi**: `RESPONDENT` (bên bị khiếu nại), `COMPLAINANT` (người khiếu nại), `BOTH` (cả hai), `NONE` (không bên nào). Cả hai bên được thông báo. | `FAULT_PARTY_REQUIRED`, `COMPLAINT_REVIEW_FORBIDDEN` |
| Chuyển người xử lý | Chỉ admin, sang nhân viên `CUSTOMER_SERVICE` đang hoạt động | `COMPLAINT_REASSIGN_FORBIDDEN` |
| Ảnh hưởng đến việc khác | Khi khiếu nại đang mở: **giữ** việc xác nhận và tự hoàn tất buổi học (mục 8), **chặn** duyệt hoàn tiền (mục 9) | LessonService, AdminRefundRequestService |

## 11. Ví, nạp tiền, rút tiền, tài khoản ngân hàng

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Ví | Mỗi người dùng có 1 ví, gồm **số dư** và **số dư đang chờ** (tiền đang giữ cho lệnh rút) | Wallet |
| Nạp tiền | Qua VNPay, **10.000đ – 100.000.000đ**, số VND nguyên | PaymentService, WalletTopUpRequest; `INVALID_WALLET_TOP_UP_AMOUNT` |
| Trừ tiền ví | Không đủ số dư thì báo lỗi. Mỗi giao dịch có khoá chống trùng (1–100 ký tự), nên bấm lặp không bị trừ hai lần. | WalletService; `INSUFFICIENT_WALLET_BALANCE` |
| Tiền vào ví từ đâu | Nạp tiền; hoàn phí đăng bài bị từ chối; hoàn học phí; phần gia sư nhận khi học viên vắng; tiền hoàn lại khi huỷ lệnh rút | WalletTransactionPurpose |
| Tài khoản ngân hàng | Mã ngân hàng ≤20 ký tự (chữ, số, `_`, `-`); số tài khoản **6–34 chữ số**; tên chủ tài khoản ≤150 ký tự. Lưu ở dạng **mã hoá**. Tài khoản phải ACTIVE. | UpdateBankAccountRequest, BankAccountCryptoService |
| Rút tiền | Phải có tài khoản ngân hàng; số tiền ≥ 0,01 (tối đa 2 chữ số thập phân); ghi chú ≤500 ký tự. Số tiền chuyển từ **số dư** sang **số dư đang chờ**. Lệnh ở trạng thái `PENDING`. | WithdrawalService; `BANK_ACCOUNT_REQUIRED` |
| Huỷ lệnh rút | Chỉ khi lệnh còn `PENDING`. Tiền trả lại số dư. | `WITHDRAWAL_NOT_CANCELLABLE` |

## 12. Hồ sơ gia sư và chứng chỉ

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Duyệt hồ sơ gia sư | Chỉ duyệt hồ sơ `PENDING`. Duyệt thì chuyển `VERIFIED`. Từ chối thì chuyển `REJECTED`, lý do **10–500 ký tự**. | AdminTutorService; `TUTOR_PROFILE_ALREADY_REVIEWED`, `INVALID_PROFILE_REJECTION_REASON` |
| Tệp chứng chỉ | PDF, JPEG hoặc PNG, **≤10 MB** | CertificateFileStorageService; `CERTIFICATE_FILE_TOO_LARGE`, `UNSUPPORTED_CERTIFICATE_FILE_TYPE` |
| Thông tin chứng chỉ | Tên, đơn vị cấp, link ≤255 ký tự; mô tả ≤500 ký tự | CertificateRequest |
| Sửa chứng chỉ | **Không sửa được chứng chỉ đã duyệt.** Sửa chứng chỉ khác thì nó quay về `PENDING`. Gia sư được xoá chứng chỉ. | `VERIFIED_CERTIFICATE_IMMUTABLE` |
| Duyệt chứng chỉ | Chỉ chứng chỉ `PENDING`. **Không duyệt chứng chỉ đã hết hạn** (ngày hết hạn trước hôm nay). Từ chối thì lý do ≤500 ký tự. | AdminCertificateService; `CERTIFICATE_EXPIRED` |
| Ai xem chứng chỉ | Học viên chỉ thấy chứng chỉ **đã duyệt** của gia sư | CertificateService › findVerifiedByTutorId |

## 13. Đánh giá

| Quy định | Chi tiết | Nguồn |
|---|---|---|
| Trang công khai | Chỉ hiện đánh giá **5 sao** mới nhất, mặc định 3, tối đa 50 | ReviewService |
| Bộ lọc của admin | Số sao 1–5 | AdminTutorService; `INVALID_REVIEW_RATING` |

## 14. Điểm đề xuất gia sư

💡 *Hiểu nhanh:* mỗi gia sư có điểm 0–100, tính lại mỗi ngày lúc 02:30 UTC (`app.tutor-recommendation.recalculation-cron`), công thức phiên bản `v1`. Đây là cơ chế **phạt vi phạm duy nhất có trên `dev`**: mỗi lần bị đình chỉ trừ 5 điểm, tối đa trừ 15.

| Thành phần | Điểm tối đa | Cách tính |
|---|---|---|
| Đánh giá | 35 | Điểm trung bình được kéo về mức 3,5 khi có ít đánh giá (trọng số = số đánh giá ÷ (số đánh giá + 5)), rồi nhân 7 |
| Hợp đồng hoàn tất | 20 | Tính tới 20 hợp đồng, mỗi hợp đồng 1 điểm |
| Tỉ lệ nhận | 15 | Tỉ lệ % × 15. Chưa có dữ liệu thì được 7,5 |
| Tốc độ phản hồi | 10 | ≤1 giờ: 10 điểm; ≥48 giờ: 0 điểm; ở giữa giảm dần đều. Chưa có dữ liệu thì được 5 |
| Kinh nghiệm | 10 | Tính tới 20 học viên đã dạy |
| Xác minh | 10 | Hồ sơ đã duyệt: 5 điểm; có ít nhất 1 chứng chỉ đã duyệt: 5 điểm |
| **Trừ vi phạm** | −15 | Số lần bị đình chỉ × 5, tối đa 15 |

Nguồn: TutorRecommendationScoreService. Admin lọc gia sư theo khoảng điểm trong 0–100, min ≤ max (`INVALID_RECOMMENDATION_SCORE_RANGE`).

> Nhánh `feat/tutor-recommendation` (chưa merge) thêm **trừ điểm khi khiếu nại kết luận gia sư có lỗi** (TutorComplaintPenaltyRepository). Trên `dev` chưa có phần này.

## 15. Thông báo

| Quy định | Chi tiết | Nguồn |
|---|---|---|
| Thông báo tự động | Khoảng 40 loại sự kiện: hợp đồng cần ký, đã ký, bị huỷ, đề xuất gia hạn; buổi học đã dạy hoặc bị huỷ; khiếu nại; hoàn tiền; duyệt bài, hồ sơ, chứng chỉ; đình chỉ hoặc mở lại tài khoản; kỳ học phí đến hạn… | NotificationEventType |
| Thông báo hàng loạt | Tiêu đề 5–255 ký tự, nội dung 10–5000 ký tự. Gửi cho `ALL` (tất cả), `STUDENT` (học viên) hoặc `TUTOR` (gia sư), chỉ những tài khoản đang hoạt động. Loại: `MAINTENANCE` (bảo trì), `PROMOTION` (khuyến mãi), `SERVICE_UPDATE` (cập nhật dịch vụ), `GENERAL` (chung). | NotificationCampaignService |
| Hẹn giờ | Ít nhất **5 phút** sau thời điểm hẹn. Mỗi phút hệ thống quét các thông báo đã hẹn, xử lý tối đa 50 chiến dịch mỗi lần, gửi theo lô 500 người. | `INVALID_NOTIFICATION_SCHEDULE`; NotificationCampaignScheduler |
| Sửa / xoá | Không sửa được khi đang gửi, đã huỷ hoặc đã thu hồi. Đã gửi thì không đổi được đối tượng nhận. Xoá thông báo đã gửi nghĩa là **thu hồi** (`RETRACTED`). Không xoá được thông báo đang gửi. | `NOTIFICATION_IS_PROCESSING` |

## 16. Quản lý nhân viên

| Quy định | Chi tiết | Nguồn / mã lỗi |
|---|---|---|
| Ai quản lý | Chỉ **admin đang hoạt động** | AdminEmployeeService; `ADMIN_REQUIRED` |
| Tạo nhân viên | Email và CCCD (12 chữ số) không trùng; mật khẩu ban đầu 8–72 ký tự | CreateEmployeeRequest |
| Khoá hoặc đổi quyền nhân viên | Lý do 10–1000 ký tự. **Phải chuyển hết** khiếu nại và yêu cầu hoàn tiền đang mở của người đó sang người khác trước. | `EMPLOYEE_HAS_OPEN_ASSIGNMENTS`, `INVALID_EMPLOYEE_ACCESS_REASON` |
| Mở lại nhân viên | Phải có chức năng | `EMPLOYEE_FUNCTION_REQUIRED` |

## 17. Các tác vụ tự động

| Tác vụ | Lịch chạy (mặc định) | Việc làm |
|---|---|---|
| Kết thúc hợp đồng | 00:00 hằng ngày, giờ VN | `ACTIVE` đã tới ngày kết thúc → `COMPLETED`; `PENDING` đã tới ngày kết thúc → `CANCELLED` |
| Nhắc đóng học phí | 09:00 hằng ngày, giờ VN | Nhắc các kỳ đến hạn trong 3 ngày tới |
| Tự hoàn tất buổi học | Mỗi giờ | Buổi đã đánh dấu "đã dạy" quá 3 ngày → `COMPLETED`, trừ buổi đang bị giữ |
| Hết hạn đình chỉ | Mỗi 60 giây | Mở lại tài khoản hết hạn khoá tạm thời |
| Gửi thông báo hẹn giờ | Mỗi 60 giây | Gửi các chiến dịch đến giờ |
| Tính lại điểm đề xuất | 02:30 UTC hằng ngày | Tính lại điểm mọi gia sư |

---

## 18. ⚠️ Khoảng trống: những gì code chưa quy định

Đây là những câu người dùng **rất có thể hỏi** mà hệ thống chưa có quy định trong code. Chatbot phải trả lời "hiện chưa có quy định" hoặc chuyển cho nhân viên, **không được tự suy ra**.

1. **Chưa có bảng vi phạm → hình phạt.** Loại khiếu nại (kể cả `SERIOUS_MISCONDUCT`) và bên có lỗi chỉ được **ghi nhận**. Không có hình phạt tự động như trừ tiền, cảnh cáo hay khoá tài khoản. Đình chỉ hoàn toàn do nhân viên **tự quyết**. Hình phạt tự động duy nhất là trừ điểm đề xuất theo số lần bị đình chỉ.
2. **Gia sư chưa nhận được tiền dạy.** Học phí học viên đóng không được chuyển vào ví gia sư khi buổi học hoàn tất. Tiền vào ví gia sư (`TUTOR_EARNING`) **chỉ** phát sinh ở phần 20% khi học viên vắng trong yêu cầu hoàn tiền. Chưa có phí hay hoa hồng của nền tảng.
3. **Lệnh rút tiền chưa có người xử lý.** Không có API nào cho nhân viên duyệt, chuyển khoản hay từ chối lệnh rút. Lệnh nằm ở `PENDING` cho đến khi người dùng tự huỷ. Cũng chưa có số tiền rút tối thiểu hay tối đa, hay giới hạn số lần rút mỗi ngày.
4. **Chưa huỷ được hợp đồng đang ACTIVE.** Chỉ huỷ được hợp đồng còn `PENDING`. Bảng `CancelContractRequest` có trong DB nhưng không service nào dùng.
5. **Chưa có cách viết đánh giá.** Chỉ có API đọc. Các chỉ số dùng để tính điểm đề xuất (điểm đánh giá, tỉ lệ nhận, thời gian phản hồi, số hợp đồng hoàn tất…) cũng **không thấy chỗ nào cập nhật** trên `dev`.
6. **Huỷ bài đăng không được hoàn phí.** Code chỉ hoàn phí khi nhân viên **từ chối** bài.
7. **Gia sư chưa được duyệt hồ sơ vẫn đăng bài và nhận học viên được.** Code không kiểm tra hồ sơ `VERIFIED` khi đăng bài nhận dạy, gửi đề nghị dạy hay soạn hợp đồng.
8. **Với kỳ thanh toán `PER_LESSON`, buổi học bị huỷ vẫn chiếm suất.** Kỳ thanh toán đã gắn với buổi bị huỷ không được trả lại. Vì vậy gia sư có thể bị báo hết suất tạo buổi (`CONTRACT_LESSON_LIMIT_REACHED`) sớm hơn tổng số buổi.
9. **Khiếu nại không có thời hạn gửi** và không giới hạn số khiếu nại trên một hợp đồng.
10. **Đăng nhập sai không bị khoá tạm.** Gửi OTP quên mật khẩu cũng không giới hạn số lần (code có thể trả về việc email có tồn tại hay không).
11. **Phí đăng bài không có giá trị mặc định.** Số tiền do người vận hành đặt bằng biến môi trường, nên chatbot không nên nói con số cụ thể nếu không đọc từ cấu hình.

## 19. Ghi chú cho kho tri thức của chatbot

- Mỗi mục 2–17 tương ứng gần đúng **một chủ đề** trong danh mục chủ đề của kho tri thức (thiết kế chatbot, mục 3.7). Nên thêm hai nhóm **"Ví và rút tiền"** và **"Điểm đề xuất"** nếu danh mục chưa có.
- Các giá trị đến từ cấu hình (`app.*`) nên được đọc từ biến môi trường lúc sinh tài liệu, không ghi cứng: phí đăng, 48 giờ phản hồi, 80% và 100% hoàn, 3 ngày tự hoàn tất, thời hạn token.
- Mã lỗi (`MA_LOI`) nên được đưa vào kho tri thức dưới dạng câu hỏi "Tôi gặp lỗi X là sao?". Đây là kiểu câu hỏi chatbot hỗ trợ sẽ gặp nhiều nhất.
- Nhân viên chỉ có `CUSTOMER_SERVICE` và `ACCOUNTANT`. Thiết kế chatbot đã đổi theo: yêu cầu hỗ trợ do `CUSTOMER_SERVICE` xử lý.
