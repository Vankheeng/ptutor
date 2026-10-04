import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest } from '../../hooks/useStudyingRequests';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';

export function TutorStudyingRequestPage() {
  const { requestId = '' } = useParams();
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const propose = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!request) return;
    const data = new FormData(event.currentTarget);
    setSending(true);
    setError('');
    setNotice('');
    try {
      await submitTutorStudyingRequestProposal(request.id, {
        gradeId: request.gradeId,
        proposedPrice: Number(data.get('proposedPrice')),
        teachingMode: String(data.get('teachingMode')) as 'ONLINE' | 'OFFLINE',
        preferredSchedule: String(data.get('preferredSchedule') ?? ''),
        message: String(data.get('message') ?? '')
      });
      setNotice('Your proposal was sent to the student.');
    } catch (caught) {
      setError(
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

  if (loading) return <div className="tutor-empty">Loading request…</div>;
  if (!request)
    return (
      <>
        <Link className="tutor-detail-link" to="/tutor/dashboard">
          ← Back to dashboard
        </Link>
        <div className="tutor-error" role="alert">
          {error || requestError || 'This request is no longer available.'}
        </div>
      </>
    );

  return (
    <>
      <Link className="tutor-detail-link tutor-back-link" to="/tutor/dashboard">
        ← Back to dashboard
      </Link>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">STUDENT REQUEST</span>
          <h1>{request.title || `${request.subjectName} tutoring`}</h1>
          <p>
            {request.subjectName} · {request.gradeName} · {request.learningMode === 'ONLINE' ? 'Online' : 'In person'}
          </p>
        </div>
        <span className="tutor-profile-badge">Open request</span>
      </div>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="tutor-request-detail-layout">
        <section className="tutor-panel tutor-form-panel">
          <span className="tutor-eyebrow">LEARNING GOALS</span>
          <h2 className="tutor-detail-title">What the student needs</h2>
          <p className="tutor-detail-copy">
            {request.description || request.learningGoals || 'The student has not added more details yet.'}
          </p>
          {request.learningGoals && request.description && (
            <>
              <h3 className="tutor-small-heading">Goals</h3>
              <p className="tutor-detail-copy">{request.learningGoals}</p>
            </>
          )}
          <div className="tutor-detail-facts">
            <span>
              <small>Student count</small>
              <strong>{request.quantity}</strong>
            </span>
            <span>
              <small>Budget</small>
              <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
            </span>
            <span>
              <small>Location</small>
              <strong>
                {request.districtName || (request.learningMode === 'ONLINE' ? 'Online' : 'Not specified')}
              </strong>
            </span>
            <span>
              <small>Preferred schedule</small>
              <strong>{request.preferredSchedule || 'Flexible'}</strong>
            </span>
          </div>
          {request.availabilities.length > 0 && (
            <>
              <h3 className="tutor-small-heading">Availability</h3>
              <div className="tutor-availability-list">
                {request.availabilities.map((slot, index) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                    {weekday(slot.dayOfWeek)} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                  </span>
                ))}
              </div>
            </>
          )}
        </section>
        <section className="tutor-panel tutor-form-panel">
          <div className="tutor-panel-heading">
            <span>↗</span>
            <div>
              <h2>Send a teaching proposal</h2>
              <p>Introduce yourself and include your proposed price.</p>
            </div>
          </div>
          {notice ? (
            <div className="tutor-empty compact">
              <span>✓</span>
              <h3>Proposal sent</h3>
              <p>You can follow it from “My requests to teach”.</p>
              <Link className="tutor-detail-link" to="/tutor/proposals">
                View my proposals →
              </Link>
            </div>
          ) : (
            <form className="tutor-proposal-form" onSubmit={(event) => void propose(event)}>
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
              <label>
                Teaching mode
                <select name="teachingMode" defaultValue={request.learningMode}>
                  <option value="ONLINE">Online</option>
                  <option value="OFFLINE">In person</option>
                </select>
              </label>
              <label>
                Preferred schedule
                <input name="preferredSchedule" defaultValue={request.preferredSchedule ?? ''} maxLength={500} />
              </label>
              <label>
                Message to student
                <textarea
                  name="message"
                  rows={5}
                  maxLength={10000}
                  placeholder="Share your experience and how you would help."
                />
              </label>
              <button className="tutor-button" type="submit" disabled={sending}>
                {sending ? 'Sending…' : 'Send proposal'}
              </button>
            </form>
          )}
        </section>
      </div>
    </>
  );
}

function formatPrice(min: number | null, max: number | null) {
  const format = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
  if (min === null && max === null) return 'Negotiable';
  return min !== null && max !== null ? `${format(min)}–${format(max)} VND` : `${format(min ?? max ?? 0)} VND`;
}

function weekday(dayOfWeek: number) {
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][dayOfWeek % 7];
}
