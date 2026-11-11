import { Link } from 'react-router-dom';
import type { Contract } from '../../../services/studentApi';
import type { LessonStatus } from '../../../services/refundApi';
import { confirmationDeadline, formatDateTimeValue, timeLeft } from '../../refund/refundFormat';

export type StudentLessonDetail = {
  id: string;
  contractId: string;
  title: string | null;
  date: string;
  startTime: string;
  endTime: string;
  status: LessonStatus;
  note: string | null;
  markedTaughtAt?: string | null;
  disputed?: boolean;
  refundRequestId?: string | null;
};

const statusLabels: Record<LessonStatus, string> = {
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

const statusTone: Record<LessonStatus, string> = {
  SCHEDULED: 'info',
  PENDING_CONFIRMATION: 'pending',
  CONFIRMED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'neutral'
};

type Props = {
  lesson: StudentLessonDetail | null;
  contract?: Contract | null;
  busy?: boolean;
  onConfirm?: () => void;
  onClose?: () => void;
  dialog?: boolean;
};

export function StudentLessonDetailPanel({ lesson, contract, busy, onConfirm, onClose, dialog = false }: Props) {
  if (!lesson) {
    return (
      <aside className="student-lesson-detail student-lesson-detail-empty">
        <div className="student-lesson-detail-icon" aria-hidden="true">
          ◫
        </div>
        <h2>Chi tiết buổi học</h2>
        <p>Chọn một buổi học trên lịch để xem thông tin chi tiết.</p>
      </aside>
    );
  }

  const tutorName =
    [contract?.tutorFirstName, contract?.tutorLastName].filter(Boolean).join(' ') || 'Chưa có thông tin';
  const heldByRefund = Boolean(lesson.disputed);
  const waiting = lesson.status === 'PENDING_CONFIRMATION' && !heldByRefund;
  const deadline = waiting ? confirmationDeadline(lesson.markedTaughtAt) : null;
  const dateLabel = new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(`${lesson.date}T00:00:00`));

  const panel = (
    <aside className="student-lesson-detail">
      <header className="student-lesson-detail-header">
        <div>
          <span className="student-lesson-kicker">CHI TIẾT BUỔI HỌC</span>
          <h2>{lesson.title || contract?.subjectName || 'Buổi học'}</h2>
        </div>
        {onClose && (
          <button className="student-lesson-close" type="button" aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        )}
      </header>

      {waiting && (
        <div className="student-lesson-alert student-lesson-alert-warning">
          <strong>ⓘ</strong>
          <span>Gia sư đã đánh dấu buổi học là đã dạy. Vui lòng xác nhận nếu buổi học đã diễn ra đúng thực tế.</span>
        </div>
      )}

      <div className="student-lesson-detail-facts">
        <Fact
          label="Hợp đồng"
          value={`${contract?.subjectName || 'Hợp đồng'}${contract?.gradeName ? ` · ${contract.gradeName}` : ''}`}
        />
        <Fact label="Gia sư" value={tutorName} />
        <Fact label="Ngày học" value={dateLabel} />
        <Fact label="Thời gian" value={`${lesson.startTime.slice(0, 5)} – ${lesson.endTime.slice(0, 5)}`} />
        <Fact label="Hình thức học" value={contract?.teachingMode === 'OFFLINE' ? 'Trực tiếp' : 'Trực tuyến'} />
        {contract?.teachingMode === 'OFFLINE' && contract.studentDetailAddress && (
          <Fact label="Địa điểm" value={contract.studentDetailAddress} />
        )}
        <div className="student-lesson-fact">
          <span>Trạng thái</span>
          <strong className={`student-lesson-badge ${statusTone[lesson.status]}`}>
            {heldByRefund ? 'Đang yêu cầu hoàn tiền' : statusLabels[lesson.status]}
          </strong>
        </div>
        {deadline && <Fact label="Hạn xác nhận" value={`${formatDateTimeValue(deadline)} · ${timeLeft(deadline)}`} />}
        {lesson.note && <Fact label="Ghi chú" value={lesson.note} multiline />}
      </div>

      {waiting && (
        <div className="student-lesson-actions">
          <button className="student-lesson-confirm" type="button" disabled={busy} onClick={onConfirm}>
            ✓ Xác nhận đã học
          </button>
          <Link className="student-lesson-refund" to={`/student/contracts/${lesson.contractId}/refund-request`}>
            △ Khiếu nại / Hoàn tiền
          </Link>
        </div>
      )}
      {heldByRefund && lesson.refundRequestId && (
        <Link className="student-lesson-refund" to={`/student/refund-requests/${lesson.refundRequestId}`}>
          Xem yêu cầu hoàn tiền
        </Link>
      )}
      {lesson.status === 'PENDING_CONFIRMATION' && !waiting && !lesson.refundRequestId && (
        <p className="student-lesson-held-note">Buổi học đang được giữ để xử lý yêu cầu hoàn tiền.</p>
      )}
    </aside>
  );

  if (!dialog) return panel;
  return (
    <div
      className="student-lesson-overlay"
      onMouseDown={(event) => event.target === event.currentTarget && onClose?.()}
    >
      <div role="dialog" aria-modal="true" aria-label="Chi tiết buổi học">
        {panel}
      </div>
    </div>
  );
}

function Fact({ label, value, multiline = false }: { label: string; value: string; multiline?: boolean }) {
  return (
    <div className={`student-lesson-fact${multiline ? ' multiline' : ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
