import type { AdminUserAccount, ManagedUserRole, UserAccountStatus } from '../../services/adminUserAccountApi';
import type { StatusMeta } from './adminFormat';

export const REASON_MIN = 10;
export const REASON_MAX = 1000;

export const roleLabels: Record<ManagedUserRole, string> = {
  STUDENT: 'Học viên',
  TUTOR: 'Gia sư'
};

export const accountStatusFilterLabels: Record<UserAccountStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  BLOCKED: 'Đang bị khóa',
  INACTIVE: 'Ngừng hoạt động'
};

export function accountStatusMeta(account: Pick<AdminUserAccount, 'status' | 'suspensionType'>): StatusMeta {
  if (account.status === 'ACTIVE') return { label: 'Hoạt động', tone: 'success' };
  if (account.status === 'INACTIVE') return { label: 'Ngừng hoạt động', tone: 'neutral' };
  return account.suspensionType === 'TEMPORARY'
    ? { label: 'Khóa tạm thời', tone: 'pending' }
    : { label: 'Khóa vĩnh viễn', tone: 'danger' };
}

/** Seeded staff accounts such as the default admin have no first or last name. */
export function actorName(name: string | null | undefined) {
  return name?.trim() || 'Chưa đặt tên';
}

/** The backend records this reason when a temporary suspension expires on its own. */
export function reactivationReasonLabel(reason: string) {
  return reason === 'TEMPORARY_SUSPENSION_EXPIRED' ? 'Hết thời hạn khóa.' : reason;
}

const DAY_MS = 24 * 60 * 60 * 1000;

export const suspensionPresets = [1, 7, 30, 90].map((days) => ({ days, label: `${days} ngày` }));

/** Formats a date as the value of an `<input type="datetime-local">`, in the browser's time zone. */
export function toLocalInputValue(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function daysFromNow(days: number, now = new Date()) {
  return toLocalInputValue(new Date(now.getTime() + days * DAY_MS));
}

export function daysLeft(until: string | null | undefined, now = new Date()) {
  if (!until) return null;
  const end = new Date(until).getTime();
  if (Number.isNaN(end) || end <= now.getTime()) return null;
  return Math.ceil((end - now.getTime()) / DAY_MS);
}

const errorMessages: Record<string, string> = {
  USER_NOT_FOUND: 'Không tìm thấy tài khoản.',
  ACCOUNT_TARGET_NOT_SUPPORTED: 'Chỉ quản lý được tài khoản học viên hoặc gia sư.',
  ACCOUNT_ALREADY_SUSPENDED: 'Tài khoản đã bị khóa.',
  ACCOUNT_NOT_ACTIVE: 'Chỉ khóa được tài khoản đang hoạt động.',
  ACCOUNT_NOT_SUSPENDED: 'Tài khoản không còn bị khóa.',
  INVALID_SUSPENSION_PERIOD: 'Thời điểm mở khóa phải sau hiện tại.',
  INVALID_REACTIVATION_REASON: `Lý do cần từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`,
  SELF_SUSPENSION_NOT_ALLOWED: 'Không thể tự khóa tài khoản của mình.',
  INVALID_SUSPENSION_ACTOR: 'Tài khoản của bạn không có quyền khóa tài khoản.',
  INVALID_REACTIVATION_ACTOR: 'Tài khoản của bạn không có quyền mở khóa tài khoản.',
  EMPLOYEE_FUNCTION_FORBIDDEN: 'Bạn không có quyền quản lý tài khoản người dùng.'
};

export function accountErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = String((error as { code: unknown }).code);
    if (errorMessages[code]) return errorMessages[code];
  }
  return error instanceof Error ? error.message : 'Không thể cập nhật tài khoản. Vui lòng thử lại.';
}
