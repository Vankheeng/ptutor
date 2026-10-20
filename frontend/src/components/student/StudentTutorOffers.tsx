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
          <span className="student-eyebrow">ĐỀ NGHỊ TỪ GIA SƯ</span>
          <h2>Đề nghị đã nhận</h2>
        </div>
        <span className="student-count">{offers.length}</span>
      </div>
      {offers.length ? (
        <div className="student-list">
          {offers.map((offer) => (
            <article className="student-list-row" key={offer.id}>
              <div>
                <h3>{[offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'}</h3>
                <p>
                  {offer.tutorEmail}
                  <br />
                  {formatStudyingRequestMoney(offer.proposedPrice)} ·{' '}
                  {offer.teachingMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}
                  <br />
                  {offer.message || 'Không có lời nhắn.'}
                </p>
              </div>
              <div>
                <span className={`student-status ${offer.status.toLowerCase()}`}>
                  {statusLabel(offer.status, 'application')}
                </span>
                {offer.status === 'PENDING' && requestStatus === 'OPEN' && (
                  <div className="student-form-actions">
                    <button
                      className="student-button"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'accept')}
                    >
                      Nhận
                    </button>
                    <button
                      className="student-button secondary"
                      type="button"
                      disabled={busyOfferId === offer.id}
                      onClick={() => onAction(offer, 'reject')}
                    >
                      Từ chối
                    </button>
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>Chưa có đề nghị nào</h3>
          <p>Đề nghị dạy của gia sư sẽ hiện ở đây.</p>
        </div>
      )}
    </section>
  );
}
