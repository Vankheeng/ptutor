import { useCallback, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  acceptTutorOffer,
  cancelStudyingRequest,
  getMyStudyingRequest,
  getTutorOffers,
  rejectTutorOffer,
  updateStudyingRequest,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

export function StudentStudyingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
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
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not update this offer.'
      );
    } finally {
      setBusy(null);
    }
  };
  const cancel = async () => {
    setBusy('request');
    try {
      await cancelStudyingRequest(requestId);
      setNotice('Request cancelled.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not cancel this request.');
    } finally {
      setBusy(null);
    }
  };
  const update = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setBusy('update');
    setError('');
    try {
      await updateStudyingRequest(requestId, {
        title: String(values.get('title') ?? ''),
        quantity: Number(values.get('quantity')),
        description: String(values.get('description') ?? ''),
        preferredSchedule: String(values.get('preferredSchedule') ?? '')
      });
      setNotice('Studying request updated.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not update this request.'
      );
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
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">STUDYING REQUEST</span>
          <h1>{request.title}</h1>
          <p>
            {request.subjectName} · {request.gradeName}
          </p>
        </div>
        <span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>
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
          <h2>Learning details</h2>
          <p className="student-detail-copy">
            {request.description || request.learningGoals || 'No description provided.'}
          </p>
          <div className="student-detail-facts">
            <span>
              <small>Mode</small>
              <strong>{request.learningMode === 'ONLINE' ? 'Online' : 'In person'}</strong>
            </span>
            <span>
              <small>Quantity</small>
              <strong>{request.quantity}</strong>
            </span>
            <span>
              <small>Budget</small>
              <strong>
                {formatMoney(request.minPrice)} – {formatMoney(request.maxPrice)}
              </strong>
            </span>
            <span>
              <small>Schedule</small>
              <strong>{request.preferredSchedule || 'Flexible'}</strong>
            </span>
          </div>
          {['DRAFT', 'OPEN', 'MATCHED', 'CLOSED'].includes(request.status) && (
            <button
              className="student-button secondary"
              style={{ marginTop: 16 }}
              type="button"
              disabled={busy === 'request'}
              onClick={() => void cancel()}
            >
              Cancel request
            </button>
          )}
          {['DRAFT', 'OPEN'].includes(request.status) && (
            <form className="student-form-grid" style={{ marginTop: 20 }} onSubmit={(event) => void update(event)}>
              <label>
                Title
                <input name="title" defaultValue={request.title} required />
              </label>
              <label>
                Quantity
                <input name="quantity" type="number" min="1" defaultValue={request.quantity} required />
              </label>
              <label className="student-field-full">
                Description
                <textarea name="description" rows={3} defaultValue={request.description || ''} />
              </label>
              <label className="student-field-full">
                Preferred schedule
                <input name="preferredSchedule" defaultValue={request.preferredSchedule || ''} />
              </label>
              <div className="student-form-actions">
                <button className="student-button" disabled={busy === 'update'} type="submit">
                  Save changes
                </button>
              </div>
            </form>
          )}
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

function formatMoney(value: number | null) {
  return value === null ? 'Negotiable' : `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
