import type { StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import type { TutorTeachingProposal } from '../../../services/tutorApi';
import { dateLabel, modeLabel, statusLabel, weekdayLabel } from '../../../utils/labels';

interface TutorProposalCardProps {
  proposal: TutorTeachingProposal;
  request?: StudyingRequestSearchResponse;
  cancelling: boolean;
  onCancel: () => void;
  onOpenRequest: () => void;
  onEdit: () => void;
}

export function TutorProposalCard({
  proposal,
  request,
  cancelling,
  onCancel,
  onOpenRequest,
  onEdit
}: TutorProposalCardProps) {
  return (
    <article className="tutor-panel tutor-proposal-card tutor-proposal-split">
      <section
        className="tutor-proposal-request-side"
        aria-label="Mở chi tiết tin của học viên"
        role="button"
        tabIndex={0}
        onClick={onOpenRequest}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onOpenRequest();
          }
        }}
      >
        <div className="tutor-proposal-side-heading">
          <span className="tutor-eyebrow">TIN CỦA HỌC VIÊN</span>
          <span className="tutor-subject-tag">{request?.subjectName || proposal.subjectName}</span>
        </div>
        <h2>{request?.title || proposal.studyingRequestTitle || `Tìm gia sư ${proposal.subjectName}`}</h2>
        <p className="tutor-proposal-grade">{request?.gradeName || proposal.gradeName}</p>
        <div className="tutor-highlight-grid">
          <Highlight
            icon="◷"
            label="Lịch học"
            value={request?.preferredSchedule || availability(request) || 'Linh hoạt'}
          />
          <Highlight
            icon="₫"
            label="Học phí học viên đưa ra"
            value={formatBudget(request?.minPrice, request?.maxPrice)}
          />
          {request && request.learningMode !== 'ONLINE' && (
            <Highlight
              icon="⌖"
              label="Địa chỉ"
              value={
                [request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                'Chưa ghi'
              }
            />
          )}
          <Highlight icon="▣" label="Hình thức học" value={request ? modeLabel(request.learningMode) : '—'} />
        </div>
        <div className="tutor-request-author">
          <span>
            Người đăng{' '}
            <strong>
              {[request?.studentFirstName, request?.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
            </strong>
          </span>
          <span>{request ? `Ngày đăng ${dateLabel(request.createdAt)}` : ''}</span>
        </div>
      </section>
      <section className="tutor-proposal-my-side" aria-label="Đề nghị của tôi">
        <div className="tutor-proposal-application-heading">
          <span className="tutor-proposal-application-icon" aria-hidden="true">
            ↗
          </span>
          <div>
            <span className="tutor-eyebrow">ĐỀ NGHỊ CỦA TÔI</span>
            <h3>Đề nghị của bạn</h3>
          </div>
          <span className={`tutor-cert-status status-${proposal.status.toLowerCase()}`}>{label(proposal.status)}</span>
        </div>
        <div className="tutor-proposal-offer-price">
          <small>Học phí đề nghị</small>
          <strong>
            {new Intl.NumberFormat('vi-VN').format(proposal.proposedPrice)} <span>đ</span>
          </strong>
        </div>
        <div className="tutor-proposal-summary tutor-proposal-my-summary">
          <span>
            <small>Hình thức dạy</small>
            <strong>{modeLabel(proposal.teachingMode)}</strong>
          </span>
          <span className="tutor-proposal-available-time">
            <small>Thời gian có thể dạy</small>
            <strong>{proposal.preferredSchedule || 'Linh hoạt'}</strong>
          </span>
        </div>
        {proposal.teachingMode === 'OFFLINE' && (proposal.preferredProvinceName || proposal.preferredDistrictName) && (
          <div className="tutor-application-note tutor-proposal-address">
            <span className="tutor-application-note-tag">Địa chỉ dạy</span>
            <p>{[proposal.preferredDistrictName, proposal.preferredProvinceName].filter(Boolean).join(', ')}</p>
          </div>
        )}
        {proposal.message?.trim() && (
          <div className="tutor-application-note tutor-proposal-message">
            <span className="tutor-application-note-tag">Ghi chú</span>
            <p>{proposal.message}</p>
          </div>
        )}
        <div className="tutor-proposal-footer">
          <small>
            {proposal.nextStep === 'CONTRACT_SIGNING'
              ? 'Bước tiếp theo: ký hợp đồng'
              : `Gửi ngày ${dateLabel(proposal.createdAt)}`}
          </small>
          {proposal.status === 'PENDING' && (
            <div className="tutor-proposal-actions">
              <button className="tutor-button tutor-proposal-edit" type="button" onClick={onEdit}>
                Sửa đề nghị <span aria-hidden="true">↗</span>
              </button>
              <button className="tutor-button secondary" type="button" disabled={cancelling} onClick={onCancel}>
                {cancelling ? 'Đang rút…' : 'Rút đề nghị'}
              </button>
            </div>
          )}
        </div>
      </section>
    </article>
  );
}

function Highlight({ icon, label: title, value }: { icon: string; label: string; value: string }) {
  return (
    <div className="tutor-proposal-highlight">
      <span aria-hidden="true">{icon}</span>
      <div>
        <small>{title}</small>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

function availability(request?: StudyingRequestSearchResponse) {
  const first = request?.availabilities[0];
  if (!first) return '';
  const more = (request?.availabilities.length ?? 1) - 1;
  return `${weekdayLabel(first.dayOfWeek)} ${first.startTime.slice(0, 5)}–${first.endTime.slice(0, 5)}${more > 0 ? ` +${more} buổi` : ''}`;
}

function formatBudget(min?: number | null, max?: number | null) {
  const format = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
  if ((min === null || min === undefined) && (max === null || max === undefined)) return 'Thoả thuận';
  if (min !== null && min !== undefined && max !== null && max !== undefined) return `${format(min)}–${format(max)} đ`;
  return `${format(min ?? max ?? 0)} đ`;
}

function label(value: string) {
  return statusLabel(value, 'application');
}
