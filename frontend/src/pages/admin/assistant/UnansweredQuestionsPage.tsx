import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../../hooks/useResource';
import { addScopeExemplar, getUnanswered, type UnansweredItem } from '../../../services/adminAssistantApi';
import { AdminState } from '../components/AdminUi';
import { formatDateTime } from '../adminFormat';
import { fallbackLabels } from './kbFormat';
import './kb-admin.css';

const feedbackLabels: Record<string, string> = {
  WRONG: 'Thông tin chưa chính xác',
  NOT_HELPFUL: 'Câu trả lời chưa hữu ích',
  OUTDATED: 'Thông tin đã cũ',
  OTHER: 'Lý do khác',
  INCOMPLETE: 'Câu trả lời chưa đầy đủ'
};

function reasonFor(item: UnansweredItem) {
  if (item.rating === -1) return feedbackLabels[item.feedbackReason ?? ''] ?? 'Người dùng đánh giá chưa tốt';
  return fallbackLabels[item.fallbackReason ?? ''] ?? 'Trợ lý chưa có câu trả lời đầy đủ';
}

export function UnansweredQuestionsPage() {
  const [days, setDays] = useState(30);
  const [added, setAdded] = useState<string[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getUnanswered(days, signal), [days]);
  const { data, error, isLoading } = useResource(load);

  const markInScope = async (question: string) => {
    setActionError(null);
    try {
      await addScopeExemplar(question, 'IN');
      setAdded((current) => [...current, question]);
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Không thêm được câu mẫu.');
    }
  };

  return (
    <div className="admin-page kb-unanswered-page">
      <header className="kb-unanswered-header">
        <div>
          <h1 className="admin-title">Câu hỏi cần bổ sung nội dung</h1>
        </div>
        <label className="kb-unanswered-period">
          <span>Thời gian</span>
          <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {[7, 30, 90].map((value) => <option key={value} value={value}>{value} ngày gần đây</option>)}
          </select>
        </label>
      </header>
      {data && (
        <div className="kb-unanswered-summary">
          <strong>{data.items.length}</strong>
          <span>câu hỏi trong {days} ngày gần đây</span>
        </div>
      )}
      {actionError && <div className="admin-alert admin-alert-danger" role="alert">{actionError}</div>}
      {!data ? <div className="admin-card admin-card-pad"><AdminState loading={isLoading} error={error} /></div>
        : data.items.length === 0 ? <div className="admin-card"><AdminState empty="Chưa có câu hỏi cần bổ sung trong khoảng thời gian này." /></div>
        : (
          <section className="kb-unanswered-list" aria-label="Danh sách câu hỏi cần bổ sung">
            {data.items.map((item) => (
              <article className="kb-unanswered-item" key={item.messageId}>
                <div className="kb-unanswered-item-top">
                  <span className={`kb-unanswered-reason ${item.rating === -1 ? 'is-feedback' : ''}`}>
                    {item.rating === -1 ? 'Người dùng đánh giá' : 'Trợ lý chưa trả lời đủ'}
                  </span>
                  <time>{formatDateTime(item.createdAt)}</time>
                </div>
                <h2>{item.question}</h2>
                <p className="kb-unanswered-explanation">{reasonFor(item)}</p>
                {item.rewrittenQuery && item.rewrittenQuery !== item.question && (
                  <p className="kb-unanswered-rewrite">Câu hỏi đã chuẩn hóa: {item.rewrittenQuery}</p>
                )}
                <div className="kb-unanswered-item-footer">
                  <div className="admin-button-row">
                    {item.fallbackReason?.startsWith('OUT_OF_SCOPE') && !added.includes(item.question) && (
                      <button className="admin-button small secondary" type="button" onClick={() => void markInScope(item.question)}>
                        Đánh dấu trong phạm vi
                      </button>
                    )}
                    {added.includes(item.question) && <span className="kb-unanswered-added">Đã thêm câu mẫu</span>}
                    <Link className="admin-button small" to={`/admin/assistant/documents/new?question=${encodeURIComponent(item.question)}`}>
                      Tạo tài liệu hướng dẫn →
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </section>
        )}
    </div>
  );
}
