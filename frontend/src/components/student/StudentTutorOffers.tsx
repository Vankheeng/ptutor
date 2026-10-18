import { Link } from 'react-router-dom';
import type { TutorOffer } from '../../services/studentApi';
import { formatStudyingRequestMoney, statusLabel } from './studyingRequestUtils';

interface Props {
  requestStatus: string;
  offers: TutorOffer[];
  busyOfferId: string | null;
  onAction: (offer: TutorOffer, action: 'accept' | 'reject') => void;
  onCreateContract?: (offer: TutorOffer) => void;
}

export function StudentTutorOffers({ requestStatus, offers, busyOfferId, onAction, onCreateContract }: Props) {
  return (
    <section id="tutor-offers" className="student-panel student-tutor-offers">
      <div className="student-tutor-offers-heading">
        <h2>Tutor Offers</h2>
        <p>View and manage offers from tutors.</p>
      </div>
      {offers.length ? (
        <div className="student-offer-list">
          {offers.map((offer) => (
            <article className="student-offer-row" key={offer.id}>
              <div className="student-offer-tutor">
                <span className="student-offer-avatar" aria-hidden="true">
                  {getTutorInitials(offer)}
                </span>
                <div>
                  <h3>{[offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Tutor'}</h3>
                  <p>{formatStudyingRequestMoney(offer.proposedPrice)}</p>
                  <time dateTime={offer.createdAt}>Sent {formatOfferDate(offer.createdAt)}</time>
                </div>
              </div>

              <div className="student-offer-status">
                <span className={`student-status ${offer.status.toLowerCase()}`}>{statusLabel(offer.status)}</span>
              </div>

              <div className="student-offer-actions">
                {offer.status === 'PENDING' && requestStatus === 'OPEN' && (
                  <div className="student-form-actions">
                    <button
                      className="student-button success"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'accept')}
                    >
                      Accept
                    </button>
                    <button
                      className="student-button danger"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'reject')}
                    >
                      Reject
                    </button>
                  </div>
                )}
                {offer.status === 'ACCEPTED' && ['OPEN', 'MATCHED'].includes(requestStatus) && onCreateContract && (
                  <button
                    className="student-button student-offer-create"
                    type="button"
                    onClick={() => onCreateContract(offer)}
                  >
                    Create contract
                  </button>
                )}
                {!['PENDING', 'ACCEPTED'].includes(offer.status) && (
                  <Link
                    className="student-button student-offer-view"
                    to={`/student/studying-requests/${offer.studyingRequestId}/tutor-requests/${offer.id}`}
                  >
                    View
                  </Link>
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
  );
}

function getTutorInitials(offer: TutorOffer) {
  const initials = [offer.tutorFirstName, offer.tutorLastName]
    .filter(Boolean)
    .map((part) => part!.trim().charAt(0).toUpperCase())
    .join('');
  return initials || 'T';
}

function formatOfferDate(value: string) {
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}
