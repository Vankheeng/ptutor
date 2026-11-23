// Trang "Yêu cầu hỗ trợ" của học viên / gia sư (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.6).
// Một component dùng chung cho /student/support-requests và /tutor/support-requests (như WalletPage).
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { SupportStatusBadge } from '../../components/support/SupportStatusBadge';
import { formatBackendLocal, supportStatusMeta } from '../../components/support/supportFormat';
import { useResource } from '../../hooks/useResource';
import { getMySupportRequests } from '../../services/supportRequestApi';
import type { SupportRequestStatus } from '../../types/supportRequest';
import '../../components/refund/refund.css';
import '../../components/support/support.css';

export function SupportRequestsPage() {
  const { user } = useAuth();
  const basePath = user?.role === 'TUTOR' ? '/tutor' : '/student';
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<SupportRequestStatus | ''>('');
  const load = useCallback((signal: AbortSignal) => getMySupportRequests(page, status, signal), [page, status]);
  const { data, error, isLoading } = useResource(load);

  return (
    <div className="rf-page">
      <h1>Yêu cầu hỗ trợ</h1>
      <section className="rf-card">
        <div className="support-tabs" role="group" aria-label="Lọc theo trạng thái">
          {(['', 'OPEN', 'IN_PROGRESS', 'ANSWERED', 'CLOSED', 'CANCELLED'] as const).map((value) => (
            <button
              key={value || 'ALL'}
              type="button"
              className="rf-button small secondary"
              aria-pressed={status === value}
              onClick={() => {
                setStatus(value);
                setPage(0);
              }}
            >
              {value ? supportStatusMeta[value].label : 'Tất cả'}
            </button>
          ))}
        </div>
        {isLoading && !data && <div className="rf-state">Đang tải…</div>}
        {error && (
          <div className="rf-alert rf-alert-danger" role="alert">
            {error}
          </div>
        )}
        {data && data.content.length === 0 && <div className="rf-state">Chưa có yêu cầu hỗ trợ nào.</div>}
        {data && data.content.length > 0 && (
          <div className="rf-rows">
            {data.content.map((request) => (
              <Link className="rf-row rf-link-row" key={request.id} to={`${basePath}/support-requests/${request.id}`}>
                <div className="rf-row-main">
                  <strong>
                    {request.code} ·{' '}
                    {request.question.length > 80 ? `${request.question.slice(0, 80)}…` : request.question}
                  </strong>
                  <small>{formatBackendLocal(request.createdAt)}</small>
                </div>
                <div className="rf-row-side">
                  <SupportStatusBadge status={request.status} />
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
