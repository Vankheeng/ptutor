import type { ContractStatus } from '../../services/studentApi';

type Status = ContractStatus | 'PAID';

interface Props {
  status: Status;
  label?: string;
  variant?: 'default' | 'outgoing';
}

export function StudentContractStatusBadge({ status, label, variant = 'default' }: Props) {
  const labels: Record<Status, string> = {
    PENDING: 'Đang chờ',
    ACTIVE: 'Đang hoạt động',
    COMPLETED: 'Đã hoàn tất',
    CANCELLED: 'Đã hủy',
    PAID: 'Đã thanh toán'
  };
  return <span className={`student-status ${status.toLowerCase()} ${variant}`}>{label ?? labels[status]}</span>;
}
