import type { StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import type { TutorTeachingProposal } from '../../../services/tutorApi';
import { tutorScheduleLabel, tutorStatusLabel } from '../../../utils/tutorDisplay';

const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

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
        aria-label="Mở chi tiết yêu cầu học"
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
          <span className="tutor-eyebrow">YÊU CẦU HỌC</span>
          <span className="tutor-subject-tag">{request?.subjectName || proposal.subjectName}</span>
        </div>
        <h2>{request?.title || proposal.studyingRequestTitle || `Yêu cầu học môn ${proposal.subjectName}`}</h2>
        <p className="tutor-proposal-grade">{request?.gradeName || proposal.gradeName}</p>
        <div className="tutor-highlight-grid">
          <Highlight
            icon="◷"
            label="Thời gian mong muốn"
            value={request?.preferredSchedule || availability(request) || 'Linh hoạt'}
          />
          <Highlight icon="▤" label="Lớp học" value={request?.gradeName || proposal.gradeName || 'Chưa xác định'} />
          <Highlight icon="₫" label="Ngân sách học viên" value={formatBudget(request?.minPrice, request?.maxPrice)} />
          {request && request.learningMode !== 'ONLINE' && (
            <Highlight
              icon="⌖"
              label="Địa chỉ"
              value={
                [request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                'Chưa cung cấp'
              }
            />
          )}
          <Highlight
            icon="▣"
            label="Hình thức học"
            value={request ? (request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp') : '—'}
          />
        </div>
        <div className="tutor-request-author">
          <span>
            Đăng bởi{' '}
            <strong>
              {[request?.studentFirstName, request?.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
            </strong>
          </span>
          <span>
            {request ? `Đăng ngày ${new Date(request.createdAt).toLocaleDateString('vi-VN')}` : 'Chưa có ngày đăng'}
          </span>
        </div>
      </section>
      <section className="tutor-proposal-my-side" aria-label="Đề xuất của tôi">
        <div className="tutor-proposal-application-heading">
          <span className="tutor-proposal-application-icon" aria-hidden="true">
            ↗
          </span>
          <div>
            <span className="tutor-eyebrow">ĐỀ XUẤT CỦA TÔI</span>
            <h3>Đề xuất giảng dạy</h3>
          </div>
          <span className={`tutor-cert-status status-${proposal.status.toLowerCase()}`}>
            {tutorStatusLabel(proposal.status)}
          </span>
        </div>
        <div className="tutor-proposal-offer-price">
          <small>Giá đề xuất</small>
          <strong>
            {new Intl.NumberFormat('vi-VN').format(proposal.proposedPrice)} <span>VNĐ</span>
          </strong>
        </div>
        <div className="tutor-proposal-summary tutor-proposal-my-summary">
          <span>
            <small>Hình thức dạy</small>
            <strong>{proposal.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</strong>
          </span>
          <span className="tutor-proposal-available-time">
            <small>Thời gian có thể dạy</small>
            <strong>{tutorScheduleLabel(proposal.preferredSchedule)}</strong>
          </span>
        </div>
        {proposal.teachingMode === 'OFFLINE' && (proposal.preferredProvinceName || proposal.preferredDistrictName) && (
          <div className="tutor-application-note tutor-proposal-address">
            <span className="tutor-application-note-tag">Địa chỉ dạy học</span>
            <p>{[proposal.preferredDistrictName, proposal.preferredProvinceName].filter(Boolean).join(', ')}</p>
          </div>
        )}
        {proposal.message?.trim() && (
          <div className="tutor-application-note tutor-proposal-message">
            <span className="tutor-application-note-tag">Lời nhắn</span>
            <p>{proposal.message}</p>
          </div>
        )}
        <div className="tutor-proposal-footer">
          <small>{proposal.nextStep || `Đã gửi ${new Date(proposal.createdAt).toLocaleDateString('vi-VN')}`}</small>
          {proposal.status === 'PENDING' && (
            <div className="tutor-proposal-actions">
              <button className="tutor-button tutor-proposal-edit" type="button" onClick={onEdit}>
                Sửa đề xuất <span aria-hidden="true">↗</span>
              </button>
              <button className="tutor-button secondary" type="button" disabled={cancelling} onClick={onCancel}>
                {cancelling ? 'Đang hủy…' : 'Hủy đề xuất'}
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
  return `${weekdays[first.dayOfWeek % 7]} ${first.startTime.slice(0, 5)}–${first.endTime.slice(0, 5)}${more > 0 ? ` +${more} more` : ''}`;
}

function formatBudget(min?: number | null, max?: number | null) {
  const format = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
  if ((min === null || min === undefined) && (max === null || max === undefined)) return 'Có thể thương lượng';
  if (min !== null && min !== undefined && max !== null && max !== undefined)
    return `${format(min)}–${format(max)} VNĐ`;
  return `${format(min ?? max ?? 0)} VNĐ`;
}
