import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { cancelRefundRequest, getMyRefundRequest } from '../../services/refundApi';
import { RefundRequestOverview, RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import { formatLocalDateTime, refundErrorMessage } from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

export function StudentRefundRequestDetailPage() {
  const { refundRequestId = '' } = useParams();
  const location = useLocation();
  const created = Boolean((location.state as { created?: boolean } | null)?.created);
  const [revision, setRevision] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(created ? 'Đã gửi yêu cầu hoàn tiền.' : null);
  const [busy, setBusy] = useState(false);

  const load = useCallback((signal: AbortSignal) => getMyRefundRequest(refundRequestId, signal), [refundRequestId]);
  const { data, error, isLoading } = useResource(load, revision);

  const cancel = async () => {
    setBusy(true);
    setFailure(null);
    try {
      await cancelRefundRequest(refundRequestId);
      setNotice('Đã huỷ yêu cầu hoàn tiền.');
      setRevision((value) => value + 1);
    } catch (cancelError) {
      setFailure(refundErrorMessage(cancelError));
    } finally {
      setBusy(false);
    }
  };

  const reviewed = data && (data.status === 'IN_REVIEW' || data.status === 'REFUNDED' || data.status === 'REJECTED');
  const decided = data && (data.status === 'REFUNDED' || data.status === 'REJECTED');

  return (
    <div className="rf-page">
      <Link className="rf-back" to="/student/refund-requests">
        ‹ Yêu cầu hoàn tiền
      </Link>
      {notice && (
        <div className="rf-alert rf-alert-success" role="status">
          {notice}
        </div>
      )}
      {failure && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {failure}
        </div>
      )}
      {isLoading && !data && <div className="rf-state">Đang tải…</div>}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}
      {data && (
        <>
          <div className="rf-heading">
            <div className="rf-heading-title">
              <h1>Yêu cầu hoàn tiền</h1>
              <RefundStatusBadge status={data.status} />
            </div>
            {data.status === 'PENDING' && (
              <button className="rf-button danger-outline" type="button" disabled={busy} onClick={() => void cancel()}>
                Huỷ yêu cầu
              </button>
            )}
          </div>
          <div className="rf-columns">
            <div className="rf-main">
              <RefundRequestOverview request={data} />
            </div>
            {data.status !== 'CANCELLED' && (
              <aside className="rf-side rf-card" aria-labelledby="rf-progress-title">
                <h2 id="rf-progress-title">Tiến trình</h2>
                <div className="rf-steps">
                  <div className="rf-step done">
                    <span className="rf-step-dot">1</span>
                    <span>
                      <strong>Đã gửi yêu cầu</strong>
                      <br />
                      <small className="rf-kicker">{formatLocalDateTime(data.createdAt)}</small>
                    </span>
                  </div>
                  <div className={`rf-step${reviewed ? ' done' : ''}`}>
                    <span className="rf-step-dot">2</span>
                    <strong>Kế toán xem xét</strong>
                  </div>
                  <div className={`rf-step${decided ? ' done' : ''}`}>
                    <span className="rf-step-dot">3</span>
                    <strong>Hoàn vào ví hoặc từ chối</strong>
                  </div>
                </div>
              </aside>
            )}
          </div>
        </>
      )}
    </div>
  );
}
