import { useCallback, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getTutorRefundRequest,
  respondToRefundRequest,
  type EvidenceInput,
  type RefundRequest,
  type LessonOutcome
} from '../../services/refundApi';
import { RefundEvidenceField } from '../../components/refund/RefundEvidenceField';
import { RefundRequestOverview, RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import {
  formatClockTime,
  formatLessonDate,
  outcomeLabels,
  parseClockTime,
  refundErrorMessage,
  timeLeft
} from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

const MESSAGE_LIMIT = 2000;

function canRespond(request: RefundRequest) {
  const deadline = parseClockTime(request.tutorResponseDeadline);
  return (
    (request.status === 'PENDING' || request.status === 'IN_REVIEW') &&
    !request.tutorRespondedAt &&
    deadline !== null &&
    deadline.getTime() > Date.now()
  );
}

function ResponseForm({ request, onSent }: { request: RefundRequest; onSent: () => void }) {
  const [positions, setPositions] = useState<Record<string, LessonOutcome>>({});
  const [message, setMessage] = useState('');
  const [evidences, setEvidences] = useState<EvidenceInput[]>([]);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const allPositioned = request.disputedLessons.every((lesson) => positions[lesson.lessonId]);
  const deadline = parseClockTime(request.tutorResponseDeadline);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTried(true);
    if (!allPositioned || !message.trim()) return;
    setBusy(true);
    setFailure(null);
    try {
      await respondToRefundRequest(request.id, {
        message: message.trim(),
        lessonPositions: request.disputedLessons.map((lesson) => ({
          lessonId: lesson.lessonId,
          position: positions[lesson.lessonId]
        })),
        evidences
      });
      onSent();
    } catch (submitError) {
      setFailure(refundErrorMessage(submitError));
      setBusy(false);
    }
  };

  return (
    <form className="rf-card" onSubmit={(event) => void submit(event)} noValidate aria-labelledby="rf-respond-title">
      <div className="rf-heading">
        <h2 id="rf-respond-title">Phản hồi của bạn</h2>
        <span className="rf-badge rf-badge-pending">{timeLeft(deadline)}</span>
      </div>
      {failure && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {failure}
        </div>
      )}
      {request.disputedLessons.length > 0 && (
        <div className="rf-rows">
          {request.disputedLessons.map((lesson) => (
            <div className="rf-row" key={lesson.lessonId}>
              <div className="rf-row-main">
                <strong>{formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}</strong>
                <small>
                  Bạn báo đã dạy lúc {formatClockTime(lesson.markedTaughtAt)} · Học viên:{' '}
                  {outcomeLabels[lesson.claimedOutcome].toLowerCase()}
                </small>
              </div>
              <div className="rf-seg" role="group" aria-label={`Phản hồi buổi ${lesson.date}`}>
                {(['TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT'] as const).map((value) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={positions[lesson.lessonId] === value}
                    onClick={() => setPositions((current) => ({ ...current, [lesson.lessonId]: value }))}
                  >
                    {value === 'TAUGHT' ? 'Tôi đã dạy' : value === 'TUTOR_ABSENT' ? 'Tôi không dạy' : 'Học viên nghỉ'}
                  </button>
                ))}
              </div>
            </div>
          ))}
          {tried && !allPositioned && <span className="rf-error">Vui lòng phản hồi cho từng buổi.</span>}
        </div>
      )}
      <div className="rf-field">
        <label htmlFor="rf-message">
          Lời nhắn <span className="rf-required">*</span>
        </label>
        <textarea
          id="rf-message"
          rows={4}
          maxLength={MESSAGE_LIMIT}
          value={message}
          aria-invalid={tried && !message.trim()}
          onChange={(event) => setMessage(event.target.value)}
        />
        <div className="rf-help">
          <span className="rf-error">{tried && !message.trim() ? 'Vui lòng nhập lời nhắn.' : ''}</span>
          <span>
            {message.length}/{MESSAGE_LIMIT}
          </span>
        </div>
      </div>
      <RefundEvidenceField value={evidences} onChange={setEvidences} />
      <div className="rf-actions">
        <button className="rf-button" type="submit" disabled={busy}>
          Gửi phản hồi
        </button>
      </div>
    </form>
  );
}

export function TutorRefundRequestDetailPage() {
  const { refundRequestId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getTutorRefundRequest(refundRequestId, signal), [refundRequestId]);
  const { data, error, isLoading } = useResource(load, revision);

  return (
    <div className="rf-page">
      <Link className="rf-back" to="/tutor/refund-requests">
        ‹ Yêu cầu hoàn tiền
      </Link>
      {notice && (
        <div className="rf-alert rf-alert-success" role="status">
          {notice}
        </div>
      )}
      {isLoading && !data && <div className="rf-state">Đang tải…</div>}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}
      {data && (
        <>
          <div className="rf-heading-title">
            <h1>Yêu cầu hoàn tiền</h1>
            <RefundStatusBadge status={data.status} />
          </div>
          {canRespond(data) && (
            <ResponseForm
              request={data}
              onSent={() => {
                setNotice('Đã gửi phản hồi.');
                setRevision((value) => value + 1);
              }}
            />
          )}
          <RefundRequestOverview request={data} />
        </>
      )}
    </div>
  );
}
