import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import {
  cancelStudentApplication,
  createStudentApplication,
  getStudentApplications,
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

const weekdays = ['', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'];

export function StudentTeachingRequestDetailPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [showApply, setShowApply] = useState(false);
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(
    async (signal: AbortSignal) => {
      const request = await getTeachingRequest(requestId, signal);
      const applications = await getStudentApplications(undefined, 0, 100, signal);
      return {
        request,
        application: applications.content.find((item) => item.teachingRequestId === request.id)
      };
    },
    [requestId]
  );
  const query = useResource(load, revision);
  const request = query.data?.request;
  const application = query.data?.application;

  const submitApplication = async (values: StudentApplicationFormValues) => {
    setSaving(true);
    setError('');
    setNotice('');
    try {
      if (application?.status === 'PENDING') {
        await updateStudentApplication(application.id, values);
        setNotice('Ứng tuyển đã được cập nhật.');
      } else {
        await createStudentApplication(requestId, values);
        setNotice('Ứng tuyển đã được gửi đến gia sư.');
      }
      setShowApply(false);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể gửi ứng tuyển.');
    } finally {
      setSaving(false);
    }
  };

  const cancelApplication = async () => {
    if (!application) return;
    setCancelling(true);
    setError('');
    setNotice('');
    try {
      await cancelStudentApplication(application.id);
      setNotice('Ứng tuyển đã được hủy.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể hủy ứng tuyển.');
    } finally {
      setCancelling(false);
    }
  };

  if (query.error || !request) {
    return (
      <div className="tutor-empty">
        <h3>{query.error ? 'Không thể tải yêu cầu dạy' : 'Đang tải yêu cầu dạy…'}</h3>
        {query.error && <p>{query.error}</p>}
      </div>
    );
  }

  const subject = request.subjectName || request.customSubjectName || 'Môn học tùy chỉnh';
  const grades = request.grades.map((grade) => grade.name).join(', ') || 'Chưa xác định';
  const schedule = request.preferredSchedule || formatAvailabilities(request.availabilities) || 'Lịch linh hoạt';
  const location = [request.detailAddress, ...request.districts.map((district) => district.name)]
    .filter(Boolean)
    .join(', ');

  return (
    <>
      <button
        className="tutor-button secondary tutor-navigation-button tutor-back-link"
        type="button"
        onClick={() => navigate('/student/teaching-requests')}
      >
        ← Quay lại tìm kiếm gia sư
      </button>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}

      <div className="tutor-request-detail-layout tutor-market-detail">
        <article className="tutor-panel tutor-detail-main">
          <div className="tutor-card-top">
            <span className="tutor-subject-tag">{subject}</span>
            <span>{request.status === 'OPEN' ? 'Đang mở' : 'Không còn nhận ứng tuyển'}</span>
          </div>
          <h1 className="tutor-detail-heading">{request.title || `Tìm gia sư môn ${subject}`}</h1>
          <p className="tutor-detail-subtitle">Khối {grades}</p>
          <p className="tutor-request-author">
            <span>
              Đăng bởi <strong>{request.tutorName || 'Gia sư'}</strong>
            </span>
            <span>Đăng ngày {formatDate(request.createdAt)}</span>
          </p>
          <div className="tutor-detail-facts">
            <span>
              <small>Học phí dự kiến</small>
              <strong>{formatExpectedPrice(request.expectedPrice)}</strong>
            </span>
            <span>
              <small>Khối lớp</small>
              <strong>{grades}</strong>
            </span>
            <span>
              <small>Hình thức học</small>
              <strong>{learningModeLabel(request.teachingMode)}</strong>
            </span>
            <span>
              <small>Lịch học mong muốn</small>
              <strong>{tutorScheduleLabel(schedule, 'Lịch linh hoạt')}</strong>
            </span>
            <span>
              <small>Địa điểm</small>
              <strong>{request.teachingMode === 'ONLINE' ? 'Trực tuyến' : location || 'Chưa cung cấp'}</strong>
            </span>
            <span>
              <small>Cập nhật lần cuối</small>
              <strong>{formatDate(request.updatedAt)}</strong>
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
            <p className="tutor-detail-copy">{request.description || 'Gia sư chưa bổ sung thêm thông tin.'}</p>
          </section>
          {request.availabilities.length > 0 && (
            <section className="tutor-detail-section">
              <h2 className="tutor-detail-title">Thời gian gia sư có thể dạy</h2>
              <div className="tutor-availability-list">
                {request.availabilities.map((slot, index) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                    {weekdays[slot.dayOfWeek] || `Thứ ${slot.dayOfWeek}`} · {slot.startTime.slice(0, 5)}–
                    {slot.endTime.slice(0, 5)}
                  </span>
                ))}
              </div>
            </section>
          )}
        </article>

        <aside className="tutor-panel tutor-apply-sidebar">
          {application ? (
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
                      <button
                        className="tutor-button tutor-apply-button"
                        type="button"
                        onClick={() => setShowApply(true)}
                      >
                        Sửa ứng tuyển <span>↗</span>
                      </button>
                      <button
                        className="tutor-button secondary tutor-cancel-proposal-button"
                        type="button"
                        disabled={cancelling}
                        onClick={() => void cancelApplication()}
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
                    Xem tất cả ứng tuyển →
                  </button>
                </>
              }
            />
          ) : (
            <>
              <div className="tutor-application-panel-heading">
                <span className="tutor-proposal-application-icon" aria-hidden="true">
                  ↗
                </span>
                <div>
                  <h2>Ứng tuyển với gia sư</h2>
                  <p>Gửi thông tin để gia sư xem xét yêu cầu học của bạn.</p>
                </div>
              </div>
              <button className="tutor-button tutor-apply-button" type="button" onClick={() => setShowApply(true)}>
                Ứng tuyển ngay <span>↗</span>
              </button>
            </>
          )}
        </aside>
      </div>

      {showApply && (
        <StudentApplicationForm
          application={application?.status === 'PENDING' ? application : undefined}
          initialValues={
            application
              ? undefined
              : {
                  gradeId: request.grades.length === 1 ? request.grades[0].id : '',
                  proposedPrice: request.expectedPrice ?? undefined,
                  learningMode: request.teachingMode,
                  preferredSchedule: request.preferredSchedule ?? formatAvailabilities(request.availabilities) ?? '',
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
        />
      )}
    </>
  );
}

function formatExpectedPrice(value: number | null) {
  return value === null ? 'Thỏa thuận' : `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
}

function formatAvailabilities(availabilities: TeachingRequestAvailability[]) {
  return availabilities
    .map(
      (slot) =>
        `${weekdays[slot.dayOfWeek] || `Thứ ${slot.dayOfWeek}`}, ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
    )
    .join('; ');
}

interface TeachingRequestAvailability {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

function nextStepLabel(value: string | null) {
  return value === 'CONTRACT_SIGNING' ? 'Chờ ký hợp đồng' : value || 'Đang chờ xử lý';
}
