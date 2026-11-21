import { useCallback, useEffect, useState } from 'react';
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
import { Offers } from '../../components/student/Offers';
import { useResource } from '../../hooks/useResource';
import { notifyGlobalToast } from '../../components/ui/globalToast';

export function StudentStudyingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  useEffect(() => {
    const notice = (location.state as { notice?: string } | null)?.notice;
    if (!notice) return;
    notifyGlobalToast(notice);
    navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate]);
  const load = useCallback(async () => {
    const [request, offerPage] = await Promise.all([getMyStudyingRequest(requestId), getTutorOffers(requestId)]);
    return { request, offers: offerPage.content };
  }, [requestId]);
  const query = useResource(load, revision);
  const request: StudyingRequest | null = query.data?.request ?? null;
  const offers: TutorOffer[] = query.data?.offers ?? [];

  const act = async (offer: TutorOffer, action: 'accept' | 'reject') => {
    setBusy(offer.id);
    try {
      await (action === 'accept' ? acceptTutorOffer(requestId, offer.id) : rejectTutorOffer(requestId, offer.id));
      notifyGlobalToast(action === 'accept' ? 'Đã chấp nhận đề nghị của gia sư.' : 'Đã từ chối đề nghị của gia sư.');
      setRevision((value) => value + 1);
    } catch (caught) {
      notifyGlobalToast(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể cập nhật đề nghị.',
        'error'
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
        <h3>Đang tải yêu cầu học…</h3>
      </div>
    );

  return (
    <>
      <Link className="student-link" to="/student/studying-requests">
        ← Quay lại yêu cầu học
      </Link>
      <div className="student-detail-actions student-detail-page-actions">
        {['DRAFT', 'OPEN'].includes(request.status) && (
          <Link className="student-button warning" to={`/student/studying-requests/${request.id}/edit`}>
            Chỉnh sửa
          </Link>
        )}
        <Link className="student-button back-to-list" to="/student/studying-requests">
          Quay lại danh sách
        </Link>
      </div>
      <div className="student-detail-layout">
        <StudentStudyingRequestDetails request={request} />
        <Offers
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
