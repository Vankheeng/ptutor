import { useCallback, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useResource } from '../../hooks/useResource';
import {
  approveRefundRequest,
  getAdminRefundRequest,
  rejectRefundRequest,
  startRefundReview,
  type LessonOutcome
} from '../../services/refundApi';
import {
  blockerLabels,
  computeRefund,
  evidenceName,
  formatClockTime,
  formatLessonDate,
  formatLocalDateTime,
  formatMoney,
  outcomeLabels,
  refundErrorMessage
} from '../../components/refund/refundFormat';
import { contractStatusMeta, formatDate, paymentPeriodLabels } from './adminFormat';
import { AdminModal, AdminState, DefinitionGrid, ReasonField, StatusBadge } from './components/AdminUi';
import { RefundBadge } from './AdminRefundRequestsPage';

const outcomes: LessonOutcome[] = ['TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT'];

export function AdminRefundRequestDetailPage() {
  const { refundRequestId = '' } = useParams();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [decisions, setDecisions] = useState<Record<string, LessonOutcome>>({});
  const [dialog, setDialog] = useState<'approve' | 'reject' | null>(null);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [reasonTried, setReasonTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  const load = useCallback((signal: AbortSignal) => getAdminRefundRequest(refundRequestId, signal), [refundRequestId]);
  const { data, error, isLoading } = useResource(load, revision);

  const pendingDisputed = useMemo(
    () => (data ? data.disputedLessons.filter((lesson) => lesson.decision === null) : []),
    [data]
  );

  if (!data) {
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }

  const reload = () => {
    setDecisions({});
    setRevision((value) => value + 1);
  };

  const calc = data.calculation;
  const inReview = data.status === 'IN_REVIEW';
  const mine = inReview && data.reviewer?.userId === user?.userId;
  const refunded = data.status === 'REFUNDED';
  const live = computeRefund(
    calc,
    pendingDisputed.map((lesson) => decisions[lesson.lessonId])
  );
  const refund = refunded ? (data.refundAmount ?? 0) : live.refund;
  const tutorShare = refunded ? (data.tutorShareAmount ?? 0) : live.tutorShare;
  const undecided = pendingDisputed.filter((lesson) => !decisions[lesson.lessonId]).length;
  const allTaught = undecided === 0 && refund <= 0 && tutorShare <= 0;
  const blockers = calc.blockers;
  const blockText =
    blockers.length > 0
      ? blockers.map((blocker) => blockerLabels[blocker]).join('. ') +
        (blockers.includes('TUTOR_RESPONSE_WINDOW_OPEN') ? ` đến ${formatClockTime(data.tutorResponseDeadline)}` : '')
      : undecided > 0
        ? `Còn ${undecided} buổi bị phản đối chưa kết luận.`
        : allTaught
          ? 'Mọi buổi đều được kết luận đã dạy. Hãy từ chối yêu cầu.'
          : null;

  const run = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setDialog(null);
      setMessage({ tone: 'success', text: success });
      reload();
    } catch (actionError) {
      setDialog(null);
      setMessage({ tone: 'danger', text: refundErrorMessage(actionError) });
      if ((actionError as { code?: string }).code === 'REFUND_AMOUNT_CHANGED') reload();
    } finally {
      setBusy(false);
    }
  };

  const approve = () =>
    run(
      () =>
        approveRefundRequest(data.id, {
          expectedAmount: refund,
          lessonDecisions: pendingDisputed.map((lesson) => ({
            lessonId: lesson.lessonId,
            decision: decisions[lesson.lessonId]
          })),
          note: note.trim() || null
        }),
      `Đã hoàn ${formatMoney(refund)} vào ví ${data.student.name}.`
    );

  const studentEvidence = data.evidences.filter((item) => item.uploadedByUserId === data.student.userId);
  const tutorEvidence = data.evidences.filter((item) => item.uploadedByUserId === data.tutor.userId);
  const evidenceLinks = (items: typeof data.evidences) =>
    items.length === 0 ? null : (
      <div className="admin-tags">
        {items.map((item) => (
          <a key={item.id} className="admin-link" href={item.fileUrl} target="_blank" rel="noreferrer">
            {evidenceName(item)}
          </a>
        ))}
      </div>
    );

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/refund-requests">
        ‹ Danh sách yêu cầu
      </Link>
      <div className="admin-section-heading">
        <h1 className="admin-title">Yêu cầu hoàn tiền</h1>
        <span>
          Gửi lúc {formatLocalDateTime(data.createdAt)} ·{' '}
          {data.reviewer ? `Người xử lý: ${data.reviewer.name}` : 'Chưa có người xử lý'}
        </span>
      </div>
      <div>
        <RefundBadge status={data.status} />
      </div>
      {message && (
        <div
          className={`admin-alert admin-alert-${message.tone}`}
          role={message.tone === 'danger' ? 'alert' : 'status'}
        >
          {message.text}
        </div>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          <section className="admin-card admin-card-pad">
            <h2>Yêu cầu của học viên</h2>
            <DefinitionGrid
              items={[
                ['Học viên', `${data.student.name} · ${data.student.email}`],
                ['Gia sư', `${data.tutor.name} · ${data.tutor.email}`]
              ]}
            />
            <p className="admin-prose">{data.description}</p>
            {evidenceLinks(studentEvidence)}
            {data.complaints.length > 0 && (
              <div className="admin-tags">
                {data.complaints.map((complaint) => (
                  <span key={complaint.complaintId} className="admin-badge admin-badge-pending">
                    Khiếu nại đang mở: {complaint.title}
                  </span>
                ))}
              </div>
            )}
          </section>

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Buổi học bị phản đối</h2>
              {mine && undecided > 0 && <span>Còn {undecided} buổi chưa kết luận</span>}
            </div>
            {data.disputedLessons.map((lesson) => {
              const chosen = lesson.decision ?? decisions[lesson.lessonId];
              return (
                <div key={lesson.lessonId} className="admin-section-heading">
                  <div className="admin-stack">
                    <strong>{formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}</strong>
                    <span>Gia sư báo đã dạy lúc {formatClockTime(lesson.markedTaughtAt)}</span>
                    <span>
                      Học viên: {outcomeLabels[lesson.claimedOutcome].toLowerCase()} · Gia sư:{' '}
                      {lesson.tutorPosition ? outcomeLabels[lesson.tutorPosition].toLowerCase() : 'chưa phản hồi'}
                    </span>
                  </div>
                  <div className="admin-chips" role="group" aria-label={`Kết luận buổi ${lesson.date}`}>
                    {outcomes.map((value) => (
                      <button
                        key={value}
                        type="button"
                        aria-pressed={chosen === value}
                        className={chosen === value ? 'active' : undefined}
                        disabled={!mine || lesson.decision !== null}
                        onClick={() => setDecisions((current) => ({ ...current, [lesson.lessonId]: value }))}
                      >
                        {outcomeLabels[value]}
                      </button>
                    ))}
                  </div>
                </div>
              );
            })}
          </section>

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Phản hồi của gia sư</h2>
              <span>
                {data.tutorRespondedAt
                  ? `Phản hồi lúc ${formatClockTime(data.tutorRespondedAt)}`
                  : `Hạn phản hồi ${formatClockTime(data.tutorResponseDeadline)}`}
              </span>
            </div>
            {data.tutorResponse ? (
              <>
                <p className="admin-prose">{data.tutorResponse}</p>
                {evidenceLinks(tutorEvidence)}
              </>
            ) : (
              <span className="admin-state">Chưa có phản hồi.</span>
            )}
          </section>

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Hợp đồng</h2>
              <StatusBadge meta={contractStatusMeta[data.contract.status as keyof typeof contractStatusMeta]} />
            </div>
            <DefinitionGrid
              columns={3}
              items={[
                ['Môn học', [data.contract.subjectName, data.contract.gradeName].filter(Boolean).join(' · ') || '—'],
                [
                  'Học phí',
                  `${formatMoney(data.contract.price)} · ${paymentPeriodLabels[data.contract.paymentPeriod]}`
                ],
                ['Tổng số buổi', `${data.contract.totalLessons} buổi`],
                ['Thời hạn', `${formatDate(data.contract.startDate)} – ${formatDate(data.contract.endDate)}`]
              ]}
            />
            {data.payments.length > 0 && (
              <div className="admin-key-values">
                {data.payments.map((payment) => (
                  <div key={payment.id}>
                    <span>Đóng lúc {formatLocalDateTime(payment.paidAt)}</span>
                    <strong>
                      {formatMoney(payment.amount)}
                      {payment.refundedAmount > 0 && ` · đã hoàn ${formatMoney(payment.refundedAmount)}`}
                    </strong>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Số tiền hoàn</h2>
            <div className="admin-key-values">
              <div>
                <span>Học phí mỗi buổi</span>
                <strong>{formatMoney(calc.unitPrice)}</strong>
              </div>
              <div>
                <span>Gia sư không dạy</span>
                <strong>Hoàn {calc.tutorAbsentRate}%</strong>
              </div>
              <div>
                <span>Học viên nghỉ</span>
                <strong>
                  Hoàn {calc.studentAbsentRate}%, gia sư nhận {100 - calc.studentAbsentRate}%
                </strong>
              </div>
              <div>
                <span>Còn có thể hoàn</span>
                <strong>{formatMoney(calc.refundableAmount)}</strong>
              </div>
            </div>
            <div className="admin-stat admin-stat-brand">
              <span>Hoàn vào ví học viên</span>
              <strong>{formatMoney(refund)}</strong>
            </div>
            {tutorShare > 0 && (
              <div className="admin-stat">
                <span>Cộng vào ví gia sư</span>
                <strong>{formatMoney(tutorShare)}</strong>
              </div>
            )}
            {data.rejectionReason && (
              <div className="admin-alert admin-alert-danger">Lý do từ chối: {data.rejectionReason}</div>
            )}
            {data.note && <div className="admin-alert admin-alert-muted">Ghi chú: {data.note}</div>}

            {(inReview || data.status === 'PENDING') && blockText && (
              <div
                className={`admin-alert admin-alert-${blockers.includes('CONTRACT_HAS_OPEN_COMPLAINT') ? 'danger' : 'warning'}`}
              >
                {blockText}
              </div>
            )}
            {data.status === 'PENDING' && (
              <button
                className="admin-button"
                type="button"
                disabled={busy}
                onClick={() => void run(() => startRefundReview(data.id), 'Đã tiếp nhận yêu cầu.')}
              >
                Tiếp nhận
              </button>
            )}
            {mine && (
              <div className="admin-button-row">
                <button
                  className="admin-button danger-outline"
                  type="button"
                  onClick={() => {
                    setReason('');
                    setReasonTried(false);
                    setDialog('reject');
                  }}
                >
                  Từ chối
                </button>
                <button
                  className="admin-button"
                  type="button"
                  disabled={busy || Boolean(blockText)}
                  onClick={() => setDialog('approve')}
                >
                  Duyệt và hoàn tiền
                </button>
              </div>
            )}
          </section>
        </aside>
      </div>

      {dialog === 'approve' && (
        <AdminModal
          title={`Hoàn ${formatMoney(refund)} cho ${data.student.name}?`}
          size="sm"
          onClose={() => setDialog(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" onClick={() => setDialog(null)}>
                Quay lại
              </button>
              <button className="admin-button" type="button" disabled={busy} onClick={() => void approve()}>
                Xác nhận hoàn tiền
              </button>
            </div>
          }
        >
          <div className="admin-modal-pad admin-stack-gap">
            <div className="admin-key-values">
              <div>
                <span>Cộng vào ví học viên</span>
                <strong>{formatMoney(refund)}</strong>
              </div>
              {tutorShare > 0 && (
                <div>
                  <span>Cộng vào ví gia sư</span>
                  <strong>{formatMoney(tutorShare)}</strong>
                </div>
              )}
              <div>
                <span>Buổi bị huỷ</span>
                <strong>
                  {pendingDisputed.filter((lesson) => decisions[lesson.lessonId] !== 'TAUGHT').length} buổi
                </strong>
              </div>
            </div>
            <div className="admin-field">
              <label htmlFor="refund-note">Ghi chú</label>
              <textarea
                id="refund-note"
                rows={3}
                maxLength={2000}
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </div>
          </div>
        </AdminModal>
      )}

      {dialog === 'reject' && (
        <AdminModal
          title="Từ chối yêu cầu hoàn tiền"
          size="sm"
          onClose={() => setDialog(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" onClick={() => setDialog(null)}>
                Quay lại
              </button>
              <button
                className="admin-button danger"
                type="button"
                disabled={busy}
                onClick={() => {
                  setReasonTried(true);
                  if (!reason.trim()) return;
                  void run(() => rejectRefundRequest(data.id, reason.trim()), 'Đã từ chối yêu cầu.');
                }}
              >
                Xác nhận từ chối
              </button>
            </div>
          }
        >
          <div className="admin-modal-pad">
            <ReasonField
              id="refund-reject-reason"
              label="Lý do từ chối"
              value={reason}
              minLength={1}
              maxLength={500}
              showError={reasonTried}
              onChange={setReason}
            />
          </div>
        </AdminModal>
      )}
    </div>
  );
}
