import { useCallback, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  acceptTutorOffer,
  getMyStudyingRequest,
  getTutorOffers,
  rejectTutorOffer,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import { StudentStudyingRequestDetails } from '../../components/student/StudentStudyingRequestDetails';
import { StudentTutorOffers } from '../../components/student/StudentTutorOffers';
import { useResource } from '../../hooks/useResource';

export function StudentStudyingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
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
        <StudentStudyingRequestDetails request={request} />
        <StudentTutorOffers
          requestStatus={request.status}
          offers={offers}
          busyOfferId={busy}
          onAction={act}
          onCreateContract={(offer) =>
            navigate(`/student/studying-requests/${request.id}/tutor-requests/${offer.id}/contracts/new`)
          }
        />
      </div>
    </>
  );
}
