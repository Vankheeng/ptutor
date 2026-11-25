import { useCallback, useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  cancelRefundRequest,
  getMyRefundRequest,
  type AbsenceOutcome,
  type RefundEvidence,
  type RefundRequest,
  type RefundRequestStatus
} from '../../services/refundApi';
import { RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import {
  evidenceName,
  formatClockTime,
  formatLocalDateTime,
  formatMoney,
  refundErrorMessage
} from '../../components/refund/refundFormat';
import { Icon } from '../../components/ui/Icon';
import '../../components/refund/refund.css';

const claimLabels: Record<AbsenceOutcome, string> = {
  TUTOR_ABSENT: 'Gia sư vắng buổi học',
  STUDENT_ABSENT: 'Học viên vắng buổi học',
  TUTOR_LATE_OR_SHORT: 'Gia sư dạy muộn hoặc thiếu giờ'
};

const statusDescriptions: Record<RefundRequestStatus, string> = {
  PENDING: 'Yêu cầu đang chờ bộ phận phụ trách tiếp nhận.',
  IN_REVIEW: 'Yêu cầu đang được nhân viên phụ trách xem xét.',
  REFUNDED: 'Yêu cầu đã được duyệt và tiền hoàn đã được ghi nhận.',
  REJECTED: 'Yêu cầu đã được xem xét và không đủ điều kiện hoàn tiền.',
  CANCELLED: 'Bạn đã hủy yêu cầu hoàn tiền này.'
};

function contractCode(contractId: string) {
  return `HD-${contractId.slice(0, 8).toUpperCase()}`;
}

function formatLessonDay(value: string | undefined) {
  if (!value) return 'Chưa cập nhật';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0).toLocaleUpperCase('vi-VN'))
    .join('');
}

function evidenceTypeLabel(fileType: string) {
  const labels: Record<string, string> = {
    'application/pdf': 'Tài liệu PDF',
    'image/jpeg': 'Ảnh JPEG',
    'image/png': 'Ảnh PNG',
    'image/webp': 'Ảnh WEBP',
    'video/mp4': 'Video MP4',
    'video/webm': 'Video WEBM',
    'text/plain': 'Tệp văn bản',
    'application/msword': 'Tài liệu Word',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'Tài liệu Word'
  };
  return labels[fileType] || fileType || 'Tệp minh chứng';
}

function StudentRefundEvidenceList({ items }: { items: RefundEvidence[] }) {
  if (items.length === 0) {
    return <p className="student-refund-detail-empty-copy">Chưa có minh chứng được đính kèm.</p>;
  }

  return (
    <div className="student-refund-detail-evidence-list">
      {items.map((item) => (
        <article className="student-refund-detail-evidence" key={item.id}>
          <a
            className="student-refund-detail-evidence-preview"
            href={item.fileUrl}
            target="_blank"
            rel="noreferrer"
            aria-label={`Xem ${evidenceName(item)}`}
          >
            {item.fileType.startsWith('image/') ? (
              <img src={item.fileUrl} alt="" />
            ) : (
              <Icon name="contract" size={25} />
            )}
          </a>
          <span className="student-refund-detail-evidence-copy">
            <strong>{evidenceName(item)}</strong>
            <small>{evidenceTypeLabel(item.fileType)}</small>
          </span>
          <a className="student-refund-detail-download" href={item.fileUrl} target="_blank" rel="noreferrer" download>
            <Icon name="send" size={14} /> Tải xuống
          </a>
        </article>
      ))}
    </div>
  );
}

function StudentRefundInformation({ request }: { request: RefundRequest }) {
  const firstLesson = request.disputedLessons[0];
  const claimTypes = Array.from(new Set(request.disputedLessons.map((lesson) => lesson.claimedOutcome)));
  const lessonLabel = firstLesson?.title || 'Buổi học yêu cầu hoàn tiền';
  const multipleLessons = request.disputedLessons.length > 1;

  return (
    <section className="student-refund-detail-card student-refund-detail-information">
      <h2 className="student-refund-detail-card-title">
        <Icon name="contract" size={18} /> Thông tin yêu cầu
      </h2>
      <div className="student-refund-detail-information-body">
        <span className="student-refund-detail-main-icon" aria-hidden="true">
          <Icon name="refund" size={34} />
        </span>
        <div className="student-refund-detail-information-copy">
          <div className="student-refund-detail-claim-badges">
            {claimTypes.map((claim) => (
              <span key={claim}>{claimLabels[claim]}</span>
            ))}
          </div>
          <h3>{multipleLessons ? `Yêu cầu hoàn tiền ${request.disputedLessons.length} buổi học` : lessonLabel}</h3>
          <div className="student-refund-detail-facts">
            <div>
              <small>Hợp đồng</small>
              <strong>{contractCode(request.contractId)}</strong>
            </div>
            <div>
              <small>Môn học</small>
              <strong>{request.subjectName || 'Chưa cập nhật'}</strong>
            </div>
            <div>
              <small>Lớp</small>
              <strong>{request.gradeName || 'Chưa cập nhật'}</strong>
            </div>
            <div>
              <small>Buổi học</small>
              <strong>{multipleLessons ? `${request.disputedLessons.length} buổi học` : lessonLabel}</strong>
            </div>
            <div>
              <small>Ngày học</small>
              <strong>{formatLessonDay(firstLesson?.date)}</strong>
            </div>
            <div>
              <small>Thời gian</small>
              <strong>
                {firstLesson
                  ? `${firstLesson.startTime.slice(0, 5)} – ${firstLesson.endTime.slice(0, 5)}`
                  : 'Chưa cập nhật'}
              </strong>
            </div>
          </div>
        </div>
      </div>
      <div className="student-refund-detail-participants">
        <div>
          <span className="student-refund-detail-avatar">{initials(request.tutorName) || '?'}</span>
          <span>
            <small>Gia sư</small>
            <strong>{request.tutorName}</strong>
          </span>
        </div>
        <div>
          <span className="student-refund-detail-avatar">{initials(request.studentName) || '?'}</span>
          <span>
            <small>Học viên</small>
            <strong>{request.studentName}</strong>
          </span>
        </div>
        <div>
          <Icon name="calendar" size={18} />
          <span>
            <small>Ngày tạo yêu cầu</small>
            <strong>{formatLocalDateTime(request.createdAt)}</strong>
          </span>
        </div>
      </div>
    </section>
  );
}

function StudentRefundStatus({ request }: { request: RefundRequest }) {
  return (
    <section className="student-refund-detail-card student-refund-detail-status-card">
      <h2 className="student-refund-detail-card-title">
        <Icon name="award" size={18} /> Trạng thái
      </h2>
      <div className="student-refund-detail-status-badge">
        <RefundStatusBadge status={request.status} />
      </div>
      <p>{statusDescriptions[request.status]}</p>
      {request.status === 'REFUNDED' && request.refundAmount !== null && (
        <strong className="student-refund-detail-refunded-amount">Đã hoàn: {formatMoney(request.refundAmount)}</strong>
      )}
      {request.status === 'REJECTED' && request.rejectionReason && (
        <div className="student-refund-detail-rejection">Lý do: {request.rejectionReason}</div>
      )}
    </section>
  );
}

function StudentRefundTimeline({ request }: { request: RefundRequest }) {
  const reviewReached = ['IN_REVIEW', 'REFUNDED', 'REJECTED'].includes(request.status);
  const finalReached = ['REFUNDED', 'REJECTED', 'CANCELLED'].includes(request.status);
  const reviewActive = request.status === 'PENDING' || request.status === 'IN_REVIEW';
  const resultDescription =
    request.status === 'REFUNDED'
      ? `Đã hoàn ${formatMoney(request.refundAmount)} cho học viên.`
      : request.status === 'REJECTED'
        ? 'Yêu cầu không được chấp thuận.'
        : request.status === 'CANCELLED'
          ? 'Yêu cầu đã được học viên hủy.'
          : 'Chưa có kết quả.';

  return (
    <section className="student-refund-detail-card student-refund-detail-progress">
      <h2 className="student-refund-detail-card-title">
        <Icon name="history" size={18} /> Tiến trình xử lý
      </h2>
      <div className="student-refund-detail-timeline">
        <div className="done">
          <span>✓</span>
          <div>
            <strong>Đã gửi yêu cầu</strong>
            <small>{formatLocalDateTime(request.createdAt)}</small>
            <p>Bạn đã gửi yêu cầu hoàn tiền.</p>
          </div>
        </div>
        <div className={`${reviewReached ? 'done' : ''}${reviewActive ? ' active' : ''}`}>
          <span>{reviewReached ? '✓' : '2'}</span>
          <div>
            <strong>Đang xem xét</strong>
            <small>{request.reviewedAt ? formatClockTime(request.reviewedAt) : 'Đang chờ cập nhật'}</small>
            <p>
              {reviewReached
                ? 'Yêu cầu đã được bộ phận phụ trách xem xét.'
                : request.status === 'PENDING'
                  ? 'Yêu cầu đang chờ được tiếp nhận.'
                  : 'Bước xử lý chưa bắt đầu.'}
            </p>
          </div>
        </div>
        <div className={finalReached ? 'done' : ''}>
          <span>{finalReached ? '✓' : '3'}</span>
          <div>
            <strong>Kết quả xử lý</strong>
            <p>{resultDescription}</p>
          </div>
        </div>
      </div>
    </section>
  );
}

export function StudentRefundRequestDetailPage() {
  const { refundRequestId = '' } = useParams();
  const location = useLocation();
  const created = Boolean((location.state as { created?: boolean } | null)?.created);
  const [revision, setRevision] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(created ? 'Đã gửi yêu cầu hoàn tiền.' : null);
  const [busy, setBusy] = useState(false);

  const load = useCallback((signal: AbortSignal) => getMyRefundRequest(refundRequestId, signal), [refundRequestId]);
  const { data, error, isLoading } = useResource(load, revision);
  const studentEvidence = useMemo(
    () => data?.evidences.filter((item) => item.uploadedByUserId === data.studentUserId) ?? [],
    [data]
  );
  const tutorEvidence = useMemo(
    () => data?.evidences.filter((item) => item.uploadedByUserId === data.tutorUserId) ?? [],
    [data]
  );

  const cancel = async () => {
    setBusy(true);
    setFailure(null);
    try {
      await cancelRefundRequest(refundRequestId);
      setNotice('Đã hủy yêu cầu hoàn tiền.');
      setRevision((value) => value + 1);
    } catch (cancelError) {
      setFailure(refundErrorMessage(cancelError));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rf-page student-refund-detail-page">
      <Link className="student-refund-detail-back" to="/student/refund-requests">
        ← Quay lại danh sách yêu cầu
      </Link>
      {notice && (
        <div className="rf-alert rf-alert-success" role="status">
          {notice}
        </div>
      )}
      {failure && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {failure}
        </div>
      )}
      {isLoading && !data && <div className="rf-card rf-state">Đang tải chi tiết yêu cầu hoàn tiền…</div>}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}
      {data && (
        <>
          <header className="student-refund-detail-header">
            <h1>Chi tiết yêu cầu hoàn tiền</h1>
            {data.status === 'PENDING' && (
              <button
                className="student-refund-detail-cancel"
                type="button"
                disabled={busy}
                onClick={() => void cancel()}
              >
                <Icon name="close" size={15} /> Hủy yêu cầu
              </button>
            )}
          </header>

          <div className="student-refund-detail-layout">
            <main className="student-refund-detail-main">
              <StudentRefundInformation request={data} />

              <section className="student-refund-detail-card student-refund-detail-content">
                <h2 className="student-refund-detail-card-title">
                  <Icon name="complaint" size={18} /> Nội dung yêu cầu hoàn tiền
                </h2>
                <p>{data.description}</p>
              </section>

              <section className="student-refund-detail-card student-refund-detail-evidence-card">
                <h2 className="student-refund-detail-card-title">
                  <Icon name="send" size={18} /> Minh chứng ({studentEvidence.length})
                </h2>
                <StudentRefundEvidenceList items={studentEvidence} />
              </section>

              <section className="student-refund-detail-card student-refund-detail-tutor-response">
                <div className="student-refund-detail-response-heading">
                  <h2 className="student-refund-detail-card-title">
                    <Icon name="complaint" size={18} /> Phản hồi của gia sư
                  </h2>
                  <span>
                    <Icon name="history" size={14} /> Hạn phản hồi: {formatClockTime(data.tutorResponseDeadline)}
                  </span>
                </div>
                {data.tutorResponse ? (
                  <>
                    <p>{data.tutorResponse}</p>
                    {data.tutorRespondedAt && <small>Phản hồi lúc {formatClockTime(data.tutorRespondedAt)}</small>}
                    {tutorEvidence.length > 0 && <StudentRefundEvidenceList items={tutorEvidence} />}
                  </>
                ) : (
                  <p className="student-refund-detail-empty-copy">Gia sư chưa gửi phản hồi.</p>
                )}
              </section>
            </main>

            <aside className="student-refund-detail-sidebar">
              <StudentRefundStatus request={data} />
              <StudentRefundTimeline request={data} />
            </aside>
          </div>
        </>
      )}
    </div>
  );
}
