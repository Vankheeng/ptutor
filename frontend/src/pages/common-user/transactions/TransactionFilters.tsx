import type { FinancialTransactionFilters } from '../../../services/studentApi';

interface Props {
  filters: FinancialTransactionFilters;
  search: string;
  fromDate: string;
  toDate: string;
  onFiltersChange: (filters: FinancialTransactionFilters) => void;
  onSearchChange: (value: string) => void;
  onFromDateChange: (value: string) => void;
  onToDateChange: (value: string) => void;
  onClear: () => void;
}

export function TransactionFilters({
  filters,
  search,
  fromDate,
  toDate,
  onFiltersChange,
  onSearchChange,
  onFromDateChange,
  onToDateChange,
  onClear
}: Props) {
  const update = (key: keyof FinancialTransactionFilters, value: string) =>
    onFiltersChange({ ...filters, [key]: value });
  return (
    <section className="transactions-filter-panel">
      <label className="transactions-search">
        <span aria-hidden="true">⌕</span>
        <input
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="Tìm theo mã GD, nội dung, ngân hàng…"
        />
      </label>
      <div className="transactions-filter-grid">
        <label>
          Loại giao dịch
          <select value={filters.type ?? ''} onChange={(event) => update('type', event.target.value)}>
            <option value="">Tất cả giao dịch</option>
            <option value="TOP_UP">Nạp tiền</option>
            <option value="DEBIT">Thanh toán</option>
            <option value="WITHDRAWAL">Rút tiền</option>
            <option value="REFUND">Hoàn tiền</option>
          </select>
        </label>
        <label>
          Trạng thái
          <select value={filters.status ?? ''} onChange={(event) => update('status', event.target.value)}>
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Đang xử lý</option>
            <option value="PROCESSING">Đang xét duyệt</option>
            <option value="COMPLETED">Hoàn tất</option>
            <option value="FAILED">Thất bại</option>
            <option value="CANCELLED">Đã hủy</option>
            <option value="REFUNDED">Đã hoàn tiền</option>
            <option value="REJECTED">Từ chối</option>
          </select>
        </label>
        <label>
          Phương thức
          <select value={filters.method ?? ''} onChange={(event) => update('method', event.target.value)}>
            <option value="">Tất cả phương thức</option>
            <option value="VNPAY">VNPay</option>
            <option value="WALLET">Ví PTutor</option>
          </select>
        </label>
        <div className="transactions-date-range">
          <label>
            Từ ngày
            <input type="date" value={fromDate} onChange={(event) => onFromDateChange(event.target.value)} />
          </label>
          <label>
            Đến ngày
            <input type="date" value={toDate} onChange={(event) => onToDateChange(event.target.value)} />
          </label>
        </div>
        <button className="transactions-clear-button" type="button" onClick={onClear}>
          ↻ Xóa bộ lọc
        </button>
      </div>
    </section>
  );
}
