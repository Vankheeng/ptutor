import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  cancelStudyingRequest,
  getMyStudyingRequests,
  payStudyingRequest,
  updateStudyingRequestStatus,
  type RequestStatus
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

const statuses: Array<'ALL' | RequestStatus> = [
  'ALL',
  'DRAFT',
  'PENDING_REVIEW',
  'OPEN',
  'MATCHED',
  'CLOSED',
  'CANCELLED',
  'REJECTED'
];

export function StudentStudyingRequestsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [status, setStatus] = useState<'ALL' | RequestStatus>('ALL');
  const [sort, setSort] = useState<'ASC' | 'DESC'>('DESC');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
  const menuRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(
    (signal: AbortSignal) => getMyStudyingRequests(status === 'ALL' ? undefined : status, page, 20, signal, sort),
    [status, page, sort]
  );
  const query = useResource(load, revision);

  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  useEffect(() => {
    if (!menuId) return;
    const closeOutside = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuId(null);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuId(null);
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [menuId]);

  const run = async (id: string, action: 'cancel' | 'pay' | 'open' | 'close') => {
    setBusy(id);
    setError('');
    setMenuId(null);
    try {
      if (action === 'cancel') await cancelStudyingRequest(id);
      if (action === 'pay') {
        const payment = await payStudyingRequest(id);
        window.location.assign(payment.paymentUrl);
        return;
      }
      if (action === 'open' || action === 'close')
        await updateStudyingRequestStatus(id, action.toUpperCase() as RequestStatus);
      setNotice(action === 'cancel' ? 'Studying request cancelled.' : `Request ${action}d.`);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Could not update this request.'
      );
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">YOUR LEARNING NEEDS</span>
          <h1>My Studying Requests</h1>
          <p>Manage the requests you send to tutors.</p>
        </div>
        <Link className="student-button draft" to="/student/studying-requests/new">
          Create request
        </Link>
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
      <div className="student-toolbar">
        <label>
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as 'ALL' | RequestStatus);
              setPage(0);
            }}
          >
            <option value="ALL">All statuses</option>
            {statuses.slice(1).map((item) => (
              <option key={item} value={item}>
                {label(item)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Sort by
          <select
            value={sort}
            onChange={(event) => {
              setSort(event.target.value as 'ASC' | 'DESC');
              setPage(0);
            }}
          >
            <option value="DESC">Newest first</option>
            <option value="ASC">Oldest first</option>
          </select>
        </label>
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
            <article
              className={`student-panel student-request-card status-${request.status.toLowerCase()}`}
              key={request.id}
              onClick={() => navigate(`/student/studying-requests/${request.id}`)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') navigate(`/student/studying-requests/${request.id}`);
              }}
              tabIndex={0}
              role="link"
            >
              <div className="student-card-top">
                <span className="student-tag">{request.subjectName || 'Custom subject'}</span>
                <div className="student-card-menu" ref={menuId === request.id ? menuRef : undefined}>
                  <span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>
                  <button
                    type="button"
                    aria-label="Request actions"
                    onClick={(event) => {
                      event.stopPropagation();
                      setMenuId(menuId === request.id ? null : request.id);
                    }}
                  >
                    ⋯
                  </button>
                  {menuId === request.id && (
                    <RequestMenu
                      status={request.status}
                      requestId={request.id}
                      busy={busy === request.id}
                      onAction={run}
                      onNavigate={navigate}
                    />
                  )}
                </div>
              </div>
              <h3>{request.title}</h3>
              <p>{request.description || request.learningGoals || request.note || 'No description provided.'}</p>
              <div className="student-card-actions">
                <span>
                  {request.gradeName} · {request.learningMode === 'ONLINE' ? 'Online' : 'In person'}
                </span>
                <span className="student-card-created">Created {formatDate(request.createdAt)}</span>
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

function RequestMenu({
  status,
  requestId,
  busy,
  onAction,
  onNavigate
}: {
  status: RequestStatus;
  requestId: string;
  busy: boolean;
  onAction: (id: string, action: 'cancel' | 'pay' | 'open' | 'close') => void;
  onNavigate: (path: string) => void;
}) {
  const item = (labelText: string, action: () => void, className = '') => (
    <button
      className={className}
      type="button"
      disabled={busy}
      onClick={(event) => {
        event.stopPropagation();
        action();
      }}
    >
      {labelText}
    </button>
  );
  return (
    <div className="student-request-menu" onClick={(event) => event.stopPropagation()}>
      {(status === 'DRAFT' || status === 'OPEN') &&
        item('Edit information', () => onNavigate(`/student/studying-requests/${requestId}/edit`), 'warning')}
      {status === 'DRAFT' && item('Pay to publish', () => onAction(requestId, 'pay'), 'payment')}
      {status === 'OPEN' && item('Close request', () => onAction(requestId, 'close'))}
      {status === 'CLOSED' && item('Reopen request', () => onAction(requestId, 'open'))}
      {['DRAFT', 'OPEN', 'MATCHED', 'CLOSED'].includes(status) &&
        item('Cancel request', () => onAction(requestId, 'cancel'), 'danger')}
    </div>
  );
}

function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
