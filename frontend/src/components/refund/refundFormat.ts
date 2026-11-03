import type {
  EvidenceInput,
  LessonOutcome,
  LessonStatus,
  RefundBlocker,
  RefundCalculation,
  RefundRequestStatus
} from '../../services/refundApi';

export function refundRequestTitle(request: { subjectName: string | null; gradeName: string | null }) {
  return [request.subjectName, request.gradeName].filter(Boolean).join(' · ') || 'Hợp đồng';
}

export const refundStatusMeta: Record<RefundRequestStatus, { label: string; tone: string }> = {
  PENDING: { label: 'Chờ tiếp nhận', tone: 'pending' },
  IN_REVIEW: { label: 'Đang xử lý', tone: 'info' },
  REFUNDED: { label: 'Đã hoàn tiền', tone: 'success' },
  REJECTED: { label: 'Đã từ chối', tone: 'danger' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' }
};

export const lessonStatusLabels: Record<LessonStatus, string> = {
  SCHEDULED: 'Sắp diễn ra',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã học',
  COMPLETED: 'Đã học',
  CANCELLED: 'Đã huỷ'
};

export const outcomeLabels: Record<LessonOutcome, string> = {
  TAUGHT: 'Đã dạy',
  TUTOR_ABSENT: 'Gia sư không dạy',
  STUDENT_ABSENT: 'Học viên nghỉ'
};

export const outcomeTone: Record<LessonOutcome, string> = {
  TAUGHT: 'success',
  TUTOR_ABSENT: 'danger',
  STUDENT_ABSENT: 'pending'
};

export const blockerLabels: Record<RefundBlocker, string> = {
  TUTOR_RESPONSE_WINDOW_OPEN: 'Chờ gia sư phản hồi',
  CONTRACT_HAS_OPEN_COMPLAINT: 'Hợp đồng còn khiếu nại chưa có kết luận',
  NOTHING_TO_REFUND: 'Không còn khoản học phí nào để hoàn'
};

// Matches app.lesson.auto-completion-grace-days on the backend.
export const CONFIRMATION_GRACE_DAYS = 3;

/**
 * Mirrors RefundCalculator on the backend: each lesson costs a whole-đồng unit price; a missed lesson refunds the
 * tutor-absent share and a skipped lesson the student-absent share, with the rest of its price going to the tutor.
 */
export function computeRefund(calculation: RefundCalculation, outcomes: (LessonOutcome | null | undefined)[]) {
  const unit = calculation.unitPrice;
  const tutorAbsentPart = Math.floor((unit * calculation.tutorAbsentRate) / 100);
  const studentAbsentPart = Math.floor((unit * calculation.studentAbsentRate) / 100);
  const tutorAbsent = outcomes.filter((outcome) => outcome === 'TUTOR_ABSENT').length;
  const studentAbsent = outcomes.filter((outcome) => outcome === 'STUDENT_ABSENT').length;
  const refund = Math.min(
    tutorAbsentPart * tutorAbsent + studentAbsentPart * studentAbsent,
    calculation.refundableAmount
  );
  return { refund, tutorShare: (unit - studentAbsentPart) * studentAbsent };
}

/**
 * Business timestamps (deadlines, markedTaughtAt, reviewedAt) are written with the backend's UTC clock and
 * serialised without an offset, unlike audit fields such as createdAt.
 */
export function parseClockTime(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTimeValue(date: Date | null) {
  return date ? date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

export function formatClockTime(value: string | null | undefined) {
  return formatDateTimeValue(parseClockTime(value));
}

export function formatLocalDateTime(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : formatDateTimeValue(date);
}

export function formatLessonDate(date: string, startTime: string, endTime: string) {
  const day = new Date(`${date}T00:00:00`);
  const label = Number.isNaN(day.getTime())
    ? date
    : day.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
  return `${label} · ${startTime.slice(0, 5)}–${endTime.slice(0, 5)}`;
}

export function confirmationDeadline(markedTaughtAt: string | null | undefined) {
  const marked = parseClockTime(markedTaughtAt);
  return marked ? new Date(marked.getTime() + CONFIRMATION_GRACE_DAYS * 24 * 3600 * 1000) : null;
}

export function timeLeft(deadline: Date | null, now = new Date()) {
  if (!deadline) return null;
  const hours = Math.floor((deadline.getTime() - now.getTime()) / 3600000);
  if (hours < 0) return 'Đã quá hạn';
  if (hours < 24) return `Còn ${Math.max(hours, 1)} giờ`;
  return `Còn ${Math.floor(hours / 24)} ngày`;
}

export function formatMoney(value: number | null | undefined) {
  return `${Math.round(value ?? 0).toLocaleString('vi-VN')}đ`;
}

export function guessFileType(url: string) {
  const extension = url.split('?')[0].split('.').pop()?.toLowerCase() ?? '';
  const types: Record<string, string> = {
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    webp: 'image/webp',
    gif: 'image/gif',
    pdf: 'application/pdf',
    mp4: 'video/mp4',
    mp3: 'audio/mpeg',
    m4a: 'audio/mp4'
  };
  return types[extension] ?? 'application/octet-stream';
}

export function evidenceName(evidence: EvidenceInput) {
  try {
    const path = new URL(evidence.fileUrl).pathname;
    return decodeURIComponent(path.split('/').filter(Boolean).pop() ?? evidence.fileUrl);
  } catch {
    return evidence.fileUrl;
  }
}

const errorMessages: Record<string, string> = {
  REFUND_REQUEST_ALREADY_OPEN: 'Hợp đồng này đã có một yêu cầu hoàn tiền đang xử lý.',
  CONTRACT_NOT_REFUNDABLE: 'Hợp đồng chưa có học phí nào đã đóng để hoàn.',
  LESSON_NOT_DISPUTABLE: 'Có buổi học không còn ở trạng thái chờ xác nhận. Vui lòng tải lại trang.',
  INVALID_CLAIMED_OUTCOME: 'Vui lòng chọn nguyên nhân cho từng buổi.',
  TUTOR_RESPONSE_ALREADY_SUBMITTED: 'Bạn đã gửi phản hồi cho yêu cầu này.',
  TUTOR_RESPONSE_NOT_ALLOWED: 'Yêu cầu không còn nhận phản hồi.',
  LESSON_POSITION_REQUIRED: 'Vui lòng phản hồi cho từng buổi bị phản đối.',
  TUTOR_RESPONSE_WINDOW_OPEN: 'Gia sư vẫn còn thời gian phản hồi.',
  CONTRACT_HAS_OPEN_COMPLAINT: 'Hợp đồng còn khiếu nại chưa có kết luận.',
  LESSON_DECISION_REQUIRED: 'Vui lòng kết luận cho từng buổi bị phản đối.',
  REFUND_AMOUNT_CHANGED: 'Số tiền hoàn vừa thay đổi. Dữ liệu đã được tải lại, vui lòng xem lại.',
  NOTHING_TO_REFUND: 'Không có buổi nào cần hoàn. Hãy từ chối yêu cầu nếu mọi buổi đều đã dạy.',
  USER_INACTIVE: 'Tài khoản học viên hoặc gia sư đang bị khoá, chưa thể hoàn tiền.',
  REFUND_REQUEST_REVIEW_FORBIDDEN: 'Chỉ người đang phụ trách mới xử lý được yêu cầu này.',
  REFUND_REQUEST_ALREADY_ASSIGNED: 'Yêu cầu đã được người khác tiếp nhận.',
  INVALID_REFUND_REQUEST_STATUS_TRANSITION: 'Yêu cầu đã được xử lý. Vui lòng tải lại trang.',
  LESSON_COMPLETION_ON_HOLD: 'Buổi học đang bị tạm giữ, chưa thể xác nhận.'
};

export function refundErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = String((error as { code: unknown }).code);
    if (errorMessages[code]) return errorMessages[code];
  }
  return error instanceof Error ? error.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}
