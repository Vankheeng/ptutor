import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest, useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';
import { updateTutorProposal } from '../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';

const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

export function TutorStudyingRequestPage() {
  const navigate = useNavigate();
  const { requestId = '' } = useParams();
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const relatedQuery = useStudyingRequests({ page: 0, size: 30 });
  const [proposalRevision, setProposalRevision] = useState(0);
  const proposalsQuery = useTutorProposals(undefined, proposalRevision);
  const myProposal = proposalsQuery.data?.find((proposal) => proposal.studyingRequestId === requestId);
  const [showApply, setShowApply] = useState(false);
  const [editingProposal, setEditingProposal] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const recommendations = useMemo(
    () =>
      (relatedQuery.data?.content ?? [])
        .filter((item) => item.id !== requestId)
        .sort((a, b) => Number(b.subjectId === request?.subjectId) - Number(a.subjectId === request?.subjectId))
        .slice(0, 3),
    [relatedQuery.data, requestId, request?.subjectId]
  );

  const propose = async (values: TutorApplicationValues) => {
    if (!request) return;
    setSending(true);
    setError('');
    setNotice('');
    try {
      const payload = {
        ...values,
        gradeId: request.gradeId
      };
      if (editingProposal && myProposal) {
        await updateTutorProposal(myProposal.id, payload);
        setNotice('Your application has been updated.');
      } else {
        await submitTutorStudyingRequestProposal(request.id, payload);
        setNotice(
          myProposal?.status === 'CANCELLED'
            ? 'Your application was sent again.'
            : 'Your proposal was sent to the student.'
        );
      }
      setShowApply(false);
      setEditingProposal(false);
      setProposalRevision((value) => value + 1);
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

  const openApplication = (edit: boolean) => {
    setError('');
    setEditingProposal(edit);
    setShowApply(true);
  };

  if (loading) return <div className="tutor-empty">Loading request…</div>;
  if (!request)
    return (
      <>
        <button
          className="tutor-button secondary tutor-navigation-button"
          type="button"
          onClick={() => navigate('/tutor/dashboard#marketplace')}
        >
          ← Back to requests
        </button>
        <div className="tutor-error" role="alert">
          {requestError || 'This request is no longer available.'}
        </div>
      </>
    );

  return (
    <>
      <button
        className="tutor-button secondary tutor-navigation-button tutor-back-link"
        type="button"
        onClick={() => navigate('/tutor/dashboard#marketplace')}
      >
        ← Back to requests
      </button>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}{' '}
          <button
            className="tutor-button secondary tutor-navigation-compact"
            type="button"
            onClick={() => navigate('/tutor/proposals')}
          >
            View my proposals →
          </button>
        </div>
      )}
      {error && !showApply && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="tutor-request-detail-layout tutor-market-detail">
        <article className="tutor-panel tutor-detail-main">
          <div className="tutor-card-top">
            <span className="tutor-subject-tag">{request.subjectName || 'Tutoring'}</span>
            <span>{request.learningMode === 'ONLINE' ? 'Online' : 'In person'} · Open request</span>
          </div>
          <h1 className="tutor-detail-heading">{request.title || `${request.subjectName} tutoring`}</h1>
          <p className="tutor-detail-subtitle">{request.gradeName}</p>
          <p className="tutor-request-author">
            <span>
              Posted by{' '}
              <strong>
                {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Student'}
              </strong>
            </span>
            <span>Posted {new Date(request.createdAt).toLocaleDateString('vi-VN')}</span>
          </p>
          <div className="tutor-detail-facts">
            <span>
              <small>Budget</small>
              <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
            </span>
            <span>
              <small>Learning mode</small>
              <strong>{request.learningMode === 'ONLINE' ? 'Online' : 'In person'}</strong>
            </span>
            {request.learningMode === 'OFFLINE' && (
              <span>
                <small className="tutor-detail-fact-tag">Teaching address</small>
                <strong>
                  {[request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                    'Not specified'}
                </strong>
              </span>
            )}
            <span>
              <small>Preferred schedule</small>
              <strong>{request.preferredSchedule || 'Flexible'}</strong>
            </span>
            <span>
              <small>Request date</small>
              <strong>{new Date(request.createdAt).toLocaleDateString()}</strong>
            </span>
          </div>
          {request.note?.trim() && (
            <div className="tutor-application-note">
              <span className="tutor-application-note-tag">Note</span>
              <p>{request.note}</p>
            </div>
          )}
          <section className="tutor-detail-section">
            <span className="tutor-eyebrow">LEARNING GOALS</span>
            <h2 className="tutor-detail-title">What the student needs</h2>
            {request.description?.trim() && (
              <>
                <h3 className="tutor-small-heading">Request details</h3>
                <p className="tutor-detail-copy">{request.description}</p>
              </>
            )}
            {request.learningGoals?.trim() && (
              <>
                <h3 className="tutor-small-heading">Goals</h3>
                <p className="tutor-detail-copy">{request.learningGoals}</p>
              </>
            )}
            {!request.description?.trim() && !request.learningGoals?.trim() && (
              <p className="tutor-detail-copy">The student has not added more details yet.</p>
            )}
          </section>
          {request.availabilities.length > 0 && (
            <section className="tutor-detail-section">
              <h2 className="tutor-detail-title">Student availability</h2>
              <div className="tutor-availability-list">
                {request.availabilities.map((slot, index) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                    {weekdays[slot.dayOfWeek % 7]} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                  </span>
                ))}
              </div>
            </section>
          )}
          <section className="tutor-recommendations">
            <div className="tutor-section-heading">
              <div>
                <span className="tutor-eyebrow">KEEP EXPLORING</span>
                <h2>Similar requests</h2>
              </div>
              <button
                className="tutor-button secondary tutor-navigation-compact"
                type="button"
                onClick={() => navigate('/tutor/dashboard#marketplace')}
              >
                See all →
              </button>
            </div>
            {recommendations.length ? (
              <div className="tutor-recommendation-list">
                {recommendations.map((item) => (
                  <button
                    className="tutor-recommendation-card"
                    type="button"
                    onClick={() => navigate(`/tutor/studying-requests/${item.id}`)}
                    key={item.id}
                  >
                    <span className="tutor-subject-tag">{item.subjectName || 'Tutoring'}</span>
                    <strong>{item.title || `${item.subjectName} tutoring`}</strong>
                    <small>
                      {item.gradeName} · {item.districtName || 'Flexible location'}
                    </small>
                  </button>
                ))}
              </div>
            ) : (
              <p className="tutor-muted">More requests will appear here as students post them.</p>
            )}
          </section>
        </article>
        <aside className="tutor-panel tutor-apply-sidebar">
          {proposalsQuery.isLoading ? (
            <>
              <div className="tutor-application-panel-heading">
                <span className="tutor-proposal-application-icon" aria-hidden="true">
                  ↗
                </span>
                <div>
                  <span className="tutor-eyebrow">MY APPLICATION</span>
                  <h2>Loading application…</h2>
                </div>
              </div>
            </>
          ) : myProposal ? (
            <>
              <div className="tutor-application-panel-heading">
                <span className="tutor-proposal-application-icon" aria-hidden="true">
                  ↗
                </span>
                <div>
                  <span className="tutor-eyebrow">MY APPLICATION</span>
                  <h2>Your proposal</h2>
                </div>
                <span className={`tutor-cert-status status-${myProposal.status.toLowerCase()}`}>
                  {myProposal.status.toLowerCase()}
                </span>
              </div>
              <div className="tutor-apply-summary">
                <small>My proposed price</small>
                <strong className="tutor-proposed-price-value">
                  {new Intl.NumberFormat('vi-VN').format(myProposal.proposedPrice)} VND
                </strong>
                <small>Available time</small>
                <strong className="tutor-application-time">
                  {myProposal.preferredSchedule || 'Flexible schedule'}
                </strong>
                <small>Teaching mode</small>
                <strong>{myProposal.teachingMode === 'ONLINE' ? 'Online' : 'In person'}</strong>
                {myProposal.teachingMode === 'OFFLINE' &&
                  (myProposal.preferredProvinceName || myProposal.preferredDistrictName) && (
                    <>
                      <small className="tutor-application-address-label">Teaching address</small>
                      <strong className="tutor-application-address-value">
                        {[myProposal.preferredDistrictName, myProposal.preferredProvinceName]
                          .filter(Boolean)
                          .join(', ')}
                      </strong>
                    </>
                  )}
                {myProposal.message?.trim() && (
                  <>
                    <small>Note</small>
                    <strong>{myProposal.message}</strong>
                  </>
                )}
              </div>
              {myProposal.status === 'PENDING' ? (
                <div className="tutor-my-application-actions">
                  <button
                    className="tutor-button tutor-apply-button"
                    type="button"
                    onClick={() => openApplication(true)}
                  >
                    Edit application <span>↗</span>
                  </button>
                  <button
                    className="tutor-button secondary tutor-navigation-compact"
                    type="button"
                    onClick={() => navigate('/tutor/proposals')}
                  >
                    View all applications →
                  </button>
                </div>
              ) : myProposal.status === 'CANCELLED' ? (
                <button
                  className="tutor-button tutor-apply-button"
                  type="button"
                  onClick={() => openApplication(false)}
                >
                  Apply again <span>→</span>
                </button>
              ) : (
                <button
                  className="tutor-button tutor-apply-button"
                  type="button"
                  onClick={() => navigate('/tutor/proposals')}
                >
                  View my applications <span>→</span>
                </button>
              )}
            </>
          ) : (
            <>
              <span className="tutor-eyebrow">INTERESTED IN THIS STUDENT?</span>
              <h2>Send a proposal</h2>
              <p>Introduce yourself and suggest a time that works for you.</p>
              <div className="tutor-apply-summary">
                <small>Student budget</small>
                <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
                <small>Availability</small>
                <strong>{request.preferredSchedule || 'Flexible schedule'}</strong>
              </div>
              <button className="tutor-button tutor-apply-button" type="button" onClick={() => openApplication(false)}>
                Apply for this request <span>→</span>
              </button>
              <button
                className="tutor-button secondary tutor-save-button"
                type="button"
                onClick={() => navigate('/tutor/proposals')}
              >
                My applications
              </button>
            </>
          )}
        </aside>
      </div>
      {showApply && (
        <TutorApplicationForm
          key={`${editingProposal ? 'edit' : 'apply'}-${myProposal?.id ?? request.id}`}
          subjectName={request.subjectName}
          requestMode={request.learningMode}
          proposal={myProposal}
          editing={editingProposal}
          sending={sending}
          error={error}
          onClose={() => {
            setShowApply(false);
            setEditingProposal(false);
          }}
          onSubmit={(values) => void propose(values)}
        />
      )}
    </>
  );
}

function formatPrice(min: number | null, max: number | null) {
  const format = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
  if (min === null && max === null) return 'Negotiable';
  return min !== null && max !== null ? `${format(min)}–${format(max)} VND` : `${format(min ?? max ?? 0)} VND`;
}
