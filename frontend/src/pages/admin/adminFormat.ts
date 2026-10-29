import type {
  AccountStatus,
  CertificateStatus,
  ContractStatus,
  LessonStatus,
  PaymentPeriod,
  TeachingMode,
  TutorProfileStatus
} from '../../services/adminTutorApi';

export type BadgeTone = 'pending' | 'success' | 'danger' | 'neutral' | 'info' | 'brand';

export interface StatusMeta {
  label: string;
  tone: BadgeTone;
}

export const profileStatusMeta: Record<TutorProfileStatus, StatusMeta> = {
  PENDING: { label: 'Chờ duyệt', tone: 'pending' },
  VERIFIED: { label: 'Đã duyệt', tone: 'success' },
  REJECTED: { label: 'Đã từ chối', tone: 'danger' }
};

export const accountStatusMeta: Record<AccountStatus, StatusMeta> = {
  ACTIVE: { label: 'Hoạt động', tone: 'success' },
  INACTIVE: { label: 'Không hoạt động', tone: 'neutral' },
  BLOCKED: { label: 'Bị khóa', tone: 'danger' }
};

export const certificateStatusMeta: Record<CertificateStatus, StatusMeta> = {
  PENDING: { label: 'Chờ xác minh', tone: 'pending' },
  VERIFIED: { label: 'Đã xác minh', tone: 'success' },
  REJECTED: { label: 'Đã từ chối', tone: 'danger' },
  EXPIRED: { label: 'Hết hạn', tone: 'neutral' }
};

export const contractStatusMeta: Record<ContractStatus, StatusMeta> = {
  PENDING: { label: 'Chờ ký', tone: 'pending' },
  ACTIVE: { label: 'Đang hiệu lực', tone: 'info' },
  COMPLETED: { label: 'Hoàn thành', tone: 'success' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' }
};

export const lessonStatusMeta: Record<LessonStatus, StatusMeta> = {
  SCHEDULED: { label: 'Đã lên lịch', tone: 'info' },
  PENDING_CONFIRMATION: { label: 'Chờ xác nhận', tone: 'pending' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'brand' },
  COMPLETED: { label: 'Hoàn thành', tone: 'success' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' }
};

export const teachingModeLabels: Record<TeachingMode, string> = {
  ONLINE: 'Trực tuyến',
  OFFLINE: 'Tại nhà'
};

export const paymentPeriodLabels: Record<PaymentPeriod, string> = {
  PER_LESSON: '/ buổi',
  WEEKLY: '/ tuần',
  MONTHLY: '/ tháng',
  PACKAGE: '/ trọn gói'
};

export const genderLabels = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' } as const;

export function fullName(firstName: string | null | undefined, lastName: string | null | undefined, fallback = '—') {
  return [firstName, lastName].filter(Boolean).join(' ').trim() || fallback;
}

export function formatDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('vi-VN');
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatNumber(value: number | null | undefined, digits = 2) {
  return value === null || value === undefined ? '—' : Number(value).toFixed(digits);
}

export function formatTime(value: string | null | undefined) {
  return value ? value.slice(0, 5) : '—';
}

export function isExpired(expiryDate: string | null | undefined) {
  if (!expiryDate) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(`${expiryDate}T00:00:00`) < today;
}

/** Avatars stored in S3 come back as s3:// references that browsers cannot load. */
export function displayableUrl(value: string | null | undefined) {
  return value && /^https?:\/\//i.test(value) ? value : null;
}

export function splitTags(value: string | null | undefined) {
  return (value ?? '')
    .split(/[,;\n]/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}
