import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { Link } from 'react-router-dom';
import { useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorDashboard } from '../../hooks/useTutorWorkspace';
import {
  submitTutorStudyingRequestProposal,
  type StudyingRequestSearchResponse
} from '../../services/studyingRequestApi';
import { useAuth } from '../../auth/useAuth';

export function TutorDashboardPage() {
  const { user } = useAuth();
  const requestQuery = useStudyingRequests({ page: 0, size: 30 });
  const dashboardQuery = useTutorDashboard();
  const requests = requestQuery.data?.content ?? [];
  const totalRequests = requestQuery.error ? '—' : (requestQuery.data?.totalElements ?? 0);
  const contracts = dashboardQuery.data?.contracts ?? [];
  const upcomingLessons = dashboardQuery.data?.upcomingLessons ?? [];
  const [activeRequest, setActiveRequest] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [sending, setSending] = useState(false);

  const submitProposal = async (event: FormEvent<HTMLFormElement>, request: StudyingRequestSearchResponse) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setSending(true);
    setNotice('');
    try {
      await submitTutorStudyingRequestProposal(request.id, {
        gradeId: request.gradeId,
        proposedPrice: Number(values.get('proposedPrice')),
        teachingMode: String(values.get('teachingMode')) as 'ONLINE' | 'OFFLINE',
        preferredSchedule: String(values.get('preferredSchedule') ?? ''),
        message: String(values.get('message') ?? '')
      });
      setActiveRequest(null);
      setNotice('Your teaching proposal was sent.');
    } catch (caught) {
      setNotice(
        caught instanceof ApiError && caught.status === 409
          ? 'You already have a pending proposal for this request.'
          : caught instanceof Error
            ? caught.message
            : 'Could not send your proposal.'
      );
    } finally {
      setSending(false);
    }
  };

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
        <article className="tutor-stat-card">
          <span className="tutor-stat-icon violet">▤</span>
          <div>
            <small>Active contracts</small>
            <strong>{contracts.filter((contract) => contract.status === 'ACTIVE').length}</strong>
          </div>
        </article>
        <article className="tutor-stat-card">
          <span className="tutor-stat-icon orange">◷</span>
          <div>
            <small>Lessons next 7 days</small>
            <strong>{upcomingLessons.length}</strong>
          </div>
        </article>
        <article className="tutor-stat-card">
          <span className="tutor-stat-icon green">✧</span>
          <div>
            <small>Open student requests</small>
            <strong>{totalRequests}</strong>
          </div>
        </article>
        <article className="tutor-stat-card">
          <span className="tutor-stat-icon blue">↗</span>
          <div>
            <small>Pending contracts</small>
            <strong>{contracts.filter((contract) => contract.status === 'PENDING').length}</strong>
          </div>
        </article>
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
      <div className="tutor-section-heading">
        <div id="marketplace">
          <span className="tutor-eyebrow">STUDENT MARKETPLACE</span>
          <h2>Open learning requests</h2>
        </div>
        <span className="tutor-count">{totalRequests} available</span>
      </div>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {requestQuery.error && (
        <div className="tutor-error" role="alert">
          {requestQuery.error}
        </div>
      )}
      {requestQuery.error ? null : requestQuery.isLoading ? (
        <div className="tutor-request-grid">
          {[0, 1, 2].map((item) => (
            <div className="tutor-skeleton" key={item} />
          ))}
        </div>
      ) : requests.length === 0 ? (
        <div className="tutor-empty">
          <span>✧</span>
          <h3>No open requests yet</h3>
          <p>New student requests will appear here when they are available.</p>
        </div>
      ) : (
        <div className="tutor-request-grid">
          {requests.map((request) => (
            <article className="tutor-request-card" key={request.id}>
              <div className="tutor-card-top">
                <span className="tutor-subject-tag">{request.subjectName}</span>
                <span>{request.learningMode === 'ONLINE' ? 'Online' : 'In person'}</span>
              </div>
              <h3>{request.title || `${request.subjectName} tutoring`}</h3>
              <p className="tutor-request-meta">
                {request.gradeName} <span>·</span> {request.quantity} student{request.quantity === 1 ? '' : 's'}
                {request.districtName ? ` · ${request.districtName}` : ''}
              </p>
              <p className="tutor-request-description">
                {request.description ||
                  request.learningGoals ||
                  'The student is looking for a tutor. Send a proposal to discuss the learning plan.'}
              </p>
              <Link className="tutor-detail-link" to={`/tutor/studying-requests/${request.id}`}>
                View request details ↗
              </Link>
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
              {activeRequest === request.id ? (
                <form className="tutor-proposal-form" onSubmit={(event) => void submitProposal(event, request)}>
                  <label>
                    Proposed price (VND)
                    <input
                      name="proposedPrice"
                      type="number"
                      min="0"
                      defaultValue={request.maxPrice ?? request.minPrice ?? ''}
                      required
                    />
                  </label>
                  <div className="tutor-form-row">
                    <label>
                      Teaching mode
                      <select name="teachingMode" defaultValue={request.learningMode}>
                        <option value="ONLINE">Online</option>
                        <option value="OFFLINE">In person</option>
                      </select>
                    </label>
                    <label>
                      Schedule
                      <input
                        name="preferredSchedule"
                        defaultValue={request.preferredSchedule ?? ''}
                        placeholder="e.g. Weekday evenings"
                      />
                    </label>
                  </div>
                  <label>
                    Message
                    <textarea name="message" rows={3} placeholder="Introduce yourself and how you can help." />
                  </label>
                  <div className="tutor-form-actions">
                    <button className="tutor-button secondary" type="button" onClick={() => setActiveRequest(null)}>
                      Cancel
                    </button>
                    <button className="tutor-button" type="submit" disabled={sending}>
                      {sending ? 'Sending…' : 'Send proposal'}
                    </button>
                  </div>
                </form>
              ) : (
                <button
                  className="tutor-button tutor-propose-button"
                  type="button"
                  onClick={() => setActiveRequest(request.id)}
                >
                  Send a proposal <span>→</span>
                </button>
              )}
            </article>
          ))}
        </div>
      )}
    </>
  );
}

function formatBudget(min: number | null, max: number | null) {
  const format = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
  if (min === null && max === null) return 'Negotiable';
  if (min !== null && max !== null) return `${format(min)}–${format(max)} VND`;
  return `${format(min ?? max ?? 0)} VND`;
}
