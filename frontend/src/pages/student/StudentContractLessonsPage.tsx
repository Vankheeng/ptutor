import { useCallback, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  confirmContractLesson,
  getMyRefundRequests,
  getStudentContractLessons,
  type ContractLesson,
  type RefundRequest
} from '../../services/refundApi';
import { getContract, type Contract } from '../../services/studentApi';
import { refundErrorMessage } from '../../components/refund/refundFormat';
import { StudentLessonCard } from '../../components/student/lesson/StudentLessonCard';
import { StudentLessonRefundDialog } from '../../components/student/lesson/StudentLessonRefundDialog';
import { isLessonFinished } from '../../components/student/lesson/studentLessonUtils';
import './student-lessons.css';

const PAGE_SIZE_OPTIONS = [6, 12, 24];
const DEFAULT_PAGE_SIZE = 6;

const filters = [
  ['ALL', 'Tất cả'],
  ['PENDING_CONFIRMATION', 'Chờ xác nhận'],
  ['SCHEDULED', 'Đã lên lịch'],
  ['CONFIRMED', 'Đã học'],
  ['COMPLETED', 'Đã hoàn tất'],
  ['CANCELLED', 'Đã hủy']
] as const;

type LessonFilter = (typeof filters)[number][0];
type TimeFilter = 'ALL' | 'UPCOMING' | 'PAST';
type SortOrder = 'NEWEST' | 'OLDEST';

const formatDate = (value: string) => {
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
};

const contractStatusLabels: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ ký',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

function sortLessons(lessons: ContractLesson[], order: SortOrder) {
  return [...lessons].sort((left, right) => {
    const leftKey = `${left.date}T${left.startTime}`;
    const rightKey = `${right.date}T${right.startTime}`;
    return order === 'NEWEST' ? rightKey.localeCompare(leftKey) : leftKey.localeCompare(rightKey);
  });
}

export function StudentContractLessonsPage() {
  const { contractId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [filter, setFilter] = useState<LessonFilter>('ALL');
  const [timeFilter, setTimeFilter] = useState<TimeFilter>('ALL');
  const [sortOrder, setSortOrder] = useState<SortOrder>('NEWEST');
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [page, setPage] = useState(0);
  const [refundLessonId, setRefundLessonId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(
    async (signal: AbortSignal) => {
      const [contract, lessons, requests] = await Promise.all([
        getContract(contractId),
        getStudentContractLessons(contractId, signal),
        getMyRefundRequests(0, signal)
      ]);
      const open = requests.content.find(
        (request: RefundRequest) =>
          request.contractId === contractId && (request.status === 'PENDING' || request.status === 'IN_REVIEW')
      );
      return { contract, lessons, open };
    },
    [contractId]
  );
  const { data, error, isLoading } = useResource(load, revision);
  const openRefund = data?.open ?? null;
  const heldIds = useMemo(
    () => new Set(openRefund?.disputedLessons.map((lesson) => lesson.lessonId) ?? []),
    [openRefund]
  );

  const filteredLessons = useMemo(() => {
    const lessons = data?.lessons ?? [];
    const nowFiltered = lessons.filter((lesson) => {
      if (filter !== 'ALL' && lesson.status !== filter) return false;
      if (timeFilter === 'UPCOMING' && isLessonFinished(lesson)) return false;
      if (timeFilter === 'PAST' && !isLessonFinished(lesson)) return false;
      return true;
    });
    return sortLessons(nowFiltered, sortOrder);
  }, [data, filter, sortOrder, timeFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredLessons.length / pageSize));
  const safePage = Math.min(page, totalPages - 1);
  const pageLessons = filteredLessons.slice(safePage * pageSize, (safePage + 1) * pageSize);
  const refundLesson = data?.lessons.find((lesson) => lesson.id === refundLessonId) ?? null;
  const lessons = data?.lessons ?? [];
  const learnedCount = lessons.filter(
    (lesson) => lesson.status === 'CONFIRMED' || lesson.status === 'COMPLETED'
  ).length;
  const awaitingCount = lessons.filter((lesson) => lesson.status === 'PENDING_CONFIRMATION').length;
  const scheduledCount = lessons.filter((lesson) => lesson.status === 'SCHEDULED').length;
  const cancelledCount = lessons.filter((lesson) => lesson.status === 'CANCELLED').length;
  const expectedLessons = data?.contract.totalLessons ?? 0;
  const progress = expectedLessons > 0 ? Math.min(100, Math.round((learnedCount / expectedLessons) * 100)) : 0;

  const confirm = async (lesson: ContractLesson) => {
    if (!isLessonFinished(lesson)) {
      setMessage({ tone: 'danger', text: 'Chỉ có thể xác nhận sau khi buổi học đã kết thúc.' });
      return;
    }
    setBusyId(lesson.id);
    setMessage(null);
    try {
      await confirmContractLesson(lesson.id);
      setMessage({ tone: 'success', text: 'Đã xác nhận buổi học.' });
      setRevision((value) => value + 1);
    } catch (failure) {
      setMessage({ tone: 'danger', text: refundErrorMessage(failure) });
    } finally {
      setBusyId(null);
    }
  };

  const contract = data?.contract as Contract | undefined;
  const subject = contract?.subjectName || 'Hợp đồng học tập';
  const grade = contract?.gradeName ? ` · ${contract.gradeName}` : '';
  const tutor = [contract?.tutorFirstName, contract?.tutorLastName].filter(Boolean).join(' ') || 'Chưa có thông tin';
  const firstVisible = filteredLessons.length === 0 ? 0 : safePage * pageSize + 1;
  const lastVisible = Math.min((safePage + 1) * pageSize, filteredLessons.length);

  return (
    <div className="student-lesson-page student-contract-lessons-page">
      <Link className="student-lesson-back" to={`/student/contracts/${contractId}`}>
        ‹ Quay lại hợp đồng
      </Link>

      <header className="student-lesson-heading">
        <h1>Danh sách buổi học</h1>
      </header>

      {message && (
        <div
          className={message.tone === 'danger' ? 'lc-error' : 'lc-notice'}
          role={message.tone === 'danger' ? 'alert' : 'status'}
        >
          {message.text}
        </div>
      )}
      {error && (
        <div className="lc-error" role="alert">
          {error}
        </div>
      )}
      {isLoading && !data && <div className="student-lesson-empty">Đang tải danh sách buổi học…</div>}

      {contract && (
        <section className="student-lesson-summary" aria-label="Thông tin hợp đồng">
          <div className="student-lesson-summary-subject">
            <span className="student-lesson-summary-icon" aria-hidden="true">
              ▤
            </span>
            <div>
              <strong>
                {subject}
                {grade}
              </strong>
              <small>♙ Gia sư: {tutor}</small>
              <small>Hợp đồng: {contract.id.slice(0, 8).toUpperCase()}</small>
            </div>
          </div>
          <div className="student-lesson-summary-fact">
            <span>Thời gian hợp đồng</span>
            <strong>
              {formatDate(contract.startDate)} – {formatDate(contract.endDate)}
            </strong>
          </div>
          <div className="student-lesson-summary-fact">
            <span>Hình thức học</span>
            <strong>{contract.teachingMode === 'OFFLINE' ? 'Trực tiếp' : 'Trực tuyến'}</strong>
          </div>
          <div className="student-lesson-summary-fact">
            <span>Trạng thái</span>
            <strong className="student-lesson-summary-status">
              ● {contractStatusLabels[contract.status] ?? contract.status}
            </strong>
          </div>
          <div className="student-lesson-summary-fact">
            <span>Số buổi dự kiến</span>
            <strong>{expectedLessons} buổi</strong>
          </div>
        </section>
      )}

      {data && (
        <section className="student-lesson-progress-card" aria-label="Tiến độ học tập">
          <div className="student-lesson-progress-main">
            <h2>
              <span aria-hidden="true">▥</span> Tiến độ học tập
            </h2>
            <div className="student-lesson-progress-label">
              <strong>
                Đã học <b>{learnedCount}</b> / {expectedLessons} buổi
              </strong>
              <strong>{progress}%</strong>
            </div>
            <div className="student-lesson-progress-track">
              <span style={{ width: `${progress}%` }} />
            </div>
          </div>
          <div className="student-lesson-progress-stats">
            <div className="student-lesson-progress-stat success">
              <span>✓</span>
              <small>Đã học</small>
              <strong>{learnedCount}</strong>
            </div>
            <div className="student-lesson-progress-stat pending">
              <span>⌛</span>
              <small>Chờ xác nhận</small>
              <strong>{awaitingCount}</strong>
            </div>
            <div className="student-lesson-progress-stat info">
              <span>▣</span>
              <small>Đã lên lịch</small>
              <strong>{scheduledCount}</strong>
            </div>
            <div className="student-lesson-progress-stat danger">
              <span>×</span>
              <small>Đã hủy</small>
              <strong>{cancelledCount}</strong>
            </div>
          </div>
        </section>
      )}

      {data && (
        <div className="student-lesson-controls">
          <label>
            <span>Trạng thái</span>
            <select
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value as LessonFilter);
                setPage(0);
              }}
            >
              {filters.map(([value, label]) => (
                <option key={value} value={value}>
                  {label} (
                  {value === 'ALL' ? lessons.length : lessons.filter((lesson) => lesson.status === value).length})
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Khoảng thời gian</span>
            <select
              value={timeFilter}
              onChange={(event) => {
                setTimeFilter(event.target.value as TimeFilter);
                setPage(0);
              }}
            >
              <option value="ALL">Tất cả</option>
              <option value="UPCOMING">Sắp tới</option>
              <option value="PAST">Đã diễn ra</option>
            </select>
          </label>
          <label className="student-lesson-sort-control">
            <span>Sắp xếp</span>
            <select
              value={sortOrder}
              onChange={(event) => {
                setSortOrder(event.target.value as SortOrder);
                setPage(0);
              }}
            >
              <option value="NEWEST">Ngày mới nhất</option>
              <option value="OLDEST">Ngày cũ nhất</option>
            </select>
          </label>
        </div>
      )}

      {data && data.lessons.length === 0 && (
        <div className="student-lesson-empty">
          <h2>Chưa có buổi học nào</h2>
          <p>Các buổi học sẽ xuất hiện sau khi gia sư tạo lịch trong hợp đồng này.</p>
        </div>
      )}
      {data && data.lessons.length > 0 && filteredLessons.length === 0 && (
        <div className="student-lesson-empty">
          <h2>Không có buổi học phù hợp</h2>
          <p>Không có buổi học nào thuộc điều kiện bạn đã chọn.</p>
        </div>
      )}
      {pageLessons.length > 0 && (
        <section className="student-lesson-timeline" aria-label="Danh sách buổi học">
          {pageLessons.map((lesson) => (
            <StudentLessonCard
              key={lesson.id}
              lesson={lesson}
              contract={contract}
              held={heldIds.has(lesson.id)}
              busy={busyId === lesson.id}
              onConfirm={() => void confirm(lesson)}
              onRefund={() => setRefundLessonId(lesson.id)}
            />
          ))}
        </section>
      )}

      {filteredLessons.length > 0 && (
        <footer className="student-lesson-pagination">
          <span>
            Hiển thị {firstVisible} – {lastVisible} trên tổng số {filteredLessons.length} buổi học
          </span>
          <div className="student-lesson-page-buttons">
            <button
              type="button"
              aria-label="Trang trước"
              disabled={safePage === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              ‹
            </button>
            {Array.from({ length: totalPages }, (_, index) => (
              <button
                key={index}
                type="button"
                className={safePage === index ? 'active' : ''}
                onClick={() => setPage(index)}
              >
                {index + 1}
              </button>
            ))}
            <button
              type="button"
              aria-label="Trang sau"
              disabled={safePage >= totalPages - 1}
              onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))}
            >
              ›
            </button>
          </div>
          <label className="student-lesson-page-size">
            <select
              value={pageSize}
              onChange={(event) => {
                setPageSize(Number(event.target.value));
                setPage(0);
              }}
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size} buổi / trang
                </option>
              ))}
            </select>
          </label>
        </footer>
      )}

      {refundLesson && (
        <StudentLessonRefundDialog
          lesson={refundLesson}
          contract={contract}
          onClose={() => setRefundLessonId(null)}
          onSubmitted={() => {
            setRefundLessonId(null);
            setMessage({ tone: 'success', text: 'Đã gửi yêu cầu khiếu nại / hoàn tiền.' });
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
