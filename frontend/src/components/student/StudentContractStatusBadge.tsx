import type { ContractStatus } from '../../services/studentApi';

type Status = ContractStatus | 'PAID';

export function StudentContractStatusBadge({ status }: { status: Status }) {
  const labels: Record<Status, string> = {
    PENDING: 'Đang chờ',
    ACTIVE: 'Đang hoạt động',
    COMPLETED: 'Đã hoàn tất',
    CANCELLED: 'Đã hủy',
    PAID: 'Đã thanh toán'
  };
  return <span className={`student-status ${status.toLowerCase()}`}>{labels[status]}</span>;
}
