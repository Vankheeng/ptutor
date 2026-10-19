import type {
  AdminStudyingRequestDetail,
  AdminTeachingRequestDetail,
  ReviewKind,
  ReviewStatus,
  Reviewer
} from '../../services/adminRequestReviewApi';
import type { Availability, Subject, TeachingMode } from '../../types/api';
import { formatCurrency } from '../../utils/formatters';
import { formatTime, type StatusMeta } from './adminFormat';

export const reviewStatuses: ReviewStatus[] = ['PENDING_REVIEW', 'OPEN', 'REJECTED'];

export const reviewStatusMeta: Record<ReviewStatus, StatusMeta> = {
  PENDING_REVIEW: { label: 'Chờ duyệt', tone: 'pending' },
  OPEN: { label: 'Đã duyệt', tone: 'success' },
  REJECTED: { label: 'Đã từ chối', tone: 'danger' }
};

export const reviewKindLabels: Record<ReviewKind, string> = {
  teaching: 'Yêu cầu dạy',
  studying: 'Yêu cầu học'
};

export const modeLabels: Record<TeachingMode, string> = {
  ONLINE: 'Trực tuyến',
  OFFLINE: 'Tại nhà'
};

const weekdays = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];

export function formatAvailability({ dayOfWeek, startTime, endTime }: Availability) {
  return `${weekdays[dayOfWeek - 1] ?? `Ngày ${dayOfWeek}`} · ${formatTime(startTime)}–${formatTime(endTime)}`;
}

export function formatPriceRange(min: number | null, max: number | null) {
  if (min === null && max === null) return '—';
  if (min === null || max === null || min === max) return formatCurrency(min ?? max);
  return `${formatCurrency(min)} – ${formatCurrency(max)}`;
}

export function normalizeName(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase();
}

/** Picks the active subject the custom name most likely means: an exact match first, then one it contains. */
export function suggestSubject(customName: string | null, subjects: Subject[]) {
  const name = normalizeName(customName);
  if (!name) return null;
  const active = subjects.filter((subject) => subject.status === 'ACTIVE');
  return (
    active.find((subject) => normalizeName(subject.name) === name) ??
    active.find((subject) => name.includes(normalizeName(subject.name))) ??
    null
  );
}

/** The fields the review dialog shows, shared by teaching and studying requests. */
export interface ReviewItem {
  kind: ReviewKind;
  id: string;
  title: string;
  status: ReviewStatus;
  createdAt: string;
  personLabel: string;
  personName: string;
  personEmail: string;
  personAvatarUrl: string | null;
  /** The subject name typed by the user; set only while the subject is outside the catalogue. */
  customSubject: string | null;
  originalSubject: string | null;
  subjectName: string | null;
  facts: [string, string][];
  description: string | null;
  goals: string | null;
  adminNote: string | null;
  availabilities: Availability[];
  reviewer: Reviewer | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

export function fromTeachingRequest(request: AdminTeachingRequestDetail): ReviewItem {
  const custom = request.subjectId ? null : request.customSubjectName;
  return {
    kind: 'teaching',
    id: request.id,
    title: request.title,
    status: request.status,
    createdAt: request.createdAt,
    personLabel: 'Gia sư',
    personName: request.tutorName,
    personEmail: request.tutorEmail,
    personAvatarUrl: request.tutorAvatarUrl,
    customSubject: custom,
    originalSubject: request.customSubjectName,
    subjectName: request.subjectName,
    facts: [
      ['Môn học', request.subjectName ?? request.customSubjectName ?? '—'],
      ['Lớp', request.grades.map((grade) => grade.name).join(', ') || '—'],
      ['Số học sinh nhận', `${request.quantity} học sinh`],
      ['Học phí mong muốn', formatCurrency(request.expectedPrice)],
      ['Hình thức', modeLabels[request.teachingMode]],
      ['Khu vực', request.districts.map((district) => district.name).join(', ') || '—'],
      ['Địa chỉ', request.detailAddress || '—'],
      ['Lịch dạy', request.preferredSchedule || '—']
    ],
    description: request.description,
    goals: null,
    adminNote: request.note,
    availabilities: request.availabilities,
    reviewer: request.reviewer,
    reviewedAt: request.reviewedAt,
    rejectionReason: request.rejectionReason
  };
}

export function fromStudyingRequest(request: AdminStudyingRequestDetail): ReviewItem {
  // A studying request is reviewed only for a custom subject, which the student types into the note.
  const custom = request.subjectId ? null : request.note;
  return {
    kind: 'studying',
    id: request.id,
    title: request.title,
    status: request.status,
    createdAt: request.createdAt,
    personLabel: 'Học sinh',
    personName: request.studentName,
    personEmail: request.studentEmail,
    personAvatarUrl: request.studentAvatarUrl,
    customSubject: custom,
    originalSubject: request.note,
    subjectName: request.subjectName,
    facts: [
      ['Môn đề xuất', request.note || '—'],
      ['Lớp', request.grade.name],
      ['Học phí mong muốn', formatPriceRange(request.minPrice, request.maxPrice)],
      ['Hình thức', modeLabels[request.learningMode]],
      ['Khu vực', request.district?.name ?? '—'],
      ['Địa chỉ', request.detailAddress || '—'],
      ['Lịch mong muốn', request.preferredSchedule || '—']
    ],
    description: request.description,
    goals: request.learningGoals,
    adminNote: null,
    availabilities: request.availabilities,
    reviewer: request.reviewer,
    reviewedAt: request.reviewedAt,
    rejectionReason: request.rejectionReason
  };
}

const errorMessages: Record<string, string> = {
  TEACHING_REQUEST_ALREADY_REVIEWED: 'Yêu cầu đã được nhân viên khác xử lý.',
  STUDYING_REQUEST_ALREADY_REVIEWED: 'Yêu cầu đã được nhân viên khác xử lý.',
  SUBJECT_ALREADY_EXISTS: 'Môn học này đã có trong danh mục.',
  TEACHING_REQUEST_PAYMENT_NOT_FOUND: 'Không tìm thấy khoản phí đăng bài để hoàn.',
  STUDYING_REQUEST_PAYMENT_NOT_FOUND: 'Không tìm thấy khoản phí đăng bài để hoàn.',
  INVALID_SUBJECT: 'Môn học không tồn tại hoặc đã ngừng dùng.',
  INVALID_SUBJECT_NAME: 'Tên môn bắt buộc và tối đa 100 ký tự.',
  INVALID_REJECTION_REASON: 'Lý do từ chối bắt buộc và tối đa 500 ký tự.'
};

export function reviewErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const message = errorMessages[String(error.code)];
    if (message) return message;
  }
  return error instanceof Error ? error.message : 'Không thể xử lý yêu cầu.';
}
