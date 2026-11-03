import type { ComplaintRelation, TransactionSource } from '../../services/adminStudentApi';
import type { StatusMeta } from './adminFormat';

export const transactionSourceLabels: Record<TransactionSource, string> = {
  PAYMENT: 'Thanh toán',
  WALLET_TRANSACTION: 'Giao dịch ví',
  WITHDRAWAL: 'Rút tiền'
};

/** Payment types, wallet entry directions and withdrawals share one column in the backend's union query. */
export const transactionTypeLabels: Record<string, string> = {
  TUITION_PAYMENT: 'Học phí',
  WALLET_TOP_UP: 'Nạp ví',
  STUDYING_REQUEST_FEE: 'Phí đăng yêu cầu học',
  TEACHING_REQUEST_FEE: 'Phí đăng tin dạy',
  CREDIT: 'Cộng ví',
  DEBIT: 'Trừ ví',
  WITHDRAWAL: 'Rút tiền'
};

export const transactionMethodLabels: Record<string, string> = {
  VNPAY: 'VNPay',
  WALLET: 'Ví'
};

export const transactionStatusMeta: Record<string, StatusMeta> = {
  PENDING: { label: 'Đang chờ', tone: 'pending' },
  PROCESSING: { label: 'Đang xử lý', tone: 'info' },
  PAID: { label: 'Đã thanh toán', tone: 'success' },
  COMPLETED: { label: 'Hoàn tất', tone: 'success' },
  REFUNDED: { label: 'Đã hoàn', tone: 'info' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
  REJECTED: { label: 'Bị từ chối', tone: 'danger' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' }
};

export function transactionStatus(status: string): StatusMeta {
  return transactionStatusMeta[status] ?? { label: status, tone: 'neutral' };
}

export const complaintRelationMeta: Record<Exclude<ComplaintRelation, 'ALL'>, StatusMeta> = {
  SUBMITTED: { label: 'Đã gửi', tone: 'brand' },
  RECEIVED: { label: 'Bị khiếu nại', tone: 'pending' }
};
