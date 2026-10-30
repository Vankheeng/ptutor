import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminStudentComplaints, type ComplaintRelation } from '../../services/adminStudentApi';
import type { ComplaintStatus } from '../../services/complaintApi';
import { formatDate } from './adminFormat';
import { useAdminStudent } from './adminStudentContext';
import { complaintStatusMeta, reviewerName } from './complaintFormat';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { complaintRelationMeta } from './studentFormat';

const relations: { value: ComplaintRelation; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'SUBMITTED', label: 'Đã gửi' },
  { value: 'RECEIVED', label: 'Bị khiếu nại' }
];

export function AdminStudentComplaintsPage() {
  const { account } = useAdminStudent();
  const [relation, setRelation] = useState<ComplaintRelation>('ALL');
  const [status, setStatus] = useState<ComplaintStatus | ''>('');
  const [page, setPage] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminStudentComplaints(account.userId, { relation, status: status || undefined, page }, signal),
    [account.userId, page, relation, status]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <section className="admin-card">
      <div className="admin-filters">
        <FilterChips<ComplaintRelation>
          label="Quan hệ với khiếu nại"
          value={relation}
          options={relations}
          onChange={(value) => {
            setRelation(value);
            setPage(0);
          }}
        />
        <label>
          Trạng thái
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ComplaintStatus | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            {(Object.keys(complaintStatusMeta) as ComplaintStatus[]).map((value) => (
              <option key={value} value={value}>
                {complaintStatusMeta[value].label}
              </option>
            ))}
          </select>
        </label>
      </div>
      {!data ? (
        <div className="admin-card-pad">
          <AdminState loading={isLoading} error={error} />
        </div>
      ) : data.content.length === 0 ? (
        <AdminState empty="Chưa có khiếu nại nào." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Tiêu đề</th>
                <th>Quan hệ</th>
                <th>Người khiếu nại</th>
                <th>Phụ trách</th>
                <th>Trạng thái</th>
                <th>Ngày tạo</th>
                <th>
                  <span className="admin-sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((complaint) => (
                <tr key={complaint.complaintId}>
                  <td className="admin-strong">{complaint.title}</td>
                  <td>
                    <StatusBadge meta={complaintRelationMeta[complaint.relation]} />
                  </td>
                  <td>
                    <div className="admin-stack">
                      <strong>{complaint.complainantName || '—'}</strong>
                      {complaint.complainantEmail && <span>{complaint.complainantEmail}</span>}
                    </div>
                  </td>
                  <td>{complaint.assignedEmployeeId ? reviewerName(complaint.assignedEmployeeName) : '—'}</td>
                  <td>
                    <StatusBadge meta={complaintStatusMeta[complaint.status]} />
                  </td>
                  <td className="admin-nowrap">{formatDate(complaint.createdAt)}</td>
                  <td className="admin-cell-action">
                    <Link className="admin-button small secondary" to={`/admin/complaints/${complaint.complaintId}`}>
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
            unit="khiếu nại"
            onChange={setPage}
          />
        </div>
      )}
    </section>
  );
}
