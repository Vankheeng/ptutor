import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  acceptTutorOffer,
  getMyStudyingRequestDetail,
  getStudyingRequestPaymentFee,
  getTutorOffers,
  payStudyingRequest,
  payStudyingRequestWithWallet,
  rejectTutorOffer,
  updateStudyingRequestStatus,
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
  const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
  const [paymentFee, setPaymentFee] = useState<number | null>(null);
  const [paymentFeeLoading, setPaymentFeeLoading] = useState(false);
  const [paymentFeeError, setPaymentFeeError] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'wallet' | 'vnpay'>('wallet');
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [paymentError, setPaymentError] = useState('');

  useEffect(() => {
    const notice = (location.state as { notice?: string } | null)?.notice;
    if (!notice || handledNoticeLocationKey.current === location.key) return;
    handledNoticeLocationKey.current = location.key;
    notifyGlobalToast(notice);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.key, location.pathname, location.state, navigate]);

  useEffect(() => {
    if (!paymentDialogOpen) return;
    let active = true;
    void getStudyingRequestPaymentFee()
      .then((fee) => {
        if (active) setPaymentFee(fee);
      })
      .catch((caught: unknown) => {
        if (active) {
          setPaymentFeeError(
            caught instanceof ApiError || caught instanceof Error
              ? caught.message
              : 'Không thể tải phí đăng yêu cầu. Vui lòng thử lại.'
          );
        }
      })
      .finally(() => {
        if (active) setPaymentFeeLoading(false);
      });
    return () => {
      active = false;
    };
  }, [paymentDialogOpen]);

  useEffect(() => {
    if (!paymentDialogOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !paymentBusy) setPaymentDialogOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [paymentDialogOpen, paymentBusy]);

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

  const closeRequest = async () => {
    if (!request || request.status !== 'OPEN') return;
    setBusy('close-request');
    try {
      await updateStudyingRequestStatus(request.id, 'CLOSED');
      notifyGlobalToast('Đã đóng yêu cầu tìm gia sư.');
      setRevision((value) => value + 1);
    } catch (caught) {
      notifyGlobalToast(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể đóng yêu cầu.',
        'error'
      );
    } finally {
      setBusy(null);
    }
  };

  const openPaymentDialog = () => {
    setPaymentFee(null);
    setPaymentFeeError('');
    setPaymentFeeLoading(true);
    setPaymentError('');
    setPaymentDialogOpen(true);
  };

  const payDraft = async () => {
    if (!request || request.status !== 'DRAFT' || paymentFee === null) return;
    setPaymentBusy(true);
    setPaymentError('');
    try {
      if (paymentMethod === 'wallet') {
        await payStudyingRequestWithWallet(request.id);
        setPaymentDialogOpen(false);
        notifyGlobalToast('Thanh toán thành công. Trạng thái yêu cầu đã được cập nhật.');
        setRevision((value) => value + 1);
        return;
      }

      const payment = await payStudyingRequest(request.id);
      if (!payment.paymentUrl) throw new Error('VNPay không trả về liên kết thanh toán.');
      let paymentUrl: URL;
      try {
        paymentUrl = new URL(payment.paymentUrl);
      } catch {
        throw new Error('Liên kết thanh toán VNPay không hợp lệ.');
      }
      if (!['http:', 'https:'].includes(paymentUrl.protocol)) {
        throw new Error('Liên kết thanh toán VNPay không hợp lệ.');
      }
      window.location.assign(paymentUrl.toString());
    } catch (caught) {
      setPaymentError(
        caught instanceof ApiError || caught instanceof Error
          ? caught.message
          : 'Thanh toán không thành công. Vui lòng thử lại.'
      );
    } finally {
      setPaymentBusy(false);
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
          <div className="student-studying-request-detail-actions">
            <Link className="student-button draft" to={`/student/studying-requests/${request.id}/edit`}>
              Chỉnh sửa
            </Link>
            {request.status === 'OPEN' ? (
              <button
                className="student-button draft"
                type="button"
                disabled={busy !== null}
                onClick={() => void closeRequest()}
              >
                {busy === 'close-request' ? 'Đang đóng…' : 'Đóng yêu cầu'}
              </button>
            ) : (
              <button className="student-button payment" type="button" onClick={openPaymentDialog}>
                Thanh toán
              </button>
            )}
          </div>
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
      {paymentDialogOpen && request.status === 'DRAFT' && (
        <div
          className="student-studying-request-payment-dialog-backdrop"
          role="presentation"
          onClick={() => {
            if (!paymentBusy) setPaymentDialogOpen(false);
          }}
        >
          <section
            className="student-panel student-studying-request-payment-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="studying-request-payment-title"
            onClick={(event) => event.stopPropagation()}
          >
            <header className="student-studying-request-payment-dialog-header">
              <div>
                <h2 id="studying-request-payment-title">Thanh toán phí đăng bài</h2>
              </div>
              <button
                type="button"
                className="student-studying-request-payment-dialog-close"
                aria-label="Đóng"
                disabled={paymentBusy}
                onClick={() => setPaymentDialogOpen(false)}
              >
                ×
              </button>
            </header>
            <div className="student-studying-request-payment-dialog-body">
              <div className="student-studying-request-payment-heading">
                <span className="student-studying-request-payment-icon" aria-hidden="true">
                  $
                </span>
                <div>
                  <h2>Yêu cầu tìm gia sư</h2>
                  <p>{request.title}</p>
                </div>
                <strong className="student-studying-request-payment-price">
                  {paymentFeeLoading
                    ? 'Đang tải…'
                    : paymentFee === null
                      ? '—'
                      : `${paymentFee.toLocaleString('vi-VN')} VNĐ`}
                </strong>
              </div>
              <p className="student-studying-request-payment-policy">
                Phí đăng bài không được hoàn lại sau khi thanh toán. Yêu cầu có môn học chưa có trong danh mục sẽ chờ
                duyệt; chỉ trường hợp này nếu bị quản trị viên từ chối do không hợp lệ mới được hoàn phí.
              </p>
              <label className="student-studying-request-payment-method">
                Phương thức thanh toán
                <select
                  value={paymentMethod}
                  onChange={(event) => setPaymentMethod(event.target.value as 'wallet' | 'vnpay')}
                  disabled={paymentBusy}
                >
                  <option value="wallet">Ví PTutor</option>
                  <option value="vnpay">VNPay</option>
                </select>
              </label>
              {(paymentFeeError || paymentError) && (
                <div className="student-error" role="alert">
                  {paymentFeeError || paymentError}
                </div>
              )}
            </div>
            <footer className="student-studying-request-payment-dialog-footer">
              <button
                className="student-button danger"
                type="button"
                disabled={paymentBusy}
                onClick={() => setPaymentDialogOpen(false)}
              >
                Hủy
              </button>
              <button
                className="student-button payment"
                type="button"
                disabled={paymentBusy || paymentFeeLoading || paymentFee === null || Boolean(paymentFeeError)}
                onClick={() => void payDraft()}
              >
                {paymentBusy ? 'Đang xử lý…' : 'Thanh toán'}
              </button>
            </footer>
          </section>
        </div>
      )}
    </>
  );
}
