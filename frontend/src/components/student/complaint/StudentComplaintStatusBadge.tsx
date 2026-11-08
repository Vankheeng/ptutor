import type { ComplaintStatus } from '../../../services/studentComplaintApi';
import { complaintStatusLabels } from './studentComplaintFormat';

export function StudentComplaintStatusBadge({ status }: { status: ComplaintStatus }) {
  return <span className={`student-complaint-status ${status.toLowerCase()}`}>{complaintStatusLabels[status]}</span>;
}
