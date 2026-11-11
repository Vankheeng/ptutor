import type {
  NotificationAudience,
  NotificationCampaign,
  NotificationCampaignStatus,
  NotificationCategory
} from '../../services/adminNotificationApi';
import type { StatusMeta } from './adminFormat';
import { toLocalInputValue } from './accountFormat';

export const TITLE_MIN = 5;
export const TITLE_MAX = 255;
export const CONTENT_MIN = 10;
export const CONTENT_MAX = 5000;
/** The backend refuses schedules sooner than this. */
export const SCHEDULE_LEAD_MS = 5 * 60 * 1000;

export const notificationStatuses: NotificationCampaignStatus[] = [
  'DRAFT',
  'SCHEDULED',
  'PROCESSING',
  'SENT',
  'FAILED',
  'CANCELLED',
  'RETRACTED'
];
export const notificationCategories: NotificationCategory[] = ['MAINTENANCE', 'PROMOTION', 'SERVICE_UPDATE', 'GENERAL'];
export const notificationAudiences: NotificationAudience[] = ['ALL', 'STUDENT', 'TUTOR'];

export const notificationStatusMeta: Record<NotificationCampaignStatus, StatusMeta> = {
  DRAFT: { label: 'Nháp', tone: 'neutral' },
  SCHEDULED: { label: 'Đã lên lịch', tone: 'info' },
  PROCESSING: { label: 'Đang gửi', tone: 'brand' },
  SENT: { label: 'Đã gửi', tone: 'success' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' },
  RETRACTED: { label: 'Đã thu hồi', tone: 'pending' }
};

export const categoryLabels: Record<NotificationCategory, string> = {
  MAINTENANCE: 'Bảo trì',
  PROMOTION: 'Khuyến mãi',
  SERVICE_UPDATE: 'Cập nhật dịch vụ',
  GENERAL: 'Chung'
};

export const audienceLabels: Record<NotificationAudience, string> = {
  ALL: 'Mọi người dùng',
  STUDENT: 'Học viên',
  TUTOR: 'Gia sư'
};

type Status = NotificationCampaignStatus;
const is = (status: Status, ...allowed: Status[]) => allowed.includes(status);

/** Mirrors the status transitions NotificationCampaignService allows. */
export const canEdit = (status: Status) => is(status, 'DRAFT', 'SCHEDULED', 'FAILED', 'SENT');
export const canSend = (status: Status) => is(status, 'DRAFT', 'FAILED');
export const canSchedule = (status: Status) => is(status, 'DRAFT', 'SCHEDULED', 'FAILED');
export const canCancelSchedule = (status: Status) => status === 'SCHEDULED';
export const canCancel = (status: Status) => is(status, 'DRAFT', 'SCHEDULED', 'FAILED');
export const canRetract = (status: Status) => status === 'SENT';

/** The time worth showing in a list row, with what it means. */
export function notificationTime(campaign: NotificationCampaign): { label: string; value: string } {
  if (campaign.status === 'SCHEDULED' && campaign.scheduledAt) return { label: 'Hẹn gửi', value: campaign.scheduledAt };
  if (campaign.sentAt) return { label: 'Đã gửi', value: campaign.sentAt };
  if (campaign.processingStartedAt) return { label: 'Bắt đầu gửi', value: campaign.processingStartedAt };
  return { label: 'Tạo lúc', value: campaign.createdAt };
}

export function readRate(readCount: number, recipientCount: number) {
  return recipientCount > 0 ? Math.round((readCount / recipientCount) * 1000) / 10 : 0;
}

export function inMinutes(minutes: number, now = new Date()) {
  return toLocalInputValue(new Date(now.getTime() + minutes * 60 * 1000));
}

export function tomorrowAt(hour: number, now = new Date()) {
  const date = new Date(now);
  date.setDate(date.getDate() + 1);
  date.setHours(hour, 0, 0, 0);
  return toLocalInputValue(date);
}

const errorMessages: Record<string, string> = {
  NOTIFICATION_CAMPAIGN_NOT_FOUND: 'Không tìm thấy thông báo.',
  INVALID_NOTIFICATION_STATUS_TRANSITION: 'Trạng thái thông báo đã thay đổi. Vui lòng tải lại trang.',
  INVALID_NOTIFICATION_SCHEDULE: 'Thời điểm gửi phải sau hiện tại ít nhất 5 phút.',
  NO_ELIGIBLE_RECIPIENTS: 'Không có người nhận phù hợp với đối tượng đã chọn.',
  NOTIFICATION_IS_PROCESSING: 'Thông báo đang được gửi, chưa thể thao tác.',
  NOTIFICATION_MANAGER_REQUIRED: 'Tài khoản của bạn không có quyền quản lý thông báo.',
  EMPLOYEE_FUNCTION_FORBIDDEN: 'Bạn không có quyền quản lý thông báo.',
  VALIDATION_ERROR: `Tiêu đề cần ${TITLE_MIN}–${TITLE_MAX} ký tự, nội dung cần ${CONTENT_MIN}–${CONTENT_MAX} ký tự.`
};

export function notificationErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = String((error as { code: unknown }).code);
    if (errorMessages[code]) return errorMessages[code];
  }
  return error instanceof Error ? error.message : 'Không thể xử lý thông báo. Vui lòng thử lại.';
}
