import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getContract, renewContract, type Contract, type ContractTerms } from '../../services/studentApi';
import {
  createTutorLesson,
  getTutorContractLessons,
  requestTutorContractStop,
  setTutorLessonStatus,
  updateTutorLesson,
  type TutorLesson
} from '../../services/tutorApi';
import { Icon } from '../../components/ui/Icon';
import { StudentContractRenewalForm } from '../../components/student/StudentContractRenewalForm';
import { formatPreferredSchedule } from '../../utils/scheduleFormat';
import '../student/student.css';

export function TutorContractDetailPage() {
  const { contractId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [lessonPage, setLessonPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [editingLesson, setEditingLesson] = useState<TutorLesson | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const query = useResource(
    useCallback(
      async (signal: AbortSignal) => {
        const [contract, lessonPage] = await Promise.all([
          getContract(contractId),
          getTutorContractLessons(contractId, signal)
        ]);
        return { contract, lessons: lessonPage.content };
      },
      [contractId]
    ),
    revision
  );
  const contract = query.data?.contract;
  const lessons = query.data?.lessons?.length ? query.data.lessons : contract ? plannedLessons(contract) : [];
  const lessonsPerPage = 5;
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
      setError(caught instanceof Error ? caught.message : 'Không thể tạo buổi học.');
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
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật buổi học.');
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

  const completedLessons = lessons.filter(
    (lesson) => lesson.status === 'COMPLETED' || lesson.status === 'CONFIRMED'
  ).length;
  const waitingLessons = lessons.filter((lesson) => lesson.status === 'PENDING_CONFIRMATION').length;
  const scheduledLessons = lessons.filter((lesson) => lesson.status === 'SCHEDULED').length;
  const progress = contract.totalLessons
    ? Math.min(100, Math.round((completedLessons / contract.totalLessons) * 100))
    : 0;

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
            <strong>{student}</strong>
            <small>Học viên</small>
          </div>
        </div>
        <span className={`tutor-contract-status status-${contract.status.toLowerCase()}`}>
          {statusLabel(contract.status)}
        </span>
      </section>
      <section className="tutor-panel tutor-contract-overview">
        <h2>Tổng quan hợp đồng</h2>
        <div className="tutor-contract-detail-grid">
          <Detail label="Môn học" value={contract.subjectName} />
          <Detail label="Lớp" value={contract.gradeName} />
          <Detail label="Hình thức dạy" value={contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
          <Detail label="Học viên" value={student} />
          <Detail label="Giá" value={`${formatMoney(contract.price)} · ${paymentLabel(contract.paymentPeriod)}`} />
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
      <section className="tutor-contract-lessons-layout">
        <div className="tutor-panel">
          <div className="tutor-contract-section-heading">
            <div>
              <h2>Buổi học</h2>
              <p>Quản lý các buổi dạy theo lịch trong hợp đồng.</p>
            </div>
            <span className="tutor-count">{lessons.length} buổi</span>
          </div>
          {lessons.length ? (
            <>
              <div className="tutor-contract-lesson-list">
                <div className="tutor-contract-lesson-list-header">
                  <span>Buổi học</span>
                  <span>Đã thanh toán lúc</span>
                  <span>Trạng thái</span>
                  <span>Thao tác</span>
                </div>
                {displayedLessons.map((lesson) => (
                  <LessonRow
                    lesson={lesson}
                    key={lesson.id}
                    onEdit={() => setEditingLesson(lesson)}
                    onCancel={() => void cancelLesson(lesson)}
                  />
                ))}
              </div>
              {lessonPageCount > 1 && (
                <div className="tutor-lesson-pagination">
                  <button type="button" disabled={lessonPage === 0} onClick={() => setLessonPage((page) => page - 1)}>
                    ‹
                  </button>
                  <span>
                    Trang {lessonPage + 1}/{lessonPageCount}
                  </span>
                  <button
                    type="button"
                    disabled={lessonPage === lessonPageCount - 1}
                    onClick={() => setLessonPage((page) => page + 1)}
                  >
                    ›
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
        </div>
        <section className="tutor-panel">
          <h2>Tạo buổi học</h2>
          <p className="tutor-muted">Chỉ chọn ngày trong khoảng hợp đồng và đúng ngày học đã thỏa thuận.</p>
          <form className="tutor-form-grid" onSubmit={(event) => void createLesson(event)}>
            <label className="tutor-field-full">
              Tiêu đề
              <input name="title" placeholder={`Buổi học ${contract.subjectName}`} />
            </label>
            <label className="tutor-field-full">
              Ngày học
              <input name="date" type="date" min={contract.startDate} max={contract.endDate} required />
            </label>
            <label>
              Giờ bắt đầu
              <input name="startTime" type="time" required />
            </label>
            <label>
              Giờ kết thúc
              <input name="endTime" type="time" required />
            </label>
            <div className="tutor-form-actions tutor-field-full">
              <button className="tutor-button" disabled={busy}>
                {busy ? 'Đang tạo...' : 'Tạo buổi học'}
              </button>
            </div>
          </form>
        </section>
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
      {editingLesson && (
        <div className="tutor-lesson-edit-backdrop">
          <form className="tutor-panel tutor-lesson-edit-form" onSubmit={(event) => void editLesson(event)}>
            <div className="tutor-contract-section-heading">
              <h2>Sửa buổi học</h2>
              <button className="tutor-back-button" type="button" onClick={() => setEditingLesson(null)}>
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
              <input name="startTime" type="time" defaultValue={editingLesson.startTime.slice(0, 5)} required />
            </label>
            <label>
              Giờ kết thúc
              <input name="endTime" type="time" defaultValue={editingLesson.endTime.slice(0, 5)} required />
            </label>
            <div className="tutor-form-actions">
              <button className="tutor-button" disabled={busy}>
                {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}

function LessonRow({ lesson, onEdit, onCancel }: { lesson: TutorLesson; onEdit: () => void; onCancel: () => void }) {
  return (
    <div className="tutor-contract-lesson-row">
      <div className="tutor-lesson-row-main">
        <strong>
          {formatDate(lesson.date)} · {lesson.startTime.slice(0, 5)} – {lesson.endTime.slice(0, 5)}
        </strong>
        <small>{lesson.title}</small>
      </div>
      <div className="tutor-lesson-row-paid">{lesson.paidAt ? formatDateTime(lesson.paidAt) : 'Chưa thanh toán'}</div>
      <div className="tutor-lesson-row-status">
        <span className={`tutor-contract-status status-${lesson.status.toLowerCase()}`}>
          {lessonStatus(lesson.status)}
        </span>
      </div>
      <div className="tutor-lesson-row-actions">
        {lesson.status === 'SCHEDULED' && (
          <>
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
      <strong>{value || '—'}</strong>
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
function paymentLabel(value: Contract['paymentPeriod']) {
  return (
    { PER_LESSON: 'Theo buổi', WEEKLY: 'Hàng tuần', MONTHLY: 'Hàng tháng', PACKAGE: 'Trọn gói' } as Record<
      string,
      string
    >
  )[value];
}
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VNĐ`;
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
        `${['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'][item.dayOfWeek - 1]} ${item.startTime.slice(0, 5)}–${item.endTime.slice(0, 5)}`
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
function plannedLessons(contract: Contract): TutorLesson[] {
  if (contract.status !== 'PENDING' || !contract.schedules?.length || !contract.totalLessons) return [];
  const result: TutorLesson[] = [];
  for (
    let date = new Date(`${contract.startDate}T12:00:00`);
    date <= new Date(`${contract.endDate}T12:00:00`) && result.length < contract.totalLessons;
    date.setDate(date.getDate() + 1)
  ) {
    const day = date.getDay() || 7;
    contract.schedules
      .filter((schedule) => schedule.dayOfWeek === day)
      .forEach((schedule) => {
        if (result.length < contract.totalLessons) {
          const dateValue = date.toISOString().slice(0, 10);
          result.push({
            id: `planned-${dateValue}-${schedule.startTime}`,
            contractId: contract.id,
            title: 'Buổi học dự kiến',
            date: dateValue,
            startTime: schedule.startTime,
            endTime: schedule.endTime,
            teachingMode: contract.teachingMode,
            status: 'SCHEDULED',
            note: 'Lịch dự kiến, chờ hợp đồng được ký.',
            paidAt: null
          });
        }
      });
  }
  return result;
}
