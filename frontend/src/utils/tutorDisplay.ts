const weekdayLabels: Record<string, string> = {
  Sunday: 'Chủ nhật',
  Monday: 'Thứ Hai',
  Tuesday: 'Thứ Ba',
  Wednesday: 'Thứ Tư',
  Thursday: 'Thứ Năm',
  Friday: 'Thứ Sáu',
  Saturday: 'Thứ Bảy'
};

const statusLabels: Record<string, string> = {
  PENDING: 'Đang chờ',
  PENDING_REVIEW: 'Chờ duyệt',
  ACCEPTED: 'Đã chấp nhận',
  VERIFIED: 'Đã xác minh',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Đã hết hạn',
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  BLOCKED: 'Đã khóa',
  ALL: 'Tất cả'
};

export function tutorStatusLabel(status: string) {
  return statusLabels[status] ?? status;
}

export function tutorScheduleLabel(schedule?: string | null, fallback = 'Linh hoạt') {
  if (!schedule?.trim()) return fallback;
  return schedule
    .replace(/\bWeekday evenings\b/gi, 'Các buổi tối trong tuần')
    .replace(/\bWeekday mornings\b/gi, 'Các buổi sáng trong tuần')
    .replace(/\bWeekends?\b/gi, 'Cuối tuần')
    .replace(/\bWeekdays?\b/gi, 'Các ngày trong tuần')
    .replace(/\bFlexible schedule\b/gi, 'Lịch linh hoạt')
    .replace(/\bFlexible\b/gi, 'Linh hoạt')
    .replace(
      /\b(Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday)\b/gi,
      (day) => weekdayLabels[day[0].toUpperCase() + day.slice(1).toLowerCase()] ?? day
    );
}
