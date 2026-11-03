// 👍 / 👎 cho một câu trả lời. 👎 → hỏi lý do + ghi chú tuỳ chọn (docs/support-chatbot/08 mục 8.1).
import { useState } from 'react';
import { sendFeedback } from '../../services/assistantApi';
import type { FeedbackReason } from '../../types/assistant';

const REASONS: { value: FeedbackReason; label: string }[] = [
  { value: 'WRONG', label: 'Sai' },
  { value: 'NOT_HELPFUL', label: 'Không hữu ích' },
  { value: 'OUTDATED', label: 'Đã cũ' },
  { value: 'OTHER', label: 'Khác' }
];

interface Props {
  messageId: string;
  rating: 1 | -1 | null | undefined;
  onRated: (rating: 1 | -1) => void;
  onNegative: () => void;
}

export function FeedbackButtons({ messageId, rating, onRated, onNegative }: Props) {
  const [askReason, setAskReason] = useState(false);
  const [reason, setReason] = useState<FeedbackReason>('WRONG');
  const [comment, setComment] = useState('');

  const rate = async (value: 1 | -1, withReason?: FeedbackReason) => {
    try {
      await sendFeedback(messageId, value, withReason, value === -1 ? comment.trim() || undefined : undefined);
      onRated(value);
      if (value === -1) onNegative();
    } catch {
      // Đánh giá hỏng không ảnh hưởng cuộc trò chuyện; người dùng bấm lại được.
    }
  };

  return (
    <div className="assistant-feedback">
      <button
        type="button"
        aria-label="Hữu ích"
        aria-pressed={rating === 1}
        className={rating === 1 ? 'active' : ''}
        onClick={() => void rate(1)}
      >
        👍
      </button>
      <button
        type="button"
        aria-label="Chưa hữu ích"
        aria-pressed={rating === -1}
        className={rating === -1 ? 'active' : ''}
        onClick={() => setAskReason(true)}
      >
        👎
      </button>
      {askReason && (
        <form
          className="assistant-feedback-form"
          onSubmit={(event) => {
            event.preventDefault();
            setAskReason(false);
            void rate(-1, reason);
          }}
        >
          <select value={reason} onChange={(event) => setReason(event.target.value as FeedbackReason)}>
            {REASONS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
          <input
            value={comment}
            maxLength={500}
            placeholder="Ghi chú (không bắt buộc)"
            onChange={(event) => setComment(event.target.value)}
          />
          <button type="submit">Gửi</button>
        </form>
      )}
    </div>
  );
}
