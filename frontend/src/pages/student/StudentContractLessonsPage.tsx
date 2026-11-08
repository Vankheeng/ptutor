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
import {
  StudentLessonDetailPanel,
  type StudentLessonDetail
} from '../../components/student/lesson/StudentLessonDetailPanel';
import { StudentLessonCard } from '../../components/student/lesson/StudentLessonCard';
import './student-lessons.css';

const filters = [
  ['ALL', 'Tất cả'],
  ['PENDING_CONFIRMATION', 'Chờ xác nhận'],
  ['SCHEDULED', 'Đã lên lịch'],
  ['CONFIRMED', 'Đã xác nhận'],
  ['COMPLETED', 'Đã hoàn tất'],
  ['CANCELLED', 'Đã hủy']
] as const;

const formatDate = (value: string) => {
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
};

const toDetail = (lesson: ContractLesson, refundRequestId: string | null): StudentLessonDetail => ({
  ...lesson,
  disputed: Boolean(refundRequestId),
  refundRequestId
});

export function StudentContractLessonsPage() {
  const { contractId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [filter, setFilter] = useState<(typeof filters)[number][0]>('ALL');
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null);
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
  const filteredLessons = useMemo(
    () => data?.lessons.filter((lesson) => filter === 'ALL' || lesson.status === filter) ?? [],
    [data, filter]
  );
  const selectedLesson = data?.lessons.find((lesson) => lesson.id === selectedLessonId) ?? null;
  const selectedDetail = selectedLesson
    ? toDetail(selectedLesson, heldIds.has(selectedLesson.id) ? (openRefund?.id ?? null) : null)
    : null;

  const confirm = async (lesson: ContractLesson) => {
    setBusyId(lesson.id);
    setMessage(null);
    try {
      await confirmContractLesson(lesson.id);
      setMessage({ tone: 'success', text: 'Đã xác nhận buổi học.' });
      setSelectedLessonId(null);
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

  return (
    <div className="student-lesson-page">
      <Link className="student-lesson-back" to={`/student/contracts/${contractId}`}>
        ‹ Quay lại hợp đồng
      </Link>
      <header className="student-lesson-heading">
        <div>
          <span className="student-lesson-kicker">LỊCH HỌC</span>
          <h1>Buổi học</h1>
          <p>Danh sách các buổi học trong hợp đồng này.</p>
        </div>
        {data &&
          (openRefund ? (
            <Link className="student-lesson-view" to={`/student/refund-requests/${openRefund.id}`}>
              Xem yêu cầu hoàn tiền
            </Link>
          ) : (
            <Link className="student-lesson-refund" to={`/student/contracts/${contractId}/refund-request`}>
              Yêu cầu hoàn tiền
            </Link>
          ))}
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
        <section className="student-lesson-summary">
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
          <span className="student-lesson-badge success">
            {contract.status === 'ACTIVE' ? 'Đang hoạt động' : contract.status}
          </span>
        </section>
      )}

      {data && (
        <div className="student-lesson-filter" aria-label="Lọc trạng thái buổi học">
          {filters.map(([value, label]) => {
            const count =
              value === 'ALL' ? data.lessons.length : data.lessons.filter((lesson) => lesson.status === value).length;
            return (
              <button
                key={value}
                className={filter === value ? 'active' : ''}
                type="button"
                onClick={() => setFilter(value)}
              >
                {label}
                <span>({count})</span>
              </button>
            );
          })}
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
          <p>Không có buổi học nào thuộc trạng thái bạn đã chọn.</p>
        </div>
      )}
      {filteredLessons.length > 0 && (
        <section className="student-lesson-timeline" aria-label="Danh sách buổi học">
          {filteredLessons.map((lesson) => (
            <StudentLessonCard
              key={lesson.id}
              lesson={toDetail(lesson, heldIds.has(lesson.id) ? (openRefund?.id ?? null) : null)}
              contract={contract}
              held={heldIds.has(lesson.id)}
              busy={busyId === lesson.id}
              refundHref={
                openRefund
                  ? `/student/refund-requests/${openRefund.id}`
                  : `/student/contracts/${contractId}/refund-request`
              }
              onSelect={() => setSelectedLessonId(lesson.id)}
              onConfirm={() => void confirm(lesson)}
            />
          ))}
        </section>
      )}

      {selectedDetail && (
        <StudentLessonDetailPanel
          lesson={selectedDetail}
          contract={contract}
          dialog
          busy={busyId === selectedDetail.id}
          onClose={() => setSelectedLessonId(null)}
          onConfirm={() => void confirm(selectedLesson!)}
        />
      )}
    </div>
  );
}
