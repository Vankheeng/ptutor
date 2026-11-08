import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useResource } from '../../hooks/useResource';
import { getContracts, type Contract, type ContractStatus } from '../../services/studentApi';
import { Icon } from '../../components/ui/Icon';
import './contracts.css';

export type ContractRole = 'student' | 'tutor';
const statuses: Array<'ALL' | ContractStatus> = ['ALL', 'PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
const PAGE_SIZE = 8;

export function ContractsPage({ role }: { role: ContractRole }) {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<'ALL' | ContractStatus>('ALL');
  const [subject, setSubject] = useState('ALL');
  const [order, setOrder] = useState<'NEWEST' | 'OLDEST'>('NEWEST');
  const [page, setPage] = useState(0);
  const query = useResource(useCallback((signal: AbortSignal) => getContracts(undefined, 0, 100, signal), []));
  const allContracts = query.data?.content ?? [];
  const subjects = [...new Set(allContracts.map((item) => item.subjectName).filter(Boolean))] as string[];
  const contracts = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return allContracts
      .filter((item) => {
        const otherName =
          role === 'student'
            ? `${item.tutorFirstName ?? ''} ${item.tutorLastName ?? ''}`
            : `${item.studentFirstName ?? ''} ${item.studentLastName ?? ''}`;
        const searchable = `${item.subjectName} ${item.gradeName} ${otherName}`.toLocaleLowerCase('vi-VN');
        return (
          (!term || searchable.includes(term)) &&
          (status === 'ALL' || item.status === status) &&
          (subject === 'ALL' || item.subjectName === subject)
        );
      })
      .sort((first, second) => {
        const difference = new Date(first.startDate).getTime() - new Date(second.startDate).getTime();
        return order === 'NEWEST' ? -difference : difference;
      });
  }, [allContracts, order, role, search, status, subject]);
  const pageCount = Math.max(1, Math.ceil(contracts.length / PAGE_SIZE));
  const visible = contracts.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const resetPage = () => setPage(0);
  const currentName =
    role === 'student'
      ? [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || 'Học viên'
      : [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || 'Gia sư';
  const basePath = role === 'student' ? '/student/contracts' : '/tutor/sections/contracts';

  return (
    <>
      <div className="tutor-contracts-heading">
        <div>
          <h1>Hợp đồng</h1>
        </div>
      </div>
      {query.error && (
        <div className="tutor-error" role="alert">
          {query.error}
        </div>
      )}
      <div className="tutor-contract-list-filters" aria-label="Bộ lọc hợp đồng">
        <label className="tutor-contract-search">
          <Icon name="search" size={17} />
          <span className="sr-only">Tìm theo tên hợp đồng</span>
          <input
            aria-label="Tìm theo tên hợp đồng"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetPage();
            }}
            placeholder="Tìm theo tên hợp đồng"
          />
        </label>
        <label>
          <span className="sr-only">Lọc theo trạng thái</span>
          <select
            aria-label="Lọc theo trạng thái"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as 'ALL' | ContractStatus);
              resetPage();
            }}
          >
            <option value="ALL">Tất cả trạng thái</option>
            {statuses
              .filter((item) => item !== 'ALL')
              .map((item) => (
                <option value={item} key={item}>
                  {statusLabel(item)}
                </option>
              ))}
          </select>
        </label>
        <label>
          <span className="sr-only">Chọn môn học</span>
          <select
            aria-label="Chọn môn học"
            value={subject}
            onChange={(event) => {
              setSubject(event.target.value);
              resetPage();
            }}
          >
            <option value="ALL">Chọn môn học</option>
            {subjects.map((item) => (
              <option value={item} key={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span className="sr-only">Sắp xếp</span>
          <select
            aria-label="Sắp xếp"
            value={order}
            onChange={(event) => {
              setOrder(event.target.value as 'NEWEST' | 'OLDEST');
              resetPage();
            }}
          >
            <option value="NEWEST">Mới nhất trước</option>
            <option value="OLDEST">Cũ nhất trước</option>
          </select>
        </label>
      </div>
      {query.isLoading ? (
        <div className="tutor-empty">
          <h3>Đang tải hợp đồng...</h3>
        </div>
      ) : !visible.length ? (
        <div className="tutor-empty">
          <h3>Không có hợp đồng phù hợp</h3>
          <p>Thử thay đổi từ khóa hoặc bộ lọc.</p>
        </div>
      ) : (
        <div className="tutor-contract-row-list">
          {visible.map((contract) => (
            <ContractRow
              contract={contract}
              role={role}
              currentName={currentName}
              basePath={basePath}
              key={contract.id}
            />
          ))}
        </div>
      )}
      {!query.isLoading && contracts.length > 0 && (
        <div className="tutor-contract-list-pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            ‹
          </button>
          <div>
            {Array.from({ length: pageCount }, (_, index) => (
              <button
                className={index === page ? 'active' : ''}
                type="button"
                key={index}
                onClick={() => setPage(index)}
              >
                {index + 1}
              </button>
            ))}
          </div>
          <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((value) => value + 1)}>
            ›
          </button>
        </div>
      )}
    </>
  );
}

function ContractRow({
  contract,
  role,
  currentName,
  basePath
}: {
  contract: Contract;
  role: ContractRole;
  currentName: string;
  basePath: string;
}) {
  const otherName =
    role === 'student'
      ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'
      : currentName;
  const otherRole = role === 'student' ? 'Gia sư' : 'Gia sư';
  return (
    <article className="tutor-contract-row-card">
      <div className="tutor-contract-row-subject">
        <span className="tutor-contract-row-icon">
          <Icon name="contract" size={21} />
        </span>
        <div>
          <strong>{contract.subjectName || 'Môn học'}</strong>
          <small>{contract.gradeName || 'Chưa có lớp'}</small>
        </div>
      </div>
      <div className="tutor-contract-row-person">
        <span className="tutor-contract-row-avatar">{initials(otherName)}</span>
        <div>
          <strong>{otherName}</strong>
          <small>{otherRole}</small>
        </div>
      </div>
      <div className="tutor-contract-row-price">
        <strong>
          {formatMoney(contract.price)} / {paymentLabel(contract.paymentPeriod)}
        </strong>
        <small>{contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</small>
        <small>
          ▣ {formatDate(contract.startDate)} – {formatDate(contract.endDate)}
        </small>
      </div>
      <span className={`tutor-contract-status status-${contract.status.toLowerCase()}`}>
        {statusLabel(contract.status)}
      </span>
      <Link className="tutor-contract-row-action" to={`${basePath}/${contract.id}`}>
        Xem hợp đồng <span>→</span>
      </Link>
    </article>
  );
}

function statusLabel(value: ContractStatus) {
  return (
    { PENDING: 'Đang chờ', ACTIVE: 'Đang hoạt động', COMPLETED: 'Đã hoàn tất', CANCELLED: 'Đã hủy' } as Record<
      string,
      string
    >
  )[value];
}
function paymentLabel(value: Contract['paymentPeriod']) {
  return ({ PER_LESSON: 'buổi', WEEKLY: 'tuần', MONTHLY: 'tháng', PACKAGE: 'gói' } as Record<string, string>)[value];
}
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`));
}
function initials(value: string) {
  return value
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
