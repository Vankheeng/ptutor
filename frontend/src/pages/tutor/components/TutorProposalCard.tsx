import type { StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import type { TutorTeachingProposal } from '../../../services/tutorApi';

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
        aria-label="Open student request details"
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
          <span className="tutor-eyebrow">STUDENT REQUEST</span>
          <span className="tutor-subject-tag">{request?.subjectName || proposal.subjectName}</span>
        </div>
        <h2>{request?.title || proposal.studyingRequestTitle || `${proposal.subjectName} learning request`}</h2>
        <p className="tutor-proposal-grade">{request?.gradeName || proposal.gradeName}</p>
        <div className="tutor-highlight-grid">
          <Highlight
            icon="◷"
            label="Requested time"
            value={request?.preferredSchedule || availability(request) || 'Flexible'}
          />
          <Highlight icon="₫" label="Student budget" value={formatBudget(request?.minPrice, request?.maxPrice)} />
          {request && request.learningMode !== 'ONLINE' && (
            <Highlight
              icon="⌖"
              label="Address"
              value={
                [request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                'Not specified'
              }
            />
          )}
          <Highlight
            icon="▣"
            label="Learning mode"
            value={request ? (request.learningMode === 'ONLINE' ? 'Online' : 'In person') : '—'}
          />
        </div>
        <div className="tutor-request-author">
          <span>
            Posted by{' '}
            <strong>
              {[request?.studentFirstName, request?.studentLastName].filter(Boolean).join(' ') || 'Student'}
            </strong>
          </span>
          <span>
            {request ? `Posted ${new Date(request.createdAt).toLocaleDateString('vi-VN')}` : 'Post date unavailable'}
          </span>
        </div>
      </section>
      <section className="tutor-proposal-my-side" aria-label="My application">
        <div className="tutor-proposal-application-heading">
          <span className="tutor-proposal-application-icon" aria-hidden="true">
            ↗
          </span>
          <div>
            <span className="tutor-eyebrow">MY APPLICATION</span>
            <h3>Your proposal</h3>
          </div>
          <span className={`tutor-cert-status status-${proposal.status.toLowerCase()}`}>{label(proposal.status)}</span>
        </div>
        <div className="tutor-proposal-offer-price">
          <small>My proposed price</small>
          <strong>
            {new Intl.NumberFormat('vi-VN').format(proposal.proposedPrice)} <span>VND</span>
          </strong>
        </div>
        <div className="tutor-proposal-summary tutor-proposal-my-summary">
          <span>
            <small>Teaching mode</small>
            <strong>{proposal.teachingMode === 'ONLINE' ? 'Online' : 'In person'}</strong>
          </span>
          <span className="tutor-proposal-available-time">
            <small>Available time</small>
            <strong>{proposal.preferredSchedule || 'Flexible'}</strong>
          </span>
        </div>
        {proposal.teachingMode === 'OFFLINE' && (proposal.preferredProvinceName || proposal.preferredDistrictName) && (
          <div className="tutor-application-note tutor-proposal-address">
            <span className="tutor-application-note-tag">Teaching address</span>
            <p>{[proposal.preferredDistrictName, proposal.preferredProvinceName].filter(Boolean).join(', ')}</p>
          </div>
        )}
        {proposal.message?.trim() && (
          <div className="tutor-application-note tutor-proposal-message">
            <span className="tutor-application-note-tag">Note</span>
            <p>{proposal.message}</p>
          </div>
        )}
        <div className="tutor-proposal-footer">
          <small>{proposal.nextStep || `Sent ${new Date(proposal.createdAt).toLocaleDateString()}`}</small>
          {proposal.status === 'PENDING' && (
            <div className="tutor-proposal-actions">
              <button className="tutor-button tutor-proposal-edit" type="button" onClick={onEdit}>
                Edit application <span aria-hidden="true">↗</span>
              </button>
              <button className="tutor-button secondary" type="button" disabled={cancelling} onClick={onCancel}>
                {cancelling ? 'Cancelling…' : 'Cancel proposal'}
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
  if ((min === null || min === undefined) && (max === null || max === undefined)) return 'Negotiable';
  if (min !== null && min !== undefined && max !== null && max !== undefined)
    return `${format(min)}–${format(max)} VND`;
  return `${format(min ?? max ?? 0)} VND`;
}

function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
