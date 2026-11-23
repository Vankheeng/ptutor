// Hộp thoại "Gửi câu hỏi cho nhân viên" (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.2).
// Người dùng tự bấm gửi — trợ lý không có công cụ tạo yêu cầu (QĐ7). Đính kèm hội thoại mặc định KHÔNG chọn
// (tối thiểu hoá dữ liệu); chú thích nói rõ đoạn đính kèm được lưu cùng yêu cầu kể cả khi xoá cuộc trò chuyện (14.8).
import { useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { createSupportRequest } from '../../services/supportRequestApi';
import type { SupportRequest } from '../../types/supportRequest';
import type { SupportDraft } from './AssistantWidget';
import '../support/support.css';

const MAX_CHARS = 2000;

interface Props {
  draft: SupportDraft;
  basePath: '/student' | '/tutor';
  onClose: () => void;
}

export function SupportRequestDialog({ draft, basePath, onClose }: Props) {
  const location = useLocation();
  const [question, setQuestion] = useState(draft.question.slice(0, MAX_CHARS));
  const canAttach = Boolean(draft.conversationId && draft.messageId);
  const [attach, setAttach] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<SupportRequest | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim() || sending) return;
    setSending(true);
    setError(null);
    try {
      setCreated(
        await createSupportRequest({
          question: question.trim(),
          conversationId: draft.conversationId,
          messageId: draft.messageId ?? null,
          attachConversation: canAttach && attach,
          source: draft.source,
          pagePath: location.pathname
        })
      );
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không gửi được yêu cầu.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="support-dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="support-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Gửi câu hỏi cho nhân viên"
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <strong>Gửi câu hỏi cho nhân viên</strong>
          <button type="button" aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </header>
        {created ? (
          <div className="support-dialog-done" role="status">
            <p>
              Đã gửi yêu cầu <strong>{created.code}</strong>.
            </p>
            <Link to={`${basePath}/support-requests/${created.id}`} onClick={onClose}>
              Xem yêu cầu
            </Link>
          </div>
        ) : (
          <form onSubmit={submit}>
            <label htmlFor="support-question">Câu hỏi</label>
            <textarea
              id="support-question"
              value={question}
              maxLength={MAX_CHARS}
              rows={5}
              onChange={(event) => setQuestion(event.target.value)}
            />
            <small className="support-dialog-count">
              {question.length}/{MAX_CHARS}
            </small>
            {canAttach && (
              <label className="support-dialog-attach">
                <input type="checkbox" checked={attach} onChange={(event) => setAttach(event.target.checked)} />
                <span>
                  Đính kèm đoạn hội thoại
                  <small>Đoạn đính kèm được lưu cùng yêu cầu, kể cả khi bạn xóa cuộc trò chuyện.</small>
                </span>
              </label>
            )}
            {error && (
              <div className="support-dialog-error" role="alert">
                {error}
              </div>
            )}
            <button type="submit" disabled={sending || !question.trim()}>
              {sending ? 'Đang gửi…' : 'Gửi'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
