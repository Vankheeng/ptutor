import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useStudentDashboard } from '../../hooks/useStudentDashboard';

export function StudentDashboardPage() {
  const { user } = useAuth();
  const { data, isLoading } = useStudentDashboard();
  const firstName = user?.profile?.firstName || 'student';

  return (
    <>
      <section className="student-welcome">
        <div>
          <span className="student-eyebrow">YOUR LEARNING HOME</span>
          <h1>Welcome back, {firstName}.</h1>
          <p>Keep your learning goals, tutor conversations, contracts, and payments in one place.</p>
        </div>
        <div className="student-welcome-art" aria-hidden="true" />
      </section>
      <section className="student-dashboard-stats" aria-label="Learning overview">
        <Stat
          label="Active contracts"
          value={data?.contracts?.content.filter((item) => item.status === 'ACTIVE').length}
          loading={isLoading}
        />
        <Stat label="My studying requests" value={data?.studyingRequests?.totalElements} loading={isLoading} />
        <Stat
          label="Pending applications"
          value={data?.applications?.content.filter((item) => item.status === 'PENDING').length}
          loading={isLoading}
        />
        <Stat label="Unread notifications" value={data?.unreadCount} loading={isLoading} />
      </section>
      {data?.errors.length ? (
        <div className="student-error" role="alert">
          Some dashboard sections could not be loaded. You can still use the available sections below.
        </div>
      ) : null}
      <div className="student-section-heading">
        <div>
          <span className="student-eyebrow">QUICK ACCESS</span>
          <h2>Continue learning</h2>
        </div>
        <Link className="student-link" to="/student/studying-requests">
          View all requests →
        </Link>
      </div>
      <div className="student-card-grid">
        {(data?.studyingRequests?.content ?? []).slice(0, 3).map((request) => (
          <article className="student-panel student-request-card" key={request.id}>
            <div className="student-card-top">
              <span className="student-tag">{request.subjectName}</span>
              <span>{label(request.status)}</span>
            </div>
            <h3>{request.title}</h3>
            <p>{request.description || request.learningGoals || 'No description provided.'}</p>
            <div className="student-card-actions">
              <span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>
              <Link className="student-link" to={`/student/studying-requests/${request.id}`}>
                Open request →
              </Link>
            </div>
          </article>
        ))}
        {!isLoading && !data?.studyingRequests?.content.length ? (
          <div className="student-empty">
            <h3>No studying requests yet</h3>
            <p>Create your first learning request to find a tutor.</p>
            <Link className="student-link" to="/student/studying-requests">
              Create a request →
            </Link>
          </div>
        ) : null}
      </div>
    </>
  );
}

function Stat({ label, value, loading }: { label: string; value?: number | null; loading: boolean }) {
  return (
    <article className="student-stat-card">
      <span className="student-stat-icon">✦</span>
      <div>
        <small>{label}</small>
        <strong>{loading ? '—' : (value ?? '—')}</strong>
      </div>
    </article>
  );
}

function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
