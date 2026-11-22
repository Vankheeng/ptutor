import type { WithdrawalStatus } from '../../services/adminWithdrawalApi';

export const withdrawalLabels: Record<WithdrawalStatus, string> = {
  PENDING: 'Chờ duyệt',
  PROCESSING: 'Chờ chuyển khoản',
  COMPLETED: 'Đã chuyển khoản',
  REJECTED: 'Từ chối',
  CANCELLED: 'Người rút đã hủy'
};

export const formatWithdrawalMoney = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);

export const formatWithdrawalDate = (value: string | null) => (value ? new Date(value).toLocaleString('vi-VN') : '—');
