import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { useAuth } from '../../auth/useAuth';
import { useResource } from '../../hooks/useResource';
import {
  cancelContract,
  getContract,
  rejectContract,
  refreshContract,
  renewContract,
  signContract,
  updateContract,
  type Contract,
  type ContractTerms
} from '../../services/studentApi';
import {
  createTutorLesson,
  getTutorContractLessons,
  requestTutorContractStop,
  setTutorLessonStatus,
  updateTutorLesson,
  type TutorLesson
} from '../../services/tutorApi';
import { Icon } from '../../components/ui/Icon';
import { TimeSelect } from '../../components/ui/TimeSelect';
import { ApiError } from '../../services/httpClient';
import { StudentContractRenewalForm } from '../../components/student/StudentContractRenewalForm';
import { formatLessonPrice } from '../../utils/contractPricing';
import { PendingContractSignView } from '../common-user/components/PendingContractSignView';

function lessonErrorMessage(caught: unknown, fallback: string) {
  if (caught instanceof ApiError && caught.code === 'LESSON_SCHEDULE_CONFLICT') {
    return caught.message;
  }
  return caught instanceof Error ? caught.message : fallback;
}
import { ContractPdfLink } from '../common-user/components/ContractPdfLink';
import { formatPreferredSchedule } from '../../utils/scheduleFormat';
import '../student/student.css';
import { TutorLessonCancellationDialog } from '../../components/tutor/TutorLessonCancellationDialog';

export function TutorContractDetailPage() {
  const { contractId = '' } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [lessonPage, setLessonPage] = useState(0);
  const [lessonsPerPage, setLessonsPerPage] = useState(5);
  const [showCreateLesson, setShowCreateLesson] = useState(true);
  const [busy, setBusy] = useState(false);
  const [editingLesson, setEditingLesson] = useState<TutorLesson | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [cancellationLesson, setCancellationLesson] = useState<TutorLesson | null>(null);
  const query = useResource(
    useCallback(
      async (signal: AbortSignal) => {
        const contract = await getContract(contractId);
        const lessonPage = await getTutorContractLessons(contractId, signal);
        return { contract, lessons: lessonPage.content };
      },
      [contractId]
    ),
    revision
  );
  const contract = query.data?.contract;
  const lessons = query.data?.lessons ?? [];
  const lessonPageCount = Math.max(1, Math.ceil(lessons.length / lessonsPerPage));
  const currentLessonPage = Math.min(lessonPage, lessonPageCount - 1);
  const displayedLessons = lessons.slice(currentLessonPage * lessonsPerPage, (currentLessonPage + 1) * lessonsPerPage);
  const refreshLessons = () => {
    setLessonPage(0);
    setRevision((value) => value + 1);
  };
  if (!contract)
    return query.error ? (
      <div className="tutor-error" role="alert">
        {query.error}
      </div>
    ) : (
      <div className="tutor-empty">
        <h3>Đang tải hợp đồng...</h3>
      </div>
    );

  const student = [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên';
  const createLesson = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const date = String(data.get('date'));
    const day = new Date(`${date}T12:00:00`).getDay() || 7;
    if (date < contract.startDate || date > contract.endDate) {
      setError('Ngày buổi học phải nằm trong thời gian hợp đồng.');
      return;
    }
    if (contract.schedules?.length && !contract.schedules.some((schedule) => schedule.dayOfWeek === day)) {
      setError('Ngày đã chọn không nằm trong lịch học của hợp đồng.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await createTutorLesson(contract.id, {
        title: String(data.get('title') || `Buổi học ${contract.subjectName}`),
        date,
        startTime: String(data.get('startTime')),
        endTime: String(data.get('endTime'))
      });
      event.currentTarget.reset();
      refreshLessons();
    } catch (caught) {
      setError(lessonErrorMessage(caught, 'Không thể tạo buổi học.'));
    } finally {
      setBusy(false);
    }
  };

  const editLesson = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editingLesson) return;
    const data = new FormData(event.currentTarget);
    const date = String(data.get('date'));
    const day = new Date(`${date}T12:00:00`).getDay() || 7;
    if (date < contract.startDate || date > contract.endDate) {
      setError('Ngày buổi học phải nằm trong thời gian hợp đồng.');
      return;
    }
    if (contract.schedules?.length && !contract.schedules.some((schedule) => schedule.dayOfWeek === day)) {
      setError('Ngày đã chọn không nằm trong lịch học của hợp đồng.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await updateTutorLesson(editingLesson.id, {
        title: String(data.get('title') || editingLesson.title),
        date,
        startTime: String(data.get('startTime')),
        endTime: String(data.get('endTime'))
      });
      setEditingLesson(null);
      refreshLessons();
    } catch (caught) {
      setError(lessonErrorMessage(caught, 'Không thể cập nhật buổi học.'));
    } finally {
      setBusy(false);
    }
  };

  const cancelLesson = async (lesson: TutorLesson) => {
    if (!window.confirm('Bạn có chắc muốn hủy buổi học này không?')) return;
    setBusy(true);
    setError('');
    try {
      await setTutorLessonStatus(lesson.id, 'CANCELLED');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy buổi học.');
    } finally {
      setBusy(false);
    }
  };

  const markLessonTaught = async (lesson: TutorLesson) => {
    setBusy(true);
    setError('');
    try {
      await setTutorLessonStatus(lesson.id, 'PENDING_CONFIRMATION');
      setNotice('Đã đánh dấu buổi học đã dạy, chờ học viên xác nhận.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể đánh dấu buổi học đã dạy.');
    } finally {
      setBusy(false);
    }
  };

  const proposeRenewal = async (terms: ContractTerms) => {
    setBusy(true);
    setError('');
    try {
      await renewContract(contract.id, terms);
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể tạo đề xuất gia hạn.');
    } finally {
      setBusy(false);
    }
  };

  const requestStop = async () => {
    if (!window.confirm('Bạn có chắc muốn gửi yêu cầu dừng hợp đồng đến học viên không?')) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await requestTutorContractStop(contract.id);
      setNotice('Đã gửi yêu cầu dừng hợp đồng đến học viên.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể gửi yêu cầu dừng hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const signPendingContract = async (sign: boolean) => {
    if (!contract) return;
    setBusy(true);
    setError('');
    try {
      await (sign ? signContract(contract.id) : rejectContract(contract.id));
      setNotice(sign ? 'Đã ký hợp đồng.' : 'Đã từ chối hợp đồng.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const savePendingContract = async (terms: Partial<ContractTerms>) => {
    setBusy(true);
    setError('');
    try {
      await updateContract(contract.id, terms);
      setNotice('Đã cập nhật hợp đồng.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const cancelPendingContract = async () => {
    setBusy(true);
    setError('');
    try {
      await cancelContract(contract.id);
      setNotice('Đã hủy hợp đồng.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const refreshPendingContract = async () => {
    setBusy(true);
    setError('');
    try {
      await refreshContract(contract.id);
      setNotice('Đã làm mới thời hạn ký hợp đồng.');
      refreshLessons();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể làm mới hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const completedLessons = lessons.filter(
    (lesson) => lesson.status === 'COMPLETED' || lesson.status === 'CONFIRMED'
  ).length;
  const waitingLessons = lessons.filter((lesson) => lesson.status === 'PENDING_CONFIRMATION').length;
  const scheduledLessons = lessons.filter((lesson) => lesson.status === 'SCHEDULED').length;
  const progress = contract.totalLessons
    ? Math.min(100, Math.round((completedLessons / contract.totalLessons) * 100))
    : 0;

  if (contract.status === 'PENDING' && user?.userId && contract.createdByUserId !== user.userId) {
    return (
      <PendingContractSignView
        mode="sign"
        viewerRole="tutor"
        contract={contract}
        backPath="/tutor/sections/contracts"
        busy={busy}
        error={error}
        notice={notice}
        onSign={() => signPendingContract(true)}
        onReject={() => void signPendingContract(false)}
      />
    );
  }
  if (contract.status === 'PENDING' && user?.userId && contract.createdByUserId === user.userId) {
    return (
      <PendingContractSignView
        mode="edit"
        viewerRole="tutor"
        contract={contract}
        backPath="/tutor/sections/contracts"
        busy={busy}
        error={error}
        notice={notice}
        onSave={savePendingContract}
        onCancel={() => void cancelPendingContract()}
        onRefresh={refreshPendingContract}
      />
    );
  }

  return (
    <>
      <div className="tutor-back-link">
        <button
          className="tutor-detail-link tutor-back-button"
          type="button"
          onClick={() => navigate('/tutor/sections/contracts')}
        >
          ← Quay lại danh sách hợp đồng
        </button>
      </div>
      <section className="tutor-contract-detail-hero">
        <div className="tutor-contract-hero-subject">
          <span className="tutor-contract-hero-icon" aria-hidden="true">
            <Icon name="contract" size={24} />
          </span>
          <div>
            <span className="tutor-eyebrow">HỢP ĐỒNG</span>
            <h1>{contract.subjectName}</h1>
            <p>{contract.gradeName}</p>
          </div>
        </div>
        <div className="tutor-contract-hero-tutor">
          <span className="tutor-contract-avatar" aria-hidden="true">
            {initials(student)}
          </span>
          <div>
            <strong>
              <ProfileNameLink role="student" profileId={contract.studentId}>
                {student}
              </ProfileNameLink>
            </strong>
            <small>Học viên</small>
          </div>
        </div>
        <span className={`tutor-contract-status status-${contract.status.toLowerCase()}`}>
          {statusLabel(contract.status)}
        </span>
      </section>
      {contract.status === 'ACTIVE' && (
        <div className="tutor-contract-related-actions">
          <Link className="tutor-button secondary" to={`/tutor/complaints/new?contractId=${contract.id}`}>
            Khiếu nại hợp đồng
          </Link>
        </div>
      )}
      <section className="tutor-panel tutor-contract-overview">
        <h2>Tổng quan hợp đồng</h2>
        <div className="tutor-contract-detail-grid">
          <Detail label="Môn học" value={contract.subjectName} />
          <Detail label="Lớp" value={contract.gradeName} />
          <Detail label="Hình thức dạy" value={contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
          <Detail label="Học viên" value={student} />
          <Detail label="Giá / buổi" value={formatLessonPrice(contract)} />
          <Detail label="Tổng số buổi" value={`${contract.totalLessons} buổi`} />
          <Detail
            label="Lịch học"
            value={
              contract.schedules?.length
                ? formatSchedules(contract.schedules)
                : formatPreferredSchedule(contract.preferredSchedule)
            }
          />
          <Detail label="Thời gian" value={`${formatDate(contract.startDate)} – ${formatDate(contract.endDate)}`} />
        </div>
      </section>
      <section className="tutor-contract-progress tutor-panel">
        <div className="tutor-contract-section-heading">
          <div>
            <h2>Tiến độ hợp đồng</h2>
          </div>
          <strong>
            {completedLessons}/{contract.totalLessons} buổi
          </strong>
        </div>
        <div className="tutor-progress-track">
          <span style={{ width: `${progress}%` }} />
        </div>
        <div className="tutor-progress-stats">
          <span>
            <small>Đã hoàn thành</small>
            <strong>{completedLessons} buổi</strong>
          </span>
          <span>
            <small>Chờ xác nhận</small>
            <strong>{waitingLessons} buổi</strong>
          </span>
          <span>
            <small>Đã lên lịch</small>
            <strong>{scheduledLessons} buổi</strong>
          </span>
        </div>
      </section>
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {contract.status !== 'CANCELLED' &&
        (contract.status === 'ACTIVE' ? (
          <section className="tutor-panel tutor-contract-create-panel">
            <details open={showCreateLesson} onToggle={(event) => setShowCreateLesson(event.currentTarget.open)}>
              <summary>Tạo buổi học</summary>
              <p className="tutor-muted">Chỉ chọn ngày trong thời gian hợp đồng và đúng lịch học đã thỏa thuận.</p>
              <form className="tutor-form-grid" onSubmit={(event) => void createLesson(event)}>
                <label>
                  Tiêu đề
                  <input name="title" placeholder={`Buổi học ${contract.subjectName}`} />
                </label>
                <label>
                  Ngày học
                  <input name="date" type="date" min={contract.startDate} max={contract.endDate} required />
                </label>
                <label>
                  Giờ bắt đầu
                  <TimeSelect name="startTime" required />
                </label>
                <label>
                  Giờ kết thúc
                  <TimeSelect name="endTime" required />
                </label>
                <div className="tutor-form-actions tutor-field-full">
                  <button className="tutor-button" disabled={busy}>
                    {busy ? 'Đang tạo...' : 'Tạo buổi học'}
                  </button>
                </div>
              </form>
            </details>
          </section>
        ) : (
          <section className="tutor-panel tutor-contract-create-panel tutor-contract-create-disabled">
            <h2>Tạo buổi học</h2>
            <p>Chỉ có thể tạo buổi học sau khi hợp đồng đã được hai bên ký và kích hoạt.</p>
          </section>
        ))}
      <section className="tutor-panel tutor-contract-lessons-panel">
        <div className="tutor-contract-section-heading">
          <div>
            <h2>Buổi học</h2>
          </div>
          <div className="tutor-lessons-page-size">
            <label>
              Hiển thị
              <select
                value={lessonsPerPage}
                onChange={(event) => {
                  setLessonsPerPage(Number(event.target.value));
                  setLessonPage(0);
                }}
              >
                <option value={5}>5 buổi/trang</option>
                <option value={10}>10 buổi/trang</option>
                <option value={20}>20 buổi/trang</option>
              </select>
            </label>
            <span className="tutor-count">{lessons.length} buổi</span>
          </div>
        </div>
        {lessons.length ? (
          <>
            <div className="tutor-contract-lesson-list">
              <div className="tutor-contract-lesson-list-header">
                <span>Buổi học</span>
                <span>Được thanh toán lúc</span>
                <span>Trạng thái</span>
                <span>Thao tác</span>
              </div>
              {displayedLessons.map((lesson) => (
                <LessonRow
                  lesson={lesson}
                  key={lesson.id}
                  onEdit={() => setEditingLesson(lesson)}
                  onCancel={() => {
                    if (!lesson.paidAt) {
                      void cancelLesson(lesson);
                      return;
                    }
                    setCancellationLesson(lesson);
                  }}
                  onMarkTaught={() => void markLessonTaught(lesson)}
                  editable={contract.status === 'ACTIVE'}
                />
              ))}
            </div>
            {lessonPageCount > 1 && (
              <div className="tutor-lesson-pagination">
                <button type="button" disabled={lessonPage === 0} onClick={() => setLessonPage((page) => page - 1)}>
                  &lsaquo;
                </button>
                <span>
                  Trang {lessonPage + 1}/{lessonPageCount}
                </span>
                <button
                  type="button"
                  disabled={lessonPage === lessonPageCount - 1}
                  onClick={() => setLessonPage((page) => page + 1)}
                >
                  &rsaquo;
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="tutor-empty compact">
            <h3>Chưa có buổi học</h3>
            <p>Các buổi học sẽ được tạo tự động theo lịch hợp đồng.</p>
          </div>
        )}
      </section>
      {contract.status === 'ACTIVE' && (
        <section className="tutor-panel tutor-contract-stop-request">
          <div>
            <h2>Yêu cầu dừng hợp đồng</h2>
            <p>Gửi yêu cầu để học viên xem xét và xác nhận dừng hợp đồng này.</p>
          </div>
          <button className="tutor-button danger" type="button" disabled={busy} onClick={() => void requestStop()}>
            {busy ? 'Đang gửi...' : 'Yêu cầu dừng hợp đồng'}
          </button>
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <StudentContractRenewalForm contract={contract} loading={busy} onSubmit={proposeRenewal} />
      )}
      <section className="tutor-panel tutor-contract-document tutor-contract-document-bottom">
        <div className="student-contract-renewal-copy">
          <span className="student-contract-renewal-icon" aria-hidden="true">
            <Icon name="contract" size={17} />
          </span>
          <h2>Điều khoản hợp đồng</h2>
        </div>
        <ContractDocumentLink contract={contract} viewerUserId={user?.userId} className="tutor-button" />
      </section>
      {contract.status === 'ACTIVE' && (
        <section className="tutor-panel tutor-contract-stop-request tutor-contract-complaint-panel">
          <div className="student-contract-renewal-copy">
            <span className="student-contract-renewal-icon" aria-hidden="true">
              <Icon name="complaint" size={17} />
            </span>
            <div>
              <h2>Khiếu nại hợp đồng</h2>
              <p>Gửi khiếu nại về vấn đề phát sinh trong quá trình thực hiện hợp đồng.</p>
            </div>
          </div>
          <Link className="tutor-button" to={`/tutor/complaints/new?contractId=${contract.id}`}>
            Tạo khiếu nại
          </Link>
        </section>
      )}
      {editingLesson && (
        <div className="tutor-lesson-edit-backdrop">
          <form className="tutor-panel tutor-lesson-edit-form" onSubmit={(event) => void editLesson(event)}>
            <div className="tutor-contract-section-heading">
              <h2>Sửa buổi học</h2>
              <button
                className="tutor-back-button"
                type="button"
                onClick={() => {
                  setError('');
                  setEditingLesson(null);
                }}
              >
                Đóng
              </button>
            </div>
            <label>
              Tiêu đề
              <input name="title" defaultValue={editingLesson.title} required />
            </label>
            <label>
              Ngày học
              <input
                name="date"
                type="date"
                defaultValue={editingLesson.date}
                min={contract.startDate}
                max={contract.endDate}
                required
              />
            </label>
            <label>
              Giờ bắt đầu
              <TimeSelect name="startTime" defaultValue={editingLesson.startTime.slice(0, 5)} required />
            </label>
            <label>
              Giờ kết thúc
              <TimeSelect name="endTime" defaultValue={editingLesson.endTime.slice(0, 5)} required />
            </label>
            {error && (
              <div className="tutor-lesson-edit-error" role="alert">
                {error}
              </div>
            )}
            <div className="tutor-form-actions">
              <button
                className="tutor-button secondary"
                type="button"
                disabled={busy}
                onClick={() => {
                  setError('');
                  setEditingLesson(null);
                }}
              >
                Hủy
              </button>
              <button className="tutor-button" disabled={busy}>
                {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            </div>
          </form>
        </div>
      )}
      {cancellationLesson && (
        <TutorLessonCancellationDialog
          busy={busy}
          lesson={cancellationLesson}
          onClose={() => setCancellationLesson(null)}
          onEdit={() => {
            setCancellationLesson(null);
            setEditingLesson(cancellationLesson);
          }}
          onRefund={() => void cancelLesson(cancellationLesson).then(() => setCancellationLesson(null))}
        />
      )}
    </>
  );
}

function ContractDocumentLink({
  contract,
  viewerUserId,
  className
}: {
  contract: Contract;
  viewerUserId?: string;
  className?: string;
}) {
  const studentName = [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên';
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const createdByTutor = contract.createdByUserId === viewerUserId;
  return (
    <ContractPdfLink
      className={className}
      label="Xem chi tiết PDF"
      studentName={studentName}
      studentEmail={contract.studentEmail}
      studentAddress={contract.teachingMode === 'ONLINE' ? null : contract.studentDetailAddress}
      tutorName={tutorName}
      tutorEmail={contract.tutorEmail}
      subject={contract.subjectName}
      grade={contract.gradeName}
      teachingMode={contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
      studentSigned={!createdByTutor || Boolean(contract.signedByUserId)}
      tutorSigned={createdByTutor || Boolean(contract.signedByUserId)}
      studentSignedAt={createdByTutor ? (contract.signedByUserId ? contract.signedAt : null) : contract.createdAt}
      tutorSignedAt={createdByTutor ? contract.createdAt : contract.signedByUserId ? contract.signedAt : null}
      contractStatus={contract.status}
      cancelledAt={contract.cancelledAt ?? contract.updatedAt}
      price={contract.price}
      paymentPeriod={contract.paymentPeriod}
      startDate={contract.startDate}
      endDate={contract.endDate}
      schedules={contract.schedules}
    />
  );
}

function LessonRow({
  lesson,
  onEdit,
  onCancel,
  onMarkTaught,
  editable
}: {
  lesson: TutorLesson;
  onEdit: () => void;
  onCancel: () => void;
  onMarkTaught: () => void;
  editable: boolean;
}) {
  const waitingForPayment = lesson.status === 'SCHEDULED' && !lesson.paidAt;
  return (
    <div className="tutor-contract-lesson-row">
      <div className="tutor-lesson-row-main">
        <strong>
          {formatDate(lesson.date)} {lesson.startTime.slice(0, 5)} - {lesson.endTime.slice(0, 5)}
        </strong>
        <small>{lesson.title}</small>
      </div>
      <div className={`tutor-lesson-row-paid${lesson.paidAt ? '' : ' unpaid'}`}>
        {lesson.paidAt ? formatDateTime(lesson.paidAt) : 'Chưa thanh toán'}
      </div>
      <div className="tutor-lesson-row-status">
        <span
          className={`tutor-contract-status ${waitingForPayment ? 'status-waiting-payment' : `status-${lesson.status.toLowerCase()}`}`}
        >
          {waitingForPayment ? 'Chờ học viên thanh toán' : lessonStatus(lesson.status)}
        </span>
      </div>
      <div className="tutor-lesson-row-actions">
        {editable && lesson.status === 'SCHEDULED' && (
          <>
            {lesson.paidAt && (
              <button className="tutor-button small" type="button" onClick={onMarkTaught}>
                Đánh dấu đã dạy
              </button>
            )}
            <button className="tutor-button secondary small" type="button" onClick={onEdit}>
              Sửa
            </button>
            <button className="tutor-button danger small" type="button" onClick={onCancel}>
              Hủy
            </button>
          </>
        )}
      </div>
    </div>
  );
}
function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value || '-'}</strong>
    </div>
  );
}
function statusLabel(value: Contract['status']) {
  return (
    { PENDING: 'Đang chờ', ACTIVE: 'Đang hoạt động', COMPLETED: 'Đã hoàn tất', CANCELLED: 'Đã hủy' } as Record<
      string,
      string
    >
  )[value];
}
function lessonStatus(value: TutorLesson['status']) {
  return (
    {
      SCHEDULED: 'Đã lên lịch',
      PENDING_CONFIRMATION: 'Chờ xác nhận',
      CONFIRMED: 'Đã xác nhận',
      COMPLETED: 'Đã hoàn tất',
      CANCELLED: 'Đã hủy'
    } as Record<string, string>
  )[value];
}
function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : 'Chưa lên lịch';
}
function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}
function formatSchedules(schedules: Contract['schedules']) {
  return schedules
    .map(
      (item) =>
        `${['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'][item.dayOfWeek - 1]} ${item.startTime.slice(0, 5)}-${item.endTime.slice(0, 5)}`
    )
    .join('; ');
}
function initials(value: string) {
  return value
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
