import type {
  AdminComplaintDetail,
  ComplaintCategory,
  ComplaintFaultParty,
  ComplaintPartyRole,
  ComplaintStatus
} from '../../services/complaintApi';
import type { StatusMeta } from './adminFormat';

/** Seeded staff accounts such as the default admin have no first or last name. */
export function reviewerName(name: string | null | undefined) {
  return name?.trim() || 'Chưa đặt tên';
}

export const complaintStatusMeta: Record<ComplaintStatus, StatusMeta> = {
  PENDING: { label: 'Chờ tiếp nhận', tone: 'pending' },
  IN_REVIEW: { label: 'Đang xử lý', tone: 'info' },
  AWAITING_EVIDENCE: { label: 'Chờ bằng chứng', tone: 'brand' },
  RESOLVED: { label: 'Đã giải quyết', tone: 'success' },
  REJECTED: { label: 'Đã từ chối', tone: 'danger' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' }
};

export const complaintCategoryLabels: Record<ComplaintCategory, string> = {
  ABSENCE: 'Vắng mặt',
  LATE_OR_SHORT: 'Muộn / thiếu giờ',
  TEACHING_QUALITY: 'Chất lượng giảng dạy',
  MISCONDUCT: 'Vi phạm ứng xử',
  SERIOUS_MISCONDUCT: 'Vi phạm nghiêm trọng',
  OTHER: 'Khác'
};

export const faultPartyLabels: Record<ComplaintFaultParty, string> = {
  RESPONDENT: 'Bên bị khiếu nại',
  COMPLAINANT: 'Bên khiếu nại',
  BOTH: 'Cả hai bên',
  NONE: 'Không bên nào'
};

export const partyRoleLabels: Record<ComplaintPartyRole, string> = {
  STUDENT: 'Học viên',
  TUTOR: 'Gia sư'
};

export const installmentStatusMeta: Record<AdminComplaintDetail['installments'][number]['status'], StatusMeta> = {
  PENDING: { label: 'Chưa thanh toán', tone: 'pending' },
  PAID: { label: 'Đã thanh toán', tone: 'success' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' }
};

export const paymentStatusMeta: Record<AdminComplaintDetail['payments'][number]['status'], StatusMeta> = {
  PENDING: { label: 'Đang chờ', tone: 'pending' },
  PAID: { label: 'Đã thanh toán', tone: 'success' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' },
  REFUNDED: { label: 'Đã hoàn', tone: 'info' }
};

export const paymentMethodLabels: Record<AdminComplaintDetail['payments'][number]['paymentMethod'], string> = {
  VNPAY: 'VNPay',
  WALLET: 'Ví'
};

const errorMessages: Record<string, string> = {
  COMPLAINT_NOT_FOUND: 'Không tìm thấy khiếu nại.',
  COMPLAINT_ALREADY_ASSIGNED: 'Khiếu nại đã được người khác tiếp nhận.',
  COMPLAINT_REVIEW_FORBIDDEN: 'Chỉ người đang phụ trách mới xử lý được khiếu nại này.',
  INVALID_COMPLAINT_STATUS_TRANSITION: 'Khiếu nại đã thay đổi trạng thái. Vui lòng tải lại trang.',
  COMPLAINT_REASSIGN_FORBIDDEN: 'Chỉ admin mới chuyển được người xử lý.',
  COMPLAINT_NOT_ASSIGNED: 'Khiếu nại chưa có người xử lý.',
  COMPLAINT_ALREADY_ASSIGNED_TO_EMPLOYEE: 'Khiếu nại đang do nhân viên này xử lý.',
  EMPLOYEE_NOT_FOUND: 'Không tìm thấy nhân viên.',
  EMPLOYEE_NOT_ACTIVE: 'Tài khoản nhân viên không còn hoạt động.',
  INVALID_EMPLOYEE_FUNCTION: 'Nhân viên này không phụ trách khiếu nại.',
  EMPLOYEE_PROFILE_REQUIRED: 'Tài khoản của bạn chưa có hồ sơ nhân viên.',
  FAULT_PARTY_REQUIRED: 'Vui lòng chọn bên có lỗi.'
};

export function complaintErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = String((error as { code: unknown }).code);
    if (errorMessages[code]) return errorMessages[code];
  }
  return error instanceof Error ? error.message : 'Không thể xử lý khiếu nại. Vui lòng thử lại.';
}
