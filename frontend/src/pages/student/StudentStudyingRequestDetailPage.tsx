import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  acceptTutorOffer,
  getMyStudyingRequestDetail,
  getTutorOffers,
  rejectTutorOffer,
  type StudentStudyingRequestDetail,
  type TutorOffer
} from '../../services/studentApi';
import { StudentStudyingRequestDetails } from '../../components/student/StudentStudyingRequestDetails';
import { Offers } from '../../components/student/Offers';
import { notifyGlobalToast } from '../../components/ui/globalToast';
import { useResource } from '../../hooks/useResource';

export function StudentStudyingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const handledNoticeLocationKey = useRef<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [offerStatus, setOfferStatus] = useState<'' | TutorOffer['status']>('');
  const [offerSort, setOfferSort] = useState<'DESC' | 'ASC'>('DESC');
  const [offerPage, setOfferPage] = useState(0);
  const [offersLoading, setOffersLoading] = useState(true);

  useEffect(() => {
    const notice = (location.state as { notice?: string } | null)?.notice;
    if (!notice || handledNoticeLocationKey.current === location.key) return;
    handledNoticeLocationKey.current = location.key;
    notifyGlobalToast(notice);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.key, location.pathname, location.state, navigate]);

  const loadRequest = useCallback((signal: AbortSignal) => getMyStudyingRequestDetail(requestId, signal), [requestId]);
  const requestQuery = useResource(loadRequest, revision);
  const loadOffers = useCallback(
    async (signal: AbortSignal) => {
      setOffersLoading(true);
      try {
        return await getTutorOffers(requestId, {
          status: offerStatus || undefined,
          sort: offerSort,
          page: offerPage,
          size: 20,
          signal
        });
      } finally {
        if (!signal.aborted) setOffersLoading(false);
      }
    },
    [requestId, offerStatus, offerSort, offerPage]
  );
  const offersQuery = useResource(loadOffers, revision);
  const request: StudentStudyingRequestDetail | null = requestQuery.data ?? null;
  const offerResponse = offersQuery.data;
  const offers: TutorOffer[] = offerResponse?.content ?? [];

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

  if (requestQuery.error && !request)
    return (
      <div className="student-error" role="alert">
        {requestQuery.error}
      </div>
    );
  if (!request)
    return (
      <div className="student-empty">
        <h3>Đang tải chi tiết yêu cầu…</h3>
      </div>
    );

  return (
    <>
      <div className="student-studying-request-top-actions">
        <button
          className="student-button secondary"
          type="button"
          onClick={() => navigate('/student/studying-requests')}
        >
          ← Quay lại danh sách
        </button>
        {['DRAFT', 'OPEN'].includes(request.status.toUpperCase()) ? (
          <Link className="student-button draft" to={`/student/studying-requests/${request.id}/edit`}>
            Chỉnh sửa
          </Link>
        ) : (
          <button
            className="student-button secondary"
            type="button"
            disabled
            title="Chỉ có thể chỉnh sửa yêu cầu ở trạng thái Bản nháp hoặc Đang mở."
          >
            Chỉnh sửa
          </button>
        )}
      </div>
      <div className="student-detail-layout">
        <StudentStudyingRequestDetails request={request} />
        <Offers
          requestStatus={request.status}
          offers={offers}
          statusFilter={offerStatus}
          sortOrder={offerSort}
          page={offerResponse?.page ?? offerPage}
          totalPages={offerResponse?.totalPages ?? 0}
          totalElements={offerResponse?.totalElements ?? 0}
          loading={offersLoading || offersQuery.isLoading}
          error={offersQuery.error}
          busyOfferId={busy}
          onStatusChange={(value) => {
            setOfferStatus(value);
            setOfferPage(0);
          }}
          onSortChange={(value) => {
            setOfferSort(value);
            setOfferPage(0);
          }}
          onPageChange={setOfferPage}
          onAction={act}
          onCreateContract={(offer) =>
            navigate(`/student/studying-requests/${request.id}/tutor-requests/${offer.id}/contracts/new`)
          }
        />
      </div>
    </>
  );
}
