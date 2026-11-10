import type { AnalyticsTab } from './analyticsTypes';

interface Props {
  activeTab: AnalyticsTab;
  onChange: (tab: AnalyticsTab) => void;
}

export function AnalyticsTabs({ activeTab, onChange }: Props) {
  return (
    <div className="analytics-tabs" role="tablist" aria-label="Chế độ phân tích">
      <button
        type="button"
        className={activeTab === 'income' ? 'is-active' : ''}
        role="tab"
        aria-selected={activeTab === 'income'}
        onClick={() => onChange('income')}
      >
        ↗ Thu nhập &amp; Hợp đồng
      </button>
      <button
        type="button"
        className={activeTab === 'expense' ? 'is-active' : ''}
        role="tab"
        aria-selected={activeTab === 'expense'}
        onClick={() => onChange('expense')}
      >
        ↓ Chi tiêu &amp; Rút tiền
      </button>
    </div>
  );
}
