import { useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest, useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';
import { cancelTutorProposal, updateTutorProposal } from '../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';
import { TutorStudyingRequestCard } from './components/TutorStudyingRequestCard';
import { tutorScheduleLabel, tutorStatusLabel } from '../../utils/tutorDisplay';
import { ApplicationDetailPanel } from '../../components/applications/ApplicationDetailPanel';

const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

export function TutorStudyingRequestPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { requestId = '' } = useParams();
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const relatedQuery = useStudyingRequests({ page: 0, size: 30 });
  const [proposalRevision, setProposalRevision] = useState(0);
  const proposalsQuery = useTutorProposals(undefined, proposalRevision);
  const myProposal = proposalsQuery.data?.find((proposal) => proposal.studyingRequestId === requestId);
  const [showApply, setShowApply] = useState(() =>
    Boolean((location.state as { openApplication?: boolean } | null)?.openApplication)
  );
  const [editingProposal, setEditingProposal] = useState(false);
  const [sending, setSending] = useState(false);
  const [cancellingProposal, setCancellingProposal] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const recommendations = useMemo(
    () =>
      (relatedQuery.data?.content ?? [])
        .filter((item) => item.id !== requestId)
        .sort((a, b) => Number(b.subjectId === request?.subjectId) - Number(a.subjectId === request?.subjectId))
        .slice(0, 3),
    [relatedQuery.data, requestId, request?.subjectId]
  );

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
      <button
        className="tutor-button secondary tutor-navigation-button tutor-back-link"
        type="button"
        onClick={() => navigate(-1)}
      >
        ← Quay lại danh sách yêu cầu
      </button>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}{' '}
          <button
            className="tutor-button secondary tutor-navigation-compact"
            type="button"
            onClick={() => navigate('/tutor/proposals')}
          >
            Xem đề xuất của tôi →
          </button>
        </div>
      )}
      {error && !showApply && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="tutor-request-detail-layout tutor-market-detail">
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
                {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
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
          {request.availabilities.length > 0 && (
            <section className="tutor-detail-section">
              <h2 className="tutor-detail-title">Thời gian học viên có thể học</h2>
              <div className="tutor-availability-list">
                {request.availabilities.map((slot, index) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                    {weekdays[slot.dayOfWeek % 7]} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                  </span>
                ))}
              </div>
            </section>
          )}
          <section className="tutor-recommendations">
            <div className="tutor-section-heading">
              <div>
                <span className="tutor-eyebrow">KHÁM PHÁ THÊM</span>
              </div>
              <button
                className="tutor-button secondary tutor-navigation-compact tutor-see-all-proposals"
                type="button"
                onClick={() => navigate('/tutor/students')}
              >
                See all →
              </button>
            </div>
            {recommendations.length ? (
              <div className="tutor-recommendation-list">
                {recommendations.map((item) => (
                  <TutorStudyingRequestCard key={item.id} request={item} actionLabel="Xem yêu cầu" />
                ))}
              </div>
            ) : (
              <p className="tutor-muted">Yêu cầu mới sẽ hiển thị tại đây khi học viên đăng bài.</p>
            )}
          </section>
        </article>
        <aside className="tutor-panel tutor-apply-sidebar">
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
                  ? [{ label: 'Thời gian được duyệt', value: formatDateTime(myProposal.updatedAt) }]
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
              <div className="tutor-apply-summary">
                <small>Ngân sách học viên</small>
                <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
                <small>Thời gian mong muốn</small>
                <strong>{tutorScheduleLabel(request.preferredSchedule, 'Lịch linh hoạt')}</strong>
              </div>
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

function formatPrice(min: number | null, max: number | null) {
  const format = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
  if (min === null && max === null) return 'Có thể thương lượng';
  return min !== null && max !== null ? `${format(min)}–${format(max)} VNĐ` : `${format(min ?? max ?? 0)} VNĐ`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
