import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorDashboard } from '../../hooks/useTutorWorkspace';
import { useAuth } from '../../auth/useAuth';
import { setTutorLessonStatus, updateTutorLesson, type TutorLesson } from '../../services/tutorApi';
import '../common-user/lesson-calendar.css';

export function TutorDashboardPage() {
  const { user } = useAuth();
  const [lessonRevision, setLessonRevision] = useState(0);
  const requestQuery = useStudyingRequests({ page: 0, size: 1 });
  const dashboardQuery = useTutorDashboard(lessonRevision);
  const contracts = dashboardQuery.data?.contracts ?? [];
  const activeContracts = contracts.filter((contract) => contract.status === 'ACTIVE');
  const upcomingLessons = dashboardQuery.data?.upcomingLessons ?? [];
  const [selectedLesson, setSelectedLesson] = useState<TutorLesson | null>(null);
  const [editingLesson, setEditingLesson] = useState(false);
  const [lessonBusy, setLessonBusy] = useState(false);
  const [lessonActionError, setLessonActionError] = useState('');
  const selectedContract = contracts.find((contract) => contract.id === selectedLesson?.contractId);
  const closeLessonModal = () => {
    setSelectedLesson(null);
    setEditingLesson(false);
    setLessonActionError('');
  };
  const markLessonTaught = async () => {
    if (!selectedLesson) return;
    setLessonBusy(true);
    setLessonActionError('');
    try {
      await setTutorLessonStatus(selectedLesson.id, 'PENDING_CONFIRMATION');
      closeLessonModal();
      setLessonRevision((value) => value + 1);
    } catch (error) {
      setLessonActionError(error instanceof Error ? error.message : 'Không thể cập nhật trạng thái buổi học.');
    } finally {
      setLessonBusy(false);
    }
  };
  const cancelLesson = async () => {
    if (!selectedLesson) return;
    setLessonBusy(true);
    setLessonActionError('');
    try {
      await setTutorLessonStatus(selectedLesson.id, 'CANCELLED');
      closeLessonModal();
      setLessonRevision((value) => value + 1);
    } catch (error) {
      setLessonActionError(error instanceof Error ? error.message : 'Không thể hủy buổi học.');
    } finally {
      setLessonBusy(false);
    }
  };
  const saveLessonSchedule = async (form: FormData) => {
    if (!selectedLesson) return;
    setLessonBusy(true);
    setLessonActionError('');
    try {
      await updateTutorLesson(selectedLesson.id, {
        title: String(form.get('title')),
        date: String(form.get('date')),
        startTime: String(form.get('startTime')),
        endTime: String(form.get('endTime')),
        note: String(form.get('note') ?? '')
      });
      closeLessonModal();
      setLessonRevision((value) => value + 1);
    } catch (error) {
      setLessonActionError(error instanceof Error ? error.message : 'Không thể lưu lịch dạy.');
    } finally {
      setLessonBusy(false);
    }
  };
  return (
    <>
      <section className="tutor-welcome-card">
        <div>
          <span className="tutor-eyebrow">GÓC GIẢNG DẠY CỦA BẠN</span>
          <h1>Chào mừng trở lại, {user?.profile?.lastName || 'gia sư'}.</h1>
          <p>Theo dõi lớp đang dạy, lịch học sắp tới và những học viên đang tìm gia sư.</p>
          <a className="tutor-hero-link" href="#active-contracts">
            Xem hợp đồng đang dạy <span>→</span>
          </a>
        </div>
        <div className="tutor-welcome-art" aria-hidden="true">
          <span>✦</span>
          <i />
          <b />
        </div>
      </section>
      <section className="tutor-dashboard-stats" aria-label="Tổng quan giảng dạy">
        <Stat icon="✦" tone="violet" label="Hợp đồng đang dạy" value={activeContracts.length} />
        <Stat icon="✦" tone="violet" label="Buổi học trong 7 ngày tới" value={upcomingLessons.length} />
        <Stat icon="✦" tone="violet" label="Yêu cầu học đang mở" value={requestQuery.data?.totalElements ?? '—'} />
        <Stat
          icon="✦"
          tone="violet"
          label="Hợp đồng chờ xử lý"
          value={contracts.filter((c) => c.status === 'PENDING').length}
        />
      </section>
      {dashboardQuery.error && (
        <div className="tutor-error" role="alert">
          {dashboardQuery.error}
        </div>
      )}
      <section id="active-contracts" className="tutor-active-contracts" aria-label="Hợp đồng đang dạy">
        <div className="tutor-section-heading">
          <div>
            <span className="tutor-eyebrow">TRUY CẬP NHANH</span>
            <h2>Tiếp tục giảng dạy</h2>
          </div>
          <Link className="tutor-button secondary tutor-contract-button" to="/tutor/sections/contracts">
            Xem tất cả hợp đồng →
          </Link>
        </div>
        {activeContracts.length === 0 ? (
          <div className="tutor-empty tutor-empty-short">
            <h3>Chưa có hợp đồng đang dạy</h3>
            <p>Các hợp đồng đã được kích hoạt sẽ hiển thị tại đây.</p>
          </div>
        ) : (
          <div className="tutor-contract-grid">
            {activeContracts.slice(0, 3).map((contract) => (
              <article className="tutor-contract-card" key={contract.id}>
                <div className="tutor-card-top">
                  <span className="tutor-subject-tag">{contract.subjectName || 'Môn học'}</span>
                  <span>Đang dạy</span>
                </div>
                <h3>
                  {contract.subjectName || 'Lớp học'} {contract.gradeName ? `· ${contract.gradeName}` : ''}
                </h3>
                <p>
                  Học viên:{' '}
                  {[contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên'} ·{' '}
                  {contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
                </p>
                <div className="tutor-contract-footer">
                  <span>Đang thực hiện</span>
                  <Link className="tutor-button secondary tutor-contract-button" to="/tutor/sections/contracts">
                    Xem hợp đồng →
                  </Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
      <section className="tutor-next-lessons">
        <div className="tutor-section-heading">
          <div>
            <span className="tutor-eyebrow">THỜI KHÓA BIỂU</span>
            <h2>Lịch dạy trong 7 ngày tới</h2>
            <p className="tutor-section-description">Hôm nay, {formatToday()}</p>
          </div>
          <span className="tutor-count">7 ngày tới</span>
        </div>
        {upcomingLessons.length === 0 ? (
          <div className="tutor-empty tutor-empty-short">
            <span>◷</span>
            <h3>Tuần tới chưa có buổi học nào</h3>
            <p>Lịch học trong các hợp đồng đang hoạt động sẽ hiển thị tại đây.</p>
          </div>
        ) : (
          <div className="tutor-lesson-grid">
            {upcomingLessons.map((lesson) => {
              const contract = contracts.find((item) => item.id === lesson.contractId);
              return (
                <article
                  className={`tutor-lesson-card tutor-lesson-card-wide ${isToday(lesson.date) ? 'is-today' : ''}`}
                  key={lesson.id}
                  role="link"
                  tabIndex={0}
                  onClick={() => setSelectedLesson(lesson)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      setSelectedLesson(lesson);
                    }
                  }}
                >
                  <span className="tutor-lesson-date">
                    <small>{lessonDayLabel(lesson.date)}</small>
                    <strong>
                      {lesson.startTime.slice(0, 5)} – {lesson.endTime.slice(0, 5)}
                    </strong>
                  </span>
                  <div className="tutor-lesson-content">
                    <div className="tutor-lesson-tags">
                      <span>{contract?.subjectName || 'Lớp học'}</span>
                      <span className={lesson.teachingMode === 'ONLINE' ? 'online' : 'offline'}>
                        {lesson.teachingMode === 'ONLINE' ? '▣ Trực tuyến' : '⌖ Trực tiếp'}
                      </span>
                    </div>
                    <h3>{lesson.title}</h3>
                    <div className="tutor-lesson-meta">
                      <span>
                        ♙ Học viên:{' '}
                        {[contract?.studentFirstName, contract?.studentLastName].filter(Boolean).join(' ') ||
                          'Học viên'}
                      </span>
                      <span>♢ Học phí: {formatLessonFee(contract?.price, contract?.totalLessons)}</span>
                      {lesson.teachingMode === 'OFFLINE' && contract?.studentDetailAddress && (
                        <span className="tutor-lesson-location">⌖ Địa chỉ: {contract.studentDetailAddress}</span>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
      {selectedLesson && (
        <div className="lc-overlay" role="presentation" onClick={closeLessonModal}>
          <section
            className="lc-lesson-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="dashboard-lesson-title"
            onClick={(event) => event.stopPropagation()}
          >
            <header>
              <div>
                <small>BUỔI DẠY · {lessonStatusLabel(selectedLesson.status)}</small>
                <h2 id="dashboard-lesson-title">{selectedLesson.title}</h2>
              </div>
              <button type="button" aria-label="Đóng" onClick={closeLessonModal}>
                ×
              </button>
            </header>
            {editingLesson ? (
              <form
                className="lc-edit-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  void saveLessonSchedule(new FormData(event.currentTarget));
                }}
              >
                <label>
                  Tiêu đề buổi học
                  <input name="title" required defaultValue={selectedLesson.title} />
                </label>
                <label>
                  Ngày học
                  <input name="date" type="date" required defaultValue={selectedLesson.date} />
                </label>
                <div className="lc-time-fields">
                  <label>
                    Bắt đầu
                    <input name="startTime" type="time" required defaultValue={selectedLesson.startTime} />
                  </label>
                  <label>
                    Kết thúc
                    <input name="endTime" type="time" required defaultValue={selectedLesson.endTime} />
                  </label>
                </div>
                <label>
                  Ghi chú
                  <input name="note" defaultValue={selectedLesson.note ?? ''} />
                </label>
                {lessonActionError && (
                  <p className="lc-error" role="alert">
                    {lessonActionError}
                  </p>
                )}
                <div className="lc-modal-actions">
                  <button className="lc-primary" type="submit" disabled={lessonBusy}>
                    {lessonBusy ? 'Đang lưu…' : 'Lưu lịch'}
                  </button>
                  <button type="button" disabled={lessonBusy} onClick={() => setEditingLesson(false)}>
                    Hủy
                  </button>
                </div>
              </form>
            ) : (
              <>
                <div className="lc-lesson-facts">
                  <p>
                    <span>Hợp đồng</span>
                    <strong>{selectedContract?.subjectName || 'Hợp đồng'}</strong>
                  </p>
                  <p>
                    <span>Học viên</span>
                    <strong>
                      {[selectedContract?.studentFirstName, selectedContract?.studentLastName]
                        .filter(Boolean)
                        .join(' ') || 'Học viên'}
                    </strong>
                  </p>
                  <p>
                    <span>Ngày học</span>
                    <strong>
                      {new Intl.DateTimeFormat('vi-VN', {
                        weekday: 'long',
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric'
                      }).format(new Date(`${selectedLesson.date}T00:00:00`))}
                    </strong>
                  </p>
                  <p>
                    <span>Thời gian</span>
                    <strong>
                      {selectedLesson.startTime.slice(0, 5)} – {selectedLesson.endTime.slice(0, 5)}
                    </strong>
                  </p>
                  <p>
                    <span>Trạng thái</span>
                    <strong>{lessonStatusLabel(selectedLesson.status)}</strong>
                  </p>
                  {selectedLesson.teachingMode === 'OFFLINE' && selectedContract?.studentDetailAddress && (
                    <p>
                      <span>Địa chỉ</span>
                      <strong>{selectedContract.studentDetailAddress}</strong>
                    </p>
                  )}
                  {selectedLesson.note && (
                    <p>
                      <span>Ghi chú</span>
                      <strong>{selectedLesson.note}</strong>
                    </p>
                  )}
                </div>
                {lessonActionError && (
                  <p className="lc-error" role="alert">
                    {lessonActionError}
                  </p>
                )}
                <div className="lc-modal-actions">
                  {selectedLesson.status === 'SCHEDULED' && (
                    <>
                      <button
                        className="lc-primary"
                        type="button"
                        disabled={lessonBusy}
                        onClick={() => void markLessonTaught()}
                      >
                        {lessonBusy ? 'Đang xử lý…' : 'Đánh dấu đã dạy'}
                      </button>
                      <button type="button" disabled={lessonBusy} onClick={() => setEditingLesson(true)}>
                        Sửa lịch
                      </button>
                      <button
                        className="lc-danger-button"
                        type="button"
                        disabled={lessonBusy}
                        onClick={() => void cancelLesson()}
                      >
                        Hủy buổi học
                      </button>
                    </>
                  )}
                  <button type="button" disabled={lessonBusy} onClick={closeLessonModal}>
                    Đóng
                  </button>
                </div>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}

function Stat({ icon, tone, label, value }: { icon: string; tone: string; label: string; value: string | number }) {
  return (
    <article className="tutor-stat-card">
      <span className={`tutor-stat-icon ${tone}`}>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </article>
  );
}
function formatToday() {
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date());
}

function isToday(date: string) {
  const now = new Date();
  return (
    date ===
    `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  );
}

function lessonDayLabel(date: string) {
  if (isToday(date)) return 'Hôm nay';
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const tomorrowDate = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
  if (date === tomorrowDate) return `Ngày mai (${formatNumericDate(tomorrow)})`;
  return new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit' }).format(
    new Date(`${date}T12:00:00`)
  );
}

function formatNumericDate(date: Date) {
  return `${date.getDate()}/${date.getMonth() + 1}/${date.getFullYear()}`;
}

function formatLessonFee(price?: number, totalLessons?: number | null) {
  if (!price || !totalLessons) return 'Đang cập nhật';
  return `${new Intl.NumberFormat('vi-VN').format(Math.round(price / totalLessons))} đ/buổi`;
}

function lessonStatusLabel(status: TutorLesson['status']) {
  return (
    {
      SCHEDULED: 'Đã lên lịch',
      PENDING_CONFIRMATION: 'Chờ xác nhận',
      CONFIRMED: 'Đã xác nhận',
      COMPLETED: 'Đã hoàn tất',
      CANCELLED: 'Đã hủy'
    }[status] ?? status
  );
}
