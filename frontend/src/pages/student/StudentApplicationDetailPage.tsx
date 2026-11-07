import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import { useResource } from '../../hooks/useResource';
import {
  cancelStudentApplication,
  getStudentApplication,
  getTeachingRequest,
  updateStudentApplication
} from '../../services/studentApi';
import { ApplicationDetailPanel } from '../../components/applications/ApplicationDetailPanel';
import {
  StudentApplicationForm,
  type StudentApplicationFormValues
} from '../../components/student/StudentApplicationForm';
import {
  applicationStatusLabel,
  formatDate,
  learningModeLabel
} from '../../components/student/studentApplicationUtils';
import { tutorScheduleLabel } from '../../utils/tutorDisplay';
import '../tutor/tutor.css';

const weekdays = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

export function StudentApplicationDetailPage() {
  const { applicationId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [editError, setEditError] = useState('');

  const load = useCallback(
    async (signal: AbortSignal) => {
      const application = await getStudentApplication(applicationId, signal);
      const request = await getTeachingRequest(application.teachingRequestId, signal);
      return { application, request };
    },
    [applicationId]
  );
  const query = useResource(load, revision);
  const application = query.data?.application;
  const request = query.data?.request;

  const cancel = async () => {
    if (!application) return;
    setCancelling(true);
    setError('');
    try {
      await cancelStudentApplication(application.id);
      navigate('/student/applications');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể hủy ứng tuyển này.');
    } finally {
      setCancelling(false);
    }
  };

  const saveEdit = async (values: StudentApplicationFormValues) => {
    if (!application) return;
    setSavingEdit(true);
    setEditError('');
    try {
      await updateStudentApplication(application.id, values);
      setShowEdit(false);
      setRevision((value) => value + 1);
    } catch (caught) {
      setEditError(caught instanceof ApiError ? caught.message : 'Không thể cập nhật ứng tuyển này.');
    } finally {
      setSavingEdit(false);
    }
  };

  if (query.isLoading || !application || !request) {
    return (
      <div className="tutor-empty">
        <h3>{query.error ? 'Không thể tải chi tiết yêu cầu học' : 'Đang tải chi tiết yêu cầu học…'}</h3>
        {query.error && <p>{query.error}</p>}
      </div>
    );
  }

  const subject = request.subjectName || request.customSubjectName || 'Môn học tùy chỉnh';
  const grades = request.grades.map((grade) => grade.name).join(', ') || application.gradeName || 'Chưa xác định';
  const tutorName =
    request.tutorName || [application.tutorFirstName, application.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';

  return (
    <>
      <button
        className="tutor-button secondary tutor-navigation-button tutor-back-link"
        type="button"
        onClick={() => navigate('/student/applications')}
      >
        ← Quay lại ứng tuyển của tôi
      </button>
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="tutor-request-detail-layout tutor-market-detail">
        <article className="tutor-panel tutor-detail-main">
          <div className="tutor-card-top">
            <span className="tutor-subject-tag">{subject}</span>
            <span>{learningModeLabel(request.teachingMode)} · Đang mở</span>
          </div>
          <h1 className="tutor-detail-heading">{request.title || `Tìm học viên cho môn ${subject}`}</h1>
          <p className="tutor-detail-subtitle">{grades}</p>
          <p className="tutor-request-author">
            <span>
              Đăng bởi <strong>{tutorName}</strong>
            </span>
            <span>Đăng ngày {formatDate(request.createdAt)}</span>
          </p>
          <div className="tutor-detail-facts">
            <span>
              <small>Học phí dự kiến</small>
              <strong>{formatPrice(request.expectedPrice)}</strong>
            </span>
            <span>
              <small>Lớp học</small>
              <strong>{grades}</strong>
            </span>
            <span>
              <small>Hình thức học</small>
              <strong>{learningModeLabel(request.teachingMode)}</strong>
            </span>
            {request.teachingMode === 'OFFLINE' && (
              <span>
                <small className="tutor-detail-fact-tag">Địa chỉ học</small>
                <strong>
                  {[request.detailAddress, ...request.districts.map((district) => district.name)]
                    .filter(Boolean)
                    .join(', ') || 'Chưa cung cấp'}
                </strong>
              </span>
            )}
            <span>
              <small>Lịch học mong muốn</small>
              <strong>{tutorScheduleLabel(request.preferredSchedule, 'Lịch linh hoạt')}</strong>
            </span>
            <span>
              <small>Ngày đăng yêu cầu</small>
              <strong>{formatDate(request.createdAt)}</strong>
            </span>
          </div>
          {request.note?.trim() && (
            <div className="tutor-application-note">
              <span className="tutor-application-note-tag">Ghi chú</span>
              <p>{request.note}</p>
            </div>
          )}
          <section className="tutor-detail-section">
            <span className="tutor-eyebrow">THÔNG TIN YÊU CẦU</span>
            <h2 className="tutor-detail-title">Nhu cầu tìm gia sư</h2>
            {request.description?.trim() && (
              <>
                <h3 className="tutor-small-heading">Chi tiết yêu cầu</h3>
                <p className="tutor-detail-copy">{request.description}</p>
              </>
            )}
            {!request.description?.trim() && <p className="tutor-detail-copy">Gia sư chưa bổ sung thêm thông tin.</p>}
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
        </article>
        <aside className="tutor-panel tutor-apply-sidebar">
          <ApplicationDetailPanel
            heading="Ứng tuyển của tôi"
            statusClass={`status-${application.status.toLowerCase()}`}
            statusLabel={applicationStatusLabel(application.status)}
            priceLabel="Học phí đề xuất"
            price={application.proposedPrice}
            fields={[
              { label: 'Thời gian có thể học', value: application.preferredSchedule || 'Lịch linh hoạt' },
              { label: 'Hình thức học', value: learningModeLabel(application.learningMode) },
              { label: 'Bước tiếp theo', value: nextStepLabel(application.nextStep) },
              { label: 'Đã gửi lúc', value: formatDate(application.createdAt) },
              { label: 'Cập nhật lúc', value: formatDate(application.updatedAt) }
            ]}
            notes={application.message?.trim() ? [{ label: 'Lời nhắn', value: application.message }] : undefined}
            actions={
              <>
                {application.status === 'PENDING' && (
                  <>
                    <button className="tutor-button tutor-apply-button" type="button" onClick={() => setShowEdit(true)}>
                      Sửa đề xuất <span>↗</span>
                    </button>
                    <button
                      className="tutor-button secondary tutor-cancel-proposal-button"
                      type="button"
                      disabled={cancelling}
                      onClick={() => void cancel()}
                    >
                      {cancelling ? 'Đang hủy…' : 'Hủy ứng tuyển'}
                    </button>
                  </>
                )}
                <button
                  className="tutor-button secondary tutor-navigation-compact"
                  type="button"
                  onClick={() => navigate('/student/applications')}
                >
                  Xem tất cả yêu cầu →
                </button>
              </>
            }
          />
        </aside>
      </div>
      {showEdit && (
        <StudentApplicationForm
          application={application}
          grades={request.grades}
          saving={savingEdit}
          error={editError}
          onClose={() => {
            setShowEdit(false);
            setEditError('');
          }}
          onSubmit={(values) => void saveEdit(values)}
        />
      )}
    </>
  );
}

function formatPrice(value: number | null) {
  if (value === null) return 'Thỏa thuận';
  return `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
}

function nextStepLabel(value: string | null) {
  return value === 'CONTRACT_SIGNING' ? 'Chờ ký hợp đồng' : value || 'Đang chờ xử lý';
}
