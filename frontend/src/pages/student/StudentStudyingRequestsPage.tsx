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
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
  const menuRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(
    (signal: AbortSignal) =>
      getMyStudyingRequests(status === 'ALL' ? undefined : status, page, 20, signal, sort, searchKeyword),
    [status, page, sort, searchKeyword]
  );
  const query = useResource(load, revision);
  const normalizedKeyword = keyword.trim().toLocaleLowerCase('vi');
  const visibleRequests = (query.data?.content ?? []).filter((request) => {
    if (!normalizedKeyword) return true;
    return [request.title, request.subjectName, request.note].some((value) =>
      value?.toLocaleLowerCase('vi').includes(normalizedKeyword)
    );
  });

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
      setNotice(
        action === 'cancel'
          ? 'Đã hủy yêu cầu tìm gia sư.'
          : action === 'open'
            ? 'Đã mở lại yêu cầu.'
            : 'Đã đóng yêu cầu.'
      );
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
          <h1>Yêu cầu tìm gia sư của tôi</h1>
        </div>
        <Link className="student-button draft" to="/student/studying-requests/new">
          Tạo yêu cầu
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
        keyword={keyword}
        onKeywordChange={(value) => {
          setKeyword(value);
          setSearchKeyword(value.trim());
          setPage(0);
        }}
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
          previousLabel="Trước"
          nextLabel="Sau"
          pageLabel="Trang"
          ofLabel="trên"
        />
      )}
      {query.isLoading ? (
        <div className="student-card-grid">
          {[1, 2, 3].map((item) => (
            <div className="student-skeleton" key={item} />
          ))}
        </div>
      ) : visibleRequests.length ? (
        <div className="student-card-grid">
          {visibleRequests.map((request) => (
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
          <h3>Không có yêu cầu phù hợp</h3>
          <p>Thử thay đổi từ khóa hoặc trạng thái lọc.</p>
        </div>
      )}
    </>
  );
}
