import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { SupportStatusBadge } from '../../../components/support/SupportStatusBadge';
import { formatBackendLocal, supportSourceLabels } from '../../../components/support/supportFormat';
import { useResource } from '../../../hooks/useResource';
import { getSupportQueue, getSupportRequestStats, type SupportQueueFilters } from '../../../services/supportRequestApi';
import type { SupportSource } from '../../../components/assistant/supportSource';
import '../../../components/refund/refund.css';
import '../../../components/support/support.css';

type Tab = 'open' | 'mine' | 'answered';
const TABS: { key: Tab; label: string; filters: Omit<SupportQueueFilters, 'page'> }[] = [
  { key: 'open', label: 'Chờ tiếp nhận', filters: { status: 'OPEN' } },
  { key: 'mine', label: 'Tôi đang xử lý', filters: { status: 'IN_PROGRESS', assignedToMe: true } },
  { key: 'answered', label: 'Đã trả lời', filters: { status: 'ANSWERED' } }
];

function minutes(value: number | null) {
  if (value === null) return 'Chưa có dữ liệu';
  return value < 60 ? `${Math.round(value)} phút` : `${(value / 60).toFixed(1)} giờ`;
}

function sourceKind(source: SupportSource) {
  return source === 'MENU' ? 'Gửi trực tiếp' : 'Chuyển từ trợ lý';
}

export function SupportRequestQueuePage() {
  const [tab, setTab] = useState<Tab>('open');
  const [page, setPage] = useState(0);
  const filters = TABS.find((item) => item.key === tab)!.filters;
  const load = useCallback((signal: AbortSignal) => getSupportQueue({ ...filters, page }, signal), [filters, page]);
  const loadStats = useCallback((signal: AbortSignal) => getSupportRequestStats(signal), []);
  const { data, error, isLoading } = useResource(load);
  const stats = useResource(loadStats).data;
  const fromAssistant = stats
    ? (stats.bySource.ASSISTANT_ABSTAIN ?? 0) + (stats.bySource.ASSISTANT_PARTIAL ?? 0) +
      (stats.bySource.ASSISTANT_FEEDBACK ?? 0)
    : 0;

  return (
    <div className="admin-page rf-page support-admin-page">
      <header className="support-admin-header">
        <div>
          <h1 className="admin-title">Yêu cầu hỗ trợ</h1>
        </div>
      </header>
      {stats && (
        <>
          <section className="support-admin-summary" aria-label="Tổng quan yêu cầu hỗ trợ">
            <div className="support-admin-stat"><span>Chờ tiếp nhận</span><strong>{stats.byStatus.OPEN ?? 0}</strong></div>
            <div className="support-admin-stat"><span>Đang xử lý</span><strong>{stats.byStatus.IN_PROGRESS ?? 0}</strong></div>
            <div className="support-admin-stat"><span>Chuyển từ trợ lý</span><strong>{fromAssistant}</strong></div>
          </section>
          <details className="support-admin-insights">
            <summary>Thời gian phản hồi và chuyển tiếp</summary>
            <div className="support-admin-insights-grid">
              <div>
                <h2>Thời gian xử lý</h2>
                <p>Đến khi nhân viên nhận: {minutes(stats.minutesToAssignP50)} (trung vị)</p>
                <p>Đến khi trả lời: {minutes(stats.minutesToAnswerP50)} (trung vị)</p>
              </div>
              {stats.refusalFollowUp && (
                <div>
                  <h2>Chuyển tiếp từ trợ lý trong {stats.windowDays} ngày</h2>
                  <p>Không trả lời được: {stats.refusalFollowUp.requestsAfterAbstain}/{stats.refusalFollowUp.abstainTurns} lượt gửi nhân viên</p>
                  <p>Trả lời một phần: {stats.refusalFollowUp.requestsAfterPartial}/{stats.refusalFollowUp.partialTurns} lượt gửi nhân viên</p>
                </div>
              )}
            </div>
          </details>
        </>
      )}
      <section className="admin-card support-admin-queue" aria-label="Danh sách yêu cầu hỗ trợ">
        <div className="support-admin-queue-top">
          <div><h2>Danh sách yêu cầu</h2></div>
          {data && <span className="support-admin-total">{data.totalElements} yêu cầu</span>}
        </div>
        <div className="support-tabs" role="tablist" aria-label="Lọc yêu cầu hỗ trợ">
          {TABS.map((item) => (
            <button key={item.key} type="button" role="tab" aria-selected={tab === item.key}
              className="support-tab" onClick={() => { setTab(item.key); setPage(0); }}>
              {item.label}
            </button>
          ))}
        </div>
        {isLoading && !data && <div className="rf-state">Đang tải</div>}
        {error && <div className="rf-alert rf-alert-danger" role="alert">{error}</div>}
        {data && data.content.length === 0 && <div className="support-admin-empty">Không có yêu cầu.</div>}
        {data && data.content.length > 0 && (
          <div className="support-admin-list">
            {data.content.map((request) => (
              <Link className="support-admin-item" key={request.id} to={`/admin/support-requests/${request.id}`}>
                <div className="support-admin-item-main">
                  <div className="support-admin-item-top">
                    <span className="support-admin-code">{request.code}</span>
                    <span className={`support-admin-source ${request.source === 'MENU' ? 'is-direct' : 'is-assistant'}`}>{sourceKind(request.source)}</span>
                  </div>
                  <strong className="support-admin-question">{request.question}</strong>
                  <div className="support-admin-item-meta">
                    <span>{request.requesterName}</span>
                    <span>{request.userRole === 'TUTOR' ? 'Gia sư' : 'Học viên'}</span>
                    <span>{formatBackendLocal(request.createdAt)}</span>
                    {request.assignedEmployeeName && <span>Phụ trách: {request.assignedEmployeeName}</span>}
                  </div>
                  <small className="support-admin-source-detail">{supportSourceLabels[request.source]}</small>
                </div>
                <div className="support-admin-item-side"><SupportStatusBadge status={request.status} /><span aria-hidden="true" className="support-admin-item-arrow">→</span></div>
              </Link>
            ))}
          </div>
        )}
        {data && data.totalPages > 1 && (
          <div className="support-admin-pagination">
            <span>Trang {page + 1}/{data.totalPages}</span>
            <div>
              <button className="rf-button small secondary" type="button" disabled={data.first} onClick={() => setPage(page - 1)}>Trước</button>
              <button className="rf-button small secondary" type="button" disabled={data.last} onClick={() => setPage(page + 1)}>Sau</button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
