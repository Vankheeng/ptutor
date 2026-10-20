/** Vietnamese labels for the codes the server sends (statuses, modes, periods…), shared by every workspace page. */

const STATUS: Record<string, string> = {
  ALL: 'Tất cả',
  // Postings
  DRAFT: 'Bản nháp',
  PENDING_REVIEW: 'Chờ duyệt',
  OPEN: 'Đang mở',
  MATCHED: 'Đã có gia sư',
  CLOSED: 'Đã đóng',
  CANCELLED: 'Đã huỷ',
  REJECTED: 'Bị từ chối',
  // Applications, proposals
  PENDING: 'Đang chờ',
  ACCEPTED: 'Đã nhận',
  // Contracts
  ACTIVE: 'Đang học',
  COMPLETED: 'Hoàn thành',
  // Lessons
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  // Payments
  PAID: 'Đã thanh toán',
  FAILED: 'Thất bại',
  REFUNDED: 'Đã hoàn tiền',
  // Certificates, profiles
  VERIFIED: 'Đã xác minh',
  EXPIRED: 'Hết hạn',
  // Complaints
  IN_REVIEW: 'Đang xử lý',
  AWAITING_EVIDENCE: 'Chờ bổ sung bằng chứng',
  RESOLVED: 'Đã giải quyết',
  // Notifications
  READ: 'Đã đọc',
  UNREAD: 'Chưa đọc'
};

/** Statuses that mean something else depending on what they describe. */
const STATUS_BY_KIND: Record<string, Record<string, string>> = {
  contract: { PENDING: 'Chờ ký', ACTIVE: 'Đang học', CANCELLED: 'Đã huỷ' },
  certificate: { PENDING: 'Chờ duyệt', REJECTED: 'Không được duyệt' },
  profile: { PENDING: 'Chờ duyệt', VERIFIED: 'Đã duyệt', REJECTED: 'Không được duyệt' },
  application: { PENDING: 'Chờ trả lời', ACCEPTED: 'Được nhận', REJECTED: 'Bị từ chối', CANCELLED: 'Đã rút' },
  payment: { PENDING: 'Chờ thanh toán' }
};

export type StatusKind = keyof typeof STATUS_BY_KIND;

/** The Vietnamese label of a status; unknown codes are shown readable rather than raw. */
export function statusLabel(value: string | null | undefined, kind?: StatusKind): string {
  if (!value) return '';
  return (kind && STATUS_BY_KIND[kind]?.[value]) ?? STATUS[value] ?? value.toLowerCase().replaceAll('_', ' ');
}

export function modeLabel(value: string | null | undefined): string {
  return value === 'ONLINE' ? 'Online' : value === 'OFFLINE' ? 'Trực tiếp' : value === 'ANY' ? 'Cả hai' : '';
}

const PAYMENT_PERIOD: Record<string, string> = {
  PER_LESSON: 'Theo buổi',
  WEEKLY: 'Theo tuần',
  MONTHLY: 'Theo tháng',
  PACKAGE: 'Trọn gói'
};

export function paymentPeriodLabel(value: string | null | undefined): string {
  return value ? (PAYMENT_PERIOD[value] ?? value) : '';
}

const TRANSACTION: Record<string, string> = {
  CREDIT: 'Tiền vào',
  DEBIT: 'Tiền ra',
  TOP_UP: 'Nạp tiền',
  TUTOR_EARNING: 'Thu nhập dạy học',
  CONTRACT_REFUND: 'Hoàn tiền hợp đồng',
  TEACHING_REQUEST_REFUND: 'Hoàn phí đăng tin dạy',
  STUDYING_REQUEST_REFUND: 'Hoàn phí đăng tin tìm gia sư',
  WITHDRAWAL_REVERSAL: 'Hoàn lại lệnh rút tiền',
  TUITION_PAYMENT: 'Thanh toán học phí',
  STUDYING_REQUEST_FEE: 'Phí đăng tin tìm gia sư',
  TEACHING_REQUEST_FEE: 'Phí đăng tin dạy',
  WITHDRAWAL: 'Rút tiền',
  WALLET_TOP_UP: 'Nạp ví',
  VNPAY: 'VNPay',
  WALLET: 'Ví Ptutor',
  PAYMENT: 'Thanh toán',
  WALLET_TRANSACTION: 'Giao dịch ví'
};

/** Wallet and payment codes: transaction types, purposes, payment types and methods. */
export function transactionLabel(value: string | null | undefined): string {
  return value ? (TRANSACTION[value] ?? value.toLowerCase().replaceAll('_', ' ')) : '';
}

const WEEKDAYS = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'];

/** ISO day of week: 1 = Monday … 7 = Sunday. */
export function weekdayLabel(dayOfWeek: number): string {
  return WEEKDAYS[dayOfWeek - 1] ?? `Ngày ${dayOfWeek}`;
}

/** "1.200.000 đ"; null means the price is to be agreed. */
export function moneyLabel(value: number | null | undefined, whenMissing = 'Thoả thuận'): string {
  return value === null || value === undefined ? whenMissing : `${new Intl.NumberFormat('vi-VN').format(value)} đ`;
}

export function dateTimeLabel(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '';
}

export function dateLabel(value: string | null | undefined): string {
  return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value)) : '';
}
