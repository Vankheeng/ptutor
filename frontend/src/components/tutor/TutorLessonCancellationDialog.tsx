type Props = {
  busy?: boolean;
  onClose: () => void;
  onEdit: () => void;
  onRefund: () => void;
};

export function TutorLessonCancellationDialog({ busy = false, onClose, onEdit, onRefund }: Props) {
  return (
    <div
      className="lc-overlay"
      role="presentation"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section className="lc-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="lesson-cancellation-title">
        <h2 id="lesson-cancellation-title">Xác nhận hủy buổi học</h2>
        <p className="lc-cancel-dialog-note">
          Nếu không thể dạy, hãy chọn hoàn tiền cho học viên. Bạn cũng có thể sửa lại lịch thay vì hủy buổi học.
        </p>
        <div className="lc-modal-actions">
          <button type="button" disabled={busy} onClick={onEdit}>
            Sửa buổi học
          </button>
          <button type="button" className="lc-danger-button" disabled={busy} onClick={onRefund}>
            Hoàn tiền cho học viên
          </button>
          <button type="button" disabled={busy} onClick={onClose}>
            Đóng
          </button>
        </div>
      </section>
    </div>
  );
}
