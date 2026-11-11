import { useCallback, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminRefundRequests, type RefundRequestStatus } from '../../services/refundApi';
import { formatLocalDateTime, refundStatusMeta } from '../../components/refund/refundFormat';
import { AdminPagination, AdminState, FilterChips } from './components/AdminUi';
import type { BadgeTone } from './adminFormat';

const statuses: RefundRequestStatus[] = ['PENDING', 'IN_REVIEW', 'REFUNDED', 'REJECTED', 'CANCELLED'];

export function RefundBadge({ status }: { status: RefundRequestStatus }) {
  const meta = refundStatusMeta[status];
  return <span className={`admin-badge admin-badge-${meta.tone as BadgeTone}`}>{meta.label}</span>;
}

export function AdminRefundRequestsPage() {
  const [params, setParams] = useSearchParams();
  const status = (statuses.find((value) => value === params.get('status')) ?? 'PENDING') as RefundRequestStatus;
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);

  const load = useCallback(
    (signal: AbortSignal) => getAdminRefundRequests({ status, keyword: keyword || undefined, page }, signal),
    [keyword, page, status]
  );
  const { data, error, isLoading } = useResource(load);

  const update = (changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    setParams(next);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    update({ keyword: String(new FormData(event.currentTarget).get('keyword') ?? '').trim(), page: undefined });
  };

  return (
    <div className="admin-page">
      <h1 className="admin-title">Yêu cầu hoàn tiền</h1>
      <FilterChips<RefundRequestStatus>
        label="Trạng thái yêu cầu"
        value={status}
        options={statuses.map((value) => ({ value, label: refundStatusMeta[value].label }))}
        onChange={(value) => update({ status: value === 'PENDING' ? undefined : value, page: undefined })}
      />
      <section className="admin-card">
        <form key={keyword} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder="Tên hoặc email học viên" />
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Tìm
            </button>
          </div>
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có yêu cầu nào." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Hợp đồng</th>
                  <th>Tình trạng</th>
                  <th>Buổi phản đối</th>
                  <th>Gửi lúc</th>
                  <th>Người xử lý</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <div className="admin-stack">
                        <strong>{request.studentName}</strong>
                        <span>{request.studentEmail}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-stack">
                        <span>{[request.subjectName, request.gradeName].filter(Boolean).join(' · ') || '—'}</span>
                        <span>Gia sư {request.tutorName}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-tags">
                        {request.hasOpenComplaint && (
                          <span className="admin-badge admin-badge-pending">Có khiếu nại mở</span>
                        )}
                        {request.awaitingTutorResponse && (
                          <span className="admin-badge admin-badge-pending">Chờ gia sư phản hồi</span>
                        )}
                      </div>
                    </td>
                    <td>{request.disputedLessonCount > 0 ? `${request.disputedLessonCount} buổi` : '—'}</td>
                    <td>{formatLocalDateTime(request.createdAt)}</td>
                    <td>{request.assignedEmployeeName ?? 'Chưa có'}</td>
                    <td className="admin-cell-action">
                      <Link className="admin-button small secondary" to={`/admin/refund-requests/${request.id}`}>
                        Xem
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="admin-card-footer">
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit="yêu cầu"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
    </div>
  );
}
