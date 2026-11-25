import type { RequestStatus } from '../../services/studentApi';

export const studyingRequestStatuses: Array<'ALL' | RequestStatus> = [
  'ALL',
  'DRAFT',
  'PENDING_REVIEW',
  'OPEN',
  'MATCHED',
  'CLOSED',
  'CANCELLED',
  'REJECTED'
];

export function statusLabel(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}

export function studyingRequestStatusText(value: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Bản nháp',
    PENDING_REVIEW: 'Chờ duyệt',
    OPEN: 'Đang mở',
    MATCHED: 'Đã kết nối',
    CLOSED: 'Đã đóng',
    CANCELLED: 'Đã hủy',
    REJECTED: 'Không được duyệt'
  };
  return labels[value.toUpperCase()] ?? statusLabel(value);
}

export function formatStudyingRequestDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function formatStudyingRequestMoney(value: number | null) {
  return value === null ? 'Có thể thương lượng' : `${new Intl.NumberFormat('vi-VN').format(value)} VND`;
}

export function weekdayLabel(dayOfWeek: number) {
  return (
    ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][dayOfWeek - 1] ?? `Day ${dayOfWeek}`
  );
}
