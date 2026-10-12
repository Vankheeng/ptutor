import { useCallback, useState, type ReactNode } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  acceptTutorOffer,
  getMyStudyingRequest,
  getTutorOffers,
  rejectTutorOffer,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

export function StudentStudyingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const location = useLocation();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
  const [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(async () => {
    const [request, offerPage] = await Promise.all([getMyStudyingRequest(requestId), getTutorOffers(requestId)]);
    return { request, offers: offerPage.content };
  }, [requestId]);
  const query = useResource(load, revision);
  const request: StudyingRequest | null = query.data?.request ?? null;
  const offers: TutorOffer[] = query.data?.offers ?? [];

  const act = async (offer: TutorOffer, action: 'accept' | 'reject') => {
    setBusy(offer.id);
    setError('');
    try {
      await (action === 'accept' ? acceptTutorOffer(requestId, offer.id) : rejectTutorOffer(requestId, offer.id));
      setNotice(`Offer ${action}ed.`);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : 'Could not update this offer.');
    } finally {
      setBusy(null);
    }
  };

  if (query.error && !request)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!request)
    return (
      <div className="student-empty">
        <h3>Loading request…</h3>
      </div>
    );

  return (
    <>
      <Link className="student-link" to="/student/studying-requests">
        ← Back to studying requests
      </Link>
      <div className="student-detail-actions student-detail-page-actions">
        {['DRAFT', 'OPEN'].includes(request.status) && (
          <Link className="student-button warning" to={`/student/studying-requests/${request.id}/edit`}>
            Edit
          </Link>
        )}
        <Link className="student-button back-to-list" to="/student/studying-requests">
          Back to list
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
      <div className="student-detail-layout">
        <section className="student-panel">
          <div className="student-detail-table">
            <DetailRow label="Title" value={request.title} />
            <DetailRow
              label="Status"
              value={<span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>}
            />
            <DetailRow label="Description" value={request.description || 'No description provided.'} multiline />
            <DetailRow label="Subject" value={request.subjectName || request.note || 'Custom subject'} />
            <DetailRow label="Grade" value={request.gradeName} />
            <DetailRow label="Mode" value={request.learningMode === 'ONLINE' ? 'Online' : 'In person'} />
            <DetailRow label="Budget" value={`${formatMoney(request.minPrice)} – ${formatMoney(request.maxPrice)}`} />
            <DetailRow label="Preferred schedule" value={request.preferredSchedule || 'Flexible'} />
            <DetailRow
              label="Address"
              value={[request.detailAddress, request.districtName].filter(Boolean).join(', ') || 'Not specified'}
            />
            <DetailRow label="Learning goals" value={request.learningGoals || 'Not specified'} multiline />
            <DetailRow
              label="Availability"
              value={
                request.availabilities.length ? (
                  <div className="student-detail-availability">
                    {request.availabilities.map((slot) => (
                      <span key={`${slot.dayOfWeek}-${slot.startTime}-${slot.endTime}`}>
                        {weekday(slot.dayOfWeek)}: {slot.startTime} – {slot.endTime}
                      </span>
                    ))}
                  </div>
                ) : (
                  'Flexible'
                )
              }
              multiline
            />
            <DetailRow label="Created" value={formatDate(request.createdAt)} />
            <DetailRow label="Updated" value={formatDate(request.updatedAt)} />
          </div>
        </section>
        <section className="student-panel">
          <div className="student-section-heading" style={{ margin: 0 }}>
            <div>
              <span className="student-eyebrow">TUTOR OFFERS</span>
              <h2>Offers received</h2>
            </div>
            <span className="student-count">{offers.length}</span>
          </div>
          {offers.length ? (
            <div className="student-list">
              {offers.map((offer) => (
                <article className="student-list-row" key={offer.id}>
                  <div>
                    <h3>{[offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Tutor'}</h3>
                    <p>
                      {offer.tutorEmail}
                      <br />
                      {formatMoney(offer.proposedPrice)} · {offer.teachingMode === 'ONLINE' ? 'Online' : 'In person'}
                      <br />
                      {offer.message || 'No message included.'}
                    </p>
                  </div>
                  <div>
                    <span className={`student-status ${offer.status.toLowerCase()}`}>{label(offer.status)}</span>
                    {offer.status === 'PENDING' && request.status === 'OPEN' && (
                      <div className="student-form-actions">
                        <button
                          className="student-button"
                          type="button"
                          disabled={busy === offer.id}
                          onClick={() => void act(offer, 'accept')}
                        >
                          Accept
                        </button>
                        <button
                          className="student-button secondary"
                          type="button"
                          disabled={busy === offer.id}
                          onClick={() => void act(offer, 'reject')}
                        >
                          Reject
                        </button>
                      </div>
                    )}
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="student-empty">
              <h3>No offers yet</h3>
              <p>Tutor proposals will appear here.</p>
            </div>
          )}
        </section>
      </div>
    </>
  );
}

function DetailRow({ label, value, multiline = false }: { label: string; value: ReactNode; multiline?: boolean }) {
  return (
    <div className={`student-detail-row${multiline ? ' multiline' : ''}`}>
      <span>{label}</span>
      <div className="student-detail-value">{value}</div>
    </div>
  );
}

function formatMoney(value: number | null) {
  return value === null ? 'Negotiable' : `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
function weekday(dayOfWeek: number) {
  return (
    ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][dayOfWeek - 1] ?? `Day ${dayOfWeek}`
  );
}
