import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import { getContracts, type Contract } from '../../services/studentApi';
import {
  cancelComplaint,
  getComplaintsAgainstMe,
  getMyComplaints,
  type ComplaintStatus,
  type StudentComplaint
} from '../../services/studentComplaintApi';
import { ComplaintCard } from '../../components/common-user/complaint/ComplaintCard';
import { complaintStatusLabels } from '../../components/common-user/complaint/complaintFormat';
import { Icon } from '../../components/ui/Icon';

type Mode = 'submitted' | 'received';
type Filter = 'ALL' | ComplaintStatus;

const submittedFilters: Filter[] = [
  'ALL',
  'PENDING',
  'IN_REVIEW',
  'AWAITING_EVIDENCE',
  'RESOLVED',
  'REJECTED',
  'CANCELLED'
];
const receivedFilters: Filter[] = ['ALL', 'IN_REVIEW', 'AWAITING_EVIDENCE', 'RESOLVED', 'REJECTED'];
const PAGE_SIZE = 6;

export function ComplaintsPage({
  mode = 'submitted',
  basePath = '/student/complaints',
  role = 'student'
}: {
  mode?: Mode;
  basePath?: string;
  role?: 'student' | 'tutor';
}) {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');
  const [search, setSearch] = useState('');
  const filters = mode === 'submitted' ? submittedFilters : receivedFilters;

  const load = useCallback(
    async (signal: AbortSignal) => {
      const status = filter === 'ALL' ? undefined : filter;
      const complaintsPromise =
        mode === 'submitted'
          ? getMyComplaints(status, page, PAGE_SIZE, signal)
          : getComplaintsAgainstMe(status, page, PAGE_SIZE, signal);
      const complaints = await complaintsPromise;
      let contracts: Contract[] = [];
      try {
        contracts = (await getContracts(undefined, 0, 100, signal)).content;
      } catch (error) {
        if (signal.aborted) throw error;
      }
      return { complaints, contracts };
    },
    [filter, mode, page]
  );
  const query = useResource(load, revision);
  const complaints: StudentComplaint[] = useMemo(() => query.data?.complaints.content ?? [], [query.data]);
  const complaintPage = query.data?.complaints ?? null;
  const contracts = useMemo(() => query.data?.contracts ?? [], [query.data]);
  const contractMap = useMemo(() => new Map(contracts.map((contract) => [contract.id, contract])), [contracts]);
  const visibleComplaints = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase('vi-VN');
    if (!keyword) return complaints;
    return complaints.filter((complaint) => {
      const contract = contractMap.get(complaint.contractId);
      const contractText = contract
        ? [
            contract.subjectName,
            contract.gradeName,
            contract.tutorFirstName,
            contract.tutorLastName,
            contract.tutorEmail
          ]
            .filter(Boolean)
            .join(' ')
        : complaint.contractId;
      return [complaint.title, complaint.content, contractText].join(' ').toLocaleLowerCase('vi-VN').includes(keyword);
    });
  }, [complaints, contractMap, search]);

  const cancel = async (id: string) => {
    if (!window.confirm('Bạn có chắc muốn hủy khiếu nại này không?')) return;
    setBusyId(id);
    setActionError('');
    try {
      await cancelComplaint(id);
      setRevision((value) => value + 1);
    } catch (error) {
      setActionError(error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hủy khiếu nại.');
    } finally {
      setBusyId(null);
    }
  };

  const listPath = mode === 'submitted' ? basePath : `${basePath}/received`;

  return (
    <div className="student-complaints-page">
      <div className="student-page-heading">
        <div>
          <h1>Khiếu nại</h1>
        </div>
        {mode === 'submitted' && (
          <Link className="student-button complaint-create-button" to={`${basePath}/new`}>
            <span aria-hidden="true">+</span> Tạo khiếu nại
          </Link>
        )}
      </div>

      <div className="student-complaint-tabs" role="tablist" aria-label="Loại khiếu nại">
        <Link
          className={mode === 'submitted' ? 'active' : ''}
          to={basePath}
          role="tab"
          aria-selected={mode === 'submitted'}
        >
          <Icon name="support" size={17} />
          Khiếu nại của tôi
        </Link>
        <Link
          className={mode === 'received' ? 'active' : ''}
          to={`${basePath}/received`}
          role="tab"
          aria-selected={mode === 'received'}
        >
          <Icon name="user" size={17} />
          Khiếu nại về tôi
        </Link>
      </div>

      <div className="student-complaint-toolbar">
        <div className="student-complaint-status-filters" role="group" aria-label="Lọc trạng thái khiếu nại">
          {filters.map((value) => (
            <button
              className={filter === value ? 'active' : ''}
              type="button"
              key={value}
              onClick={() => {
                setFilter(value);
                setPage(0);
              }}
            >
              <i className={value.toLowerCase()} />
              {value === 'ALL' ? `Tất cả (${query.data?.complaints.totalElements ?? 0})` : complaintStatusLabels[value]}
            </button>
          ))}
        </div>
        <label className="student-complaint-search" htmlFor="student-complaint-search-input">
          <Icon name="search" size={16} />
          <input
            id="student-complaint-search-input"
            value={search}
            placeholder="Tìm kiếm khiếu nại..."
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>

      {(actionError || query.error) && (
        <div className="student-error" role="alert">
          {actionError || query.error}
        </div>
      )}

      {query.isLoading && !query.data ? (
        <div className="student-complaint-state">Đang tải danh sách khiếu nại…</div>
      ) : visibleComplaints.length > 0 ? (
        <div className="student-complaint-list">
          {visibleComplaints.map((complaint) => (
            <ComplaintCard
              key={complaint.id}
              complaint={complaint}
              mode={mode}
              contract={contractMap.get(complaint.contractId) ?? null}
              role={role}
              cancelling={busyId === complaint.id}
              onOpen={() => navigate(`${listPath}/${complaint.id}`)}
              onEdit={mode === 'submitted' ? () => navigate(`${basePath}/${complaint.id}?edit=1`) : undefined}
              onCancel={mode === 'submitted' ? () => void cancel(complaint.id) : undefined}
            />
          ))}
        </div>
      ) : (
        <div className="student-complaint-empty">
          <div className="student-complaint-empty-icon">
            <Icon name="search" size={42} />
          </div>
          <h3>{mode === 'submitted' ? 'Bạn chưa có khiếu nại nào' : 'Chưa có khiếu nại về bạn'}</h3>
          <p>
            {mode === 'submitted'
              ? 'Bạn có thể tạo khiếu nại từ nút bên trên khi phát sinh vấn đề.'
              : 'Các khiếu nại liên quan đến bạn sẽ hiển thị tại đây.'}
          </p>
          {mode === 'submitted' && (
            <Link className="student-button complaint-create-button" to={`${basePath}/new`}>
              <span aria-hidden="true">+</span> Tạo khiếu nại
            </Link>
          )}
        </div>
      )}
      {complaintPage && complaintPage.totalElements > 0 && (
        <div className="student-pagination student-complaint-pagination" aria-label="Phân trang khiếu nại">
          <button
            className="student-pagination-arrow"
            type="button"
            aria-label="Trang trước"
            disabled={complaintPage.first}
            onClick={() => setPage((value) => value - 1)}
          >
            ‹
          </button>
          <div className="student-pagination-pages">
            {Array.from({ length: complaintPage.totalPages }, (_, index) => (
              <button
                className={index === complaintPage.page ? 'active' : ''}
                key={index}
                type="button"
                aria-label={`Trang ${index + 1}`}
                aria-current={index === complaintPage.page ? 'page' : undefined}
                onClick={() => setPage(index)}
              >
                {index + 1}
              </button>
            ))}
          </div>
          <button
            className="student-pagination-arrow"
            type="button"
            aria-label="Trang sau"
            disabled={complaintPage.last}
            onClick={() => setPage((value) => value + 1)}
          >
            ›
          </button>
        </div>
      )}
      {query.data && query.data.complaints.totalElements > 0 && (
        <div className="student-complaint-footer">
          Hiển thị {query.data.complaints.page * query.data.complaints.size + 1} -{' '}
          {Math.min(
            query.data.complaints.page * query.data.complaints.size + query.data.complaints.size,
            query.data.complaints.totalElements
          )}{' '}
          trong tổng số {query.data.complaints.totalElements} khiếu nại
        </div>
      )}
    </div>
  );
}
