// Nhãn và định dạng thời gian của yêu cầu hỗ trợ.
// Múi giờ: createdAt do Hibernate ghi theo múi giờ JVM của backend (Asia/Ho_Chi_Minh) → hiển thị nguyên giờ;
// answeredAt, closedAt, assignedAt do service ghi bằng LocalDateTime.now(clock) (UTC) → đổi sang giờ Việt Nam.
import type { SupportSource } from '../assistant/supportSource';
import type { SupportRequestStatus } from '../../types/supportRequest';

export const supportStatusMeta: Record<SupportRequestStatus, { label: string; tone: string }> = {
  OPEN: { label: 'Chờ xử lý', tone: 'pending' },
  IN_PROGRESS: { label: 'Đang xử lý', tone: 'info' },
  ANSWERED: { label: 'Đã trả lời', tone: 'success' },
  CLOSED: { label: 'Đã đóng', tone: 'neutral' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' }
};

export const supportSourceLabels: Record<SupportSource, string> = {
  ASSISTANT_ABSTAIN: 'Trợ lý chưa trả lời được',
  ASSISTANT_PARTIAL: 'Trợ lý trả lời một phần',
  ASSISTANT_FEEDBACK: 'Đánh giá chưa tốt',
  MENU: 'Từ menu trợ lý'
};

const FORMAT: Intl.DateTimeFormatOptions = {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh'
};

/** Thời điểm backend ghi theo múi giờ JVM (Asia/Ho_Chi_Minh): hiển thị nguyên giờ. */
export function formatBackendLocal(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(`${value.slice(0, 19)}+07:00`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('vi-VN', FORMAT);
}

/** Thời điểm backend ghi theo UTC (LocalDateTime.now(clock)): đổi sang giờ Việt Nam. */
export function formatBackendUtc(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(`${value.slice(0, 19)}Z`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('vi-VN', FORMAT);
}
