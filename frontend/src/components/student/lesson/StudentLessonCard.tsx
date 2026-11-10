import type { Contract } from '../../../services/studentApi';
import type { ContractLesson } from '../../../services/refundApi';
import { confirmationDeadline, formatDateTimeValue, timeLeft } from '../../refund/refundFormat';
import { isLessonFinished } from './studentLessonUtils';

type Props = {
  lesson: ContractLesson;
  contract?: Contract | null;
  held?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onRefund: () => void;
};

const statusLabels: Record<ContractLesson['status'], string> = {
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã học',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

const tone: Record<ContractLesson['status'], string> = {
  SCHEDULED: 'info',
  PENDING_CONFIRMATION: 'pending',
  CONFIRMED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'neutral'
};

const weekdayLabels = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

function dateParts(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day, 12);
  return {
    weekday: weekdayLabels[date.getDay()] ?? value,
    day: String(day).padStart(2, '0'),
    monthYear: `${String(month).padStart(2, '0')}/${year}`
  };
}

export function StudentLessonCard({ lesson, contract, held = false, busy, onConfirm, onRefund }: Props) {
  const finished = isLessonFinished(lesson);
  const canAct = lesson.status === 'PENDING_CONFIRMATION' && finished && !held;
  const deadline = canAct ? confirmationDeadline(lesson.markedTaughtAt) : null;
  const tutorName =
    [contract?.tutorFirstName, contract?.tutorLastName].filter(Boolean).join(' ') || 'Chưa có thông tin';
  const date = dateParts(lesson.date);

  return (
    <article className={`student-lesson-card ${tone[lesson.status]}`}>
      <div className="student-lesson-card-date" aria-label={`Ngày học ${date.weekday} ${date.day}/${date.monthYear}`}>
        <span>{date.weekday}</span>
        <strong>{date.day}</strong>
        <small>{date.monthYear}</small>
      </div>

      <div className="student-lesson-card-content">
        <strong>
          {lesson.startTime.slice(0, 5)} – {lesson.endTime.slice(0, 5)}
        </strong>
        <b>{lesson.title || contract?.subjectName || 'Buổi học'}</b>
        {lesson.note && <small>▧ {lesson.note}</small>}
      </div>

      <div className="student-lesson-card-tutor">
        <span>♙ Gia sư</span>
        <strong>{tutorName}</strong>
      </div>

      <div className="student-lesson-card-state">
        <span className={`student-lesson-badge ${tone[lesson.status]}`}>
          {held ? 'Đang xử lý hoàn tiền' : statusLabels[lesson.status]}
        </span>
        {lesson.status === 'PENDING_CONFIRMATION' && held && (
          <small>Buổi học đang được giữ để xử lý yêu cầu hoàn tiền.</small>
        )}
        {lesson.status === 'PENDING_CONFIRMATION' && !held && !finished && (
          <small>Chưa đến thời điểm xác nhận buổi học.</small>
        )}
        {lesson.status === 'SCHEDULED' && finished && <small>Đang chờ gia sư xác nhận buổi học.</small>}
        {deadline && (
          <small>
            Hạn xác nhận: {formatDateTimeValue(deadline)} · {timeLeft(deadline)}
          </small>
        )}
      </div>

      <div className="student-lesson-card-actions">
        {canAct ? (
          <>
            <button className="student-lesson-confirm" type="button" disabled={busy} onClick={onConfirm}>
              ✓ Xác nhận đã học
            </button>
            <button className="student-lesson-outline-danger" type="button" onClick={onRefund} disabled={busy}>
              △ Khiếu nại / Hoàn tiền
            </button>
          </>
        ) : null}
      </div>
    </article>
  );
}
