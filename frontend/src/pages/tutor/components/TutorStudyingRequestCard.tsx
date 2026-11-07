import { useState, type MouseEvent } from 'react';
import { createPortal } from 'react-dom';
import { Link, useNavigate } from 'react-router-dom';
import { tutorScheduleLabel } from '../../../utils/tutorDisplay';
import { submitTutorStudyingRequestProposal, type StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import { cancelTutorProposal, updateTutorProposal, type TutorTeachingProposal } from '../../../services/tutorApi';
import { TutorApplicationForm, type TutorApplicationValues } from './TutorApplicationForm';

interface Props {
  request: StudyingRequestSearchResponse;
  actionLabel: string;
  applyInPlace?: boolean;
  proposal?: TutorTeachingProposal;
}

export function TutorStudyingRequestCard({ request, actionLabel, applyInPlace = false, proposal }: Props) {
  const navigate = useNavigate();
  const detailPath = `/tutor/studying-requests/${request.id}`;
  const [showApply, setShowApply] = useState(false);
  const [sending, setSending] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const submitApplication = async (values: TutorApplicationValues) => {
    setSending(true);
    setError('');
    try {
      const payload = { ...values, gradeId: request.gradeId };
      if (proposal?.status === 'PENDING') {
        await updateTutorProposal(proposal.id, payload);
        setNotice('Đề xuất của bạn đã được cập nhật.');
      } else {
        await submitTutorStudyingRequestProposal(request.id, payload);
        setNotice('Đề xuất đã được gửi đến học viên.');
      }
      setShowApply(false);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể gửi đề xuất.');
    } finally {
      setSending(false);
    }
  };

  const cancelApplication = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (!proposal) return;
    setCancelling(true);
    setError('');
    try {
      await cancelTutorProposal(proposal.id);
      setCancelled(true);
      setNotice('Đề xuất đã được hủy.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy đề xuất.');
    } finally {
      setCancelling(false);
    }
  };

  return (
    <article
      className="tutor-request-card tutor-student-search-card"
      role="link"
      tabIndex={0}
      onClick={() => navigate(detailPath)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          navigate(detailPath);
        }
      }}
    >
      <div className="tutor-card-top">
        <span className="tutor-subject-tag">{request.subjectName || 'Môn học'}</span>
        <span>{request.createdAt ? new Date(request.createdAt).toLocaleDateString('vi-VN') : ''}</span>
      </div>
      <h3>{request.title || `Tìm gia sư môn ${request.subjectName ?? ''}`}</h3>
      <p className="tutor-request-meta">
        {[request.studentFirstName, request.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
        {' · '}Lớp {request.gradeName}
        {request.districtName ? ` · ${request.districtName}` : ''}
      </p>
      <p className="tutor-request-description">
        {request.description || request.learningGoals || 'Học viên chưa bổ sung mô tả.'}
      </p>
      <div className="tutor-request-details">
        <span>
          <small>Ngân sách</small>
          <strong>{formatBudget(request.minPrice, request.maxPrice)}</strong>
        </span>
        <span>
          <small>Lịch mong muốn</small>
          <strong>{tutorScheduleLabel(request.preferredSchedule)}</strong>
        </span>
        <span>
          <small>Phương thức</small>
          <strong>{request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</strong>
        </span>
        <span>
          <small>Địa chỉ</small>
          <strong>
            {request.learningMode === 'ONLINE'
              ? 'Học trực tuyến'
              : [request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                'Học viên chưa cung cấp địa chỉ'}
          </strong>
        </span>
      </div>
      {notice && <div className="tutor-notice" role="status">{notice}</div>}
      {error && !showApply && <div className="tutor-error" role="alert">{error}</div>}
      {applyInPlace ? (
        proposal?.status === 'PENDING' && !cancelled ? (
          <div className="tutor-student-card-actions">
            <button
              className="tutor-button tutor-student-apply-button"
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                setError('');
                setShowApply(true);
              }}
              onKeyDown={(event) => event.stopPropagation()}
            >
              Sửa đề xuất
            </button>
            <button
              className="tutor-button secondary tutor-student-cancel-button"
              type="button"
              disabled={cancelling}
              onClick={(event) => void cancelApplication(event)}
              onKeyDown={(event) => event.stopPropagation()}
            >
              {cancelling ? 'Đang hủy…' : 'Hủy đề xuất'}
            </button>
          </div>
        ) : (
          <button
            className="tutor-button tutor-propose-button tutor-student-apply-button"
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setError('');
              setShowApply(true);
            }}
            onKeyDown={(event) => event.stopPropagation()}
          >
            {cancelled || proposal?.status === 'CANCELLED' ? 'Gửi lại đề xuất' : actionLabel}
          </button>
        )
      ) : (
        <Link
          className="tutor-button tutor-propose-button tutor-student-apply-button"
          to={detailPath}
          onClick={(event) => event.stopPropagation()}
          onKeyDown={(event) => event.stopPropagation()}
        >
          {actionLabel}
        </Link>
      )}
      {showApply && (
        <div onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>
          {createPortal(
            <TutorApplicationForm
              subjectName={request.subjectName}
              requestMode={request.learningMode}
              proposal={proposal?.status === 'PENDING' ? proposal : undefined}
              editing={proposal?.status === 'PENDING'}
              sending={sending}
              error={error}
              onClose={() => setShowApply(false)}
              onSubmit={(values) => void submitApplication(values)}
            />,
            document.body
          )}
        </div>
      )}
    </article>
  );
}

function formatBudget(min: number | null, max: number | null) {
  const format = (amount: number) => `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`;
  if (min === null && max === null) return 'Có thể thương lượng';
  if (min !== null && max !== null) return `${format(min)} – ${format(max)}`;
  return format(min ?? max ?? 0);
}
