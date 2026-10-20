/**
 * Vietnamese text for what the server reports: its own messages are English. Used by the HTTP client, so every
 * error that reaches a page is already in Vietnamese.
 */

/** Vietnamese text for the error codes the server returns; the server's own message is English. */
export const MESSAGES: Record<string, string> = {
  UNAUTHORIZED: 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  VALIDATION_ERROR: 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
  INVALID_REQUEST: 'Yêu cầu chưa hợp lệ.',
  RESOURCE_NOT_FOUND: 'Không tìm thấy dữ liệu.',
  DATA_INTEGRITY_ERROR: 'Dữ liệu bị trùng hoặc không khớp. Vui lòng thử lại.',
  INTERNAL_SERVER_ERROR: 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.',
  REQUEST_FAILED: 'Không thể xử lý yêu cầu. Vui lòng thử lại.',

  // Recommendation
  TUTOR_PROFILE_NOT_VERIFIED: 'Hồ sơ gia sư của bạn chưa được duyệt nên chưa xem được gợi ý học viên.',
  STUDENT_PROFILE_REQUIRED: 'Bạn cần hoàn thiện hồ sơ học sinh trước.',
  TUTOR_PROFILE_REQUIRED: 'Bạn cần hoàn thiện hồ sơ gia sư trước.',
  STUDENT_PROFILE_NOT_FOUND: 'Không tìm thấy hồ sơ học sinh.',
  TUTOR_PROFILE_NOT_FOUND: 'Không tìm thấy hồ sơ gia sư.',
  PROFILE_NOT_FOUND: 'Không tìm thấy hồ sơ.',
  RECOMMENDATION_LOG_NOT_FOUND: 'Lượt tìm này không còn. Vui lòng tìm lại.',
  CANDIDATE_NOT_IN_RESULTS: 'Kết quả này không thuộc lượt tìm hiện tại. Vui lòng tìm lại.',
  CANDIDATE_NOT_FOUND: 'Không tìm thấy tin đăng này.',
  INVALID_SUBJECT: 'Môn học không hợp lệ.',
  INVALID_GRADE: 'Lớp không hợp lệ.',
  INVALID_PROVINCE: 'Tỉnh / thành không hợp lệ.',
  SAVED_SEARCH_LIMIT_REACHED: 'Bạn đã lưu đủ 5 tìm kiếm. Hãy xoá bớt một tìm kiếm cũ.',
  SAVED_SEARCH_INCOMPLETE: 'Cần chọn môn và lớp trước khi lưu tìm kiếm.',
  INVALID_SAVED_SEARCH: 'Tiêu chí tìm kiếm chưa hợp lệ.',
  SAVED_SEARCH_DIRECTION_NOT_ALLOWED: 'Tài khoản của bạn không lưu được loại tìm kiếm này.',
  SAVED_SEARCH_NOT_FOUND: 'Không tìm thấy tìm kiếm đã lưu.',

  // Applications and proposals
  TEACHING_REQUEST_NOT_FOUND: 'Không tìm thấy tin dạy.',
  STUDYING_REQUEST_NOT_FOUND: 'Không tìm thấy tin tìm gia sư.',
  INVALID_TEACHING_REQUEST_STATUS: 'Tin dạy này không còn nhận đơn.',
  STUDYING_REQUEST_NOT_OPEN: 'Tin tìm gia sư này không còn nhận đề nghị.',
  STUDYING_REQUEST_ALREADY_MATCHED: 'Học sinh này đã chọn được gia sư.',
  DUPLICATE_STUDENT_TUTOR_REQUEST: 'Bạn đã gửi đơn vào tin này và đang chờ trả lời.',
  DUPLICATE_TUTOR_STUDENT_REQUEST: 'Bạn đã gửi đề nghị cho tin này và đang chờ trả lời.',
  INVALID_PREFERRED_LOCATION: 'Khu vực học trực tiếp chưa hợp lệ.',
  STUDENT_TUTOR_REQUEST_NOT_FOUND: 'Không tìm thấy đơn đăng ký.',
  TUTOR_STUDENT_REQUEST_NOT_FOUND: 'Không tìm thấy đề nghị dạy.',
  INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION: 'Đơn này không còn thay đổi được.',
  INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION: 'Đề nghị này không còn thay đổi được.',
  // Account, sign-in and password
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  INVALID_REFRESH_TOKEN: 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.',
  INVALID_AUTHENTICATED_USER: 'Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.',
  ACCOUNT_INACTIVE: 'Tài khoản đang không hoạt động.',
  ACCOUNT_NOT_ACTIVE: 'Tài khoản đang không hoạt động.',
  ACCOUNT_SUSPENDED: 'Tài khoản đang bị tạm khoá.',
  USER_INACTIVE: 'Tài khoản đang không hoạt động.',
  USER_NOT_FOUND: 'Không tìm thấy tài khoản.',
  STUDENT_NOT_FOUND: 'Không tìm thấy học sinh.',
  TUTOR_NOT_FOUND: 'Không tìm thấy gia sư.',
  EMAIL_ALREADY_EXISTS: 'Email này đã được dùng cho một tài khoản khác.',
  EMAIL_NOT_FOUND: 'Không có tài khoản nào dùng email này.',
  EMAIL_DELIVERY_FAILED: 'Chưa gửi được email. Vui lòng thử lại sau.',
  CITIZEN_ID_ALREADY_EXISTS: 'Số CCCD này đã được dùng cho một tài khoản khác.',
  INVALID_REGISTRATION_ROLE: 'Vai trò đăng ký không hợp lệ.',
  INVALID_OR_EXPIRED_OTP: 'Mã OTP không đúng hoặc đã hết hạn.',
  CURRENT_PASSWORD_INCORRECT: 'Mật khẩu hiện tại không đúng.',
  NEW_PASSWORD_MUST_BE_DIFFERENT: 'Mật khẩu mới phải khác mật khẩu hiện tại.',
  PASSWORD_CONFIRMATION_MISMATCH: 'Mật khẩu nhập lại không khớp.',
  INVALID_USER_PROFILE: 'Thông tin hồ sơ chưa hợp lệ.',
  ADDRESS_IDS_REQUIRED_TOGETHER: 'Cần chọn cả tỉnh / thành và quận / huyện.',
  DISTRICT_PROVINCE_MISMATCH: 'Quận / huyện không thuộc tỉnh / thành đã chọn.',
  INVALID_DISTRICT: 'Quận / huyện không hợp lệ.',
  EMPTY_UPDATE_REQUEST: 'Bạn chưa thay đổi thông tin nào.',

  // Certificates and tutor profile
  CERTIFICATE_NOT_FOUND: 'Không tìm thấy chứng chỉ.',
  CERTIFICATE_EXPIRED: 'Chứng chỉ đã hết hạn.',
  CERTIFICATE_FILE_REQUIRED: 'Vui lòng tải lên tệp chứng chỉ.',
  CERTIFICATE_FILE_NOT_FOUND: 'Không tìm thấy tệp chứng chỉ.',
  CERTIFICATE_FILE_TOO_LARGE: 'Tệp chứng chỉ quá lớn.',
  UNSUPPORTED_CERTIFICATE_FILE_TYPE: 'Định dạng tệp chưa được hỗ trợ.',
  CERTIFICATE_UPLOAD_FAILED: 'Chưa tải được tệp lên. Vui lòng thử lại.',
  CERTIFICATE_STORAGE_REJECTED: 'Chưa lưu được tệp. Vui lòng thử lại.',
  CERTIFICATE_STORAGE_UNAVAILABLE: 'Kho lưu tệp tạm thời không dùng được. Vui lòng thử lại sau.',
  CERTIFICATE_STORAGE_UNREACHABLE: 'Kho lưu tệp tạm thời không dùng được. Vui lòng thử lại sau.',
  CERTIFICATE_ALREADY_REVIEWED: 'Chứng chỉ này đã được duyệt.',
  VERIFIED_CERTIFICATE_IMMUTABLE: 'Chứng chỉ đã xác minh không sửa được.',
  TUTOR_PROFILE_ALREADY_REVIEWED: 'Hồ sơ này đã được duyệt.',

  // Postings
  INVALID_STUDYING_REQUEST: 'Tin tìm gia sư chưa hợp lệ.',
  INVALID_STUDYING_REQUEST_STATUS_TRANSITION: 'Tin tìm gia sư đang ở trạng thái không cho phép thao tác này.',
  INVALID_STUDYING_REQUEST_PAYMENT_STATUS: 'Tin tìm gia sư này không ở bước thanh toán.',
  INVALID_TEACHING_REQUEST_STATUS_TRANSITION: 'Tin dạy đang ở trạng thái không cho phép thao tác này.',
  INVALID_TEACHING_REQUEST_PAYMENT_STATUS: 'Tin dạy này không ở bước thanh toán.',
  STUDYING_REQUEST_PAYMENT_NOT_FOUND: 'Không tìm thấy khoản thanh toán của tin này.',
  TEACHING_REQUEST_PAYMENT_NOT_FOUND: 'Không tìm thấy khoản thanh toán của tin này.',
  STUDYING_REQUEST_ALREADY_REVIEWED: 'Tin này đã được duyệt.',
  TEACHING_REQUEST_ALREADY_REVIEWED: 'Tin này đã được duyệt.',
  SUBJECT_ID_REQUIRED: 'Vui lòng chọn môn học.',

  // Contracts and lessons
  CONTRACT_NOT_FOUND: 'Không tìm thấy hợp đồng.',
  CONTRACT_NOT_ACTIVE: 'Hợp đồng không còn hiệu lực.',
  CONTRACT_EXPIRED: 'Hợp đồng đã hết hạn.',
  CONTRACT_FIELD_NOT_EDITABLE: 'Không sửa được thông tin này của hợp đồng.',
  CONTRACT_LESSON_LIMIT_REACHED: 'Đã đủ số buổi của hợp đồng.',
  DUPLICATE_CONTRACT: 'Đã có hợp đồng cho đơn này.',
  DUPLICATE_CONTRACT_RENEWAL: 'Hợp đồng này đã được đề nghị gia hạn.',
  INVALID_CONTRACT_DATE_RANGE: 'Ngày kết thúc phải sau ngày bắt đầu.',
  INVALID_CONTRACT_PAYMENT_AMOUNT: 'Số tiền thanh toán chưa hợp lệ.',
  INVALID_CONTRACT_STATUS_TRANSITION: 'Hợp đồng đang ở trạng thái không cho phép thao tác này.',
  INVALID_CONTRACT_APPLICATION_STATUS: 'Đơn này chưa được nhận nên chưa lập được hợp đồng.',
  INVALID_CONTRACT_SOURCE_STATUS: 'Không lập được hợp đồng từ đơn ở trạng thái này.',
  INVALID_CONTRACT_ORIGIN: 'Hợp đồng không có nguồn hợp lệ.',
  CONTRACT_COUNTERPARTY_REQUIRED: 'Thiếu bên còn lại của hợp đồng.',
  CONTRACT_CREATOR_REQUIRED: 'Thiếu người lập hợp đồng.',
  CONTRACT_MODE_REQUIRED: 'Vui lòng chọn hình thức học.',
  CONTRACT_TEACHING_MODE_REQUIRED: 'Vui lòng chọn hình thức học.',
  CONTRACT_SUBJECT_REQUIRED: 'Vui lòng chọn môn học.',
  LESSON_NOT_FOUND: 'Không tìm thấy buổi học.',
  LESSON_NOT_FINISHED: 'Buổi học chưa kết thúc.',
  LESSON_OUTSIDE_CONTRACT_PERIOD: 'Buổi học nằm ngoài thời hạn hợp đồng.',
  LESSON_COMPLETION_ON_HOLD: 'Buổi học đang tạm giữ vì có khiếu nại.',
  INVALID_LESSON_STATUS: 'Trạng thái buổi học không hợp lệ.',
  INVALID_LESSON_STATUS_TRANSITION: 'Buổi học đang ở trạng thái không cho phép thao tác này.',
  INVALID_LESSON_TIME: 'Giờ học chưa hợp lệ.',
  INVALID_REVIEW_RATING: 'Số sao đánh giá phải từ 1 đến 5.',

  // Wallet
  WALLET_NOT_FOUND: 'Không tìm thấy ví.',
  INSUFFICIENT_WALLET_BALANCE: 'Số dư ví không đủ.',
  INVALID_WALLET_AMOUNT: 'Số tiền chưa hợp lệ.',
  INVALID_WALLET_TOP_UP_AMOUNT: 'Số tiền nạp chưa hợp lệ.',
  INVALID_WALLET_TRANSACTION: 'Giao dịch không hợp lệ.',
  INVALID_WALLET_TRANSACTION_PURPOSE: 'Loại giao dịch không hợp lệ.',
  INVALID_TRANSACTION_DATE_RANGE: 'Khoảng ngày chưa hợp lệ.',
  WALLET_TRANSACTION_NOT_FOUND: 'Không tìm thấy giao dịch.',
  TRANSACTION_NOT_FOUND: 'Không tìm thấy giao dịch.',
  BANK_ACCOUNT_REQUIRED: 'Vui lòng thêm tài khoản ngân hàng trước khi rút tiền.',
  WITHDRAWAL_NOT_FOUND: 'Không tìm thấy lệnh rút tiền.',
  WITHDRAWAL_NOT_CANCELLABLE: 'Lệnh rút tiền này không huỷ được nữa.',

  // Complaints
  COMPLAINT_NOT_FOUND: 'Không tìm thấy khiếu nại.',
  INVALID_COMPLAINT_STATUS_TRANSITION: 'Khiếu nại đang ở trạng thái không cho phép thao tác này.',
  RESPONDENT_RESPONSE_ALREADY_SUBMITTED: 'Bạn đã gửi phản hồi cho khiếu nại này.',
  RESPONDENT_RESPONSE_NOT_ALLOWED: 'Bạn không phản hồi được khiếu nại này.',

  // Notifications
  NOTIFICATION_NOT_FOUND: 'Không tìm thấy thông báo.',
  LEARNED_WEIGHTS_VERSION_NOT_FOUND: 'Không tìm thấy phiên bản trọng số.'
};

/** By HTTP status, for codes the table does not know. */
export function messageForStatus(status: number): string {
  if (status === 0) return 'Không kết nối được tới máy chủ. Vui lòng kiểm tra mạng.';
  if (status === 400) return 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.';
  if (status === 401) return 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (status === 404) return 'Không tìm thấy dữ liệu.';
  if (status === 409) return 'Thao tác không thực hiện được vì dữ liệu đã thay đổi. Vui lòng tải lại trang.';
  if (status >= 500) return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
  return 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}

const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i;

/** A field's validation message in Vietnamese, from the usual English patterns of the server. */
export function fieldMessage(message: string): string {
  if (VIETNAMESE.test(message)) return message;
  let match: RegExpMatchArray | null;
  if (/ is required$| must not be (blank|empty|null)$/i.test(message)) return 'Vui lòng nhập thông tin này.';
  if ((match = message.match(/must not exceed (\d+) characters/i))) return `Tối đa ${match[1]} ký tự.`;
  if ((match = message.match(/must contain exactly (\d+) digits/i))) return `Phải gồm đúng ${match[1]} chữ số.`;
  if ((match = message.match(/must be between (\d+) and (\d+)/i))) return `Phải từ ${match[1]} đến ${match[2]}.`;
  if (/must not be negative/i.test(message)) return 'Không được là số âm.';
  if (/greater than zero|must be positive/i.test(message)) return 'Phải lớn hơn 0.';
  if (/end time must be after start time/i.test(message)) return 'Giờ kết thúc phải sau giờ bắt đầu.';
  if (/minimum price must not exceed maximum price/i.test(message))
    return 'Giá tối thiểu không được lớn hơn giá tối đa.';
  if (/email/i.test(message)) return 'Email không hợp lệ.';
  if (/password/i.test(message)) return 'Mật khẩu chưa hợp lệ.';
  if (/date/i.test(message)) return 'Ngày chưa hợp lệ.';
  return 'Giá trị chưa hợp lệ.';
}
