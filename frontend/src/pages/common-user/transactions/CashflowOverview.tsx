import type { WalletAnalyticsData } from './analyticsTypes';
import { analyticsMoney as money } from './analyticsTypes';

interface Props {
  data: WalletAnalyticsData;
}

export function CashflowOverview({ data }: Props) {
  const kpis = [
    {
      label: 'TỔNG THU NHẬP',
      value: money(data.totalIncome),
      detail: 'Giao dịch thu đã hoàn tất',
      style: 'income-text'
    },
    {
      label: 'TỔNG CHI TIÊU',
      value: `−${money(data.totalExpense - data.totalWithdrawals)}`,
      detail: 'Các khoản chi đã hoàn tất',
      style: 'expense-text'
    },
    {
      label: 'TỔNG RÚT TIỀN',
      value: `−${money(data.totalWithdrawals)}`,
      detail: 'Bao gồm yêu cầu đang xử lý',
      style: 'withdrawal-text'
    },
    { label: 'GIAO DỊCH', value: data.transactions.length.toLocaleString('vi-VN'), detail: 'Trong 6 tháng gần đây' }
  ];

  return (
    <section className="analytics-section">
      <header className="analytics-section-heading">
        <div>
          <span>▰</span>
          <div>
            <h2>Tổng quan dòng tiền</h2>
            <p>Thu nhập và dòng tiền ra trong 6 tháng gần đây</p>
          </div>
        </div>
        <div className="analytics-legend">
          {[
            ['legend-income', 'Thu nhập'],
            ['legend-expense', 'Chi tiêu'],
            ['legend-withdrawal', 'Rút tiền']
          ].map(([style, label]) => (
            <span key={label}>
              <i className={style} /> {label}
            </span>
          ))}
        </div>
      </header>
      <div className="analytics-kpis">
        {kpis.map((kpi) => (
          <article key={kpi.label}>
            <span>{kpi.label}</span>
            <b className={kpi.style}>{kpi.value}</b>
            <small>{kpi.detail}</small>
          </article>
        ))}
      </div>
      <div className="analytics-chart" aria-label="Biểu đồ tổng quan thu nhập, chi tiêu và rút tiền theo tháng">
        <div className="analytics-y-axis">
          <span>{money(data.largest)}</span>
          <span>{money(data.largest / 2)}</span>
          <span>0 đ</span>
        </div>
        <div className="analytics-bars">
          {data.monthly.map((month) => {
            const series = [
              { name: 'Thu nhập', value: month.income, style: 'income-bar' },
              { name: 'Chi tiêu', value: month.expense, style: 'expense-bar' },
              { name: 'Rút tiền', value: month.withdrawal, style: 'withdrawal-bar' }
            ];
            return (
              <div className="analytics-month" key={month.key}>
                <div className="analytics-bar-pair">
                  {series.map((item) => (
                    <div
                      key={item.name}
                      className={`analytics-bar ${item.style}`}
                      style={{ height: `${Math.max(item.value ? 4 : 0, (item.value / data.largest) * 100)}%` }}
                      title={`${item.name} ${money(item.value)}`}
                    />
                  ))}
                </div>
                <span>{month.label}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
