import type { ComplaintStatus } from '../../../services/studentComplaintApi';
import { complaintStatusLabels } from './complaintFormat';

export function ComplaintStatusBadge({ status }: { status: ComplaintStatus }) {
  return <span className={`student-complaint-status ${status.toLowerCase()}`}>{complaintStatusLabels[status]}</span>;
}
