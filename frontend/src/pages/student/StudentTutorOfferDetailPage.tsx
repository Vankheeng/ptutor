import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getTutorOffer, type TutorOffer } from '../../services/studentApi';
import {
  statusLabel,
  formatStudyingRequestDate,
  formatStudyingRequestMoney
} from '../../components/student/studyingRequestUtils';

export function StudentTutorOfferDetailPage() {
  const { studyingRequestId = '', tutorRequestId = '' } = useParams();
  const load = useCallback(() => getTutorOffer(studyingRequestId, tutorRequestId), [studyingRequestId, tutorRequestId]);
  const query = useResource(load);
  const offer: TutorOffer | null = query.data ?? null;

  if (query.error)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!offer)
    return (
      <div className="student-empty">
        <h3>Loading tutor request…</h3>
      </div>
    );

  const tutorName = [offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Tutor';

  return (
    <>
      <Link className="student-link" to={`/student/studying-requests/${studyingRequestId}#tutor-offers`}>
        ← Back to tutor offers
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">TUTOR REQUEST</span>
          <h1>Tutor request details</h1>
          <p>Review the tutor&apos;s proposal for your studying request.</p>
        </div>
      </div>
      <section className="student-panel student-tutor-offer-detail">
        <div className="student-tutor-offer-detail-header">
          <div className="student-offer-tutor">
            <span className="student-offer-avatar" aria-hidden="true">
              {getInitials(offer)}
            </span>
            <div>
              <h2>{tutorName}</h2>
              <p>{offer.tutorEmail}</p>
            </div>
          </div>
          <span className={`student-status ${offer.status.toLowerCase()}`}>{statusLabel(offer.status)}</span>
        </div>
        <div className="student-tutor-offer-detail-grid">
          <Detail label="Studying request" value={offer.studyingRequestTitle || 'Studying request'} />
          <Detail label="Subject" value={offer.subjectName || 'Not specified'} />
          <Detail label="Grade" value={offer.gradeName || 'Not specified'} />
          <Detail label="Proposed price" value={formatStudyingRequestMoney(offer.proposedPrice)} />
          <Detail label="Teaching mode" value={offer.teachingMode === 'ONLINE' ? 'Online' : 'In person'} />
          <Detail label="Preferred schedule" value={offer.preferredSchedule || 'Flexible'} />
          <Detail label="Message" value={offer.message || 'No message provided'} />
          <Detail label="Sent at" value={formatStudyingRequestDate(offer.createdAt)} />
          <Detail label="Updated at" value={formatStudyingRequestDate(offer.updatedAt)} />
        </div>
      </section>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="student-tutor-offer-detail-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function getInitials(offer: TutorOffer) {
  const initials = [offer.tutorFirstName, offer.tutorLastName]
    .filter(Boolean)
    .map((part) => part!.trim().charAt(0).toUpperCase())
    .join('');
  return initials || 'T';
}
