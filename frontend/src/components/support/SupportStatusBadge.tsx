import type { SupportRequestStatus } from '../../types/supportRequest';
import { supportStatusMeta } from './supportFormat';
import '../refund/refund.css';

export function SupportStatusBadge({ status }: { status: SupportRequestStatus }) {
  const meta = supportStatusMeta[status];
  return <span className={`rf-badge rf-badge-${meta.tone}`}>{meta.label}</span>;
}
