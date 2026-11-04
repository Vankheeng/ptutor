// Hàng chờ yêu cầu hỗ trợ cho nhân viên CUSTOMER_SERVICE và ADMIN (docs/support-chatbot/14-yeu-cau-ho-tro.md
// mục 14.6): tab Chờ xử lý / Của tôi / Đã trả lời, cũ nhất trước; số liệu theo trạng thái và thời gian phản hồi (14.9).
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { SupportStatusBadge } from '../../../components/support/SupportStatusBadge';
import { formatBackendLocal, supportSourceLabels } from '../../../components/support/supportFormat';
import { useResource } from '../../../hooks/useResource';
import { getSupportQueue, getSupportRequestStats, type SupportQueueFilters } from '../../../services/supportRequestApi';
import '../../../components/refund/refund.css';
import '../../../components/support/support.css';

type Tab = 'open' | 'mine' | 'answered';

const TABS: { key: Tab; label: string; filters: Omit<SupportQueueFilters, 'page'> }[] = [
  { key: 'open', label: 'Chờ xử lý', filters: { status: 'OPEN' } },
  { key: 'mine', label: 'Của tôi', filters: { status: 'IN_PROGRESS', assignedToMe: true } },
  { key: 'answered', label: 'Đã trả lời', filters: { status: 'ANSWERED' } }
];

function minutes(value: number | null) {
  if (value === null) return '—';
  return value < 60 ? `${value} phút` : `${(value / 60).toFixed(1)} giờ`;
}

export function SupportRequestQueuePage() {
  const [tab, setTab] = useState<Tab>('open');
  const [page, setPage] = useState(0);
  const filters = TABS.find((item) => item.key === tab)!.filters;
  const load = useCallback((signal: AbortSignal) => getSupportQueue({ ...filters, page }, signal), [filters, page]);
  const loadStats = useCallback((signal: AbortSignal) => getSupportRequestStats(signal), []);
  const { data, error, isLoading } = useResource(load);
  const stats = useResource(loadStats).data;

  return (
    <div className="rf-page">
      <h1>Yêu cầu hỗ trợ</h1>
      {stats && (
        <section className="rf-card">
          <dl className="support-meta">
            <dt>Chờ xử lý / Đang xử lý</dt>
            <dd>
              {stats.byStatus.OPEN} / {stats.byStatus.IN_PROGRESS}
            </dd>
            <dt>Thời gian tới khi nhận (p50 / p95)</dt>
            <dd>
              {minutes(stats.minutesToAssignP50)} / {minutes(stats.minutesToAssignP95)}
            </dd>
            <dt>Thời gian tới khi trả lời (p50 / p95)</dt>
            <dd>
              {minutes(stats.minutesToAnswerP50)} / {minutes(stats.minutesToAnswerP95)}
            </dd>
          </dl>
        </section>
      )}
      <section className="rf-card">
        <div className="support-tabs" role="tablist">
          {TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              className="rf-button small secondary"
              aria-pressed={tab === item.key}
              aria-selected={tab === item.key}
              onClick={() => {
                setTab(item.key);
                setPage(0);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        {isLoading && !data && <div className="rf-state">Đang tải…</div>}
        {error && (
          <div className="rf-alert rf-alert-danger" role="alert">
            {error}
          </div>
        )}
        {data && data.content.length === 0 && <div className="rf-state">Không có yêu cầu nào.</div>}
        {data && data.content.length > 0 && (
          <div className="rf-rows">
            {data.content.map((request) => (
              <Link className="rf-row rf-link-row" key={request.id} to={`/admin/support-requests/${request.id}`}>
                <div className="rf-row-main">
                  <strong>
                    {request.code} ·{' '}
                    {request.question.length > 90 ? `${request.question.slice(0, 90)}…` : request.question}
                  </strong>
                  <small>
                    {request.requesterName} ({request.userRole === 'TUTOR' ? 'Gia sư' : 'Học viên'}) ·{' '}
                    {supportSourceLabels[request.source]} · {formatBackendLocal(request.createdAt)}
                    {request.assignedEmployeeName ? ` · ${request.assignedEmployeeName}` : ''}
                  </small>
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
