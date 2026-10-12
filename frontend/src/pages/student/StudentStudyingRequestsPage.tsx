import { useCallback, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  createStudyingRequest,
  getMyStudyingRequests,
  type LearningMode,
  type RequestStatus,
  type StudyingRequestInput
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

const statuses: Array<'ALL' | RequestStatus> = ['ALL', 'DRAFT', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED'];

export function StudentStudyingRequestsPage() {
  const [status, setStatus] = useState<'ALL' | RequestStatus>('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const load = useCallback(
    (signal: AbortSignal) => getMyStudyingRequests(status === 'ALL' ? undefined : status, page, 20, signal),
    [page, status]
  );
  const query = useResource(load, revision);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const value = (key: string) => String(values.get(key) ?? '').trim();
    const input: StudyingRequestInput = {
      subjectId: value('subjectId'),
      gradeId: value('gradeId'),
      quantity: Number(value('quantity')),
      title: value('title'),
      description: value('description') || undefined,
      learningMode: value('learningMode') as LearningMode,
      preferredSchedule: value('preferredSchedule') || undefined
    };
    setError('');
    setNotice('');
    try {
      await createStudyingRequest(input);
      setShowForm(false);
      setRevision((current) => current + 1);
      setNotice('Your studying request was created as a draft.');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not create this request.'
      );
    }
  };

  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">YOUR LEARNING NEEDS</span>
          <h1>My Studying Requests</h1>
          <p>Create and manage the requests you send to tutors.</p>
        </div>
        <button className="student-button" type="button" onClick={() => setShowForm((value) => !value)}>
          {showForm ? 'Close form' : 'Create request'}
        </button>
      </div>
      {notice && (
        <div className="student-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      {showForm && (
        <section className="student-panel" style={{ marginBottom: 16 }}>
          <h2>Create a studying request</h2>
          <form className="student-form-grid" onSubmit={(event) => void create(event)}>
            <label>
              Subject ID
              <input name="subjectId" required placeholder="Subject UUID" />
            </label>
            <label>
              Grade ID
              <input name="gradeId" required placeholder="Grade UUID" />
            </label>
            <label>
              Quantity
              <input name="quantity" type="number" min="1" defaultValue="1" required />
            </label>
            <label>
              Learning mode
              <select name="learningMode" defaultValue="ONLINE">
                <option value="ONLINE">Online</option>
                <option value="OFFLINE">In person</option>
              </select>
            </label>
            <label className="student-field-full">
              Title
              <input name="title" maxLength={255} required />
            </label>
            <label className="student-field-full">
              Description
              <textarea name="description" rows={3} />
            </label>
            <label className="student-field-full">
              Preferred schedule
              <input name="preferredSchedule" placeholder="Weekday evenings" />
            </label>
            <div className="student-form-actions">
              <button className="student-button" type="submit">
                Create draft
              </button>
            </div>
          </form>
        </section>
      )}
      <div className="student-filter-tabs" role="tablist">
        {statuses.map((item) => (
          <button
            key={item}
            className={status === item ? 'active' : ''}
            type="button"
            onClick={() => {
              setStatus(item);
              setPage(0);
            }}
          >
            {item === 'ALL' ? 'All' : label(item)}
          </button>
        ))}
      </div>
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
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
      {query.isLoading ? (
        <div className="student-card-grid">
          {[1, 2, 3].map((item) => (
            <div className="student-skeleton" key={item} />
          ))}
        </div>
      ) : query.data?.content.length ? (
        <div className="student-card-grid">
          {query.data.content.map((request) => (
            <article className="student-panel student-request-card" key={request.id}>
              <div className="student-card-top">
                <span className="student-tag">{request.subjectName}</span>
                <span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>
              </div>
              <h3>{request.title}</h3>
              <p>{request.description || request.learningGoals || 'No description provided.'}</p>
              <div className="student-card-actions">
                <span>
                  {request.gradeName} · {request.learningMode === 'ONLINE' ? 'Online' : 'In person'}
                </span>
                <Link className="student-link" to={`/student/studying-requests/${request.id}`}>
                  View details →
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>No requests in this view</h3>
          <p>Try another status or create a new studying request.</p>
        </div>
      )}
    </>
  );
}

function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
