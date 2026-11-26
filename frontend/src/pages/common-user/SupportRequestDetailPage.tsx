// Chi tiết một yêu cầu hỗ trợ của người dùng: câu hỏi, câu trả lời, hủy (khi OPEN) / đã giải quyết (khi ANSWERED).
// Câu hỏi và câu trả lời là văn bản người viết → hiển thị nguyên văn, không render HTML (14 mục 14.8).
import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { SupportStatusBadge } from '../../components/support/SupportStatusBadge';
import { formatBackendLocal, formatBackendUtc } from '../../components/support/supportFormat';
import { useResource } from '../../hooks/useResource';
import { cancelSupportRequest, closeSupportRequest, getMySupportRequest } from '../../services/supportRequestApi';
import '../../components/refund/refund.css';
import '../../components/support/support.css';

export function SupportRequestDetailPage() {
  const { requestId = '' } = useParams();
  const { user } = useAuth();
  const basePath = user?.role === 'TUTOR' ? '/tutor' : '/student';
  const [refreshKey, setRefreshKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getMySupportRequest(requestId, signal), [requestId]);
  const { data, error, isLoading } = useResource(load, refreshKey);

  const run = async (action: (id: string) => Promise<unknown>) => {
    setBusy(true);
    setActionError(null);
    try {
      await action(requestId);
      setRefreshKey((value) => value + 1);
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Không thực hiện được.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rf-page">
      <Link to={`${basePath}/support-requests`}>← Yêu cầu hỗ trợ</Link>
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
              <dt>Gửi lúc</dt>
              <dd>{formatBackendLocal(data.createdAt)}</dd>
              {data.previousRequestId && (
                <>
                  <dt>Yêu cầu trước</dt>
                  <dd>
                    <Link to={`${basePath}/support-requests/${data.previousRequestId}`}>
                      {data.previousRequestCode}
                    </Link>
                  </dd>
                </>
              )}
            </dl>
          </section>
          {data.answer && (
            <section className="rf-card">
              <h2>Trả lời của nhân viên</h2>
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
          <div className="rf-actions">
            {data.status === 'OPEN' && (
              <button
                className="rf-button danger-outline"
                type="button"
                disabled={busy}
                onClick={() => run(cancelSupportRequest)}
              >
                Hủy yêu cầu
              </button>
            )}
            {data.status === 'ANSWERED' && (
              <button className="rf-button" type="button" disabled={busy} onClick={() => run(closeSupportRequest)}>
                Đã giải quyết
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
