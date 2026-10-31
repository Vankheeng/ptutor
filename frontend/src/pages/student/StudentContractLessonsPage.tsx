import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  confirmContractLesson,
  getMyRefundRequests,
  getStudentContractLessons,
  type ContractLesson,
  type RefundRequest
} from '../../services/refundApi';
import {
  confirmationDeadline,
  formatDateTimeValue,
  formatLessonDate,
  lessonStatusLabels,
  refundErrorMessage,
  timeLeft
} from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

const statusTone: Record<ContractLesson['status'], string> = {
  SCHEDULED: 'info',
  PENDING_CONFIRMATION: 'pending',
  CONFIRMED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'neutral'
};

export function StudentContractLessonsPage() {
  const { contractId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(
    async (signal: AbortSignal) => {
      const [lessons, requests] = await Promise.all([
        getStudentContractLessons(contractId, signal),
        getMyRefundRequests(0, signal)
      ]);
      const open = requests.content.find(
        (request: RefundRequest) =>
          request.contractId === contractId && (request.status === 'PENDING' || request.status === 'IN_REVIEW')
      );
      return { lessons, open };
    },
    [contractId]
  );
  const { data, error, isLoading } = useResource(load, revision);
  const held = new Set(data?.open?.disputedLessons.map((lesson) => lesson.lessonId) ?? []);

  const confirm = async (lesson: ContractLesson) => {
    setBusyId(lesson.id);
    setMessage(null);
    try {
      await confirmContractLesson(lesson.id);
      setMessage({
        tone: 'success',
        text: `Đã xác nhận buổi ${formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}.`
      });
      setRevision((value) => value + 1);
    } catch (failure) {
      setMessage({ tone: 'danger', text: refundErrorMessage(failure) });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="rf-page">
      <Link className="rf-back" to={`/student/contracts/${contractId}`}>
        ‹ Hợp đồng
      </Link>
      <div className="rf-heading">
        <h1>Buổi học</h1>
        {data &&
          (data.open ? (
            <Link className="rf-button secondary" to={`/student/refund-requests/${data.open.id}`}>
              Xem yêu cầu hoàn tiền
            </Link>
          ) : (
            <Link className="rf-button outline" to={`/student/contracts/${contractId}/refund-request`}>
              Yêu cầu hoàn tiền
            </Link>
          ))}
      </div>
      {message && (
        <div className={`rf-alert rf-alert-${message.tone}`} role={message.tone === 'danger' ? 'alert' : 'status'}>
          {message.text}
        </div>
      )}
      <section className="rf-card">
        {isLoading && !data && <div className="rf-state">Đang tải…</div>}
        {error && (
          <div className="rf-alert rf-alert-danger" role="alert">
            {error}
          </div>
        )}
        {data && data.lessons.length === 0 && <div className="rf-state">Hợp đồng chưa có buổi học nào.</div>}
        {data && data.lessons.length > 0 && (
          <div className="rf-rows">
            {data.lessons.map((lesson) => {
              const isHeld = held.has(lesson.id) && lesson.status === 'PENDING_CONFIRMATION';
              const waiting = lesson.status === 'PENDING_CONFIRMATION' && !isHeld;
              const deadline = waiting ? confirmationDeadline(lesson.markedTaughtAt) : null;
              return (
                <div className="rf-row" key={lesson.id}>
                  <div className="rf-row-main">
                    <strong>{formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}</strong>
                    {lesson.title && <small>{lesson.title}</small>}
                  </div>
                  <div className="rf-row-side">
                    {deadline && (
                      <small className="rf-kicker">
                        Hạn xác nhận {formatDateTimeValue(deadline)} · {timeLeft(deadline)}
                      </small>
                    )}
                    {isHeld ? (
                      <span className="rf-badge rf-badge-danger">Đang yêu cầu hoàn tiền</span>
                    ) : (
                      <span className={`rf-badge rf-badge-${statusTone[lesson.status]}`}>
                        {lessonStatusLabels[lesson.status]}
                      </span>
                    )}
                    {waiting && (
                      <button
                        className="rf-button small"
                        type="button"
                        disabled={busyId === lesson.id}
                        onClick={() => void confirm(lesson)}
                      >
                        Xác nhận đã học
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
