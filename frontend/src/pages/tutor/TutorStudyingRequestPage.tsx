import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';
import { cancelTutorProposal, getTutorContracts, updateTutorProposal } from '../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';
import { tutorNextStepLabel, tutorScheduleLabel, tutorStatusLabel } from '../../utils/tutorDisplay';

const weekdays = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

export function TutorStudyingRequestPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { requestId = '' } = useParams();
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const [proposalRevision, setProposalRevision] = useState(0);
  const [contractIds, setContractIds] = useState<Record<string, string>>({});
  const proposalsQuery = useTutorProposals(undefined, proposalRevision);
  const proposalHistory = (proposalsQuery.data ?? [])
    .filter((proposal) => proposal.studyingRequestId === requestId)
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
  const myProposal = proposalHistory[0];
  const hasAcceptedProposal = proposalHistory.some((proposal) => ['ACCEPTED', 'CONNECTED'].includes(proposal.status));
  const canApply = Boolean(
    request?.status === 'OPEN' &&
    !hasAcceptedProposal &&
    (!myProposal || myProposal.status === 'REJECTED' || myProposal.status === 'CANCELLED')
  );
  const hideProposalSidebar = Boolean(canApply || myProposal?.status === 'CANCELLED');
  const [showApply, setShowApply] = useState(() =>
    Boolean((location.state as { openApplication?: boolean } | null)?.openApplication)
  );
  const [editingProposal, setEditingProposal] = useState(false);
  const [sending, setSending] = useState(false);
  const [cancellingProposal, setCancellingProposal] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getTutorContracts(controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) {
          setContractIds(
            Object.fromEntries(
              page.content
                .filter((contract) => contract.tutorStudentRequestId)
                .map((contract) => [contract.tutorStudentRequestId as string, contract.id])
            )
          );
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  const propose = async (values: TutorApplicationValues) => {
    if (!request) return;
    setSending(true);
    setError('');
    setNotice('');
    try {
      const payload = {
        ...values,
        gradeId: request.gradeId
      };
      if (editingProposal && myProposal) {
        await updateTutorProposal(myProposal.id, payload);
        setNotice('Đề xuất của bạn đã được cập nhật.');
      } else {
        await submitTutorStudyingRequestProposal(request.id, payload);
        setNotice(myProposal?.status === 'CANCELLED' ? 'Bạn đã gửi lại đề xuất.' : 'Đề xuất đã được gửi đến học viên.');
      }
      setShowApply(false);
      setEditingProposal(false);
      setProposalRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.status === 409
          ? 'Bạn đã có đề xuất đang chờ xử lý cho yêu cầu này.'
          : caught instanceof Error
            ? caught.message
            : 'Không thể gửi đề xuất.'
      );
    } finally {
      setSending(false);
    }
  };

  const openApplication = (edit: boolean) => {
    setError('');
    setEditingProposal(edit);
    setShowApply(true);
  };

  const cancelProposal = async () => {
    if (!myProposal || myProposal.status !== 'PENDING') return;
    setCancellingProposal(true);
    setError('');
    setNotice('');
    try {
      await cancelTutorProposal(myProposal.id);
      setNotice('Đề xuất đã được hủy.');
      setProposalRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy đề xuất.');
    } finally {
      setCancellingProposal(false);
    }
  };

  if (loading) return <div className="tutor-empty">Đang tải yêu cầu học…</div>;
  if (!request)
    return (
      <>
        <button className="tutor-button secondary tutor-navigation-button" type="button" onClick={() => navigate(-1)}>
          ← Quay lại danh sách yêu cầu
        </button>
        <div className="tutor-error" role="alert">
          {requestError || 'Yêu cầu học này không còn khả dụng.'}
        </div>
      </>
    );

  return (
    <>
      <div className="tutor-detail-topbar">
        <button
          className="tutor-button secondary tutor-navigation-button tutor-back-link"
          type="button"
          onClick={() => navigate(-1)}
        >
          ← Quay lại danh sách yêu cầu
        </button>
        {!proposalsQuery.isLoading && canApply && (
          <button className="tutor-button tutor-top-apply-button" type="button" onClick={() => openApplication(false)}>
            Ứng tuyển yêu cầu này <span>→</span>
          </button>
        )}
      </div>
      {notice && (
        <div className="tutor-notice tutor-toast-source" role="status">
          {notice}
        </div>
      )}
      {error && !showApply && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div
        className={`tutor-request-detail-layout tutor-market-detail${!proposalsQuery.isLoading && hideProposalSidebar ? ' tutor-market-detail-no-sidebar' : ''}${myProposal && !hideProposalSidebar ? ' tutor-market-detail-my-application' : ''}`}
      >
        <article
          className={`tutor-panel tutor-detail-main${myProposal && !hideProposalSidebar ? ' tutor-my-application-request-card' : ''}`}
        >
          {myProposal && !hideProposalSidebar ? (
            <>
              <h1 className="tutor-detail-heading">{request.title || `Tìm gia sư môn ${request.subjectName}`}</h1>
              <dl className="tutor-application-detail-list">
                <div>
                  <dt>Học viên đăng yêu cầu</dt>
                  <dd>
                    <ProfileNameLink role="student" profileId={request.studentId}>
                      {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
                    </ProfileNameLink>
                  </dd>
                </div>
                <div>
                  <dt>Trạng thái yêu cầu</dt>
                  <dd>
                    <span className={`tutor-cert-status status-request-${request.status.toLowerCase()}`}>
                      {requestStatusLabel(request.status)}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Môn học</dt>
                  <dd>{request.subjectName || 'Chưa xác định'}</dd>
                </div>
                <div>
                  <dt>Khối lớp</dt>
                  <dd>{request.gradeName || 'Chưa xác định'}</dd>
                </div>
                <div>
                  <dt>Hình thức học</dt>
                  <dd>{request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</dd>
                </div>
                <div>
                  <dt>Ngân sách</dt>
                  <dd>{formatPrice(request.minPrice, request.maxPrice)}</dd>
                </div>
                <div>
                  <dt>Lịch học mong muốn</dt>
                  <dd>{tutorScheduleLabel(request.preferredSchedule)}</dd>
                </div>
                {request.availabilities.length > 0 && (
                  <div>
                    <dt>Thời gian có thể học</dt>
                    <dd className="tutor-application-detail-availability">
                      {request.availabilities.map((slot, index) => (
                        <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                          {weekdays[slot.dayOfWeek % 7]}, {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                        </span>
                      ))}
                    </dd>
                  </div>
                )}
                <div>
                  <dt>Khu vực</dt>
                  <dd>{[request.districtName, request.provinceName].filter(Boolean).join(', ') || 'Chưa cung cấp'}</dd>
                </div>
                {request.learningMode === 'OFFLINE' && (
                  <div>
                    <dt>Địa chỉ chi tiết</dt>
                    <dd>{request.detailAddress || 'Chưa cung cấp'}</dd>
                  </div>
                )}
                <div>
                  <dt>Mô tả</dt>
                  <dd>{request.description?.trim() || 'Học viên chưa bổ sung mô tả.'}</dd>
                </div>
                {request.learningGoals?.trim() && (
                  <div>
                    <dt>Mục tiêu học tập</dt>
                    <dd>{request.learningGoals}</dd>
                  </div>
                )}
                {request.note?.trim() && (
                  <div>
                    <dt>Ghi chú</dt>
                    <dd>{request.note}</dd>
                  </div>
                )}
                <div>
                  <dt>Thời gian tạo</dt>
                  <dd>{formatDateTime(request.createdAt)}</dd>
                </div>
              </dl>
            </>
          ) : (
            <>
              <div className="tutor-card-top">
                <span className="tutor-subject-tag">{request.subjectName || 'Môn học'}</span>
                <span>{request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} · Đang mở</span>
              </div>
              <h1 className="tutor-detail-heading">{request.title || `Tìm gia sư môn ${request.subjectName}`}</h1>
              <p className="tutor-detail-subtitle">{request.gradeName}</p>
              <p className="tutor-request-author">
                <span>
                  Đăng bởi{' '}
                  <strong>
                    <ProfileNameLink role="student" profileId={request.studentId}>
                      {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
                    </ProfileNameLink>
                  </strong>
                </span>
                <span>Đăng ngày {new Date(request.createdAt).toLocaleDateString('vi-VN')}</span>
              </p>
              <div className="tutor-detail-facts">
                <span>
                  <small>Ngân sách</small>
                  <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
                </span>
                <span>
                  <small>Lớp học</small>
                  <strong>{request.gradeName || 'Chưa xác định'}</strong>
                </span>
                <span>
                  <small>Hình thức học</small>
                  <strong>{request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</strong>
                </span>
                {request.learningMode === 'OFFLINE' && (
                  <span>
                    <small className="tutor-detail-fact-tag">Địa chỉ dạy học</small>
                    <strong>
                      {[request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                        'Chưa cung cấp'}
                    </strong>
                  </span>
                )}
                <span>
                  <small>Lịch học mong muốn</small>
                  <strong>{tutorScheduleLabel(request.preferredSchedule)}</strong>
                </span>
                <span>
                  <small>Ngày đăng yêu cầu</small>
                  <strong>{new Date(request.createdAt).toLocaleDateString()}</strong>
                </span>
              </div>
              {request.note?.trim() && (
                <div className="tutor-application-note">
                  <span className="tutor-application-note-tag">Ghi chú</span>
                  <p>{request.note}</p>
                </div>
              )}
              <section className="tutor-detail-section">
                <span className="tutor-eyebrow">MỤC TIÊU HỌC TẬP</span>
                <h2 className="tutor-detail-title">Nhu cầu của học viên</h2>
                {request.description?.trim() && (
                  <>
                    <h3 className="tutor-small-heading">Chi tiết yêu cầu</h3>
                    <p className="tutor-detail-copy">{request.description}</p>
                  </>
                )}
                {request.learningGoals?.trim() && (
                  <>
                    <h3 className="tutor-small-heading">Mục tiêu</h3>
                    <p className="tutor-detail-copy">{request.learningGoals}</p>
                  </>
                )}
                {!request.description?.trim() && !request.learningGoals?.trim() && (
                  <p className="tutor-detail-copy">Học viên chưa bổ sung thêm thông tin.</p>
                )}
              </section>
            </>
          )}
          {proposalHistory.length > 1 && (
            <section className="tutor-detail-section tutor-proposal-history">
              <span className="tutor-eyebrow">LỊCH SỬ ỨNG TUYỂN</span>
              <h2 className="tutor-detail-title">Các lần ứng tuyển trước đó</h2>
              <div className="tutor-proposal-history-table-wrap">
                <table className="tutor-proposal-history-table">
                  <thead>
                    <tr>
                      <th>Thời gian</th>
                      <th>Học phí</th>
                      <th>Hình thức</th>
                      <th>Lịch dạy</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {proposalHistory.slice(1).map((proposal) => (
                      <tr key={proposal.id}>
                        <td>{formatDateTime(proposal.createdAt)}</td>
                        <td>{new Intl.NumberFormat('vi-VN').format(proposal.proposedPrice)} VNĐ</td>
                        <td>{proposal.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</td>
                        <td>{tutorScheduleLabel(proposal.preferredSchedule, 'Lịch linh hoạt')}</td>
                        <td>
                          <span className={`tutor-cert-status status-${proposal.status.toLowerCase()}`}>
                            {tutorStatusLabel(proposal.status)}
                          </span>
                        </td>
                        <td>{acceptedProposalAction(proposal, contractIds[proposal.id])}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </article>
        <aside
          className={`tutor-panel tutor-apply-sidebar${!proposalsQuery.isLoading && hideProposalSidebar ? ' tutor-apply-sidebar-empty' : ''}${myProposal && !hideProposalSidebar ? ' tutor-my-application-detail-card' : ''}`}
        >
          {proposalsQuery.isLoading ? (
            <>
              <div className="tutor-application-panel-heading">
                <span className="tutor-proposal-application-icon" aria-hidden="true">
                  ↗
                </span>
                <div>
                  <span className="tutor-eyebrow">ỨNG TUYỂN CỦA TÔI</span>
                  <h2>Đang tải đề xuất…</h2>
                </div>
              </div>
            </>
          ) : myProposal ? (
            <>
              <div className="tutor-my-application-detail-heading">
                <h2>Ứng tuyển của tôi</h2>
              </div>
              <dl className="tutor-application-detail-list">
                <div>
                  <dt>Trạng thái</dt>
                  <dd>
                    <span className={`tutor-cert-status status-${myProposal.status.toLowerCase()}`}>
                      {tutorStatusLabel(myProposal.status)}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Học phí đề xuất</dt>
                  <dd>{new Intl.NumberFormat('vi-VN').format(myProposal.proposedPrice)} VNĐ</dd>
                </div>
                <div>
                  <dt>Khối lớp</dt>
                  <dd>{myProposal.gradeName || request.gradeName || 'Chưa xác định'}</dd>
                </div>
                <div>
                  <dt>Hình thức dạy</dt>
                  <dd>{myProposal.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</dd>
                </div>
                <div>
                  <dt>Thời gian có thể dạy</dt>
                  <dd>{tutorScheduleLabel(myProposal.preferredSchedule, 'Lịch linh hoạt')}</dd>
                </div>
                {myProposal.teachingMode === 'OFFLINE' &&
                  (myProposal.preferredProvinceName || myProposal.preferredDistrictName) && (
                    <div>
                      <dt>Địa chỉ dạy học</dt>
                      <dd>
                        {[myProposal.preferredDistrictName, myProposal.preferredProvinceName]
                          .filter(Boolean)
                          .join(', ')}
                      </dd>
                    </div>
                  )}
                {myProposal.status === 'ACCEPTED' && (
                  <>
                    <div>
                      <dt>Bước tiếp theo</dt>
                      <dd>{contractIds[myProposal.id] ? 'Đã có hợp đồng' : tutorNextStepLabel(myProposal.nextStep)}</dd>
                    </div>
                    <div>
                      <dt>Thời gian được duyệt</dt>
                      <dd>{formatDateTime(myProposal.acceptedAt ?? myProposal.updatedAt)}</dd>
                    </div>
                  </>
                )}
                {myProposal.message?.trim() && (
                  <div>
                    <dt>Lời nhắn</dt>
                    <dd>{myProposal.message}</dd>
                  </div>
                )}
                <div>
                  <dt>Thời gian tạo</dt>
                  <dd>{formatDateTime(myProposal.createdAt)}</dd>
                </div>
                <div>
                  <dt>Cập nhật lần cuối</dt>
                  <dd>{formatDateTime(myProposal.updatedAt)}</dd>
                </div>
              </dl>
              <div className="tutor-my-application-detail-actions">
                {myProposal.status === 'PENDING' ? (
                  <>
                    <button
                      className="tutor-button tutor-apply-button"
                      type="button"
                      onClick={() => openApplication(true)}
                    >
                      Sửa đề xuất <span>↗</span>
                    </button>
                    <button
                      className="tutor-button secondary tutor-cancel-proposal-button"
                      type="button"
                      disabled={cancellingProposal}
                      onClick={() => void cancelProposal()}
                    >
                      {cancellingProposal ? 'Đang hủy…' : 'Hủy đề xuất'}
                    </button>
                    <button
                      className="tutor-button secondary tutor-navigation-compact"
                      type="button"
                      onClick={() => navigate('/tutor/proposals')}
                    >
                      Xem tất cả đề xuất →
                    </button>
                  </>
                ) : myProposal.status === 'CANCELLED' ? (
                  <button
                    className="tutor-button tutor-apply-button"
                    type="button"
                    onClick={() => openApplication(false)}
                  >
                    Gửi lại đề xuất <span>→</span>
                  </button>
                ) : myProposal.status === 'ACCEPTED' ? (
                  acceptedProposalAction(myProposal, contractIds[myProposal.id], true)
                ) : (
                  <button
                    className="tutor-button tutor-apply-button"
                    type="button"
                    onClick={() => navigate('/tutor/proposals')}
                  >
                    Xem đề xuất của tôi <span>→</span>
                  </button>
                )}
              </div>
            </>
          ) : (
            <>
              <h2>Gửi đề xuất giảng dạy</h2>
              <button className="tutor-button tutor-apply-button" type="button" onClick={() => openApplication(false)}>
                Ứng tuyển yêu cầu này <span>→</span>
              </button>
              <button
                className="tutor-button secondary tutor-save-button"
                type="button"
                onClick={() => navigate('/tutor/proposals')}
              >
                Ứng tuyển của tôi
              </button>
            </>
          )}
        </aside>
      </div>
      {showApply && (
        <TutorApplicationForm
          key={`${editingProposal ? 'edit' : 'apply'}-${myProposal?.id ?? request.id}`}
          subjectName={request.subjectName}
          requestMode={request.learningMode}
          requestProvinceId={request.provinceId}
          requestDistrictId={request.districtId}
          proposal={myProposal}
          editing={editingProposal}
          sending={sending}
          error={error}
          onClose={() => {
            setShowApply(false);
            setEditingProposal(false);
          }}
          onSubmit={(values) => void propose(values)}
        />
      )}
    </>
  );
}

function acceptedProposalAction(
  proposal: { id: string; status: string; studyingRequestId: string },
  contractId: string | undefined,
  prominent = false
) {
  if (proposal.status !== 'ACCEPTED') return null;
  if (contractId) {
    return (
      <Link
        className={`tutor-button secondary tutor-navigation-compact${prominent ? ' tutor-my-application-contract-link' : ''}`}
        to={`/tutor/sections/contracts/${contractId}`}
      >
        Xem hợp đồng
      </Link>
    );
  }
  return <span className="tutor-proposal-contract-waiting">Chờ học viên tạo hợp đồng</span>;
}

function requestStatusLabel(status: string) {
  return (
    (
      {
        DRAFT: 'Bản nháp',
        PENDING_REVIEW: 'Chờ duyệt',
        OPEN: 'Đang mở',
        MATCHED: 'Đã kết nối',
        CLOSED: 'Đã đóng',
        CANCELLED: 'Đã hủy',
        REJECTED: 'Không được duyệt'
      } as Record<string, string>
    )[status] ?? status
  );
}

function formatPrice(min: number | null, max: number | null) {
  const format = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
  if (min === null && max === null) return 'Có thể thương lượng';
  return min !== null && max !== null ? `${format(min)}–${format(max)} VNĐ` : `${format(min ?? max ?? 0)} VNĐ`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
