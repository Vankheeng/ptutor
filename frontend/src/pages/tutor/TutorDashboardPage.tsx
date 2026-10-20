import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorDashboard } from '../../hooks/useTutorWorkspace';
import { useAuth } from '../../auth/useAuth';

export function TutorDashboardPage() {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [mode, setMode] = useState<'ALL' | 'ONLINE' | 'OFFLINE'>('ALL');
  const [sort, setSort] = useState<'NEWEST' | 'BUDGET'>('NEWEST');
  const [page, setPage] = useState(0);
  const requestQuery = useStudyingRequests({ page, size: 50, ...(mode === 'ALL' ? {} : { learningMode: mode }) });
  const dashboardQuery = useTutorDashboard();
  const contracts = dashboardQuery.data?.contracts ?? [];
  const upcomingLessons = dashboardQuery.data?.upcomingLessons ?? [];
  const requests = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return [...(requestQuery.data?.content ?? [])]
      .filter(
        (request) =>
          !term ||
          [request.title, request.subjectName, request.gradeName, request.districtName, request.description].some(
            (value) => value?.toLocaleLowerCase().includes(term)
          )
      )
      .sort((a, b) =>
        sort === 'NEWEST'
          ? new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          : (b.maxPrice ?? b.minPrice ?? 0) - (a.maxPrice ?? a.minPrice ?? 0)
      );
  }, [requestQuery.data, search, sort]);

  return (
    <>
      <section className="tutor-welcome-card">
        <div>
          <span className="tutor-eyebrow">GÓC DẠY HỌC</span>
          <h1>Chào {user?.profile?.firstName || 'bạn'}!</h1>
          <p>Theo dõi lớp đang dạy, buổi học sắp tới và học viên mới đang tìm gia sư.</p>
          <a className="tutor-hero-link" href="#marketplace">
            Xem tin tìm gia sư <span>→</span>
          </a>
        </div>
        <div className="tutor-welcome-art" aria-hidden="true">
          <span>✦</span>
          <i />
          <b />
        </div>
      </section>
      <section className="tutor-dashboard-stats" aria-label="Tổng quan dạy học">
        <Stat
          icon="¤"
          tone="violet"
          label="Hợp đồng đang dạy"
          value={contracts.filter((c) => c.status === 'ACTIVE').length}
        />
        <Stat icon="◷" tone="orange" label="Buổi học 7 ngày tới" value={upcomingLessons.length} />
        <Stat icon="✧" tone="green" label="Tin tìm gia sư đang mở" value={requestQuery.data?.totalElements ?? '—'} />
        <Stat
          icon="↗"
          tone="blue"
          label="Hợp đồng chờ ký"
          value={contracts.filter((c) => c.status === 'PENDING').length}
        />
      </section>
      {dashboardQuery.error && (
        <div className="tutor-error" role="alert">
          {dashboardQuery.error}
        </div>
      )}
      <section className="tutor-next-lessons">
        <div className="tutor-section-heading">
          <div>
            <span className="tutor-eyebrow">LỊCH DẠY</span>
            <h2>Buổi học sắp tới</h2>
          </div>
          <span className="tutor-count">7 ngày tới</span>
        </div>
        {upcomingLessons.length === 0 ? (
          <div className="tutor-empty tutor-empty-short">
            <span>◷</span>
            <h3>Chưa có buổi học nào trong 7 ngày tới</h3>
            <p>Buổi học của các hợp đồng đang dạy sẽ hiện ở đây.</p>
          </div>
        ) : (
          <div className="tutor-lesson-grid">
            {upcomingLessons.slice(0, 4).map((lesson) => {
              const contract = contracts.find((item) => item.id === lesson.contractId);
              return (
                <article className="tutor-lesson-card" key={lesson.id}>
                  <span className="tutor-lesson-date">
                    <strong>{new Date(`${lesson.date}T12:00:00`).getDate()}</strong>
                    <small>{new Date(`${lesson.date}T12:00:00`).toLocaleDateString('vi-VN', { month: 'short' })}</small>
                  </span>
                  <div>
                    <h3>{lesson.title}</h3>
                    <p>
                      {[contract?.studentFirstName, contract?.studentLastName].filter(Boolean).join(' ') || 'Học viên'}{' '}
                      · {contract?.subjectName || 'Lớp học'}
                    </p>
                    <small>
                      {lesson.startTime.slice(0, 5)}–{lesson.endTime.slice(0, 5)} ·{' '}
                      {lesson.teachingMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}
                    </small>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
      <section id="marketplace" className="tutor-marketplace">
        <div className="tutor-section-heading">
          <div>
            <span className="tutor-eyebrow">TIN TÌM GIA SƯ</span>
            <h2>Học viên đang tìm gia sư</h2>
          </div>
          <span className="tutor-count">{requestQuery.data?.totalElements ?? 0} tin</span>
        </div>
        <div className="tutor-marketplace-toolbar">
          <label className="tutor-search">
            <span aria-hidden="true">⌕</span>
            <input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Tìm môn, lớp, khu vực…"
              aria-label="Tìm tin"
            />
          </label>
          <label>
            Hình thức học
            <select
              value={mode}
              onChange={(event) => {
                setMode(event.target.value as typeof mode);
                setPage(0);
              }}
            >
              <option value="ALL">Tất cả</option>
              <option value="ONLINE">Online</option>
              <option value="OFFLINE">Trực tiếp</option>
            </select>
          </label>
          <label>
            Sắp xếp
            <select value={sort} onChange={(event) => setSort(event.target.value as typeof sort)}>
              <option value="NEWEST">Mới nhất</option>
              <option value="BUDGET">Học phí cao nhất</option>
            </select>
          </label>
        </div>
        {requestQuery.error ? (
          <div className="tutor-error" role="alert">
            {requestQuery.error}
          </div>
        ) : requestQuery.isLoading ? (
          <div className="tutor-request-grid">
            {[0, 1, 2].map((i) => (
              <div className="tutor-skeleton" key={i} />
            ))}
          </div>
        ) : requests.length === 0 ? (
          <div className="tutor-empty">
            <span>✧</span>
            <h3>{search ? 'Không có tin phù hợp' : 'Chưa có tin nào đang mở'}</h3>
            <p>Thử đổi từ khoá hoặc bộ lọc.</p>
          </div>
        ) : (
          <div className="tutor-request-grid">
            {requests.map((request) => (
              <article className="tutor-request-card" key={request.id}>
                <div className="tutor-card-top">
                  <span className="tutor-subject-tag">{request.subjectName || 'Gia sư'}</span>
                  <span>{request.learningMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</span>
                </div>
                <h3>{request.title || `Tìm gia sư ${request.subjectName}`}</h3>
                <p className="tutor-request-meta">
                  {request.gradeName} · {request.districtName || 'Không giới hạn khu vực'}
                </p>
                <p className="tutor-request-description">
                  {request.description || request.learningGoals || 'Học viên đang tìm gia sư. Mở tin để xem chi tiết.'}
                </p>
                <div className="tutor-request-details">
                  <span>
                    <small>Học phí</small>
                    <strong>{formatBudget(request.minPrice, request.maxPrice)}</strong>
                  </span>
                  <span>
                    <small>Lịch học</small>
                    <strong>{request.preferredSchedule || 'Linh hoạt'}</strong>
                  </span>
                </div>
                <Link
                  className="tutor-button tutor-propose-button tutor-marketplace-open"
                  to={`/tutor/studying-requests/${request.id}`}
                >
                  Xem chi tiết <span>→</span>
                </Link>
              </article>
            ))}
          </div>
        )}
        {!!requestQuery.data?.totalPages && requestQuery.data.totalPages > 1 && (
          <div className="tutor-marketplace-pagination">
            <button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>
              ← Trang trước
            </button>
            <span>
              Trang {page + 1} / {requestQuery.data.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= requestQuery.data.totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Trang sau →
            </button>
          </div>
        )}
      </section>
    </>
  );
}

function Stat({ icon, tone, label, value }: { icon: string; tone: string; label: string; value: string | number }) {
  return (
    <article className="tutor-stat-card">
      <span className={`tutor-stat-icon ${tone}`}>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </article>
  );
}
function formatBudget(min: number | null, max: number | null) {
  const format = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
  if (min === null && max === null) return 'Thoả thuận';
  if (min !== null && max !== null) return `${format(min)}–${format(max)} đ`;
  return `${format(min ?? max ?? 0)} đ`;
}
