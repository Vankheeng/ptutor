import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getTutorRefundRequests, type RefundRequest } from '../../services/refundApi';
import { RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import {
  formatClockTime,
  formatLocalDateTime,
  parseClockTime,
  refundRequestTitle
} from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

function awaitingMe(request: RefundRequest) {
  return (
    (request.status === 'PENDING' || request.status === 'IN_REVIEW') &&
    !request.tutorRespondedAt &&
    (parseClockTime(request.tutorResponseDeadline)?.getTime() ?? 0) > Date.now()
  );
}

export function TutorRefundRequestsPage() {
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => getTutorRefundRequests(page, signal), [page]);
  const { data, error, isLoading } = useResource(load);

  return (
    <div className="rf-page">
      <h1>Yêu cầu hoàn tiền</h1>
      <section className="rf-card">
        {isLoading && !data && <div className="rf-state">Đang tải…</div>}
        {error && (
          <div className="rf-alert rf-alert-danger" role="alert">
            {error}
          </div>
        )}
        {data && data.content.length === 0 && <div className="rf-state">Chưa có yêu cầu hoàn tiền nào.</div>}
        {data && data.content.length > 0 && (
          <div className="rf-rows">
            {data.content.map((request) => (
              <Link className="rf-row rf-link-row" key={request.id} to={`/tutor/refund-requests/${request.id}`}>
                <div className="rf-row-main">
                  <strong>
                    {refundRequestTitle(request)} · {request.studentName}
                  </strong>
                  <small>
                    {request.disputedLessons.length} buổi · {formatLocalDateTime(request.createdAt)}
                  </small>
                </div>
                <div className="rf-row-side">
                  {awaitingMe(request) && (
                    <span className="rf-badge rf-badge-pending">
                      Phản hồi trước {formatClockTime(request.tutorResponseDeadline)}
                    </span>
                  )}
                  <RefundStatusBadge status={request.status} />
                </div>
              </Link>
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
