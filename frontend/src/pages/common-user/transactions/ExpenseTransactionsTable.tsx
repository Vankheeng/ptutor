import { Link } from 'react-router-dom';
import type { FinancialTransaction } from '../../../services/studentApi';
import { analyticsMoney as money, expenseLabel } from './analyticsTypes';

interface Props {
  basePath: string;
  transactions: FinancialTransaction[];
  totalAmount: number;
  variant: 'income' | 'expense';
}

export function ExpenseTransactionsTable({ basePath, transactions, totalAmount, variant }: Props) {
  const income = variant === 'income';
  return (
    <section className="analytics-section analytics-transactions-section">
      <header className="analytics-section-heading">
        <div>
          <span>▤</span>
          <div>
            <h2>
              {income ? 'Bảng kê chi tiết thu nhập & hợp đồng' : 'Bảng kê chi tiết các khoản chi, rút tiền & khấu trừ'}
            </h2>
            <p>Danh sách giao dịch trong 6 tháng gần đây</p>
          </div>
        </div>
        <span className={`analytics-total-expense ${income ? 'is-income' : ''}`}>
          {income ? 'Tổng thu' : 'Tổng chi'}: {income ? '+' : '−'}
          {money(totalAmount)}
        </span>
      </header>
      <div className="analytics-detail-table-wrap">
        <table className="analytics-detail-table">
          <thead>
            <tr>
              <th>MÃ GIAO DỊCH</th>
              <th>PHÂN LOẠI</th>
              <th>NỘI DUNG</th>
              <th>NGÀY GIAO DỊCH</th>
              <th>SỐ TIỀN</th>
              <th>TRẠNG THÁI</th>
            </tr>
          </thead>
          <tbody>
            {transactions.length ? (
              transactions.map((item) => {
                const isWithdrawal = item.source === 'WITHDRAWAL';
                const label = isWithdrawal ? 'Rút tiền ngân hàng' : expenseLabel(item);
                return (
                  <tr key={`${item.source}-${item.id}`}>
                    <td>
                      <Link to={`${basePath}/wallet/transactions/${item.source}/${item.id}`}>
                        {isWithdrawal ? 'WDR-' : income ? 'INC-' : 'FEE-'}
                        {item.id.slice(0, 8).toUpperCase()}
                      </Link>
                    </td>
                    <td>{label}</td>
                    <td>{item.description || item.method}</td>
                    <td>{new Date(item.occurredAt).toLocaleDateString('vi-VN')}</td>
                    <td className={`analytics-table-amount ${income ? 'is-income' : ''}`}>
                      {income ? '+' : '−'}
                      {money(item.amount)}
                    </td>
                    <td>
                      <span className={`analytics-status status-${item.status.toLowerCase()}`}>
                        {item.status === 'COMPLETED'
                          ? 'Hoàn tất'
                          : item.status === 'PROCESSING'
                            ? 'Đang xử lý'
                            : item.status === 'PENDING'
                              ? 'Đang duyệt'
                              : item.status}
                      </span>
                    </td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td className="analytics-no-rows" colSpan={6}>
                  {income ? 'Chưa có khoản thu nhập đã hoàn tất.' : 'Chưa có giao dịch chi tiêu hoặc rút tiền.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
