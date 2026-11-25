import { useCallback, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminTransactions,
  getAdminTransactionSummary,
  type AdminTransactionFilters,
  type TransactionFlow
} from '../../services/adminTransactionApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { AdminPagination, AdminState, StatusBadge } from './components/AdminUi';
import {
  transactionMethodLabels,
  transactionSourceLabels,
  transactionStatus,
  transactionStatusMeta,
  transactionTypeLabels
} from './studentFormat';
import type { TransactionSource } from '../../services/adminStudentApi';
import { flowLabels } from './transactionFlow';
import { transactionPurposeLabels } from './transactionFormat';

const sources = Object.entries(transactionSourceLabels) as [TransactionSource, string][];

export function AdminTransactionsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [draftSearch, setDraftSearch] = useState(params.get('search') ?? '');
  const sourceParam = params.get('source');
  const flowParam = params.get('flow');
  const filters: AdminTransactionFilters = {
    page: Math.max(0, Number(params.get('page')) || 0),
    source: sources.find(([value]) => value === sourceParam)?.[0],
    flow: Object.keys(flowLabels).find((value) => value === flowParam) as TransactionFlow | undefined,
    type: params.get('type') || undefined,
    method: params.get('method') || undefined,
    status: params.get('status') || undefined,
    search: params.get('search') || undefined,
    fromDate: params.get('fromDate') || undefined,
    toDate: params.get('toDate') || undefined
  };
  const queryKey = params.toString();
  const load = useCallback(
    (signal: AbortSignal) => getAdminTransactions(filters, signal),
    // URL parameters are the canonical filter state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [queryKey]
  );
  const { data, error, isLoading } = useResource(load);
  const loadSummary = useCallback(
    (signal: AbortSignal) => getAdminTransactionSummary(filters, signal),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [queryKey]
  );
  const { data: summary, error: summaryError, isLoading: summaryLoading } = useResource(loadSummary);

  const update = (key: keyof AdminTransactionFilters, value: string | number) => {
    const next = new URLSearchParams(params);
    if (value === '' || value === 0) next.delete(key);
    else next.set(key, String(value));
    if (key !== 'page') next.delete('page');
    setParams(next);
  };
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    update('search', draftSearch.trim());
  };
  const reset = () => {
    setDraftSearch('');
    setParams(new URLSearchParams());
  };

  return (
    <div className="admin-page">
      <h1 className="admin-title">Lịch sử giao dịch</h1>
      {summary ? (
        <section className="admin-stats admin-transaction-summary" aria-label="Tổng hợp dòng tiền theo bộ lọc">
          <div className="admin-stat admin-stat-success">
            <span>Tiền vào</span>
            <strong>{formatCurrency(summary.moneyIn)}</strong>
          </div>
          <div className="admin-stat">
            <span>Tiền ra</span>
            <strong>{formatCurrency(summary.moneyOut)}</strong>
          </div>
          <div className="admin-stat admin-stat-brand">
            <span>Chênh lệch</span>
            <strong>{formatCurrency(summary.net)}</strong>
          </div>
          <div className="admin-stat admin-stat-neutral">
            <span>Giao dịch nội bộ</span>
            <strong>{summary.internalCount}</strong>
          </div>
        </section>
      ) : (
        <AdminState loading={summaryLoading} error={summaryError} />
      )}
      <section className="admin-card">
        <form className="admin-transaction-filters" onSubmit={submitSearch}>
          <div className="admin-transaction-search-row">
            <label>
              Tìm kiếm
              <input
                value={draftSearch}
                onChange={(event) => setDraftSearch(event.target.value)}
                placeholder="Tên, email hoặc mã giao dịch"
              />
            </label>
            <button className="admin-button" type="submit">
              Tìm kiếm
            </button>
            <button className="admin-button ghost" type="button" onClick={reset}>
              Xóa bộ lọc
            </button>
          </div>
          <div className="admin-transaction-filter-grid">
            <label>
              Dòng tiền
              <select value={filters.flow ?? ''} onChange={(event) => update('flow', event.target.value)}>
                <option value="">Tất cả</option>
                {Object.entries(flowLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nguồn
              <select value={filters.source ?? ''} onChange={(event) => update('source', event.target.value)}>
                <option value="">Tất cả</option>
                {sources.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Loại
              <select value={filters.type ?? ''} onChange={(event) => update('type', event.target.value)}>
                <option value="">Tất cả</option>
                {Object.entries(transactionTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Phương thức
              <select value={filters.method ?? ''} onChange={(event) => update('method', event.target.value)}>
                <option value="">Tất cả</option>
                {Object.entries(transactionMethodLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Trạng thái
              <select value={filters.status ?? ''} onChange={(event) => update('status', event.target.value)}>
                <option value="">Tất cả</option>
                {Object.entries(transactionStatusMeta).map(([value, meta]) => (
                  <option key={value} value={value}>
                    {meta.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Từ ngày
              <input
                type="date"
                value={filters.fromDate ?? ''}
                max={filters.toDate}
                onChange={(event) => update('fromDate', event.target.value)}
              />
            </label>
            <label>
              Đến ngày
              <input
                type="date"
                value={filters.toDate ?? ''}
                min={filters.fromDate}
                onChange={(event) => update('toDate', event.target.value)}
              />
            </label>
          </div>
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có giao dịch phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table admin-transaction-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Giao dịch</th>
                  <th>Người liên quan</th>
                  <th>Phân loại</th>
                  <th>Số tiền</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map(({ userName, userEmail, purpose, flow, transaction }) => {
                  const detailPath = `/admin/transactions/${transaction.source}/${transaction.id}`;
                  const transactionMeta = [
                    transactionSourceLabels[transaction.source],
                    transaction.source === 'PAYMENT'
                      ? (transactionMethodLabels[transaction.method] ?? transaction.method)
                      : null,
                    `#${transaction.id.slice(-8)}`
                  ]
                    .filter(Boolean)
                    .join(' ');
                  return (
                    <tr
                      key={`${transaction.source}-${transaction.id}`}
                      className="admin-transaction-row"
                      role="link"
                      tabIndex={0}
                      aria-label={`Xem giao dịch ${transactionPurposeLabels[purpose] ?? purpose} của ${userName || userEmail}`}
                      onClick={() => navigate(detailPath)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          navigate(detailPath);
                        }
                      }}
                    >
                      <td>{formatDateTime(transaction.occurredAt)}</td>
                      <td>
                        <div className="admin-stack">
                          <strong>{transactionPurposeLabels[purpose] ?? purpose}</strong>
                          <span title={transaction.id}>{transactionMeta}</span>
                        </div>
                      </td>
                      <td>
                        <div className="admin-stack">
                          <strong>{userName || userEmail}</strong>
                          <span>{userEmail}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`admin-transaction-flow admin-transaction-flow-${flow.toLowerCase()}`}>
                          {flowLabels[flow]}
                        </span>
                      </td>
                      <td className="admin-nowrap admin-strong">{formatCurrency(transaction.amount)}</td>
                      <td>
                        <StatusBadge meta={transactionStatus(transaction.status)} />
                      </td>
                    </tr>
                  );
                })}
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
              onChange={(page) => update('page', page)}
            />
          </div>
        )}
      </section>
    </div>
  );
}
