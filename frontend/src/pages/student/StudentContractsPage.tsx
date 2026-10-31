import { useCallback, useState } from 'react';
import { getContracts, type ContractStatus } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { StudentContractCard } from '../../components/student/StudentContractCard';

const filters: Array<'ALL' | ContractStatus> = ['ALL', 'PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
export function StudentContractsPage() {
  const [filter, setFilter] = useState<'ALL' | ContractStatus>('ALL');
  const [page, setPage] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) => getContracts(filter === 'ALL' ? undefined : filter, page, 20, signal),
    [filter, page]
  );
  const query = useResource(load);
  const contractPage = query.data;
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
      <div className="student-filter-tabs student-contract-filter-tabs">
        {filters.map((item) => (
          <button
            key={item}
            className={filter === item ? 'active' : ''}
            type="button"
            onClick={() => {
              setFilter(item);
              setPage(0);
            }}
          >
            {item === 'ALL' ? 'Tất cả' : label(item)}
          </button>
        ))}
      </div>
      {query.isLoading ? (
        <div className="student-empty">
          <h3>Đang tải hợp đồng…</h3>
        </div>
      ) : contractPage?.content.length ? (
        <div className="student-list">
          {contractPage.content.map((contract) => (
            <StudentContractCard contract={contract} key={contract.id} />
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>Không có hợp đồng trong mục này</h3>
          <p>Các đề nghị từ gia sư đã được chấp nhận có thể trở thành hợp đồng.</p>
        </div>
      )}
      {contractPage?.content.length ? (
        <div className="student-pagination student-contract-pagination">
          <button
            className="student-pagination-arrow"
            type="button"
            aria-label="Trang trước"
            disabled={contractPage.first}
            onClick={() => setPage((value) => value - 1)}
          >
            ‹
          </button>
          <div className="student-pagination-pages" aria-label="Các trang hợp đồng">
            {Array.from({ length: Math.max(contractPage.totalPages, 1) }, (_, index) => (
              <button
                className={index === contractPage.page ? 'active' : ''}
                key={index}
                type="button"
                aria-label={`Trang ${index + 1}`}
                aria-current={index === contractPage.page ? 'page' : undefined}
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
            disabled={contractPage.last}
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
