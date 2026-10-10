import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { getContracts, type ContractStatus } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

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
          <span className="student-eyebrow">YOUR AGREEMENTS</span>
          <h1>Contracts</h1>
          <p>Review your learning agreements and next steps.</p>
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
            {item === 'ALL' ? 'All' : label(item)}
          </button>
        ))}
      </div>
      {query.isLoading ? (
        <div className="student-empty">
          <h3>Loading contracts…</h3>
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
                  {[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Tutor'} ·{' '}
                  {contract.paymentPeriod}
                  <br />
                  {formatMoney(contract.price)} · {contract.startDate} to {contract.endDate}
                </p>
              </div>
              <div>
                <span className={`student-status ${contract.status.toLowerCase()}`}>{label(contract.status)}</span>
                <br />
                <Link className="student-link" to={`/student/contracts/${contract.id}`}>
                  View contract →
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>No contracts in this view</h3>
          <p>Accepted tutor offers can become contracts.</p>
        </div>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="student-pagination">
          <button type="button" disabled={query.data.first} onClick={() => setPage((value) => value - 1)}>
            Previous
          </button>
          <span>
            Page {query.data.page + 1} of {query.data.totalPages}
          </span>
          <button type="button" disabled={query.data.last} onClick={() => setPage((value) => value + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  );
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
