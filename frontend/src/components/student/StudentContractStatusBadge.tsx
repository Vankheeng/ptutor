import type { ContractStatus } from '../../services/studentApi';

type Status = ContractStatus | 'PAID';

export function StudentContractStatusBadge({ status }: { status: Status }) {
  return <span className={`student-status ${status.toLowerCase()}`}>{status.toLowerCase().replaceAll('_', ' ')}</span>;
}
