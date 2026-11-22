import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { ApiError } from '../../services/httpClient';
import { useResource } from '../../hooks/useResource';
import {
  cancelStudentApplication,
  getStudentApplication,
  getTeachingRequest,
  updateStudentApplication
} from '../../services/studentApi';
import {
  StudentApplicationForm,
  type StudentApplicationFormValues
} from '../../components/student/StudentApplicationForm';
import {
  applicationStatusLabel,
  formatDate,
  learningModeLabel
} from '../../components/student/studentApplicationUtils';
import { studyingRequestStatusText } from '../../components/student/studyingRequestUtils';
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
        ← Quay lại danh sách ứng tuyển của tôi
      </button>
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="student-application-detail-cards">
        <section className="student-panel student-application-detail-card">
          <h3>{request.title || `Tìm học viên cho môn ${subject}`}</h3>
          <dl>
            <div>
              <dt>Gia sư đăng yêu cầu</dt>
              <dd>
                <ProfileNameLink role="tutor" profileId={request.tutorId}>
                  {tutorName}
                </ProfileNameLink>
              </dd>
            </div>
            <div>
              <dt>Trạng thái</dt>
              <dd>
                <span className={`student-status ${(request.status ?? 'OPEN').toLowerCase()}`}>
                  {studyingRequestStatusText(request.status ?? 'OPEN')}
                </span>
              </dd>
            </div>
            <div>
              <dt>Mô tả</dt>
              <dd>{request.description || 'Gia sư chưa bổ sung mô tả.'}</dd>
            </div>
            <div>
              <dt>Môn học</dt>
              <dd>{subject}</dd>
            </div>
            <div>
              <dt>Khối lớp</dt>
              <dd>{grades}</dd>
            </div>
            <div>
              <dt>Hình thức học</dt>
              <dd>{learningModeLabel(request.teachingMode)}</dd>
            </div>
            <div>
              <dt>Số lượng học viên</dt>
              <dd>{request.quantity ?? 'Chưa cung cấp'}</dd>
            </div>
            <div>
              <dt>Học phí dự kiến</dt>
              <dd>{formatPrice(request.expectedPrice)}</dd>
            </div>
            <div>
              <dt>Khu vực</dt>
              <dd>{request.districts.map((district) => district.name).join(', ') || 'Chưa cung cấp'}</dd>
            </div>
            <div>
              <dt>Địa chỉ chi tiết</dt>
              <dd>{request.detailAddress || 'Chưa cung cấp'}</dd>
            </div>
            {request.availabilities.length > 0 && (
              <div>
                <dt>Thời gian có thể học</dt>
                <dd className="student-application-availability">
                  {request.availabilities.map((slot, index) => (
                    <span key={`${slot.dayOfWeek}-${slot.startTime}-${index}`}>
                      {weekdays[slot.dayOfWeek % 7]} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                    </span>
                  ))}
                </dd>
              </div>
            )}
            {request.note?.trim() && (
              <div>
                <dt>Ghi chú</dt>
                <dd>{request.note}</dd>
              </div>
            )}
            <div>
              <dt>Thời gian thanh toán</dt>
              <dd>{request.paidAt ? formatDate(request.paidAt) : 'Chưa thanh toán'}</dd>
            </div>
            <div>
              <dt>Thời gian tạo</dt>
              <dd>{formatDate(request.createdAt)}</dd>
            </div>
            <div>
              <dt>Cập nhật lần cuối</dt>
              <dd>{formatDate(request.updatedAt)}</dd>
            </div>
          </dl>
        </section>

        <section
          className={`student-panel student-application-detail-card${application.status === 'PENDING' ? ' has-actions' : ''}`}
        >
          <h2>Ứng tuyển của tôi</h2>
          <dl>
            <div>
              <dt>Trạng thái</dt>
              <dd>
                <span className={`student-status ${application.status.toLowerCase()}`}>
                  {applicationStatusLabel(application.status)}
                </span>
              </dd>
            </div>
            <div>
              <dt>Học phí đề xuất</dt>
              <dd>{formatPrice(application.proposedPrice)}</dd>
            </div>
            <div>
              <dt>Khối lớp</dt>
              <dd>{application.gradeName || 'Chưa xác định'}</dd>
            </div>
            <div>
              <dt>Hình thức học</dt>
              <dd>{learningModeLabel(application.learningMode)}</dd>
            </div>
            <div>
              <dt>Thời gian có thể học</dt>
              <dd className="student-application-availability">
                {applicationScheduleEntries(application.preferredSchedule).map((entry, index) => (
                  <span key={`${entry}-${index}`}>{entry}</span>
                ))}
              </dd>
            </div>
            {application.learningMode === 'OFFLINE' && (
              <div>
                <dt>Khu vực học</dt>
                <dd>
                  {[application.detailAddress, application.preferredDistrictName, application.preferredProvinceName]
                    .filter(Boolean)
                    .join(', ') || 'Chưa cung cấp'}
                </dd>
              </div>
            )}
            <div>
              <dt>Bước tiếp theo</dt>
              <dd>{nextStepLabel(application.nextStep)}</dd>
            </div>
            {application.message?.trim() && (
              <div>
                <dt>Lời nhắn</dt>
                <dd>{application.message}</dd>
              </div>
            )}
            <div>
              <dt>Thời gian gửi</dt>
              <dd>{formatDate(application.createdAt)}</dd>
            </div>
            <div>
              <dt>Cập nhật lần cuối</dt>
              <dd>{formatDate(application.updatedAt)}</dd>
            </div>
          </dl>
          {application.status === 'PENDING' && (
            <div className="student-application-detail-card-actions">
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
            </div>
          )}
        </section>
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

function applicationScheduleEntries(value: string | null) {
  if (!value?.trim()) return ['Lịch linh hoạt'];
  return value
    .split(';')
    .map((entry) => tutorScheduleLabel(entry.trim()))
    .filter(Boolean);
}
