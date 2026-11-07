import { useCallback, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getAllFinancialTransactions, type FinancialTransaction } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { AnalyticsTabs } from './transactions/AnalyticsTabs';
import { CashflowOverview } from './transactions/CashflowOverview';
import { ExpenseBreakdown } from './transactions/ExpenseBreakdown';
import { ExpenseTransactionsTable } from './transactions/ExpenseTransactionsTable';
import { expenseLabel, type AnalyticsCategory, type AnalyticsTab } from './transactions/analyticsTypes';
import './wallet.css';
import './transactions/transactions.css';
import './transactions/analytics.css';

const MONTH_COUNT = 6;
const successful = (status: string) => ['PAID', 'COMPLETED', 'SUCCESS', 'SUCCEEDED'].includes(status.toUpperCase());
const pending = (status: string) => ['PENDING', 'PROCESSING', 'UNDER_REVIEW'].includes(status.toUpperCase());
const isTopUp = (item: FinancialTransaction) =>
  item.type === 'WALLET_TOP_UP' ||
  (item.source === 'WALLET_TRANSACTION' &&
    item.type === 'CREDIT' &&
    item.description?.toLowerCase().includes('top-up'));
const monthKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
const monthLabel = (date: Date) => date.toLocaleDateString('vi-VN', { month: 'short' });
const chartColors = ['#6335d7', '#9469ed', '#b79af5', '#f59e0b', '#eb5d76', '#23a77b'];
function toConicStops(rows: AnalyticsCategory[], total: number) {
  let progress = 0;
  return rows.map((row, index) => {
    const start = progress;
    const nextProgress = progress + (total ? (row.amount / total) * 100 : 0);
    const stop = `${chartColors[index % chartColors.length]} ${start}% ${nextProgress}%`;
    progress = nextProgress;
    return stop;
  });
}

function getIncome(item: FinancialTransaction) {
  return (
    (item.source === 'WALLET_TRANSACTION' && item.type === 'CREDIT') ||
    (item.source === 'PAYMENT' && item.type === 'WALLET_TOP_UP')
  );
}
function getExpense(item: FinancialTransaction) {
  const isWithdrawalHold =
    item.source === 'WALLET_TRANSACTION' && item.description?.toLowerCase().includes('withdrawal request');
  return (
    !isWithdrawalHold &&
    ((item.source === 'WALLET_TRANSACTION' && item.type === 'DEBIT') ||
      (item.source === 'PAYMENT' && item.method !== 'WALLET' && item.type !== 'WALLET_TOP_UP'))
  );
}

export function WalletAnalyticsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<AnalyticsTab>('income');
  const [selectedExpenseMonth, setSelectedExpenseMonth] = useState('');
  const basePath = location.pathname.startsWith('/tutor/') ? '/tutor' : '/student';
  const analyticsFromDate = useMemo(() => {
    const date = new Date();
    date.setDate(1);
    date.setMonth(date.getMonth() - (MONTH_COUNT - 1));
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`;
  }, []);
  const load = useCallback(
    (signal: AbortSignal) => getAllFinancialTransactions(signal, { fromDate: analyticsFromDate }),
    [analyticsFromDate]
  );
  const result = useResource(load);

  const analytics = useMemo(() => {
    const transactions = (result.data ?? []).filter((item) => !isTopUp(item) || successful(item.status));
    const now = new Date();
    const months = Array.from(
      { length: MONTH_COUNT },
      (_, index) => new Date(now.getFullYear(), now.getMonth() - (MONTH_COUNT - index - 1), 1)
    );
    const monthKeys = new Set(months.map(monthKey));
    const visible = transactions.filter((item) => monthKeys.has(item.occurredAt.slice(0, 7)));
    const completedIncome = visible
      .filter((item) => getIncome(item) && successful(item.status))
      .reduce((sum, item) => sum + Number(item.amount), 0);
    const incomeTransactions = visible
      .filter((item) => getIncome(item) && successful(item.status))
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
    const pendingIncome = visible
      .filter((item) => getIncome(item) && pending(item.status))
      .reduce((sum, item) => sum + Number(item.amount), 0);
    const expenses = visible.filter((item) => getExpense(item) && successful(item.status));
    const withdrawalRequests = visible.filter(
      (item) => item.source === 'WITHDRAWAL' && !['CANCELLED', 'REJECTED', 'FAILED'].includes(item.status.toUpperCase())
    );
    const pendingWithdrawals = withdrawalRequests
      .filter((item) => pending(item.status))
      .reduce((sum, item) => sum + Number(item.amount), 0);
    const completedWithdrawals = withdrawalRequests
      .filter((item) => successful(item.status))
      .reduce((sum, item) => sum + Number(item.amount), 0);
    const totalWithdrawals = withdrawalRequests.reduce((sum, item) => sum + Number(item.amount), 0);
    const refunds = visible
      .filter(
        (item) =>
          item.source === 'WALLET_TRANSACTION' && item.type === 'CREDIT' && /refund|hoàn/i.test(item.description ?? '')
      )
      .reduce((sum, item) => sum + Number(item.amount), 0);
    const totalExpense = expenses.reduce((sum, item) => sum + Number(item.amount), 0) + totalWithdrawals;
    const monthly = months.map((date) => {
      const rows = visible.filter((item) => item.occurredAt.slice(0, 7) === monthKey(date));
      return {
        key: monthKey(date),
        label: monthLabel(date),
        income: rows
          .filter((item) => getIncome(item) && successful(item.status))
          .reduce((sum, item) => sum + Number(item.amount), 0),
        expense: rows
          .filter((item) => getExpense(item) && successful(item.status))
          .reduce((sum, item) => sum + Number(item.amount), 0),
        withdrawal: rows
          .filter(
            (item) =>
              item.source === 'WITHDRAWAL' && !['CANCELLED', 'REJECTED', 'FAILED'].includes(item.status.toUpperCase())
          )
          .reduce((sum, item) => sum + Number(item.amount), 0)
      };
    });
    const categories = expenses.reduce<Record<string, number>>((groups, item) => {
      const label = expenseLabel(item);
      groups[label] = (groups[label] ?? 0) + Number(item.amount);
      return groups;
    }, {});
    const incomeCategories = incomeTransactions.reduce<Record<string, number>>((groups, item) => {
      const label =
        item.description?.trim() || (item.type === 'WALLET_TOP_UP' ? 'Nạp tiền vào ví' : 'Thu nhập từ hợp đồng');
      groups[label] = (groups[label] ?? 0) + Number(item.amount);
      return groups;
    }, {});
    if (totalWithdrawals > 0) categories['Rút tiền ngân hàng'] = totalWithdrawals;
    const categoryRows: AnalyticsCategory[] = Object.entries(categories)
      .map(([label, amount]) => ({
        label,
        amount,
        ...(label.toLowerCase().startsWith('r') && amount === totalWithdrawals
          ? { completedAmount: completedWithdrawals, pendingAmount: pendingWithdrawals }
          : {})
      }))
      .sort((a, b) => b.amount - a.amount);
    const incomeCategoryRows = Object.entries(incomeCategories)
      .map(([label, amount]) => ({ label, amount }))
      .sort((a, b) => b.amount - a.amount);
    const expenseMonths = months
      .map((date) => {
        const key = monthKey(date);
        const monthExpenses = expenses.filter((item) => item.occurredAt.slice(0, 7) === key);
        const monthWithdrawals = withdrawalRequests.filter((item) => item.occurredAt.slice(0, 7) === key);
        const groups = monthExpenses.reduce<Record<string, number>>((result, item) => {
          const label = expenseLabel(item);
          result[label] = (result[label] ?? 0) + Number(item.amount);
          return result;
        }, {});
        const monthWithdrawalTotal = monthWithdrawals.reduce((sum, item) => sum + Number(item.amount), 0);
        const withdrawalCategoryLabel = categoryRows.find((row) => row.label.toLowerCase().startsWith('r'))?.label;
        if (monthWithdrawalTotal > 0 && withdrawalCategoryLabel) groups[withdrawalCategoryLabel] = monthWithdrawalTotal;
        const rows: AnalyticsCategory[] = Object.entries(groups)
          .map(([label, amount]) => ({
            label,
            amount,
            ...(label.toLowerCase().startsWith('r') && amount === monthWithdrawalTotal
              ? {
                  completedAmount: monthWithdrawals
                    .filter((item) => successful(item.status))
                    .reduce((sum, item) => sum + Number(item.amount), 0),
                  pendingAmount: monthWithdrawals
                    .filter((item) => pending(item.status))
                    .reduce((sum, item) => sum + Number(item.amount), 0)
                }
              : {})
          }))
          .sort((a, b) => b.amount - a.amount);
        const total = rows.reduce((sum, row) => sum + row.amount, 0);
        const stops = toConicStops(rows, total);
        return {
          key,
          label: date.toLocaleDateString('vi-VN', { month: 'long', year: 'numeric' }),
          rows,
          total,
          pieBackground: stops.length ? `conic-gradient(${stops.join(', ')})` : 'conic-gradient(#e9e1f4 0 100%)'
        };
      })
      .filter((month) => month.total > 0);
    const outflowTransactions = [...expenses, ...withdrawalRequests].sort((a, b) =>
      b.occurredAt.localeCompare(a.occurredAt)
    );
    const largest = Math.max(
      ...monthly.flatMap((item) => [item.income, item.expense + item.withdrawal, item.expense, item.withdrawal]),
      1
    );
    const totalExpenseCategories = categoryRows.reduce((sum, row) => sum + row.amount, 0);
    const pieStops = toConicStops(categoryRows, totalExpenseCategories);
    const incomePieStops = toConicStops(incomeCategoryRows, completedIncome);
    return {
      transactions,
      monthly,
      completedIncome,
      totalIncome: completedIncome,
      pendingIncome,
      totalExpense,
      totalWithdrawals,
      pendingWithdrawals,
      completedWithdrawals,
      refunds,
      outflowTransactions,
      categoryRows,
      incomeCategoryRows,
      incomeTransactions,
      expenseMonths,
      largest,
      totalExpenseCategories,
      pieBackground: pieStops.length ? `conic-gradient(${pieStops.join(', ')})` : 'conic-gradient(#e9e1f4 0 100%)',
      incomePieBackground: incomePieStops.length
        ? `conic-gradient(${incomePieStops.join(', ')})`
        : 'conic-gradient(#e9e1f4 0 100%)'
    };
  }, [result.data]);

  const selectedExpenseData =
    analytics.expenseMonths.find((month) => month.key === selectedExpenseMonth) ??
    analytics.expenseMonths[analytics.expenseMonths.length - 1];

  return (
    <main className="wallet-page transactions-page analytics-page">
      <div className="transactions-back-row">
        <button type="button" onClick={() => navigate(`${basePath}/wallet`)}>
          ← Quay lại ví
        </button>
        <span>PTUTOR FINANCIAL INSIGHTS</span>
      </div>
      <header className="transactions-page-heading analytics-heading">
        <div>
          <h1>Phân tích &amp; thống kê dòng tiền</h1>
        </div>
        <button
          className="wallet-heading-button wallet-heading-button-light"
          type="button"
          onClick={() => navigate(`${basePath}/wallet/transactions`)}
        >
          ▤ Lịch sử giao dịch
        </button>
      </header>
      {result.error && (
        <div className="wallet-error" role="alert">
          {result.error}
        </div>
      )}
      {result.isLoading ? (
        <div className="transactions-table-message">Đang tải dữ liệu phân tích…</div>
      ) : (
        <>
          <CashflowOverview data={analytics} />
          <AnalyticsTabs activeTab={activeTab} onChange={setActiveTab} />
          <section className="analytics-lower-grid is-detail" id="analytics-expenses">
            {activeTab === 'income' ? (
              <>
                <ExpenseBreakdown
                  title="Cơ cấu thu nhập theo hợp đồng & giao dịch"
                  amountLabel="Tổng thu nhập"
                  rows={analytics.incomeCategoryRows}
                  total={analytics.totalIncome}
                  pieBackground={analytics.incomePieBackground}
                />
                <ExpenseTransactionsTable
                  basePath={basePath}
                  transactions={analytics.incomeTransactions}
                  totalAmount={analytics.totalIncome}
                  variant="income"
                />
              </>
            ) : (
              <>
                <ExpenseBreakdown
                  title="Phân tích chi tiêu, rút tiền & khấu trừ"
                  amountLabel="Tổng chi tiêu"
                  rows={selectedExpenseData?.rows ?? []}
                  total={selectedExpenseData?.total ?? 0}
                  pieBackground={selectedExpenseData?.pieBackground ?? 'conic-gradient(#e9e1f4 0 100%)'}
                  monthOptions={analytics.expenseMonths}
                  selectedMonth={selectedExpenseData?.key}
                  onMonthChange={setSelectedExpenseMonth}
                />
                <ExpenseTransactionsTable
                  basePath={basePath}
                  transactions={analytics.outflowTransactions}
                  totalAmount={analytics.totalExpense}
                  variant="expense"
                />
              </>
            )}
            <aside className="analytics-note">
              <b>◈ PTutor Trust &amp; Financial Insights</b>
              <p>
                Thu nhập và dòng tiền ra được thống kê riêng theo giao dịch đã ghi nhận. Yêu cầu rút tiền đang chờ duyệt
                chỉ xuất hiện trong phần chi tiêu &amp; rút tiền; nạp tiền chưa hoàn tất được ẩn.
              </p>
            </aside>
          </section>
        </>
      )}
    </main>
  );
}
