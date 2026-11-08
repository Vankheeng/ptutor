import { Link } from 'react-router-dom';
import type { StudentLessonDetail } from './StudentLessonDetailPanel';
import type { Contract } from '../../../services/studentApi';
import { confirmationDeadline, formatDateTimeValue, timeLeft } from '../../refund/refundFormat';

type Props = {
  lesson: StudentLessonDetail;
  contract?: Contract | null;
  held?: boolean;
  busy?: boolean;
  refundHref: string;
  onSelect: () => void;
  onConfirm: () => void;
};

export function StudentLessonCard({ lesson, contract, held = false, busy, refundHref, onSelect, onConfirm }: Props) {
  const waiting = lesson.status === 'PENDING_CONFIRMATION' && !held;
  const deadline = waiting ? confirmationDeadline(lesson.markedTaughtAt) : null;
  const tutorName =
    [contract?.tutorFirstName, contract?.tutorLastName].filter(Boolean).join(' ') || 'Chưa có thông tin';
  const statusLabels = {
    SCHEDULED: 'Đã lên lịch',
    PENDING_CONFIRMATION: 'Chờ xác nhận',
    CONFIRMED: 'Đã xác nhận',
    COMPLETED: 'Đã hoàn tất',
    CANCELLED: 'Đã hủy'
  } as const;
  const tone = {
    SCHEDULED: 'info',
    PENDING_CONFIRMATION: 'pending',
    CONFIRMED: 'success',
    COMPLETED: 'success',
    CANCELLED: 'neutral'
  } as const;

  return (
    <article className={`student-lesson-card ${tone[lesson.status]}`}>
      <button className="student-lesson-card-main" type="button" onClick={onSelect}>
        <span className="student-lesson-card-dot" aria-hidden="true" />
        <span className="student-lesson-card-content">
          <strong>
            {lesson.date} · {lesson.startTime.slice(0, 5)} – {lesson.endTime.slice(0, 5)}
          </strong>
          <b>{lesson.title || contract?.subjectName || 'Buổi học'}</b>
          {lesson.note && <small>▤ Ghi chú: {lesson.note}</small>}
          <small>♙ Gia sư: {tutorName}</small>
        </span>
        <span className={`student-lesson-badge ${tone[lesson.status]}`}>
          {held ? 'Đang yêu cầu hoàn tiền' : statusLabels[lesson.status]}
        </span>
      </button>

      {waiting && deadline && (
        <div className="student-lesson-deadline">
          <span>ⓘ Gia sư đang chờ bạn xác nhận buổi học này.</span>
          <span>
            Hạn xác nhận: {formatDateTimeValue(deadline)} · {timeLeft(deadline)}
          </span>
        </div>
      )}

      <div className="student-lesson-card-actions">
        {waiting ? (
          <>
            <button className="student-lesson-confirm" type="button" disabled={busy} onClick={onConfirm}>
              ✓ Xác nhận đã học
            </button>
            <Link className="student-lesson-outline-danger" to={refundHref}>
              △ Khiếu nại / Hoàn tiền
            </Link>
          </>
        ) : (
          <button className="student-lesson-view" type="button" onClick={onSelect}>
            ◉ Xem chi tiết
          </button>
        )}
      </div>
    </article>
  );
}

export type { StudentLessonDetail };
