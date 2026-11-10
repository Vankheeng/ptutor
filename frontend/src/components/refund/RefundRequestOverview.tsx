import type { LessonOutcome, RefundRequest } from '../../services/refundApi';
import { RefundEvidenceList } from './RefundEvidenceField';
import {
  formatClockTime,
  formatLessonDate,
  formatLocalDateTime,
  formatMoney,
  outcomeLabels,
  outcomeTone,
  refundRequestTitle,
  refundStatusMeta
} from './refundFormat';

export function RefundStatusBadge({ status }: { status: RefundRequest['status'] }) {
  const meta = refundStatusMeta[status];
  return <span className={`rf-badge rf-badge-${meta.tone}`}>{meta.label}</span>;
}

function OutcomeBadge({ label, outcome }: { label: string; outcome: LessonOutcome }) {
  return (
    <span className={`rf-badge rf-badge-${outcomeTone[outcome]}`}>
      {label}: {outcomeLabels[outcome].toLowerCase()}
    </span>
  );
}

/**
 * The request as both participants see it: what the student claimed and, once given, the tutor's reply and the outcome.
 */
export function RefundRequestOverview({ request }: { request: RefundRequest }) {
  const studentEvidence = request.evidences.filter((item) => item.uploadedByUserId === request.studentUserId);
  const tutorEvidence = request.evidences.filter((item) => item.uploadedByUserId === request.tutorUserId);
  return (
    <>
      {request.status === 'REFUNDED' && (
        <>
          <div className="rf-amount">
            <span>Đã cộng vào ví học viên</span>
            <strong>{formatMoney(request.refundAmount)}</strong>
          </div>
          {(request.tutorShareAmount ?? 0) > 0 && (
            <div className="rf-amount">
              <span>Đã cộng vào ví gia sư</span>
              <strong>{formatMoney(request.tutorShareAmount)}</strong>
            </div>
          )}
        </>
      )}
      {request.status === 'REJECTED' && request.rejectionReason && (
        <div className="rf-alert rf-alert-danger">Lý do từ chối: {request.rejectionReason}</div>
      )}

      <section className="rf-card">
        <dl className="rf-facts">
          <div>
            <dt>Hợp đồng</dt>
            <dd>{refundRequestTitle(request)}</dd>
          </div>
          <div>
            <dt>Học viên</dt>
            <dd>{request.studentName}</dd>
          </div>
          <div>
            <dt>Gia sư</dt>
            <dd>{request.tutorName}</dd>
          </div>
          <div>
            <dt>Gửi lúc</dt>
            <dd>{formatLocalDateTime(request.createdAt)}</dd>
          </div>
          {request.reviewedAt && (
            <div>
              <dt>Xử lý lúc</dt>
              <dd>{formatClockTime(request.reviewedAt)}</dd>
            </div>
          )}
        </dl>
        <p className="rf-text">{request.description}</p>
        <RefundEvidenceList items={studentEvidence} />
      </section>

      <section className="rf-card" aria-labelledby="rf-disputed-title">
        <h2 id="rf-disputed-title">Buổi học bị phản đối</h2>
        <div className="rf-rows">
          {request.disputedLessons.map((lesson) => (
            <div className="rf-row" key={lesson.lessonId}>
              <div className="rf-row-main">
                <strong>{formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}</strong>
                <small>Gia sư báo đã dạy lúc {formatClockTime(lesson.markedTaughtAt)}</small>
              </div>
              <div className="rf-row-side">
                <OutcomeBadge label="Học viên" outcome={lesson.claimedOutcome} />
                {lesson.tutorPosition && <OutcomeBadge label="Gia sư" outcome={lesson.tutorPosition} />}
                {lesson.decision && <OutcomeBadge label="Kết luận" outcome={lesson.decision} />}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="rf-card" aria-labelledby="rf-tutor-title">
        <div className="rf-heading">
          <h2 id="rf-tutor-title">Phản hồi của gia sư</h2>
          <span className="rf-kicker">
            {request.tutorRespondedAt
              ? `Phản hồi lúc ${formatClockTime(request.tutorRespondedAt)}`
              : `Hạn phản hồi ${formatClockTime(request.tutorResponseDeadline)}`}
          </span>
        </div>
        {request.tutorResponse ? (
          <>
            <p className="rf-text">{request.tutorResponse}</p>
            <RefundEvidenceList items={tutorEvidence} />
          </>
        ) : (
          <p className="rf-text rf-kicker">Chưa có phản hồi.</p>
        )}
      </section>
    </>
  );
}
