type NotificationFilter = 'ALL' | 'READ' | 'UNREAD';
type NotificationTypeFilter = 'ALL' | 'SYSTEM' | 'REQUEST' | 'CONTRACT' | 'PAYMENT' | 'COMPLAINT';

interface NotificationFiltersProps {
  activeFilter: NotificationFilter;
  unreadCount?: number;
  onChange: (filter: NotificationFilter) => void;
  notificationType: NotificationTypeFilter;
  onTypeChange: (type: NotificationTypeFilter) => void;
}

export function NotificationFilters({
  activeFilter,
  unreadCount,
  onChange,
  notificationType,
  onTypeChange
}: NotificationFiltersProps) {
  return (
    <div className="student-section-heading" style={{ marginTop: 0 }}>
      <div className="student-filter-tabs">
        {(['ALL', 'UNREAD', 'READ'] as const).map((filter) => (
          <button
            key={filter}
            className={activeFilter === filter ? 'active' : ''}
            type="button"
            onClick={() => onChange(filter)}
          >
            {filter === 'ALL' ? 'Tất cả' : filter === 'UNREAD' ? 'Chưa đọc' : 'Đã đọc'}
          </button>
        ))}
      </div>
      <label className="student-notification-type-filter">
        <span>Phân loại</span>
        <select
          value={notificationType}
          onChange={(event) => onTypeChange(event.target.value as NotificationTypeFilter)}
        >
          <option value="ALL">Tất cả</option>
          <option value="SYSTEM">Hệ thống</option>
          <option value="REQUEST">Yêu cầu</option>
          <option value="CONTRACT">Hợp đồng</option>
          <option value="PAYMENT">Thanh toán</option>
          <option value="COMPLAINT">Khiếu nại</option>
        </select>
      </label>
      <span className="student-count">{unreadCount ?? '—'} chưa đọc</span>
    </div>
  );
}
