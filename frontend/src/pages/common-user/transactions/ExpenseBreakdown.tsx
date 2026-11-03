import type { AnalyticsCategory, AnalyticsCategoryMonth } from './analyticsTypes';
import { analyticsMoney as money } from './analyticsTypes';

const COLORS = ['#6335d7', '#9469ed', '#b79af5', '#f59e0b', '#eb5d76', '#23a77b'];
interface Props {
  title: string;
  amountLabel: string;
  rows: AnalyticsCategory[];
  total: number;
  pieBackground: string;
  monthOptions?: Pick<AnalyticsCategoryMonth, 'key' | 'label'>[];
  selectedMonth?: string;
  onMonthChange?: (month: string) => void;
}

export function ExpenseBreakdown({
  title,
  amountLabel,
  rows,
  total,
  pieBackground,
  monthOptions,
  selectedMonth,
  onMonthChange
}: Props) {
  return (
    <article className="analytics-section analytics-donut-section">
      <header className="analytics-section-heading">
        <div>
          <span>◉</span>
          <div>
            <h2>{title}</h2>
            <p>{monthOptions?.length ? 'Phân loại giao dịch theo tháng' : 'Chưa có giao dịch để phân loại'}</p>
          </div>
        </div>
        {monthOptions?.length && selectedMonth && onMonthChange && (
          <label className="analytics-month-select">
            <span>Tháng</span>
            <select
              value={selectedMonth}
              onChange={(event) => onMonthChange(event.target.value)}
              aria-label="Chọn tháng xem phân loại giao dịch"
            >
              {monthOptions.map((month) => (
                <option key={month.key} value={month.key}>
                  {month.label}
                </option>
              ))}
            </select>
          </label>
        )}
      </header>
      <div className="analytics-donut-layout">
        <div className="analytics-donut" style={{ background: pieBackground }}>
          <div>
            <strong>{money(total)}</strong>
            <span>{amountLabel}</span>
          </div>
        </div>
        <div className="analytics-category-list">
          {rows.length ? (
            rows.map((category, index) => (
              <div className="analytics-category" key={category.label}>
                <div>
                  <span>
                    <i style={{ background: COLORS[index % COLORS.length] }} />
                    {category.label}
                  </span>
                  <b>{money(category.amount)}</b>
                </div>
                <div className="analytics-category-track">
                  {category.completedAmount !== undefined || category.pendingAmount !== undefined ? (
                    <>
                      <i
                        className="analytics-withdrawal-completed"
                        style={{ width: `${total ? ((category.completedAmount ?? 0) / total) * 100 : 0}%` }}
                      />
                      <i
                        className="analytics-withdrawal-pending"
                        style={{ width: `${total ? ((category.pendingAmount ?? 0) / total) * 100 : 0}%` }}
                      />
                    </>
                  ) : (
                    <i
                      style={{
                        width: `${total ? (category.amount / total) * 100 : 0}%`,
                        background: COLORS[index % COLORS.length]
                      }}
                    />
                  )}
                </div>
              </div>
            ))
          ) : (
            <p className="analytics-empty">Chưa có giao dịch trong giai đoạn này.</p>
          )}
        </div>
      </div>
    </article>
  );
}
