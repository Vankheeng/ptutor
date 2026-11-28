import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import type { Contract } from '../../services/studentApi';

const MAX_COMMENT_LENGTH = 2000;
const ratingLabels = ['', 'Rất không hài lòng', 'Không hài lòng', 'Bình thường', 'Hài lòng', 'Rất hài lòng'];

interface Props {
  contract: Contract;
  busy: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (rating: number, comment: string) => void;
}

export function StudentTutorReviewDialog({ contract, busy, error, onClose, onSubmit }: Props) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const dialogRef = useRef<HTMLElement>(null);
  const starRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'gia sư của bạn';
  const contractCode = `HD-${contract.id.slice(0, 8).toUpperCase()}`;

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    starRefs.current[0]?.focus();

    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled)')
      );
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [busy, onClose]);

  const selectRating = (value: number) => {
    setRating(value);
    starRefs.current[value - 1]?.focus();
  };

  const handleStarKeyDown = (event: KeyboardEvent<HTMLButtonElement>, value: number) => {
    let next: number;
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next = Math.min(5, value + 1);
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next = Math.max(1, value - 1);
    else if (event.key === 'Home') next = 1;
    else if (event.key === 'End') next = 5;
    else return;
    event.preventDefault();
    selectRating(next);
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!rating || busy) return;
    onSubmit(rating, comment);
  };

  return (
    <div
      className="student-review-dialog-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <section
        ref={dialogRef}
        className="student-review-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="student-review-dialog-title"
        aria-describedby="student-review-dialog-description"
      >
        <form onSubmit={submit}>
          <header className="student-review-dialog-header">
            <span className="student-review-dialog-heading-icon" aria-hidden="true">
              ★
            </span>
            <div>
              <h2 id="student-review-dialog-title">Đánh giá gia sư</h2>
              <p id="student-review-dialog-description">Chia sẻ trải nghiệm học với {tutorName}.</p>
            </div>
            <button
              className="student-review-dialog-close"
              type="button"
              aria-label="Đóng hộp thoại đánh giá"
              disabled={busy}
              onClick={onClose}
            >
              ×
            </button>
          </header>

          <div className="student-review-dialog-contract">
            <span>{contract.subjectName || 'Môn học'}</span>
            <span aria-hidden="true">·</span>
            <span>{contract.gradeName || 'Chưa có lớp'}</span>
            <span aria-hidden="true">/</span>
            <span>Hợp đồng {contractCode}</span>
          </div>

          <div className="student-review-dialog-body">
            <fieldset className="student-review-rating-fieldset">
              <legend>Mức độ hài lòng *</legend>
              <div className="student-review-stars" role="radiogroup" aria-label="Mức độ hài lòng từ 1 đến 5 sao">
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    key={value}
                    ref={(element) => {
                      starRefs.current[value - 1] = element;
                    }}
                    className={value <= rating ? 'selected' : ''}
                    type="button"
                    role="radio"
                    aria-checked={rating === value}
                    aria-label={`${value} trên 5 sao, ${ratingLabels[value]}`}
                    tabIndex={rating === value || (!rating && value === 1) ? 0 : -1}
                    disabled={busy}
                    onClick={() => selectRating(value)}
                    onKeyDown={(event) => handleStarKeyDown(event, value)}
                  >
                    <span aria-hidden="true">{value <= rating ? '★' : '☆'}</span>
                  </button>
                ))}
              </div>
              <p className={rating ? 'selected' : ''} aria-live="polite">
                {rating ? `${rating}/5 · ${ratingLabels[rating]}` : 'Chưa chọn'}
              </p>
            </fieldset>

            <label className="student-review-comment-field">
              <span>Nhận xét (không bắt buộc)</span>
              <span className="student-review-comment-control">
                <textarea
                  value={comment}
                  maxLength={MAX_COMMENT_LENGTH}
                  disabled={busy}
                  placeholder="Chia sẻ cảm nhận của bạn về buổi học..."
                  onChange={(event) => setComment(event.target.value)}
                />
                <small>{new Intl.NumberFormat('vi-VN').format(comment.length)} / 2.000</small>
              </span>
            </label>

            {error && (
              <div className="student-review-dialog-error" role="alert">
                {error}
              </div>
            )}
          </div>

          <footer className="student-review-dialog-footer">
            <button className="student-button secondary" type="button" disabled={busy} onClick={onClose}>
              Hủy
            </button>
            <button className="student-button primary" type="submit" disabled={!rating || busy}>
              {busy ? 'Đang gửi…' : 'Gửi đánh giá'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
