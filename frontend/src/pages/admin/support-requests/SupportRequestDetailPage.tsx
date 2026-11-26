import { useCallback, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { SupportStatusBadge } from '../../../components/support/SupportStatusBadge';
import { formatBackendLocal, formatBackendUtc, supportSourceLabels } from '../../../components/support/supportFormat';
import { useResource } from '../../../hooks/useResource';
import { answerSupportRequest, assignSupportRequest, getSupportRequestForStaff, releaseSupportRequest } from '../../../services/supportRequestApi';
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
    <div className="rf-page support-admin-page support-admin-detail">
      <Link className="support-admin-back" to="/admin/support-requests">← Danh sách yêu cầu</Link>
      {isLoading && !data && <div className="rf-state">Đang tải</div>}
      {error && <div className="rf-alert rf-alert-danger" role="alert">{error}</div>}
      {data && (
        <>
          <header className="support-admin-header support-admin-detail-header">
            <div>
              <div className="support-admin-detail-title"><h1>{data.code}</h1><SupportStatusBadge status={data.status} /></div>
              <p className="support-admin-intro">Gửi lúc {formatBackendLocal(data.createdAt)}</p>
            </div>
            {data.status === 'OPEN' && (
              <button className="rf-button" type="button" disabled={busy} onClick={() => void run(() => assignSupportRequest(requestId))}>Nhận xử lý</button>
            )}
          </header>

          <section className="rf-card support-admin-question-card">
            <div className="support-admin-section-head"><h2>Nội dung cần hỗ trợ</h2></div>
            <p className="support-admin-question-text support-text">{data.question}</p>
            <div className="support-admin-context">
              <span className={`support-admin-source ${data.source === 'MENU' ? 'is-direct' : 'is-assistant'}`}>
                {data.source === 'MENU' ? 'Gửi trực tiếp' : 'Chuyển từ trợ lý'}
              </span>
              <span>{supportSourceLabels[data.source]}</span>
            </div>
          </section>

          <section className="rf-card support-admin-info-card">
            <div className="support-admin-section-head"><h2>Thông tin tiếp nhận</h2></div>
            <dl className="support-admin-facts">
              <div><dt>Người gửi</dt><dd><strong>{data.requester.fullName}</strong><span>{data.userRole === 'TUTOR' ? 'Gia sư' : 'Học viên'}</span><span>{data.requester.email}</span></dd></div>
              <div><dt>Nhân viên phụ trách</dt><dd>{data.assignedEmployeeName ?? 'Chưa phân công'}</dd></div>
              {data.pagePath && <div><dt>Trang gửi yêu cầu</dt><dd>{data.pagePath}</dd></div>}
              {data.assignedAt && <div><dt>Nhận xử lý lúc</dt><dd>{formatBackendUtc(data.assignedAt)}</dd></div>}
            </dl>
          </section>

          {data.conversationExcerpt && data.conversationExcerpt.length > 0 && (
            <section className="rf-card support-admin-conversation-card">
              <div className="support-admin-section-head"><h2>Hội thoại đính kèm</h2></div>
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
            <section className="rf-card support-admin-answer-card">
              <div className="support-admin-section-head"><h2>Câu trả lời đã gửi</h2></div>
              <p className="support-text">{data.answer}</p>
              <small>Trả lời lúc {formatBackendUtc(data.answeredAt)}</small>
            </section>
          )}

          {actionError && <div className="rf-alert rf-alert-danger" role="alert">{actionError}</div>}

          {data.status === 'IN_PROGRESS' && !data.assignedToMe && (
            <div className="support-admin-next-step">Đang xử lý: {data.assignedEmployeeName ?? 'nhân viên khác'}</div>
          )}
          {data.status === 'IN_PROGRESS' && data.assignedToMe && (
            <section className="rf-card support-admin-answer-card">
              <div className="support-admin-section-head"><h2>Trả lời người gửi</h2></div>
              <form className="support-answer-form" onSubmit={submitAnswer}>
                <label htmlFor="support-answer">Nội dung trả lời</label>
                <textarea id="support-answer" rows={5} maxLength={MAX_ANSWER} value={answer}
                  placeholder="Nhập nội dung trả lời"
                  onChange={(event) => setAnswer(event.target.value)} />
                <div className="support-admin-form-footer">
                  <span>{answer.length}/{MAX_ANSWER} ký tự</span>
                  <div className="rf-actions">
                    <button className="rf-button secondary" type="button" disabled={busy} onClick={() => void run(() => releaseSupportRequest(requestId))}>Trả lại hàng chờ</button>
                    <button className="rf-button" type="submit" disabled={busy || !answer.trim()}>Gửi trả lời</button>
                  </div>
                </div>
              </form>
            </section>
          )}

          {data.history.length > 0 && (
            <section className="rf-card support-admin-history">
              <h2>Yêu cầu khác của người gửi</h2>
              <div className="rf-rows">
                {data.history.map((item) => (
                  <Link className="rf-row rf-link-row" key={item.id} to={`/admin/support-requests/${item.id}`}>
                    <div className="rf-row-main"><strong>{item.code}{item.id === data.previousRequestId ? ' (yêu cầu trước)' : ''}</strong><small>{formatBackendLocal(item.createdAt)}</small></div>
                    <div className="rf-row-side"><SupportStatusBadge status={item.status} /></div>
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
