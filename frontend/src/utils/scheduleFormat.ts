import type { ContractSchedule } from '../services/studentApi';

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
/** Read older contracts that stored lesson times only as a display string. */
export function parsePreferredSchedules(value: string | null | undefined): ContractSchedule[] {
  if (!value) return [];
  const normalized = value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
  const days = [
    ...normalized.matchAll(
      /\b(?:thu\s*(?:[2-7]|hai|ba|tu|nam|sau|bay)|t[2-7]|chu\s*nhat|cn|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b/g
    )
  ];
  const times = [...normalized.matchAll(/(\d{1,2}:\d{2})\s*(?:-|–|—|to)\s*(\d{1,2}:\d{2})/g)];
  const result: ContractSchedule[] = [];
  let previousTimeEnd = 0;
  for (const time of times) {
    const startTime = normalizeClockTime(time[1]);
    const endTime = normalizeClockTime(time[2]);
    if (!startTime || !endTime || endTime <= startTime) continue;
    for (const day of days) {
      if (day.index === undefined || day.index < previousTimeEnd || day.index >= time.index!) continue;
      const dayOfWeek = weekdayNumber(day[0]);
      if (dayOfWeek) result.push({ dayOfWeek, startTime, endTime });
    }
    previousTimeEnd = time.index! + time[0].length;
  }
  return result;
}

function weekdayNumber(raw: string) {
  const value = raw.replace(/\s+/g, ' ').trim();
  const numeric = value.match(/^(?:thu\s*|t)([2-7])$/);
  if (numeric) return Number(numeric[1]) - 1;
  const names: Record<string, number> = {
    'thu hai': 1,
    'thu ba': 2,
    'thu tu': 3,
    'thu nam': 4,
    'thu sau': 5,
    'thu bay': 6,
    'chu nhat': 7,
    cn: 7,
    monday: 1,
    tuesday: 2,
    wednesday: 3,
    thursday: 4,
    friday: 5,
    saturday: 6,
    sunday: 7
  };
  return names[value] ?? 0;
}

function normalizeClockTime(value: string) {
  const [hour, minute] = value.split(':').map(Number);
  if (hour > 23 || minute > 59) return '';
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}
