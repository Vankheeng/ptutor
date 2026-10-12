import type { TutorOffer } from '../../services/studentApi';
import { formatStudyingRequestMoney, statusLabel } from './studyingRequestUtils';

interface Props {
  requestStatus: string;
  offers: TutorOffer[];
  busyOfferId: string | null;
  onAction: (offer: TutorOffer, action: 'accept' | 'reject') => void;
}

export function StudentTutorOffers({ requestStatus, offers, busyOfferId, onAction }: Props) {
  return (
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
                  {formatStudyingRequestMoney(offer.proposedPrice)} ·{' '}
                  {offer.teachingMode === 'ONLINE' ? 'Online' : 'In person'}
                  <br />
                  {offer.message || 'No message included.'}
                </p>
              </div>
              <div>
                <span className={`student-status ${offer.status.toLowerCase()}`}>{statusLabel(offer.status)}</span>
                {offer.status === 'PENDING' && requestStatus === 'OPEN' && (
                  <div className="student-form-actions">
                    <button
                      className="student-button"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'accept')}
                    >
                      Accept
                    </button>
                    <button
                      className="student-button secondary"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'reject')}
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
  );
}
