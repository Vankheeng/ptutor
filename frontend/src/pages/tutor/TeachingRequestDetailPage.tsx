import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { useResource } from '../../hooks/useResource';
import { useTutorProfile } from '../../hooks/useTutorWorkspace';
import {
  getMyTeachingRequest,
  getTeachingRequestApplications,
  cancelMyTeachingRequest,
  payTeachingRequestByWallet,
  startTeachingRequestVnPay,
  teachingRequestWalletErrorMessage,
  updateMyTeachingRequestStatus,
  updateTeachingRequestApplication,
  TEACHING_REQUEST_FEE_PER_STUDENT,
  type StudentTeachingApplication
} from '../../services/myTeachingRequestApi';
import type { TeachingRequest } from '../../types/api';
import { getTutorContracts } from '../../services/tutorApi';
import '../student/student.css';
import './teaching-request-detail.css';

export function TeachingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [confirmingApplication, setConfirmingApplication] = useState<StudentTeachingApplication | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'wallet' | 'vnpay'>('wallet');
  const profileQuery = useTutorProfile();
  const canPublish = profileQuery.data?.profileStatus === 'VERIFIED';
  const load = useCallback(
    async (signal: AbortSignal) => {
      const [request, applications, contracts] = await Promise.all([
        getMyTeachingRequest(requestId, signal),
        getTeachingRequestApplications(requestId, signal),
        getTutorContracts(signal)
      ]);
      return { request, applications, contracts: contracts.content };
    },
    [requestId]
  );
  const query = useResource(load, revision);
  const request: TeachingRequest | null = query.data?.request ?? null;
  const applications = query.data?.applications ?? [];
  const currentContractByApplicationId = new Map(
    (query.data?.contracts ?? [])
      .filter((contract) => contract.studentTutorRequestId && contract.status !== 'CANCELLED')
      .map((contract) => [contract.studentTutorRequestId as string, contract.id])
  );
  const previousContractByApplicationId = new Map(
    (query.data?.contracts ?? [])
      .filter((contract) => contract.studentTutorRequestId)
      .map((contract) => [contract.studentTutorRequestId as string, contract.id])
  );

  const toggleRequestStatus = async () => {
    if (!request || !['OPEN', 'CLOSED'].includes(request.status ?? '') || request.reviewedAt) return;
    const nextStatus = request.status === 'OPEN' ? 'CLOSED' : 'OPEN';
    if (nextStatus === 'OPEN' && !canPublish) {
      setError('Hồ sơ gia sư cần được duyệt trước khi mở lại yêu cầu.');
      return;
    }
    setBusy('status');
    setError('');
    setNotice('');
    try {
      await updateMyTeachingRequestStatus(request.id, nextStatus);
      setNotice(nextStatus === 'CLOSED' ? 'Đã đóng yêu cầu dạy.' : 'Đã mở lại yêu cầu dạy.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật trạng thái yêu cầu.');
    } finally {
      setBusy(null);
    }
  };

  const applyApplicationStatus = async (application: StudentTeachingApplication, status: 'ACCEPTED' | 'REJECTED') => {
    setBusy(application.id);
    setError('');
    setNotice('');
    try {
      await updateTeachingRequestApplication(requestId, application.id, status);
      setNotice(status === 'ACCEPTED' ? 'Đã chấp nhận đề nghị của học viên.' : 'Đã từ chối đề nghị của học viên.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật đề nghị.');
    } finally {
      setBusy(null);
    }
  };

  const updateApplication = async (application: StudentTeachingApplication, status: 'ACCEPTED' | 'REJECTED') => {
    if (status === 'ACCEPTED' && request) {
      const acceptedCount = applications.filter((item) => item.status === 'ACCEPTED').length;
      const remainingPending = applications.filter(
        (item) => item.status === 'PENDING' && item.id !== application.id
      ).length;
      if (acceptedCount + 1 >= (request.quantity ?? 0) && remainingPending > 0) {
        setConfirmingApplication(application);
        return;
      }
    }
    await applyApplicationStatus(application, status);
  };

  const payDraft = async () => {
    if (!request) return;
    if (!canPublish) {
      setError('Hồ sơ gia sư cần được duyệt trước khi thanh toán và đăng yêu cầu.');
      return;
    }
    const paymentTab = paymentMethod === 'vnpay' ? window.open('about:blank', '_blank') : null;
    setBusy('payment');
    setError('');
    setNotice('');
    try {
      if (paymentMethod === 'wallet') {
        await payTeachingRequestByWallet(request.id);
        setNotice('Thanh toán bằng ví thành công. Yêu cầu đã được gửi đi.');
        setRevision((value) => value + 1);
      } else {
        const payment = await startTeachingRequestVnPay(request.id);
        if (!payment.paymentUrl) throw new Error('Không nhận được liên kết thanh toán VNPay.');
        if (paymentTab) paymentTab.location.href = payment.paymentUrl;
        else window.location.assign(payment.paymentUrl);
      }
    } catch (caught) {
      paymentTab?.close();
      if (paymentMethod === 'wallet') {
        const message = teachingRequestWalletErrorMessage(caught);
        window.alert(message);
        setError(message);
        return;
      }
      setError(caught instanceof Error ? caught.message : 'Không thể thanh toán yêu cầu dạy.');
    } finally {
      setBusy(null);
    }
  };

  const cancelDraft = async () => {
    if (!request || request.status !== 'DRAFT' || !window.confirm('Bạn có chắc muốn hủy bản nháp này không?')) return;
    setBusy('cancel');
    setError('');
    setNotice('');
    try {
      await cancelMyTeachingRequest(request.id);
      setNotice('Đã hủy bản nháp yêu cầu dạy.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy bản nháp yêu cầu dạy.');
    } finally {
      setBusy(null);
    }
  };

  if (query.error)
    return (
      <div className="student-error" role="alert">
        {query.error}
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
      {!canPublish && (request.status === 'DRAFT' || request.status === 'CLOSED') && (
        <div className="teaching-profile-note" role="status">
          <span>
            {profileQuery.isLoading
              ? 'Đang kiểm tra trạng thái hồ sơ gia sư…'
              : profileQuery.error
                ? 'Không thể kiểm tra trạng thái hồ sơ gia sư.'
                : 'Hồ sơ gia sư cần được duyệt trước khi thanh toán hoặc mở lại yêu cầu.'}
          </span>
          <button className="teaching-profile-note-action" type="button" onClick={() => navigate('/tutor/profile')}>
            Xem hồ sơ
          </button>
        </div>
      )}
      <div className="teaching-detail-top-actions">
        <button className="student-button secondary" onClick={() => navigate('/tutor/sections/teaching-requests')}>
          ← Quay lại danh sách
        </button>
        <div className="teaching-detail-actions-right">
          {['OPEN', 'CLOSED'].includes(request.status ?? '') && !request.reviewedAt && (
            <button
              className="student-button draft"
              disabled={busy === 'status' || (request.status === 'CLOSED' && !canPublish)}
              onClick={() => void toggleRequestStatus()}
            >
              {busy === 'status' ? 'Đang cập nhật…' : request.status === 'OPEN' ? 'Đóng yêu cầu' : 'Mở lại yêu cầu'}
            </button>
          )}
          {['DRAFT', 'OPEN', 'PENDING_REVIEW'].includes(request.status ?? '') && (
            <button
              className="student-button draft"
              onClick={() => navigate('/tutor/sections/teaching-requests', { state: { editRequest: request } })}
            >
              Chỉnh sửa
            </button>
          )}
          {request.status === 'DRAFT' && (
            <button
              className="student-button secondary"
              type="button"
              disabled={busy === 'cancel'}
              onClick={() => void cancelDraft()}
            >
              {busy === 'cancel' ? 'Đang hủy…' : 'Hủy bản nháp'}
            </button>
          )}
        </div>
      </div>
      {notice && (
        <div className="student-notice tutor-toast-source" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error tutor-toast-source" role="alert">
          {error}
        </div>
      )}
      <div className="student-detail-layout teaching-detail-layout">
        <section className="student-panel teaching-detail-info">
          <h1>{request.title}</h1>
          <dl>
            <div>
              <dt>Trạng thái</dt>
              <dd>
                <span className={`student-status ${(request.status ?? 'DRAFT').toLowerCase()}`}>
                  {statusText(request.status ?? 'DRAFT')}
                </span>
              </dd>
            </div>
            {(request.description || request.note) && (
              <div>
                <dt>Mô tả</dt>
                <dd>{request.description || request.note}</dd>
              </div>
            )}
            <div>
              <dt>Môn học</dt>
              <dd>{request.subjectName || request.customSubjectName || 'Môn học tùy chỉnh'}</dd>
            </div>
            <div>
              <dt>Khối lớp</dt>
              <dd>{request.grades.map((grade) => grade.name).join(', ') || '—'}</dd>
            </div>
            <div>
              <dt>Hình thức</dt>
              <dd>{request.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</dd>
            </div>
            <div>
              <dt>Số lượng học viên</dt>
              <dd>{request.quantity ?? '—'}</dd>
            </div>
            <div>
              <dt>Học phí dự kiến</dt>
              <dd>{request.expectedPrice?.toLocaleString('vi-VN')} VNĐ</dd>
            </div>
            <div>
              <dt>Khu vực</dt>
              <dd>{request.districts.map((district) => district.name).join(', ') || 'Chưa chọn'}</dd>
            </div>
            <div>
              <dt>Địa chỉ chi tiết</dt>
              <dd>{request.detailAddress || 'Chưa có'}</dd>
            </div>
            <div>
              <dt>Thời gian tạo</dt>
              <dd>{new Date(request.createdAt).toLocaleString('vi-VN')}</dd>
            </div>
            <div>
              <dt>Thời gian thanh toán</dt>
              <dd>{request.paidAt ? new Date(request.paidAt).toLocaleString('vi-VN') : 'Chưa thanh toán'}</dd>
            </div>
            <div>
              <dt>Cập nhật lần cuối</dt>
              <dd>{new Date(request.updatedAt).toLocaleString('vi-VN')}</dd>
            </div>
          </dl>
          {request.status === 'REJECTED' && request.rejectionReason && (
            <div className="teaching-rejection-reason" role="alert">
              Lý do từ chối: {request.rejectionReason}
            </div>
          )}
          {request.status === 'DRAFT' && (
            <div className="student-panel teaching-draft-payment">
              <div className="teaching-payment-summary">
                <strong>
                  Số tiền cần thanh toán:{' '}
                  {((request.quantity ?? 0) * TEACHING_REQUEST_FEE_PER_STUDENT).toLocaleString('vi-VN')} VNĐ
                </strong>
                <span>
                  {TEACHING_REQUEST_FEE_PER_STUDENT.toLocaleString('vi-VN')} VNĐ / 1 học viên × {request.quantity ?? 0}{' '}
                  học viên.
                </span>
                <span>
                  Phí đăng bài không được hoàn lại sau khi thanh toán, trừ yêu cầu môn học ngoài danh mục bị quản trị
                  viên từ chối do không hợp lệ.
                </span>
              </div>
              <label>
                Phương thức thanh toán
                <select
                  value={paymentMethod}
                  onChange={(event) => setPaymentMethod(event.target.value as 'wallet' | 'vnpay')}
                >
                  <option value="wallet">Ví điện tử</option>
                  <option value="vnpay">VNPay</option>
                </select>
              </label>
              <button
                className="student-button draft"
                disabled={busy === 'payment' || !canPublish}
                onClick={() => void payDraft()}
              >
                {busy === 'payment' ? 'Đang xử lý…' : 'Thanh toán và đăng yêu cầu'}
              </button>
            </div>
          )}
        </section>
        <section className="student-panel teaching-detail-applications">
          <h2>Đề nghị từ học viên</h2>
          {!applications.length ? (
            <div className="student-empty">
              <h3>Chưa có đề nghị từ học viên</h3>
            </div>
          ) : (
            applications.map((application) => (
              <article className="teaching-application" key={application.id}>
                <div className="teaching-application-avatar">{application.studentFirstName?.[0] ?? 'H'}</div>
                <div className="teaching-application-content">
                  <strong>
                    <ProfileNameLink role="student" profileId={application.studentId}>
                      {[application.studentFirstName, application.studentLastName].filter(Boolean).join(' ') ||
                        application.studentEmail}
                    </ProfileNameLink>
                  </strong>
                  <span>
                    {application.gradeName} · {application.proposedPrice.toLocaleString('vi-VN')} VNĐ
                  </span>
                  <span>
                    {application.message ||
                      formatPreferredSchedule(application.preferredSchedule) ||
                      'Chưa có lời nhắn.'}
                  </span>
                </div>
                <span className={`student-status ${application.status.toLowerCase()}`}>
                  {applicationStatusText(application.status)}
                </span>
                {application.status === 'PENDING' && request.status === 'OPEN' && (
                  <div className="teaching-application-actions">
                    <button
                      className="student-button draft"
                      disabled={busy === application.id}
                      onClick={() => void updateApplication(application, 'ACCEPTED')}
                    >
                      Chấp nhận
                    </button>
                    <button
                      className="student-button secondary"
                      disabled={busy === application.id}
                      onClick={() => void updateApplication(application, 'REJECTED')}
                    >
                      Từ chối
                    </button>
                  </div>
                )}
                {application.status === 'PENDING' && request.status === 'MATCHED' && (
                  <span className="teaching-application-closed-note">Yêu cầu đã đủ số lượng học viên.</span>
                )}
                {(() => {
                  const currentContractId =
                    application.contractId ?? currentContractByApplicationId.get(application.id);
                  const previousContractId =
                    application.previousContractId ?? previousContractByApplicationId.get(application.id);
                  const oldContractId =
                    previousContractId && previousContractId !== currentContractId ? previousContractId : null;
                  const previousContract = (query.data?.contracts ?? []).find((item) => item.id === previousContractId);
                  const previousContractStatus = application.previousContractStatus ?? previousContract?.status ?? null;
                  const canCreateContract =
                    !currentContractId &&
                    (!previousContractId || previousContractStatus === 'CANCELLED' || previousContractStatus === null);
                  const contract = (query.data?.contracts ?? []).find((item) => item.id === currentContractId);
                  const canRejectAccepted =
                    application.status === 'ACCEPTED' && (!contract || contract.status === 'PENDING');

                  if (application.status === 'ACCEPTED' && canRejectAccepted) {
                    return (
                      <div className="teaching-application-actions">
                        {currentContractId ? (
                          <Link className="student-button draft" to={`/tutor/sections/contracts/${currentContractId}`}>
                            Xem hợp đồng
                          </Link>
                        ) : null}
                        {!currentContractId && canCreateContract ? (
                          <button
                            className="student-button draft"
                            type="button"
                            onClick={() =>
                              navigate(
                                `/tutor/sections/teaching-requests/${requestId}/student-requests/${application.id}/contracts/new`
                              )
                            }
                          >
                            Tạo hợp đồng
                          </button>
                        ) : null}
                        <button
                          className="student-button secondary"
                          disabled={busy === application.id}
                          onClick={() => void updateApplication(application, 'REJECTED')}
                        >
                          Từ chối
                        </button>
                        {oldContractId ? (
                          <Link className="student-button secondary" to={`/tutor/sections/contracts/${oldContractId}`}>
                            Xem hợp đồng cũ
                          </Link>
                        ) : null}
                      </div>
                    );
                  }

                  if (['ACCEPTED', 'CONNECTED'].includes(application.status) && currentContractId) {
                    return (
                      <div className="teaching-application-actions">
                        <Link className="student-button draft" to={`/tutor/sections/contracts/${currentContractId}`}>
                          Xem hợp đồng
                        </Link>
                        <Link
                          className="student-button secondary"
                          to={`/tutor/complaints/new?contractId=${currentContractId}`}
                        >
                          Khiếu nại
                        </Link>
                      </div>
                    );
                  }

                  if (application.status === 'ACCEPTED') {
                    return (
                      <div className="teaching-application-actions">
                        {canCreateContract ? (
                          <button
                            className="student-button draft"
                            type="button"
                            onClick={() =>
                              navigate(
                                `/tutor/sections/teaching-requests/${requestId}/student-requests/${application.id}/contracts/new`
                              )
                            }
                          >
                            Tạo hợp đồng
                          </button>
                        ) : previousContractId ? (
                          <Link className="student-button draft" to={`/tutor/sections/contracts/${previousContractId}`}>
                            Xem hợp đồng
                          </Link>
                        ) : null}
                        {oldContractId ? (
                          <Link className="student-button secondary" to={`/tutor/sections/contracts/${oldContractId}`}>
                            Xem hợp đồng cũ
                          </Link>
                        ) : null}
                      </div>
                    );
                  }

                  if (oldContractId && ['REJECTED', 'CANCELLED'].includes(application.status)) {
                    return (
                      <div className="teaching-application-actions">
                        <Link className="student-button secondary" to={`/tutor/sections/contracts/${oldContractId}`}>
                          Xem hợp đồng cũ
                        </Link>
                      </div>
                    );
                  }
                  return null;
                })()}
              </article>
            ))
          )}
        </section>
      </div>
      {confirmingApplication && (
        <div className="teaching-confirm-backdrop" role="presentation">
          <section className="student-panel teaching-confirm-dialog" role="dialog" aria-modal="true">
            <h2>Xác nhận học viên cuối cùng</h2>
            <p>
              Sau khi chấp nhận học viên này, yêu cầu sẽ đủ số lượng học viên. Các đề nghị đang chờ còn lại sẽ tự động
              chuyển sang trạng thái từ chối.
            </p>
            <div className="student-form-actions">
              <button className="student-button secondary" type="button" onClick={() => setConfirmingApplication(null)}>
                Hủy
              </button>
              <button
                className="student-button draft"
                type="button"
                disabled={busy !== null}
                onClick={() => {
                  const application = confirmingApplication;
                  setConfirmingApplication(null);
                  void applyApplicationStatus(application, 'ACCEPTED');
                }}
              >
                {busy ? 'Đang cập nhật…' : 'Xác nhận và từ chối còn lại'}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

function statusText(status: string) {
  return (
    (
      {
        DRAFT: 'Bản nháp',
        PENDING_REVIEW: 'Chờ duyệt',
        OPEN: 'Đang mở',
        MATCHED: 'Đã kết nối',
        CLOSED: 'Đã đóng',
        CANCELLED: 'Đã hủy',
        REJECTED: 'Bị từ chối'
      } as Record<string, string>
    )[status] ?? status
  );
}

function applicationStatusText(status: string) {
  return (
    (
      {
        PENDING: 'Đang chờ',
        ACCEPTED: 'Đã chấp nhận',
        CONNECTED: 'Đã kết nối',
        REJECTED: 'Đã từ chối',
        CANCELLED: 'Đã hủy'
      } as Record<string, string>
    )[status] ?? status
  );
}

function formatPreferredSchedule(value: string | null) {
  if (!value) return '';
  const labels: Record<string, string> = {
    Sunday: 'Chủ Nhật',
    Monday: 'Thứ Hai',
    Tuesday: 'Thứ Ba',
    Wednesday: 'Thứ Tư',
    Thursday: 'Thứ Năm',
    Friday: 'Thứ Sáu',
    Saturday: 'Thứ Bảy'
  };
  return value.replace(
    /Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday/gi,
    (day) => labels[day[0].toUpperCase() + day.slice(1).toLowerCase()] ?? day
  );
}
