import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getTutorRefundRequests, type RefundRequest, type RefundRequestStatus } from '../../services/refundApi';
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
  const [status, setStatus] = useState<'ALL' | RefundRequestStatus>('ALL');
  const load = useCallback(
    (signal: AbortSignal) => getTutorRefundRequests(page, signal, status === 'ALL' ? undefined : status),
    [page, status]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <div className="rf-page">
      <h1>Yêu cầu hoàn tiền</h1>
      <div className="rf-filter-row">
        <label htmlFor="tutor-refund-status">Lọc theo trạng thái</label>
        <select
          id="tutor-refund-status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as 'ALL' | RefundRequestStatus);
            setPage(0);
          }}
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="PENDING">Chờ xử lý</option>
          <option value="IN_REVIEW">Đang xem xét</option>
          <option value="REFUNDED">Đã hoàn tiền</option>
          <option value="REJECTED">Đã từ chối</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
      </div>
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
