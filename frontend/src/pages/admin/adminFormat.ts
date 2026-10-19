export type BadgeTone = 'pending' | 'success' | 'danger' | 'neutral' | 'info' | 'brand';

export interface StatusMeta {
  label: string;
  tone: BadgeTone;
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatTime(value: string | null | undefined) {
  return value ? value.slice(0, 5) : '—';
}

/** Avatars stored in S3 come back as s3:// references that browsers cannot load. */
export function displayableUrl(value: string | null | undefined) {
  return value && /^https?:\/\//i.test(value) ? value : null;
}
