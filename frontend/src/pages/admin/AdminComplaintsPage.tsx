import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminComplaints,
  startComplaintReview,
  type ComplaintCategory,
  type ComplaintFaultParty,
  type ComplaintStatus
} from '../../services/complaintApi';
import { formatLocalDateTime } from '../../components/refund/refundFormat';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import {
  complaintCategoryLabels,
  complaintErrorMessage,
  complaintStatusMeta,
  faultPartyLabels,
  partyRoleLabels,
  reviewerName
} from './complaintFormat';

type StatusFilter = ComplaintStatus | 'ALL';

const statuses: StatusFilter[] = [
  'ALL',
  'PENDING',
  'IN_REVIEW',
  'AWAITING_EVIDENCE',
  'RESOLVED',
  'REJECTED',
  'CANCELLED'
];
const categories = Object.keys(complaintCategoryLabels) as ComplaintCategory[];
const faultParties = Object.keys(faultPartyLabels) as ComplaintFaultParty[];
const uuidPattern = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';

function pick<T extends string>(options: readonly T[], value: string | null) {
  return options.find((option) => option === value);
}

export function AdminComplaintsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = pick(statuses, params.get('status')) ?? 'ALL';
  const category = pick(categories, params.get('category'));
  const faultParty = pick(faultParties, params.get('faultParty'));
  const contractId = params.get('contractId') ?? '';
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [claiming, setClaiming] = useState<string | null>(null);
  const [claimError, setClaimError] = useState<string | null>(null);

  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminComplaints(
        {
          status: status === 'ALL' ? undefined : status,
          category,
          faultParty,
          contractId: contractId || undefined,
          keyword: keyword || undefined,
          page
        },
        signal
      ),
    [category, contractId, faultParty, keyword, page, status]
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
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? '').trim();
    update({
      keyword: text('keyword'),
      category: text('category'),
      faultParty: text('faultParty'),
      contractId: text('contractId'),
      page: undefined
    });
  };

  const claim = async (id: string) => {
    setClaiming(id);
    setClaimError(null);
    try {
      await startComplaintReview(id);
      navigate(`/admin/complaints/${id}`);
    } catch (claimFailure) {
      setClaimError(complaintErrorMessage(claimFailure));
      setClaiming(null);
    }
  };

  return (
    <div className="admin-page">
      <h1 className="admin-title">Khiếu nại</h1>
      <FilterChips<StatusFilter>
        label="Trạng thái khiếu nại"
        value={status}
        options={statuses.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : complaintStatusMeta[value].label
        }))}
        onChange={(value) => update({ status: value === 'ALL' ? undefined : value, page: undefined })}
      />
      {claimError && (
        <div className="admin-alert admin-alert-danger" role="alert">
          {claimError}
        </div>
      )}
      <section className="admin-card">
        <form key={params.toString()} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder="Tên, email hoặc tiêu đề" />
          </label>
          <label>
            Danh mục
            <select name="category" defaultValue={category ?? ''}>
              <option value="">Tất cả</option>
              {categories.map((value) => (
                <option key={value} value={value}>
                  {complaintCategoryLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Bên có lỗi
            <select name="faultParty" defaultValue={faultParty ?? ''}>
              <option value="">Tất cả</option>
              {faultParties.map((value) => (
                <option key={value} value={value}>
                  {faultPartyLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Mã hợp đồng
            <input
              name="contractId"
              type="text"
              defaultValue={contractId}
              pattern={uuidPattern}
              title="Mã hợp đồng dạng UUID"
            />
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
          <AdminState empty="Không có khiếu nại nào." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Người khiếu nại</th>
                  <th>Tiêu đề</th>
                  <th>Danh mục</th>
                  <th>Trạng thái</th>
                  <th>Người xử lý</th>
                  <th>Gửi lúc</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((complaint) => (
                  <tr key={complaint.id}>
                    <td>
                      <div className="admin-stack">
                        <div className="admin-tags">
                          <strong>{complaint.complainantName}</strong>
                          <span
                            className={`admin-badge admin-badge-${complaint.complainantRole === 'TUTOR' ? 'brand' : 'neutral'}`}
                          >
                            {partyRoleLabels[complaint.complainantRole]}
                          </span>
                        </div>
                        <span>{complaint.complainantEmail}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-stack">
                        <strong>{complaint.title}</strong>
                        <span title={complaint.contractId}>HĐ {complaint.contractId.slice(0, 8)}</span>
                      </div>
                    </td>
                    <td className="admin-nowrap">{complaintCategoryLabels[complaint.category]}</td>
                    <td>
                      <div className="admin-stack">
                        <div>
                          <StatusBadge meta={complaintStatusMeta[complaint.status]} />
                        </div>
                        {complaint.faultParty && <span>Lỗi: {faultPartyLabels[complaint.faultParty]}</span>}
                      </div>
                    </td>
                    <td>{complaint.assignedEmployeeId ? reviewerName(complaint.assignedEmployeeName) : 'Chưa có'}</td>
                    <td className="admin-nowrap">{formatLocalDateTime(complaint.createdAt)}</td>
                    <td className="admin-cell-action">
                      {complaint.status === 'PENDING' && !complaint.assignedEmployeeId ? (
                        <button
                          className="admin-button small"
                          type="button"
                          disabled={claiming !== null}
                          onClick={() => void claim(complaint.id)}
                        >
                          Tiếp nhận
                        </button>
                      ) : (
                        <Link className="admin-button small secondary" to={`/admin/complaints/${complaint.id}`}>
                          Xem
                        </Link>
                      )}
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
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
    </div>
  );
}
