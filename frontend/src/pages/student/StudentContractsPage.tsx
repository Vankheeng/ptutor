import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { getContracts, type ContractStatus } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { dateLabel, moneyLabel, paymentPeriodLabel, statusLabel } from '../../utils/labels';

const filters: Array<'ALL' | ContractStatus> = ['ALL', 'PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
export function StudentContractsPage() {
  const [filter, setFilter] = useState<'ALL' | ContractStatus>('ALL');
  const [page, setPage] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) => getContracts(filter === 'ALL' ? undefined : filter, page, 20, signal),
    [filter, page]
  );
  const query = useResource(load);
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">THOẢ THUẬN HỌC</span>
          <h1>Hợp đồng</h1>
          <p>Xem các hợp đồng học và việc cần làm tiếp theo.</p>
        </div>
      </div>
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      )}
      <div className="student-filter-tabs">
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
      ) : query.data?.content.length ? (
        <div className="student-list">
          {query.data.content.map((contract) => (
            <article className="student-list-row" key={contract.id}>
              <div>
                <h3>
                  {contract.subjectName} · {contract.gradeName}
                </h3>
                <p>
                  {[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'} ·{' '}
                  {paymentPeriodLabel(contract.paymentPeriod)}
                  <br />
                  {formatMoney(contract.price)} · {dateLabel(contract.startDate)} đến {dateLabel(contract.endDate)}
                </p>
              </div>
              <div>
                <span className={`student-status ${contract.status.toLowerCase()}`}>{label(contract.status)}</span>
                <br />
                <Link className="student-link" to={`/student/contracts/${contract.id}`}>
                  Xem hợp đồng →
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>Không có hợp đồng nào ở mục này</h3>
          <p>Đơn hoặc đề nghị được nhận sẽ thành hợp đồng.</p>
        </div>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="student-pagination">
          <button type="button" disabled={query.data.first} onClick={() => setPage((value) => value - 1)}>
            Trang trước
          </button>
          <span>
            Trang {query.data.page + 1} / {query.data.totalPages}
          </span>
          <button type="button" disabled={query.data.last} onClick={() => setPage((value) => value + 1)}>
            Trang sau
          </button>
        </div>
      )}
    </>
  );
}
function label(value: string) {
  return statusLabel(value, 'contract');
}
function formatMoney(value: number) {
  return moneyLabel(value);
}
