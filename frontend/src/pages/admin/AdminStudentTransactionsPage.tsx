import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getAdminStudentTransactions, type TransactionFilters } from '../../services/adminStudentApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { useAdminStudent } from './adminStudentContext';
import { AdminPagination, AdminState, StatusBadge } from './components/AdminUi';
import {
  transactionMethodLabels,
  transactionSourceLabels,
  transactionStatus,
  transactionStatusMeta,
  transactionTypeLabels
} from './studentFormat';

type FilterKey = Exclude<keyof TransactionFilters, 'page'>;

const filterFields: { key: FilterKey; label: string; options: Record<string, string> }[] = [
  { key: 'source', label: 'Nguồn', options: transactionSourceLabels },
  { key: 'type', label: 'Loại', options: transactionTypeLabels },
  { key: 'method', label: 'Phương thức', options: transactionMethodLabels },
  {
    key: 'status',
    label: 'Trạng thái',
    options: Object.fromEntries(Object.entries(transactionStatusMeta).map(([key, meta]) => [key, meta.label]))
  }
];

export function AdminStudentTransactionsPage() {
  const { account } = useAdminStudent();
  const [filters, setFilters] = useState<TransactionFilters>({ page: 0 });
  const load = useCallback(
    (signal: AbortSignal) => getAdminStudentTransactions(account.userId, filters, signal),
    [account.userId, filters]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <section className="admin-card">
      <div className="admin-filters">
        {filterFields.map((field) => (
          <label key={field.key}>
            {field.label}
            <select
              value={filters[field.key] ?? ''}
              onChange={(event) =>
                setFilters((current) => ({ ...current, [field.key]: event.target.value || undefined, page: 0 }))
              }
            >
              <option value="">Tất cả</option>
              {Object.entries(field.options).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        ))}
      </div>
      {!data ? (
        <div className="admin-card-pad">
          <AdminState loading={isLoading} error={error} />
        </div>
      ) : data.content.length === 0 ? (
        <AdminState empty="Chưa có giao dịch nào." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Nguồn</th>
                <th>Loại</th>
                <th>Phương thức</th>
                <th>Mô tả</th>
                <th>Số tiền</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((transaction) => (
                <tr key={`${transaction.source}-${transaction.id}`}>
                  <td className="admin-nowrap">{formatDateTime(transaction.occurredAt)}</td>
                  <td>{transactionSourceLabels[transaction.source]}</td>
                  <td>{transactionTypeLabels[transaction.type] ?? transaction.type}</td>
                  <td>{transactionMethodLabels[transaction.method] ?? transaction.method}</td>
                  <td>{transaction.description || '—'}</td>
                  <td className="admin-nowrap admin-strong">{formatCurrency(transaction.amount)}</td>
                  <td>
                    <StatusBadge meta={transactionStatus(transaction.status)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && (
        <div className="admin-card-footer">
          <AdminPagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            unit="giao dịch"
            onChange={(page) => setFilters((current) => ({ ...current, page }))}
          />
        </div>
      )}
    </section>
  );
}
