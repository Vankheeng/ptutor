import type { Contract } from '../../../services/studentApi';
import type { StudentComplaint } from '../../../services/studentComplaintApi';
import { complaintCategoryLabels, formatComplaintDate } from './complaintFormat';
import { ComplaintStatusBadge } from './ComplaintStatusBadge';
import { Icon, type IconName } from '../../ui/Icon';

interface Props {
  complaint: StudentComplaint;
  mode: 'submitted' | 'received';
  contract: Contract | null;
  role?: 'student' | 'tutor';
  cancelling?: boolean;
  onOpen: () => void;
  onEdit?: () => void;
  onCancel?: () => void;
}

export function ComplaintCard({
  complaint,
  mode,
  contract,
  role = 'student',
  cancelling = false,
  onOpen,
  onEdit,
  onCancel
}: Props) {
  const categoryIcon: IconName =
    complaint.category === 'TEACHING_QUALITY'
      ? 'book'
      : complaint.category === 'MISCONDUCT' || complaint.category === 'SERIOUS_MISCONDUCT'
        ? 'shield'
        : complaint.category === 'OTHER'
          ? 'support'
          : 'calendar';
  const otherName = contract
    ? role === 'tutor'
      ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || contract.tutorEmail
      : [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || contract.studentEmail
    : 'Hợp đồng liên quan';

  return (
    <article
      className="student-complaint-card"
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpen();
        }
      }}
    >
      <div className={`student-complaint-card-icon ${complaint.category.toLowerCase()}`}>
        <Icon name={categoryIcon} size={24} />
      </div>
      <div className="student-complaint-card-main">
        <div className="student-complaint-card-heading">
          <span className="student-complaint-category">{complaintCategoryLabels[complaint.category]}</span>
        </div>
        <h2>{complaint.title}</h2>
        <div className="student-complaint-card-line">
          <span>
            <Icon name="user" size={14} />
            {otherName}
          </span>
          <span>
            <Icon name="contract" size={14} />
            Hợp đồng:{' '}
            {contract ? `${contract.subjectName} · ${contract.gradeName}` : `#${complaint.contractId.slice(0, 8)}`}
          </span>
        </div>
        <div className="student-complaint-card-line created">
          <span>
            <Icon name="calendar" size={14} />
            Tạo lúc: {formatComplaintDate(complaint.createdAt)}
          </span>
        </div>
      </div>
      <div className="student-complaint-card-right">
        <ComplaintStatusBadge status={complaint.status} />
        {mode === 'submitted' && complaint.status === 'PENDING' ? (
          <div className="student-complaint-card-actions">
            <button
              className="student-button secondary"
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onEdit?.();
              }}
            >
              Chỉnh sửa
            </button>
            <button
              className="student-button danger"
              type="button"
              disabled={cancelling}
              onClick={(event) => {
                event.stopPropagation();
                onCancel?.();
              }}
            >
              {cancelling ? 'Đang hủy…' : 'Hủy'}
            </button>
          </div>
        ) : (
          <button
            className="student-button secondary student-complaint-view-button"
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onOpen();
            }}
          >
            Xem chi tiết
          </button>
        )}
      </div>
    </article>
  );
}
