import { ApiError } from './httpClient';

/** Vietnamese text for the error codes the server returns; the server's own message is English. */
const MESSAGES: Record<string, string> = {
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
  INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION: 'Đề nghị này không còn thay đổi được.'
};

/** A Vietnamese message for an error: by its code when we know it, else {@code fallback}. */
export function errorMessage(caught: unknown, fallback: string): string {
  if (caught instanceof ApiError) return MESSAGES[caught.code] ?? fallback;
  // fetch itself fails (TypeError) when the server cannot be reached.
  if (caught instanceof TypeError) return 'Không kết nối được tới máy chủ. Vui lòng kiểm tra mạng.';
  return fallback;
}

/** The Vietnamese text of a code, or undefined when there is none. */
export function messageForCode(code: string): string | undefined {
  return MESSAGES[code];
}
