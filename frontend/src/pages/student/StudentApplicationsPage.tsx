import { useCallback, useState } from 'react';
import { ApiError } from '../../services/httpClient';
import { cancelStudentApplication, getStudentApplications, type ApplicationStatus } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

const filters: Array<'ALL' | ApplicationStatus> = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'];

export function StudentApplicationsPage() {
  const [filter, setFilter] = useState<'ALL' | ApplicationStatus>('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const load = useCallback(
    (signal: AbortSignal) => getStudentApplications(filter === 'ALL' ? undefined : filter, page, 20, signal),
    [filter, page]
  );
  const query = useResource(load, revision);
  const cancel = async (id: string) => {
    setError('');
    try {
      await cancelStudentApplication(id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not cancel this application.');
    }
  };
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">YOUR APPLICATIONS</span>
          <h1>My requests to studying</h1>
          <p>Track the requests you sent to tutors.</p>
        </div>
      </div>
      {(error || query.error) && (
        <div className="student-error" role="alert">
          {error || query.error}
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
          <h3>Loading applications…</h3>
        </div>
      ) : query.data?.content.length ? (
        <div className="student-list">
          {query.data.content.map((item) => (
            <article className="student-list-row" key={item.id}>
              <div>
                <h3>{item.teachingRequestTitle || item.subjectName || item.customSubjectName || 'Teaching request'}</h3>
                <p>
                  {[item.tutorFirstName, item.tutorLastName].filter(Boolean).join(' ') || 'Tutor'} · {item.gradeName}
                  <br />
                  {formatMoney(item.proposedPrice)} · {item.learningMode === 'ONLINE' ? 'Online' : 'In person'}
                </p>
              </div>
              <div>
                <span className={`student-status ${item.status.toLowerCase()}`}>{label(item.status)}</span>
                {item.status === 'PENDING' && (
                  <button
                    className="student-button secondary"
                    type="button"
                    style={{ marginTop: 8 }}
                    onClick={() => void cancel(item.id)}
                  >
                    Cancel
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>No applications in this view</h3>
          <p>Browse teaching requests to send your first application.</p>
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
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
