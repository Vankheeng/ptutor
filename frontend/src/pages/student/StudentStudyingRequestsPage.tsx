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
import { StudentPagination } from '../../components/student/StudentPagination';
import { StudentStudyingRequestCard } from '../../components/student/StudentStudyingRequestCard';
import { StudentStudyingRequestToolbar } from '../../components/student/StudentStudyingRequestToolbar';
import type { StudyingRequestAction } from '../../components/student/StudentStudyingRequestMenu';
import { useResource } from '../../hooks/useResource';

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

  const run = async (id: string, action: StudyingRequestAction) => {
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
      if (action === 'open' || action === 'close') {
        await updateStudyingRequestStatus(id, action.toUpperCase() as RequestStatus);
      }
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
      <StudentStudyingRequestToolbar
        status={status}
        sort={sort}
        onStatusChange={(nextStatus) => {
          setStatus(nextStatus);
          setPage(0);
        }}
        onSortChange={(nextSort) => {
          setSort(nextSort);
          setPage(0);
        }}
      />
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      )}
      {query.data && (
        <StudentPagination
          page={query.data.page}
          totalPages={query.data.totalPages}
          first={query.data.first}
          last={query.data.last}
          onPrevious={() => setPage((value) => value - 1)}
          onNext={() => setPage((value) => value + 1)}
        />
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
            <StudentStudyingRequestCard
              key={request.id}
              request={request}
              menuOpen={menuId === request.id}
              busy={busy === request.id}
              menuRef={menuId === request.id ? menuRef : undefined}
              onOpen={(id) => navigate(`/student/studying-requests/${id}`)}
              onToggleMenu={(id) => setMenuId((currentId) => (currentId === id ? null : id))}
              onAction={run}
              onNavigate={navigate}
            />
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
