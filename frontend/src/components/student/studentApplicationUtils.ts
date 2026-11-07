import type { StudentApplication } from '../../services/studentApi';

export function formatMoney(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function learningModeLabel(value: StudentApplication['learningMode']) {
  return value === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp';
}

export function applicationStatusLabel(value: StudentApplication['status']) {
  const labels: Record<StudentApplication['status'], string> = {
    PENDING: 'Đang chờ',
    ACCEPTED: 'Đã chấp nhận',
    REJECTED: 'Bị từ chối',
    CANCELLED: 'Đã hủy'
  };
  return labels[value];
}
