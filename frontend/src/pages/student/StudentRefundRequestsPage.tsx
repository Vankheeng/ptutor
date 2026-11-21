import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getMyRefundRequests } from '../../services/refundApi';
import { getContracts } from '../../services/studentApi';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import { formatLocalDateTime, formatMoney, refundRequestTitle } from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

export function StudentRefundRequestsPage() {
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => getMyRefundRequests(page, signal), [page]);
  const { data, error, isLoading } = useResource(load);
  const loadContracts = useCallback((signal: AbortSignal) => getContracts(undefined, 0, 100, signal), []);
  const contracts = useResource(loadContracts).data?.content;

  return (
    <div className="rf-page">
      <div className="rf-heading">
        <h1>Yêu cầu hoàn tiền</h1>
        <Link className="rf-button" to="/student/refund-requests/new">
          + Tạo yêu cầu hoàn tiền
        </Link>
      </div>
      <section className="rf-card">
        {isLoading && !data && <div className="rf-state">Đang tải…</div>}
        {error && (
          <div className="rf-alert rf-alert-danger" role="alert">
            {error}
          </div>
        )}
        {data && data.content.length === 0 && <div className="rf-state">Bạn chưa gửi yêu cầu hoàn tiền nào.</div>}
        {data && data.content.length > 0 && (
          <div className="rf-rows">
            {data.content.map((request) => (
              <div className="rf-row" key={request.id}>
                <div className="rf-row-main">
                  <strong>
                    <Link className="rf-link-row" to={`/student/refund-requests/${request.id}`}>
                      {refundRequestTitle(request)}
                    </Link>
                    {' · '}
                    <ProfileNameLink
                      role="tutor"
                      profileId={contracts?.find((contract) => contract.id === request.contractId)?.tutorId}
                    >
                      {request.tutorName}
                    </ProfileNameLink>
                  </strong>
                  <small>
                    {request.disputedLessons.length} buổi · {formatLocalDateTime(request.createdAt)}
                  </small>
                </div>
                <div className="rf-row-side">
                  {request.status === 'REFUNDED' && <strong>{formatMoney(request.refundAmount)}</strong>}
                  <RefundStatusBadge status={request.status} />
                </div>
              </div>
            ))}
          </div>
        )}
        {data && data.totalPages > 1 && (
          <div className="rf-actions">
            <button
              className="rf-button small secondary"
              type="button"
              disabled={data.first}
              onClick={() => setPage(page - 1)}
            >
              Trước
            </button>
            <button
              className="rf-button small secondary"
              type="button"
              disabled={data.last}
              onClick={() => setPage(page + 1)}
            >
              Sau
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
