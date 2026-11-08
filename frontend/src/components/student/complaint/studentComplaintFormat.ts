import type { ComplaintCategory, ComplaintFaultParty, ComplaintStatus } from '../../../services/studentComplaintApi';

export const complaintStatusLabels: Record<ComplaintStatus, string> = {
  PENDING: 'Chờ tiếp nhận',
  IN_REVIEW: 'Đang xử lý',
  AWAITING_EVIDENCE: 'Chờ bổ sung bằng chứng',
  RESOLVED: 'Đã giải quyết',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã hủy'
};

export const complaintCategoryLabels: Record<ComplaintCategory, string> = {
  ABSENCE: 'Vắng mặt',
  LATE_OR_SHORT: 'Muộn hoặc thiếu giờ',
  TEACHING_QUALITY: 'Chất lượng giảng dạy',
  MISCONDUCT: 'Vi phạm ứng xử',
  SERIOUS_MISCONDUCT: 'Vi phạm nghiêm trọng',
  OTHER: 'Khác'
};

export const complaintFaultPartyLabels: Record<ComplaintFaultParty, string> = {
  RESPONDENT: 'Bên bị khiếu nại',
  COMPLAINANT: 'Bên khiếu nại',
  BOTH: 'Cả hai bên',
  NONE: 'Không bên nào'
};

export function formatComplaintDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}

export function complaintErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = String((error as { code: unknown }).code);
    const messages: Record<string, string> = {
      COMPLAINT_NOT_FOUND: 'Không tìm thấy khiếu nại.',
      CONTRACT_NOT_FOUND: 'Không tìm thấy hợp đồng hoặc bạn không có quyền sử dụng hợp đồng này.',
      INVALID_COMPLAINT_STATUS_TRANSITION: 'Khiếu nại đã thay đổi trạng thái. Vui lòng tải lại trang.',
      RESPONDENT_RESPONSE_NOT_ALLOWED: 'Khiếu nại không còn nhận phản hồi.',
      RESPONDENT_RESPONSE_ALREADY_SUBMITTED: 'Bạn đã gửi phản hồi cho khiếu nại này.',
      COMPLAINT_FILE_REQUIRED: 'Vui lòng chọn tệp minh chứng.',
      COMPLAINT_FILE_TOO_LARGE: 'Tệp minh chứng không được vượt quá 10 MB.',
      UNSUPPORTED_COMPLAINT_FILE_TYPE: 'Định dạng tệp minh chứng chưa được hỗ trợ.',
      COMPLAINT_STORAGE_UNAVAILABLE: 'Dịch vụ lưu trữ minh chứng hiện chưa sẵn sàng.',
      COMPLAINT_STORAGE_REJECTED: 'Dịch vụ lưu trữ đã từ chối tệp minh chứng.',
      COMPLAINT_STORAGE_UNREACHABLE: 'Không thể kết nối tới dịch vụ lưu trữ minh chứng.'
    };
    if (messages[code]) return messages[code];
  }
  return error instanceof Error ? error.message : 'Không thể xử lý khiếu nại. Vui lòng thử lại.';
}
