import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import type { TransactionSource } from '../../services/adminStudentApi';
import { getAdminTransaction } from '../../services/adminTransactionApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { AdminState, DefinitionGrid, StatusBadge } from './components/AdminUi';
import { transactionMethodLabels, transactionSourceLabels, transactionStatus, transactionTypeLabels } from './studentFormat';
import { flowLabels } from './transactionFlow';

const sources: TransactionSource[] = ['PAYMENT', 'WALLET_TRANSACTION', 'WITHDRAWAL'];

export function AdminTransactionDetailPage() {
  const { source, transactionId } = useParams();
  const validSource = sources.find((value) => value === source);
  const load = useCallback(
    (signal: AbortSignal) => {
      if (!validSource || !transactionId) return Promise.reject(new Error('Mã giao dịch không hợp lệ.'));
      return getAdminTransaction(validSource, transactionId, signal);
    },
    [validSource, transactionId]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/transactions">← Lịch sử giao dịch</Link>
      <h1 className="admin-title">Chi tiết giao dịch</h1>
      {!data ? <section className="admin-card admin-card-pad"><AdminState loading={isLoading} error={error} /></section> : (
        <section className="admin-card admin-card-pad">
          <DefinitionGrid items={[
            ['Mã giao dịch', data.transaction.id],
            ['Người dùng', <span key="user">{data.userName || data.userEmail}<br />{data.userEmail}</span>],
            ['Thời gian', formatDateTime(data.transaction.occurredAt)],
            ['Nguồn', transactionSourceLabels[data.transaction.source]],
            ['Nghiệp vụ', data.purpose],
            ['Dòng tiền nền tảng', flowLabels[data.flow]],
            ['Loại', transactionTypeLabels[data.transaction.type] ?? data.transaction.type],
            ['Phương thức', transactionMethodLabels[data.transaction.method] ?? data.transaction.method],
            ['Số tiền', formatCurrency(data.transaction.amount)],
            ['Trạng thái', <StatusBadge key="status" meta={transactionStatus(data.transaction.status)} />],
            ['Loại tham chiếu', data.transaction.referenceType || '—'],
            ['Mã tham chiếu', data.transaction.referenceId || '—'],
            ['Mô tả', data.transaction.description || '—']
          ]} />
        </section>
      )}
    </div>
  );
}
