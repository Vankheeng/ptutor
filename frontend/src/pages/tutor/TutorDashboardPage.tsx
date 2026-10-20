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
          <span className="tutor-eyebrow">YOUR TEACHING HOME</span>
          <h1>Welcome back, {user?.profile?.firstName || 'tutor'}.</h1>
          <p>Keep track of your active classes, next lessons, and new students looking for a tutor.</p>
          <a className="tutor-hero-link" href="#marketplace">
            Explore student requests <span>→</span>
          </a>
        </div>
        <div className="tutor-welcome-art" aria-hidden="true">
          <span>✦</span>
          <i />
          <b />
        </div>
      </section>
      <section className="tutor-dashboard-stats" aria-label="Teaching overview">
        <Stat
          icon="¤"
          tone="violet"
          label="Active contracts"
          value={contracts.filter((c) => c.status === 'ACTIVE').length}
        />
        <Stat icon="◷" tone="orange" label="Lessons next 7 days" value={upcomingLessons.length} />
        <Stat icon="✧" tone="green" label="Open student requests" value={requestQuery.data?.totalElements ?? '—'} />
        <Stat
          icon="↗"
          tone="blue"
          label="Pending contracts"
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
            <span className="tutor-eyebrow">YOUR SCHEDULE</span>
            <h2>Next lessons</h2>
          </div>
          <span className="tutor-count">Next 7 days</span>
        </div>
        {upcomingLessons.length === 0 ? (
          <div className="tutor-empty tutor-empty-short">
            <span>◷</span>
            <h3>No lessons scheduled for next week</h3>
            <p>Lessons from your active contracts will appear here.</p>
          </div>
        ) : (
          <div className="tutor-lesson-grid">
            {upcomingLessons.slice(0, 4).map((lesson) => {
              const contract = contracts.find((item) => item.id === lesson.contractId);
              return (
                <article className="tutor-lesson-card" key={lesson.id}>
                  <span className="tutor-lesson-date">
                    <strong>{new Date(`${lesson.date}T12:00:00`).getDate()}</strong>
                    <small>{new Date(`${lesson.date}T12:00:00`).toLocaleDateString('en', { month: 'short' })}</small>
                  </span>
                  <div>
                    <h3>{lesson.title}</h3>
                    <p>
                      {[contract?.studentFirstName, contract?.studentLastName].filter(Boolean).join(' ') || 'Student'} ·{' '}
                      {contract?.subjectName || 'Class'}
                    </p>
                    <small>
                      {lesson.startTime.slice(0, 5)}–{lesson.endTime.slice(0, 5)} ·{' '}
                      {lesson.teachingMode === 'ONLINE' ? 'Online' : 'In person'}
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
            <span className="tutor-eyebrow">STUDENT MARKETPLACE</span>
            <h2>Open learning requests</h2>
          </div>
          <span className="tutor-count">{requestQuery.data?.totalElements ?? 0} available</span>
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
              placeholder="Search subject, grade, area..."
              aria-label="Search requests"
            />
          </label>
          <label>
            Learning mode
            <select
              value={mode}
              onChange={(event) => {
                setMode(event.target.value as typeof mode);
                setPage(0);
              }}
            >
              <option value="ALL">All modes</option>
              <option value="ONLINE">Online</option>
              <option value="OFFLINE">In person</option>
            </select>
          </label>
          <label>
            Sort by
            <select value={sort} onChange={(event) => setSort(event.target.value as typeof sort)}>
              <option value="NEWEST">Newest</option>
              <option value="BUDGET">Highest budget</option>
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
            <h3>{search ? 'No matching requests' : 'No open requests yet'}</h3>
            <p>Try changing your search or filters.</p>
          </div>
        ) : (
          <div className="tutor-request-grid">
            {requests.map((request) => (
              <article className="tutor-request-card" key={request.id}>
                <div className="tutor-card-top">
                  <span className="tutor-subject-tag">{request.subjectName || 'Tutoring'}</span>
                  <span>{request.learningMode === 'ONLINE' ? 'Online' : 'In person'}</span>
                </div>
                <h3>{request.title || `${request.subjectName} tutoring`}</h3>
                <p className="tutor-request-meta">
                  {request.gradeName} · {request.districtName || 'Location flexible'}
                </p>
                <p className="tutor-request-description">
                  {request.description ||
                    request.learningGoals ||
                    'The student is looking for a tutor. Open the request to see more details.'}
                </p>
                <div className="tutor-request-details">
                  <span>
                    <small>Budget</small>
                    <strong>{formatBudget(request.minPrice, request.maxPrice)}</strong>
                  </span>
                  <span>
                    <small>Schedule</small>
                    <strong>{request.preferredSchedule || 'Flexible'}</strong>
                  </span>
                </div>
                <Link
                  className="tutor-button tutor-propose-button tutor-marketplace-open"
                  to={`/tutor/studying-requests/${request.id}`}
                >
                  View details <span>→</span>
                </Link>
              </article>
            ))}
          </div>
        )}
        {!!requestQuery.data?.totalPages && requestQuery.data.totalPages > 1 && (
          <div className="tutor-marketplace-pagination">
            <button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>
              ← Previous
            </button>
            <span>
              Page {page + 1} of {requestQuery.data.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= requestQuery.data.totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Next →
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
  if (min === null && max === null) return 'Negotiable';
  if (min !== null && max !== null) return `${format(min)}–${format(max)} VND`;
  return `${format(min ?? max ?? 0)} VND`;
}
