import type { FinancialTransaction } from '../../../services/studentApi';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);

export function TransactionSummary({
  transactions,
  totalCount
}: {
  transactions: FinancialTransaction[];
  totalCount: number;
}) {
  const walletEntries = transactions.filter((item) => item.source === 'WALLET_TRANSACTION');
  const incoming = walletEntries
    .filter((item) => item.type === 'CREDIT')
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const outgoing = walletEntries
    .filter((item) => item.type === 'DEBIT')
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const pending = transactions.filter((item) => ['PENDING', 'PROCESSING'].includes(item.status.toUpperCase())).length;
  const completed = transactions.filter((item) =>
    ['PAID', 'COMPLETED', 'SUCCESS', 'SUCCEEDED'].includes(item.status.toUpperCase())
  ).length;
  return (
    <section className="transactions-summary-grid" aria-label="Tổng quan giao dịch">
      <article>
        <span>TỔNG GIAO DỊCH</span>
        <b>{totalCount.toLocaleString('vi-VN')}</b>
        <small>bản ghi phù hợp</small>
      </article>
      <article className="is-income">
        <span>TỔNG THU</span>
        <b>{money(incoming)}</b>
        <small>trong khoảng thời gian đã chọn</small>
      </article>
      <article className="is-expense">
        <span>TỔNG CHI</span>
        <b>{money(outgoing)}</b>
        <small>trong khoảng thời gian đã chọn</small>
      </article>
      <article className="is-pending">
        <span>ĐANG XỬ LÝ</span>
        <b>{pending.toLocaleString('vi-VN')}</b>
        <small>{completed.toLocaleString('vi-VN')} giao dịch đã hoàn tất trên trang</small>
      </article>
    </section>
  );
}
