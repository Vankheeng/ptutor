type Props = {
  busy?: boolean;
  onClose: () => void;
  onEdit: () => void;
  onRefund: () => void;
  lesson?: {
    title: string;
    date: string;
    startTime: string;
    endTime: string;
    status: string;
    paidAt: string | null;
  };
};

export function TutorLessonCancellationDialog({ busy = false, onClose, onEdit, onRefund, lesson }: Props) {
  const canRefund = lesson?.paidAt !== null;
  const statusLabel = lesson
    ? lesson.status === 'SCHEDULED'
      ? canRefund
        ? 'Đã lên lịch'
        : 'Chờ thanh toán'
      : lesson.status === 'PENDING_CONFIRMATION'
        ? 'Chờ học viên xác nhận'
        : lesson.status === 'CONFIRMED'
          ? 'Đã xác nhận'
          : lesson.status === 'COMPLETED'
            ? 'Đã hoàn tất'
            : lesson.status === 'CANCELLED'
              ? 'Đã hủy'
              : lesson.status
    : null;
  const dateLabel = lesson
    ? new Date(`${lesson.date}T12:00:00`).toLocaleDateString('vi-VN', {
        weekday: 'long',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      })
    : null;
  return (
    <div
      className="lc-overlay"
      role="presentation"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section className="lc-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="lesson-cancellation-title">
        <h2 id="lesson-cancellation-title">Xác nhận hủy buổi học</h2>
        {lesson && (
          <div className="lc-cancel-dialog-details">
            <strong>{lesson.title}</strong>
            <span>
              {dateLabel} · {lesson.startTime.slice(0, 5)}–{lesson.endTime.slice(0, 5)}
            </span>
            <span>Trạng thái: {statusLabel}</span>
          </div>
        )}
        {canRefund && (
          <p className="lc-cancel-dialog-note">
            Nếu không thể dạy, hãy chọn hoàn tiền cho học viên. Bạn cũng có thể sửa lại lịch thay vì hủy buổi học.
          </p>
        )}
        <div className="lc-modal-actions">
          <button type="button" disabled={busy} onClick={onEdit}>
            Sửa buổi học
          </button>
          <button type="button" className="lc-danger-button" disabled={busy} onClick={onRefund}>
            {canRefund ? 'Hoàn tiền cho học viên' : 'Xác nhận hủy'}
          </button>
          <button type="button" disabled={busy} onClick={onClose}>
            Đóng
          </button>
        </div>
      </section>
    </div>
  );
}
