import { useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import { useStudyingRequest, useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { recordEvent } from '../../services/recommendationApi';
import { submitTutorStudyingRequestProposal } from '../../services/studyingRequestApi';
import { updateTutorProposal } from '../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';
import { dateLabel, modeLabel, moneyLabel, statusLabel, weekdayLabel } from '../../utils/labels';

export function TutorStudyingRequestPage() {
  const navigate = useNavigate();
  const { requestId = '' } = useParams();
  // Opened from the recommendations: the search to report the proposal to.
  const recommendationLogId = (useLocation().state as { recommendationLogId?: string } | null)?.recommendationLogId;
  const { data: request, isLoading: loading, error: requestError } = useStudyingRequest(requestId);
  const relatedQuery = useStudyingRequests({ page: 0, size: 30 });
  const [proposalRevision, setProposalRevision] = useState(0);
  const proposalsQuery = useTutorProposals(undefined, proposalRevision);
  const myProposal = proposalsQuery.data?.find((proposal) => proposal.studyingRequestId === requestId);
  const [showApply, setShowApply] = useState(false);
  const [editingProposal, setEditingProposal] = useState(false);
  const [sending, setSending] = useState(false);
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
        setNotice('Đã cập nhật đề nghị.');
      } else {
        await submitTutorStudyingRequestProposal(request.id, payload);
        if (recommendationLogId) {
          recordEvent(recommendationLogId, request.id, 'CONTACTED').catch(() => undefined);
        }
        setNotice(myProposal?.status === 'CANCELLED' ? 'Đã gửi lại đề nghị.' : 'Đã gửi đề nghị tới học viên.');
      }
      setShowApply(false);
      setEditingProposal(false);
      setProposalRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.status === 409
          ? 'Bạn đã có một đề nghị đang chờ cho tin này.'
          : caught instanceof Error
            ? caught.message
            : 'Chưa gửi được đề nghị.'
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

  if (loading) return <div className="tutor-empty">Đang tải tin…</div>;
  if (!request)
    return (
      <>
        <button
          className="tutor-button secondary tutor-navigation-button"
          type="button"
          onClick={() => navigate('/tutor/dashboard#marketplace')}
        >
          ← Quay lại danh sách tin
        </button>
        <div className="tutor-error" role="alert">
          {requestError || 'Tin này không còn nữa.'}
        </div>
      </>
    );

  return (
    <>
      <button
        className="tutor-button secondary tutor-navigation-button tutor-back-link"
        type="button"
        onClick={() => navigate('/tutor/dashboard#marketplace')}
      >
        ← Quay lại danh sách tin
      </button>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}{' '}
          <button
            className="tutor-button secondary tutor-navigation-compact"
            type="button"
            onClick={() => navigate('/tutor/proposals')}
          >
            Xem đề nghị của tôi →
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
            <span className="tutor-subject-tag">{request.subjectName || 'Gia sư'}</span>
            <span>{modeLabel(request.learningMode)} · Đang mở</span>
          </div>
          <h1 className="tutor-detail-heading">{request.title || `Tìm gia sư ${request.subjectName}`}</h1>
          <p className="tutor-detail-subtitle">{request.gradeName}</p>
          <p className="tutor-request-author">
            <span>
              Người đăng{' '}
              <strong>
                {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
              </strong>
            </span>
            <span>Ngày đăng {dateLabel(request.createdAt)}</span>
          </p>
          <div className="tutor-detail-facts">
            <span>
              <small>Học phí</small>
              <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
            </span>
            <span>
              <small>Hình thức học</small>
              <strong>{modeLabel(request.learningMode)}</strong>
            </span>
            {request.learningMode === 'OFFLINE' && (
              <span>
                <small className="tutor-detail-fact-tag">Địa chỉ dạy</small>
                <strong>
                  {[request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                    'Chưa ghi'}
                </strong>
              </span>
            )}
            <span>
              <small>Lịch học mong muốn</small>
              <strong>{request.preferredSchedule || 'Linh hoạt'}</strong>
            </span>
            <span>
              <small>Ngày đăng</small>
              <strong>{dateLabel(request.createdAt)}</strong>
            </span>
          </div>
          {request.note?.trim() && (
            <div className="tutor-application-note">
              <span className="tutor-application-note-tag">Ghi chú</span>
              <p>{request.note}</p>
            </div>
          )}
          <section className="tutor-detail-section">
            <span className="tutor-eyebrow">MỤC TIÊU HỌC</span>
            <h2 className="tutor-detail-title">Học viên cần gì</h2>
            {request.description?.trim() && (
              <>
                <h3 className="tutor-small-heading">Mô tả</h3>
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
              <p className="tutor-detail-copy">Học viên chưa thêm mô tả.</p>
            )}
          </section>
          {request.availabilities.length > 0 && (
            <section className="tutor-detail-section">
              <h2 className="tutor-detail-title">Thời gian rảnh của học viên</h2>
              <div className="tutor-availability-list">
                {request.availabilities.map((slot, index) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                    {weekdayLabel(slot.dayOfWeek)} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                  </span>
                ))}
              </div>
            </section>
          )}
          <section className="tutor-recommendations">
            <div className="tutor-section-heading">
              <div>
                <span className="tutor-eyebrow">XEM THÊM</span>
                <h2>Tin tương tự</h2>
              </div>
              <button
                className="tutor-button secondary tutor-navigation-compact"
                type="button"
                onClick={() => navigate('/tutor/dashboard#marketplace')}
              >
                Xem tất cả →
              </button>
            </div>
            {recommendations.length ? (
              <div className="tutor-recommendation-list">
                {recommendations.map((item) => (
                  <button
                    className="tutor-recommendation-card"
                    type="button"
                    onClick={() => navigate(`/tutor/studying-requests/${item.id}`)}
                    key={item.id}
                  >
                    <span className="tutor-subject-tag">{item.subjectName || 'Gia sư'}</span>
                    <strong>{item.title || `Tìm gia sư ${item.subjectName}`}</strong>
                    <small>
                      {item.gradeName} · {item.districtName || 'Không giới hạn khu vực'}
                    </small>
                  </button>
                ))}
              </div>
            ) : (
              <p className="tutor-muted">Chưa có tin tương tự.</p>
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
                  <span className="tutor-eyebrow">ĐỀ NGHỊ CỦA TÔI</span>
                  <h2>Đang tải đề nghị…</h2>
                </div>
              </div>
            </>
          ) : myProposal ? (
            <>
              <div className="tutor-application-panel-heading">
                <span className="tutor-proposal-application-icon" aria-hidden="true">
                  ↗
                </span>
                <div>
                  <span className="tutor-eyebrow">ĐỀ NGHỊ CỦA TÔI</span>
                  <h2>Đề nghị của bạn</h2>
                </div>
                <span className={`tutor-cert-status status-${myProposal.status.toLowerCase()}`}>
                  {statusLabel(myProposal.status, 'application')}
                </span>
              </div>
              <div className="tutor-apply-summary">
                <small>Học phí đề nghị</small>
                <strong className="tutor-proposed-price-value">{moneyLabel(myProposal.proposedPrice)}</strong>
                <small>Thời gian có thể dạy</small>
                <strong className="tutor-application-time">{myProposal.preferredSchedule || 'Linh hoạt'}</strong>
                <small>Hình thức dạy</small>
                <strong>{modeLabel(myProposal.teachingMode)}</strong>
                {myProposal.teachingMode === 'OFFLINE' &&
                  (myProposal.preferredProvinceName || myProposal.preferredDistrictName) && (
                    <>
                      <small className="tutor-application-address-label">Địa chỉ dạy</small>
                      <strong className="tutor-application-address-value">
                        {[myProposal.preferredDistrictName, myProposal.preferredProvinceName]
                          .filter(Boolean)
                          .join(', ')}
                      </strong>
                    </>
                  )}
                {myProposal.message?.trim() && (
                  <>
                    <small>Ghi chú</small>
                    <strong>{myProposal.message}</strong>
                  </>
                )}
              </div>
              {myProposal.status === 'PENDING' ? (
                <div className="tutor-my-application-actions">
                  <button
                    className="tutor-button tutor-apply-button"
                    type="button"
                    onClick={() => openApplication(true)}
                  >
                    Sửa đề nghị <span>↗</span>
                  </button>
                  <button
                    className="tutor-button secondary tutor-navigation-compact"
                    type="button"
                    onClick={() => navigate('/tutor/proposals')}
                  >
                    Xem tất cả đề nghị →
                  </button>
                </div>
              ) : myProposal.status === 'CANCELLED' ? (
                <button
                  className="tutor-button tutor-apply-button"
                  type="button"
                  onClick={() => openApplication(false)}
                >
                  Gửi lại đề nghị <span>→</span>
                </button>
              ) : (
                <button
                  className="tutor-button tutor-apply-button"
                  type="button"
                  onClick={() => navigate('/tutor/proposals')}
                >
                  Xem đề nghị của tôi <span>→</span>
                </button>
              )}
            </>
          ) : (
            <>
              <span className="tutor-eyebrow">BẠN MUỐN DẠY HỌC VIÊN NÀY?</span>
              <h2>Gửi đề nghị dạy</h2>
              <div className="tutor-apply-summary">
                <small>Học phí học viên đưa ra</small>
                <strong>{formatPrice(request.minPrice, request.maxPrice)}</strong>
                <small>Lịch học</small>
                <strong>{request.preferredSchedule || 'Linh hoạt'}</strong>
              </div>
              <button className="tutor-button tutor-apply-button" type="button" onClick={() => openApplication(false)}>
                Gửi đề nghị <span>→</span>
              </button>
              <button
                className="tutor-button secondary tutor-save-button"
                type="button"
                onClick={() => navigate('/tutor/proposals')}
              >
                Đề nghị của tôi
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
  if (min === null && max === null) return 'Thoả thuận';
  return min !== null && max !== null ? `${format(min)}–${format(max)} đ` : `${format(min ?? max ?? 0)} đ`;
}
