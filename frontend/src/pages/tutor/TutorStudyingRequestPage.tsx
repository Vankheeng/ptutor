import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { recordEvent } from '../../services/recommendationApi';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';
import { cancelTutorProposal, getTutorContracts, updateTutorProposal } from '../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';
import { tutorNextStepLabel, tutorScheduleLabel, tutorStatusLabel } from '../../utils/tutorDisplay';
import { ApplicationDetailPanel } from '../../components/applications/ApplicationDetailPanel';

export function TutorStudyingRequestPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { requestId = '' } = useParams();
  // Opened from the recommendations: the search to report the proposal to.
  const recommendationLogId = (useLocation().state as { recommendationLogId?: string } | null)?.recommendationLogId;
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const [proposalRevision, setProposalRevision] = useState(0);
  const [contractIds, setContractIds] = useState<Record<string, string>>({});
  const proposalsQuery = useTutorProposals(undefined, proposalRevision);
  const proposalHistory = (proposalsQuery.data ?? [])
    .filter((proposal) => proposal.studyingRequestId === requestId)
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
  const myProposal = proposalHistory[0];
  const hasAcceptedProposal = proposalHistory.some((proposal) => proposal.status === 'ACCEPTED');
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
        if (recommendationLogId) {
          recordEvent(recommendationLogId, request.id, 'CONTACTED').catch(() => undefined);
        }
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
        className={`tutor-request-detail-layout tutor-market-detail${!proposalsQuery.isLoading && hideProposalSidebar ? ' tutor-market-detail-no-sidebar' : ''}`}
      >
        <article className="tutor-panel tutor-detail-main">
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
          className={`tutor-panel tutor-apply-sidebar${!proposalsQuery.isLoading && hideProposalSidebar ? ' tutor-apply-sidebar-empty' : ''}`}
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
            <ApplicationDetailPanel
              heading="Ứng tuyển của tôi"
              statusClass={`status-${myProposal.status.toLowerCase()}`}
              statusLabel={tutorStatusLabel(myProposal.status)}
              priceLabel="Học phí đề xuất"
              price={myProposal.proposedPrice}
              fields={[
                {
                  label: 'Thời gian có thể dạy',
                  value: tutorScheduleLabel(myProposal.preferredSchedule, 'Lịch linh hoạt')
                },
                { label: 'Hình thức dạy', value: myProposal.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp' },
                ...(myProposal.teachingMode === 'OFFLINE' &&
                (myProposal.preferredProvinceName || myProposal.preferredDistrictName)
                  ? [
                      {
                        label: 'Địa chỉ dạy học',
                        value: [myProposal.preferredDistrictName, myProposal.preferredProvinceName]
                          .filter(Boolean)
                          .join(', ')
                      }
                    ]
                  : [])
              ]}
              notes={myProposal.message?.trim() ? [{ label: 'Lời nhắn', value: myProposal.message }] : undefined}
              metadata={[
                { label: 'Thời gian tạo', value: formatDateTime(myProposal.createdAt) },
                { label: 'Cập nhật lần cuối', value: formatDateTime(myProposal.updatedAt) },
                ...(myProposal.status === 'ACCEPTED'
                  ? [
                      { label: 'Thời gian được duyệt', value: formatDateTime(myProposal.updatedAt) },
                      {
                        label: 'Bước tiếp theo',
                        value: contractIds[myProposal.id] ? 'Đã có hợp đồng' : tutorNextStepLabel(myProposal.nextStep)
                      }
                    ]
                  : [])
              ]}
              actions={
                myProposal.status === 'PENDING' ? (
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
                  acceptedProposalAction(myProposal, contractIds[myProposal.id])
                ) : (
                  <button
                    className="tutor-button tutor-apply-button"
                    type="button"
                    onClick={() => navigate('/tutor/proposals')}
                  >
                    Xem đề xuất của tôi <span>→</span>
                  </button>
                )
              }
            />
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
  contractId: string | undefined
) {
  if (proposal.status !== 'ACCEPTED') return null;
  if (contractId) {
    return (
      <Link className="tutor-button secondary tutor-navigation-compact" to={`/tutor/sections/contracts/${contractId}`}>
        Xem hợp đồng
      </Link>
    );
  }
  return <span className="tutor-proposal-contract-waiting">Chờ học viên tạo hợp đồng</span>;
}

function formatPrice(min: number | null, max: number | null) {
  const format = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
  if (min === null && max === null) return 'Có thể thương lượng';
  return min !== null && max !== null ? `${format(min)}–${format(max)} VNĐ` : `${format(min ?? max ?? 0)} VNĐ`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
