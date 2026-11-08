import { useCallback, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  cancelMyWithdrawal,
  getFinancialTransactions,
  type FinancialTransactionFilters
} from '../../services/studentApi';
import { ApiError } from '../../services/httpClient';
import { useResource } from '../../hooks/useResource';
import { TransactionFilters } from './transactions/TransactionFilters';
import { TransactionSummary } from './transactions/TransactionSummary';
import { TransactionTable } from './transactions/TransactionTable';
import { transactionLabels, transactionStatusLabels } from './transactions/transactionLabels';
import './wallet.css';
import './transactions/transactions.css';

const PAGE_SIZES = [10, 20, 50];

function toDateInputValue(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function defaultDateRange() {
  const to = new Date();
  const from = new Date(to);
  from.setMonth(from.getMonth() - 1);
  return { fromDate: toDateInputValue(from), toDate: toDateInputValue(to) };
}

export function WalletTransactionsPage() {
  const location = useLocation();
  const basePath = location.pathname.startsWith('/tutor/') ? '/tutor' : '/student';
  const [filters, setFilters] = useState<FinancialTransactionFilters>({});
  const [search, setSearch] = useState('');
  const [fromDate, setFromDate] = useState(() => defaultDateRange().fromDate);
  const [toDate, setToDate] = useState(() => defaultDateRange().toDate);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [cancellingWithdrawalId, setCancellingWithdrawalId] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState('');
  const load = useCallback(
    (signal: AbortSignal) => getFinancialTransactions(page, pageSize, signal, { ...filters, fromDate, toDate }),
    [filters, fromDate, page, pageSize, toDate]
  );
  const result = useResource(load, refreshKey);

  const transactions = useMemo(
    () =>
      (result.data?.content ?? []).filter((item) => {
        const query = search.trim().toLocaleLowerCase('vi-VN');
        const searchable =
          `${item.id} ${item.description ?? ''} ${item.type} ${item.method} ${item.source} ${item.status}`.toLocaleLowerCase(
            'vi-VN'
          );
        return !query || searchable.includes(query);
      }),
    [result.data, search]
  );

  const changeFilters = (next: FinancialTransactionFilters) => {
    setFilters(next);
    setPage(0);
  };
  const changeSearch = (value: string) => {
    setSearch(value);
    setPage(0);
  };
  const changeFromDate = (value: string) => {
    setFromDate(value);
    setPage(0);
  };
  const changeToDate = (value: string) => {
    setToDate(value);
    setPage(0);
  };
  const refresh = () => setRefreshKey((value) => value + 1);
  const cancelWithdrawal = async (withdrawalId: string) => {
    setCancellingWithdrawalId(withdrawalId);
    setCancelError('');
    try {
      await cancelMyWithdrawal(withdrawalId);
      refresh();
    } catch (error) {
      setCancelError(
        error instanceof ApiError ? error.message : 'Không thể thu hồi yêu cầu rút tiền. Vui lòng thử lại.'
      );
    } finally {
      setCancellingWithdrawalId(null);
    }
  };
  const clearFilters = () => {
    setFilters({});
    setSearch('');
    const dateRange = defaultDateRange();
    setFromDate(dateRange.fromDate);
    setToDate(dateRange.toDate);
    setPage(0);
  };
  const changePageSize = (size: number) => {
    setPageSize(size);
    setPage(0);
  };
  const totalPages = result.data?.totalPages ?? 0;
  const pageNumbers = Array.from({ length: totalPages }, (_, index) => index).filter(
    (pageNumber) => pageNumber === 0 || pageNumber === totalPages - 1 || Math.abs(pageNumber - page) <= 1
  );
  const exportCsv = () => {
    const lines = [
      ['Mã giao dịch', 'Thời gian', 'Loại giao dịch', 'Nội dung', 'Phương thức', 'Trạng thái', 'Số tiền'],
      ...transactions.map((item) => {
        const topUp =
          item.type === 'WALLET_TOP_UP' ||
          (item.source === 'WALLET_TRANSACTION' &&
            item.type === 'CREDIT' &&
            item.description?.toLowerCase().includes('top-up'));
        const credit = (item.source === 'WALLET_TRANSACTION' && item.type === 'CREDIT') || topUp;
        const status = item.status.toUpperCase();
        const date = new Date(item.occurredAt).toLocaleString('vi-VN', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit'
        });
        const type = topUp
          ? 'Nạp tiền'
          : (transactionLabels[item.type.toUpperCase()] ?? item.type.replaceAll('_', ' '));
        const description =
          item.description || transactionLabels[item.type.toUpperCase()] || item.type.replaceAll('_', ' ');
        const method =
          item.method === 'VNPAY' ? 'VNPay' : item.method === 'WALLET' ? 'Ví PTutor' : item.method.replaceAll('_', ' ');
        const statusLabel =
          item.source === 'WITHDRAWAL' && status === 'PENDING'
            ? 'Chờ duyệt'
            : (transactionStatusLabels[status] ?? status.replaceAll('_', ' '));
        const amount = new Intl.NumberFormat('vi-VN', {
          style: 'currency',
          currency: 'VND',
          maximumFractionDigits: 0
        }).format(Number(item.amount));
        return [
          `TX-${item.id.slice(0, 8).toUpperCase()} · ${item.source.replaceAll('_', ' ')}`,
          date,
          type,
          description,
          method,
          statusLabel,
          `${credit ? '+' : '−'}${amount}`
        ];
      })
    ];
    const csv =
      '\uFEFF' +
      lines.map((row) => row.map((field) => `"${String(field).replaceAll('"', '""')}"`).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `ptutor-transactions-page-${page + 1}-${new Date().toISOString().slice(0, 10)}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="wallet-page transactions-page">
      <header className="transactions-page-heading">
        <div>
          <h1>Lịch Sử Giao Dịch</h1>
        </div>
      </header>

      {result.error && (
        <div className="wallet-error" role="alert">
          {result.error}
        </div>
      )}
      <TransactionSummary transactions={transactions} totalCount={result.data?.totalElements ?? 0} />
      <TransactionFilters
        filters={filters}
        search={search}
        fromDate={fromDate}
        toDate={toDate}
        onFiltersChange={changeFilters}
        onSearchChange={changeSearch}
        onFromDateChange={changeFromDate}
        onToDateChange={changeToDate}
        onClear={clearFilters}
      />

      <section className="transactions-results">
        <header className="transactions-results-header">
          <div>
            <b>Danh sách giao dịch</b>
            <span>{result.data?.totalElements ?? 0} giao dịch</span>
          </div>
          <div className="transactions-results-header-actions">
            <small>Hiển thị {transactions.length} dòng trên trang này</small>
            <button type="button" className="transactions-export" onClick={exportCsv} disabled={!transactions.length}>
              ⇩ Xuất CSV
            </button>
          </div>
        </header>
        {cancelError && (
          <div className="wallet-error" role="alert">
            {cancelError}
          </div>
        )}
        <TransactionTable
          transactions={transactions}
          isLoading={result.isLoading}
          basePath={basePath}
          cancellingWithdrawalId={cancellingWithdrawalId}
          onCancelWithdrawal={cancelWithdrawal}
        />
        {result.data && (
          <nav className="transactions-pagination" aria-label="Phân trang giao dịch">
            <label className="transactions-page-size">
              Số dòng
              <select
                value={pageSize}
                onChange={(event) => changePageSize(Number(event.target.value))}
                disabled={result.isLoading}
              >
                {PAGE_SIZES.map((size) => (
                  <option key={size} value={size}>
                    {size}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              disabled={result.data.first || result.isLoading}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              ← Trước
            </button>
            {pageNumbers.map((pageNumber, index) => (
              <span className="transactions-page-number-group" key={pageNumber}>
                {index > 0 && pageNumber - pageNumbers[index - 1] > 1 && <span aria-hidden="true">…</span>}
                <button
                  type="button"
                  className={pageNumber === page ? 'is-current' : ''}
                  aria-current={pageNumber === page ? 'page' : undefined}
                  disabled={result.isLoading}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber + 1}
                </button>
              </span>
            ))}
            <button
              type="button"
              disabled={result.data.last || result.isLoading}
              onClick={() => setPage((value) => value + 1)}
            >
              Tiếp →
            </button>
          </nav>
        )}
      </section>
    </div>
  );
}
