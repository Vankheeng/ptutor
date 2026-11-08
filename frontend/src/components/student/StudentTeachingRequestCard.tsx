import { useState, type MouseEvent } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  cancelStudentApplication,
  createStudentApplication,
  updateStudentApplication,
  type StudentApplication
} from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { tutorScheduleLabel } from '../../utils/tutorDisplay';
import { StudentApplicationForm, type StudentApplicationFormValues } from './StudentApplicationForm';
import { applicationStatusLabel, formatDate, learningModeLabel } from './studentApplicationUtils';

interface Props {
  request: TeachingRequest;
  application?: StudentApplication;
  onChanged: () => void;
}

export function StudentTeachingRequestCard({ request, application, onChanged }: Props) {
  const navigate = useNavigate();
  const [showApply, setShowApply] = useState(false);
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const subject = request.subjectName || request.customSubjectName || 'Môn học tùy chỉnh';
  const grades = request.grades.map((grade) => grade.name).join(', ') || 'Chưa xác định';
  const schedule = request.preferredSchedule || formatAvailabilities(request) || 'Lịch linh hoạt';
  const location = [request.detailAddress, ...request.districts.map((district) => district.name)]
    .filter(Boolean)
    .join(', ');

  const submitApplication = async (values: StudentApplicationFormValues) => {
    setSaving(true);
    setError('');
    try {
      if (application?.status === 'PENDING') {
        await updateStudentApplication(application.id, values);
        setNotice('Ứng tuyển đã được cập nhật.');
      } else {
        await createStudentApplication(request.id, values);
        setNotice('Ứng tuyển đã được gửi đến gia sư.');
      }
      setShowApply(false);
      onChanged();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể gửi ứng tuyển.');
    } finally {
      setSaving(false);
    }
  };

  const cancelApplication = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (!application) return;
    setCancelling(true);
    setError('');
    try {
      await cancelStudentApplication(application.id);
      setNotice('Ứng tuyển đã được hủy.');
      onChanged();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể hủy ứng tuyển.');
    } finally {
      setCancelling(false);
    }
  };

  const openApplicationForm = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    setError('');
    setShowApply(true);
  };

  return (
    <article
      className="tutor-request-card tutor-student-search-card student-teaching-request-card"
      role="link"
      tabIndex={0}
      onClick={() => navigate(`/student/teaching-requests/${request.id}`)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          navigate(`/student/teaching-requests/${request.id}`);
        }
      }}
    >
      <div className="tutor-card-top">
        <span className="tutor-subject-tag">{subject}</span>
        <span>{formatDate(request.createdAt)}</span>
      </div>
      <h3>{request.title || `Tìm gia sư môn ${subject}`}</h3>
      <p className="tutor-request-meta">
        Gia sư: {request.tutorName || 'Chưa cập nhật'} · Khối {grades}
        {request.districts.length ? ` · ${request.districts.map((district) => district.name).join(', ')}` : ''}
      </p>
      <p className="tutor-request-description">
        {request.description || request.note || 'Gia sư chưa bổ sung mô tả cho yêu cầu này.'}
      </p>
      <div className="tutor-request-details">
        <span>
          <small>Học phí dự kiến</small>
          <strong>{formatExpectedPrice(request.expectedPrice)}</strong>
        </span>
        <span>
          <small>Lịch mong muốn</small>
          <strong>{tutorScheduleLabel(schedule, 'Lịch linh hoạt')}</strong>
        </span>
        <span>
          <small>Hình thức</small>
          <strong>{learningModeLabel(request.teachingMode)}</strong>
        </span>
        <span>
          <small>Địa điểm</small>
          <strong>{request.teachingMode === 'ONLINE' ? 'Trực tuyến' : location || 'Chưa cung cấp'}</strong>
        </span>
      </div>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {error && !showApply && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      {!application && (
        <button
          className="tutor-button tutor-propose-button tutor-student-apply-button"
          type="button"
          onClick={openApplicationForm}
          onKeyDown={(event) => event.stopPropagation()}
        >
          Ứng tuyển
        </button>
      )}
      {application?.status === 'PENDING' && (
        <div className="tutor-student-card-actions">
          <button
            className="tutor-button tutor-student-apply-button"
            type="button"
            onClick={openApplicationForm}
            onKeyDown={(event) => event.stopPropagation()}
          >
            Sửa ứng tuyển
          </button>
          <button
            className="tutor-button secondary tutor-student-cancel-button"
            type="button"
            disabled={cancelling}
            onClick={(event) => void cancelApplication(event)}
            onKeyDown={(event) => event.stopPropagation()}
          >
            {cancelling ? 'Đang hủy…' : 'Hủy ứng tuyển'}
          </button>
        </div>
      )}
      {application && application.status !== 'PENDING' && (
        <button
          className="tutor-button tutor-propose-button tutor-student-apply-button"
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            navigate(`/student/applications/${application.id}`);
          }}
          onKeyDown={(event) => event.stopPropagation()}
        >
          {applicationStatusLabel(application.status)} · Xem đơn
        </button>
      )}
      {showApply &&
        createPortal(
          <StudentApplicationForm
            application={application?.status === 'PENDING' ? application : undefined}
            initialValues={
              application
                ? undefined
                : {
                    gradeId: request.grades.length === 1 ? request.grades[0].id : '',
                    proposedPrice: request.expectedPrice ?? undefined,
                    learningMode: request.teachingMode,
                    preferredSchedule: request.preferredSchedule ?? formatAvailabilities(request) ?? '',
                    message: ''
                  }
            }
            grades={request.grades}
            saving={saving}
            error={error}
            title={application ? 'Chỉnh sửa chi tiết ứng tuyển' : 'Ứng tuyển với gia sư'}
            submitLabel={application ? 'Lưu thay đổi' : 'Gửi ứng tuyển'}
            onClose={() => {
              setShowApply(false);
              setError('');
            }}
            onSubmit={(values) => void submitApplication(values)}
          />,
          document.body
        )}
    </article>
  );
}

function formatExpectedPrice(value: number | null) {
  return value === null ? 'Thỏa thuận' : `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
}

function formatAvailabilities(request: TeachingRequest) {
  const weekdays = ['', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'];
  return request.availabilities
    .map(
      (slot) =>
        `${weekdays[slot.dayOfWeek] || `Thứ ${slot.dayOfWeek}`}, ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
    )
    .join('; ');
}
