import { useCallback, useMemo, useState } from 'react';
import { getContracts, type ContractStatus } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { StudentContractCard } from '../../components/student/StudentContractCard';

const filters: Array<'ALL' | ContractStatus> = ['ALL', 'PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
const PAGE_SIZE = 20;

export function StudentContractsPage() {
  const [filter, setFilter] = useState<'ALL' | ContractStatus>('ALL');
  const [subjectQuery, setSubjectQuery] = useState('');
  const [timeOrder, setTimeOrder] = useState<'ASC' | 'DESC'>('DESC');
  const [page, setPage] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) => getContracts(filter === 'ALL' ? undefined : filter, 0, 100, signal),
    [filter]
  );
  const query = useResource(load);
  const contractPage = query.data;
  const filteredContracts = useMemo(() => {
    const normalizedQuery = subjectQuery.trim().toLocaleLowerCase('vi-VN');
    const multiplier = timeOrder === 'ASC' ? 1 : -1;

    return (contractPage?.content ?? [])
      .filter(
        (contract) => !normalizedQuery || contract.subjectName.toLocaleLowerCase('vi-VN').includes(normalizedQuery)
      )
      .sort(
        (first, second) => multiplier * (new Date(first.createdAt).getTime() - new Date(second.createdAt).getTime())
      );
  }, [contractPage?.content, subjectQuery, timeOrder]);
  const totalPages = Math.max(1, Math.ceil(filteredContracts.length / PAGE_SIZE));
  const visibleContracts = filteredContracts.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">THỎA THUẬN CỦA BẠN</span>
          <h1>Hợp đồng</h1>
          <p>Xem và quản lý các hợp đồng dạy học của bạn với gia sư.</p>
        </div>
      </div>
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      )}
      <div className="student-contract-filters" aria-label="Bộ lọc hợp đồng">
        <label className="student-contract-filter-search">
          <span className="sr-only">Tìm theo tên môn học</span>
          <input
            type="search"
            value={subjectQuery}
            onChange={(event) => {
              setSubjectQuery(event.target.value);
              setPage(0);
            }}
            placeholder="Tìm theo tên môn học"
          />
        </label>
        <label className="student-contract-filter-select">
          <span className="sr-only">Lọc theo trạng thái</span>
          <select
            aria-label="Lọc theo trạng thái"
            value={filter}
            onChange={(event) => {
              setFilter(event.target.value as 'ALL' | ContractStatus);
              setPage(0);
            }}
          >
            {filters.map((item) => (
              <option key={item} value={item}>
                {item === 'ALL' ? 'Tất cả trạng thái' : label(item)}
              </option>
            ))}
          </select>
        </label>
        <label className="student-contract-filter-select">
          <span className="sr-only">Sắp xếp theo thời gian</span>
          <select
            aria-label="Sắp xếp theo thời gian"
            value={timeOrder}
            onChange={(event) => {
              setTimeOrder(event.target.value as 'ASC' | 'DESC');
              setPage(0);
            }}
          >
            <option value="DESC">Mới nhất trước</option>
            <option value="ASC">Cũ nhất trước</option>
          </select>
        </label>
      </div>
      {query.isLoading ? (
        <div className="student-empty">
          <h3>Đang tải hợp đồng…</h3>
        </div>
      ) : visibleContracts.length ? (
        <div className="student-list">
          {visibleContracts.map((contract) => (
            <StudentContractCard contract={contract} key={contract.id} />
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>{subjectQuery.trim() ? 'Không tìm thấy hợp đồng phù hợp' : 'Không có hợp đồng trong mục này'}</h3>
          <p>
            {subjectQuery.trim()
              ? 'Hãy thử tìm với tên môn học khác.'
              : 'Các đề nghị từ gia sư đã được chấp nhận có thể trở thành hợp đồng.'}
          </p>
        </div>
      )}
      {filteredContracts.length ? (
        <div className="student-pagination student-contract-pagination">
          <button
            className="student-pagination-arrow"
            type="button"
            aria-label="Trang trước"
            disabled={page === 0}
            onClick={() => setPage((value) => value - 1)}
          >
            ‹
          </button>
          <div className="student-pagination-pages" aria-label="Các trang hợp đồng">
            {Array.from({ length: totalPages }, (_, index) => (
              <button
                className={index === page ? 'active' : ''}
                key={index}
                type="button"
                aria-label={`Trang ${index + 1}`}
                aria-current={index === page ? 'page' : undefined}
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
            disabled={page >= totalPages - 1}
            onClick={() => setPage((value) => value + 1)}
          >
            ›
          </button>
        </div>
      ) : null}
    </>
  );
}
function label(value: string) {
  return (
    {
      PENDING: 'Đang chờ',
      ACTIVE: 'Đang hoạt động',
      COMPLETED: 'Đã hoàn tất',
      CANCELLED: 'Đã hủy'
    }[value] ?? value
  );
}
