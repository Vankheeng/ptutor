const weekdayLabels: Record<string, string> = {
  Sunday: 'Chủ nhật',
  Monday: 'Thứ Hai',
  Tuesday: 'Thứ Ba',
  Wednesday: 'Thứ Tư',
  Thursday: 'Thứ Năm',
  Friday: 'Thứ Sáu',
  Saturday: 'Thứ Bảy'
};

export function formatPreferredSchedule(value: string | null | undefined) {
  if (!value) return 'Lịch linh hoạt';
  return value
    .replace(
      /Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday/gi,
      (day) => weekdayLabels[day[0].toUpperCase() + day.slice(1).toLowerCase()] ?? day
    )
    .replace(/\bto\b/gi, 'đến');
}
