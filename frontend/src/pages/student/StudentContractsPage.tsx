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
          <span className="student-eyebrow">YOUR AGREEMENTS</span>
          <h1>Contracts</h1>
          <p>View and manage your tutoring contracts with tutors.</p>
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
            {item === 'ALL' ? 'All' : label(item)}
          </button>
        ))}
      </div>
      {query.isLoading ? (
        <div className="student-empty">
          <h3>Loading contracts…</h3>
        </div>
      ) : contractPage?.content.length ? (
        <div className="student-list">
          {contractPage.content.map((contract) => (
            <StudentContractCard contract={contract} key={contract.id} />
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>No contracts in this view</h3>
          <p>Accepted tutor offers can become contracts.</p>
        </div>
      )}
      {contractPage?.content.length ? (
        <div className="student-pagination student-contract-pagination">
          <button
            className="student-pagination-arrow"
            type="button"
            aria-label="Previous page"
            disabled={contractPage.first}
            onClick={() => setPage((value) => value - 1)}
          >
            ‹
          </button>
          <div className="student-pagination-pages" aria-label="Contract pages">
            {Array.from({ length: Math.max(contractPage.totalPages, 1) }, (_, index) => (
              <button
                className={index === contractPage.page ? 'active' : ''}
                key={index}
                type="button"
                aria-label={`Page ${index + 1}`}
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
            aria-label="Next page"
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
  return value.toLowerCase().replaceAll('_', ' ');
}
