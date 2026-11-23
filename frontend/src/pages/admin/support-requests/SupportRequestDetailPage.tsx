// Chi tiết yêu cầu hỗ trợ cho nhân viên: câu hỏi, đoạn hội thoại (nếu người dùng đồng ý đính kèm), người gửi,
// yêu cầu trước; nhận xử lý / trả lại hàng chờ / trả lời (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5, 14.6).
// Mọi văn bản người viết hiển thị nguyên văn (không render HTML) — 14 mục 14.8.
import { useCallback, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { SupportStatusBadge } from '../../../components/support/SupportStatusBadge';
import { formatBackendLocal, formatBackendUtc, supportSourceLabels } from '../../../components/support/supportFormat';
import { useResource } from '../../../hooks/useResource';
import {
  answerSupportRequest,
  assignSupportRequest,
  getSupportRequestForStaff,
  releaseSupportRequest
} from '../../../services/supportRequestApi';
import '../../../components/refund/refund.css';
import '../../../components/support/support.css';

const MAX_ANSWER = 4000;

export function SupportRequestDetailPage() {
  const { requestId = '' } = useParams();
  const [refreshKey, setRefreshKey] = useState(0);
  const [answer, setAnswer] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getSupportRequestForStaff(requestId, signal), [requestId]);
  const { data, error, isLoading } = useResource(load, refreshKey);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      setRefreshKey((value) => value + 1);
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Không thực hiện được.');
    } finally {
      setBusy(false);
    }
  };

  const submitAnswer = (event: FormEvent) => {
    event.preventDefault();
    if (!answer.trim()) return;
    void run(async () => {
      await answerSupportRequest(requestId, answer.trim());
      setAnswer('');
    });
  };

  return (
    <div className="rf-page">
      <Link to="/admin/support-requests">← Hàng chờ</Link>
      {isLoading && !data && <div className="rf-state">Đang tải…</div>}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}
      {data && (
        <>
          <h1>
            {data.code} <SupportStatusBadge status={data.status} />
          </h1>
          <section className="rf-card">
            <h2>Câu hỏi</h2>
            <p className="support-text">{data.question}</p>
            <dl className="support-meta">
              <dt>Người gửi</dt>
              <dd>
                {data.requester.fullName} ({data.userRole === 'TUTOR' ? 'Gia sư' : 'Học viên'}) · {data.requester.email}
              </dd>
              <dt>Nguồn</dt>
              <dd>{supportSourceLabels[data.source]}</dd>
              {data.pagePath && (
                <>
                  <dt>Trang</dt>
                  <dd>{data.pagePath}</dd>
                </>
              )}
              <dt>Gửi lúc</dt>
              <dd>{formatBackendLocal(data.createdAt)}</dd>
              {data.assignedEmployeeName && (
                <>
                  <dt>Người xử lý</dt>
                  <dd>{data.assignedEmployeeName}</dd>
                </>
              )}
            </dl>
          </section>

          {data.conversationExcerpt && data.conversationExcerpt.length > 0 && (
            <section className="rf-card">
              <h2>Đoạn hội thoại với trợ lý</h2>
              <div className="support-excerpt">
                {data.conversationExcerpt.map((turn, index) => (
                  <div key={index} className={`support-excerpt-turn ${turn.role}`}>
                    <small>{turn.role === 'user' ? 'Người dùng' : 'Trợ lý'}</small>
                    <p className="support-text">{turn.content}</p>
                  </div>
                ))}
              </div>
            </section>
          )}

          {data.answer && (
            <section className="rf-card">
              <h2>Câu trả lời</h2>
              <p className="support-text">{data.answer}</p>
              <dl className="support-meta">
                <dt>Trả lời lúc</dt>
                <dd>{formatBackendUtc(data.answeredAt)}</dd>
              </dl>
            </section>
          )}

          {actionError && (
            <div className="rf-alert rf-alert-danger" role="alert">
              {actionError}
            </div>
          )}

          {data.status === 'OPEN' && (
            <div className="rf-actions">
              <button
                className="rf-button"
                type="button"
                disabled={busy}
                onClick={() => run(() => assignSupportRequest(requestId))}
              >
                Nhận xử lý
              </button>
            </div>
          )}

          {data.status === 'IN_PROGRESS' && data.assignedToMe && (
            <section className="rf-card">
              <form className="support-answer-form" onSubmit={submitAnswer}>
                <label htmlFor="support-answer">Trả lời</label>
                <textarea
                  id="support-answer"
                  rows={6}
                  maxLength={MAX_ANSWER}
                  value={answer}
                  onChange={(event) => setAnswer(event.target.value)}
                />
                <div className="rf-actions">
                  <button
                    className="rf-button secondary"
                    type="button"
                    disabled={busy}
                    onClick={() => run(() => releaseSupportRequest(requestId))}
                  >
                    Trả lại hàng chờ
                  </button>
                  <button className="rf-button" type="submit" disabled={busy || !answer.trim()}>
                    Gửi trả lời
                  </button>
                </div>
              </form>
            </section>
          )}

          {data.history.length > 0 && (
            <section className="rf-card">
              <h2>Yêu cầu khác của người này</h2>
              <div className="rf-rows">
                {data.history.map((item) => (
                  <Link className="rf-row rf-link-row" key={item.id} to={`/admin/support-requests/${item.id}`}>
                    <div className="rf-row-main">
                      <strong>
                        {item.code}
                        {item.id === data.previousRequestId ? ' · yêu cầu trước' : ''}
                      </strong>
                      <small>{formatBackendLocal(item.createdAt)}</small>
                    </div>
                    <div className="rf-row-side">
                      <SupportStatusBadge status={item.status} />
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
