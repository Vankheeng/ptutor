import type { RequestStatus } from '../../services/studentApi';
import { dateTimeLabel, moneyLabel, statusLabel as labelOf, weekdayLabel, type StatusKind } from '../../utils/labels';

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

export function statusLabel(value: string, kind?: StatusKind) {
  return labelOf(value, kind);
}

export function formatStudyingRequestDate(value: string) {
  return dateTimeLabel(value);
}

export function formatStudyingRequestMoney(value: number | null) {
  return moneyLabel(value);
}

export { weekdayLabel };
