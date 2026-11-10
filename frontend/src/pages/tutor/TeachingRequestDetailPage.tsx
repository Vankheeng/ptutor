import { useCallback, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  createTeachingRequestContract,
  getMyTeachingRequest,
  getTeachingRequestApplications,
  payTeachingRequestByWallet,
  startTeachingRequestVnPay,
  teachingRequestWalletErrorMessage,
  updateMyTeachingRequestStatus,
  updateTeachingRequestApplication,
  TEACHING_REQUEST_FEE_PER_STUDENT,
  type StudentTeachingApplication
} from '../../services/myTeachingRequestApi';
import type { TeachingRequest } from '../../types/api';
import '../student/student.css';
import './teaching-request-detail.css';

export function TeachingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [contractFor, setContractFor] = useState<StudentTeachingApplication | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'wallet' | 'vnpay'>('wallet');
  const load = useCallback(
    async (signal: AbortSignal) => {
      const [request, applications] = await Promise.all([
        getMyTeachingRequest(requestId, signal),
        getTeachingRequestApplications(requestId, signal)
      ]);
      return { request, applications };
    },
    [requestId]
  );
  const query = useResource(load, revision);
  const request: TeachingRequest | null = query.data?.request ?? null;
  const applications = query.data?.applications ?? [];

  const toggleRequestStatus = async () => {
    if (!request || !['OPEN', 'CLOSED'].includes(request.status ?? '') || request.reviewedAt) return;
    const nextStatus = request.status === 'OPEN' ? 'CLOSED' : 'OPEN';
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

  const updateApplication = async (application: StudentTeachingApplication, status: 'ACCEPTED' | 'REJECTED') => {
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

  const createContract = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!contractFor) return;
    setBusy(contractFor.id);
    setError('');
    const data = new FormData(event.currentTarget);
    try {
      await createTeachingRequestContract(requestId, contractFor.id, {
        price: Number(data.get('price')),
        paymentPeriod: String(data.get('paymentPeriod')) as 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE',
        startDate: String(data.get('startDate')),
        endDate: String(data.get('endDate')),
        totalLessons: data.get('totalLessons') ? Number(data.get('totalLessons')) : undefined,
        preferredSchedule: String(data.get('preferredSchedule') ?? '') || undefined
      });
      setContractFor(null);
      setNotice('Đã tạo hợp đồng và gửi cho học viên ký.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể tạo hợp đồng.');
    } finally {
      setBusy(null);
    }
  };

  const payDraft = async () => {
    if (!request) return;
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
      <div className="teaching-detail-top-actions">
        <button className="student-button secondary" onClick={() => navigate('/tutor/sections/teaching-requests')}>
          ← Quay lại danh sách
        </button>
        <div className="teaching-detail-actions-right">
          {['OPEN', 'CLOSED'].includes(request.status ?? '') && !request.reviewedAt && (
            <button
              className="student-button draft"
              disabled={busy === 'status'}
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
        </div>
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
            <div>
              <dt>Mô tả</dt>
              <dd>{request.description || request.note || 'Chưa có mô tả.'}</dd>
            </div>
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
              <button className="student-button draft" disabled={busy === 'payment'} onClick={() => void payDraft()}>
                {busy === 'payment' ? 'Đang xử lý…' : 'Thanh toán và đăng yêu cầu'}
              </button>
            </div>
          )}
        </section>
        <section className="student-panel teaching-detail-applications">
          <h2>Đề nghị từ học viên</h2>
          <p>Xem và quản lý các đề nghị nhận được cho yêu cầu này.</p>
          {!applications.length ? (
            <div className="student-empty">
              <h3>Chưa có đề nghị nào</h3>
              <p>Đề nghị từ học viên sẽ xuất hiện ở đây.</p>
            </div>
          ) : (
            applications.map((application) => (
              <article className="teaching-application" key={application.id}>
                <div className="teaching-application-avatar">{application.studentFirstName?.[0] ?? 'H'}</div>
                <div className="teaching-application-content">
                  <strong>
                    {[application.studentFirstName, application.studentLastName].filter(Boolean).join(' ') ||
                      application.studentEmail}
                  </strong>
                  <span>
                    {application.gradeName} · {application.proposedPrice.toLocaleString('vi-VN')} VNĐ
                  </span>
                  <span>{application.message || application.preferredSchedule || 'Chưa có lời nhắn.'}</span>
                </div>
                <span className={`student-status ${application.status.toLowerCase()}`}>
                  {applicationStatusText(application.status)}
                </span>
                {application.status === 'PENDING' && (
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
                {application.status === 'ACCEPTED' && (
                  <button className="student-button draft" onClick={() => setContractFor(application)}>
                    Tạo hợp đồng
                  </button>
                )}
              </article>
            ))
          )}
        </section>
      </div>
      {contractFor && (
        <div className="teaching-contract-backdrop">
          <form className="student-panel teaching-contract-form" onSubmit={(event) => void createContract(event)}>
            <h2>Tạo hợp đồng</h2>
            <p>Học viên: {[contractFor.studentFirstName, contractFor.studentLastName].filter(Boolean).join(' ')}</p>
            <label>
              Học phí (VNĐ)
              <input name="price" type="number" min="0" defaultValue={contractFor.proposedPrice} required />
            </label>
            <label>
              Chu kỳ thanh toán
              <select name="paymentPeriod">
                <option value="PER_LESSON">Theo buổi</option>
                <option value="WEEKLY">Hàng tuần</option>
                <option value="MONTHLY">Hàng tháng</option>
                <option value="PACKAGE">Trọn gói</option>
              </select>
            </label>
            <label>
              Ngày bắt đầu
              <input name="startDate" type="date" required />
            </label>
            <label>
              Ngày kết thúc
              <input name="endDate" type="date" required />
            </label>
            <label>
              Số buổi
              <input name="totalLessons" type="number" min="1" />
            </label>
            <label>
              Lịch học
              <input name="preferredSchedule" defaultValue={contractFor.preferredSchedule ?? ''} />
            </label>
            <div className="student-form-actions">
              <button className="student-button draft" disabled={busy === contractFor.id}>
                Tạo hợp đồng
              </button>
              <button type="button" className="student-button secondary" onClick={() => setContractFor(null)}>
                Hủy
              </button>
            </div>
          </form>
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
      { PENDING: 'Đang chờ', ACCEPTED: 'Đã chấp nhận', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' } as Record<
        string,
        string
      >
    )[status] ?? status
  );
}
