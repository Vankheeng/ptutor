import type { Contract } from '../../../services/studentApi';
import { ProfileNameLink } from '../../common-user/ProfileNameLink';
import type { ContractLesson } from '../../../services/refundApi';
import { confirmationDeadline, formatDateTimeValue, timeLeft } from '../../refund/refundFormat';
import { Icon } from '../../ui/Icon';
import { isLessonFinished } from './studentLessonUtils';

type Props = {
  lesson: ContractLesson;
  contract?: Contract | null;
  held?: boolean;
  busy?: boolean;
  onOpen: () => void;
  onConfirm: () => void;
  onRefund: () => void;
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

export function StudentLessonCard({ lesson, contract, held = false, busy, onOpen, onConfirm, onRefund }: Props) {
  const waitingForPayment = !lesson.paidAt;
  const finished = isLessonFinished(lesson);
  const canAct = lesson.status === 'PENDING_CONFIRMATION' && finished && !held;
  const deadline = canAct ? confirmationDeadline(lesson.markedTaughtAt) : null;
  const tutorName =
    [contract?.tutorFirstName, contract?.tutorLastName].filter(Boolean).join(' ') || 'Chưa có thông tin';
  const date = dateParts(lesson.date);
  const paymentLabel = held ? 'Đang xử lý hoàn tiền' : waitingForPayment ? 'Chưa thanh toán' : 'Đã thanh toán';
  const paymentTone = held ? 'pending' : waitingForPayment ? 'waiting-payment' : 'success';

  return (
    <article className={`student-lesson-card ${waitingForPayment ? 'waiting-payment' : tone[lesson.status]}`}>
      <button
        className="student-lesson-card-open"
        type="button"
        aria-label={`Xem chi tiết buổi học ${lesson.title || contract?.subjectName || ''}, ${date.weekday} ${date.day}/${date.monthYear}`}
        onClick={onOpen}
      />
      <div className="student-lesson-card-summary">
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
      </div>

      <div className="student-lesson-card-tutor">
        <span className="student-lesson-card-section-label">
          <Icon name="user" size={11} /> Gia sư
        </span>
        <strong>
          <ProfileNameLink role="tutor" profileId={contract?.tutorId}>
            {tutorName}
          </ProfileNameLink>
        </strong>
      </div>

      <div className="student-lesson-card-state">
        <span className="student-lesson-card-section-label">
          <Icon name="wallet" size={11} /> Tình trạng thanh toán
        </span>
        <span className={`student-lesson-badge ${paymentTone}`}>{paymentLabel}</span>
      </div>

      <div className="student-lesson-card-meeting">
        <span className="student-lesson-card-section-label">
          <Icon name="book" size={11} /> Lớp học trực tuyến
        </span>
        {lesson.meetingLink ? (
          <a href={lesson.meetingLink} target="_blank" rel="noreferrer">
            Truy cập lớp học <span aria-hidden="true">↗</span>
          </a>
        ) : (
          <small>Chưa có đường dẫn</small>
        )}
      </div>

      {(held ||
        (lesson.status === 'PENDING_CONFIRMATION' && !finished) ||
        (lesson.status === 'SCHEDULED' && finished) ||
        deadline) && (
        <div className="student-lesson-card-notes">
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
      )}

      <div className="student-lesson-card-actions">
        {canAct ? (
          <>
            <button className="student-lesson-confirm" type="button" disabled={busy} onClick={onConfirm}>
              ✓ Xác nhận đã học
            </button>
            <button className="student-lesson-outline-danger" type="button" onClick={onRefund} disabled={busy}>
              △ Yêu cầu hoàn tiền
            </button>
          </>
        ) : null}
      </div>
    </article>
  );
}
