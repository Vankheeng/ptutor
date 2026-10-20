type NotificationFilter = 'ALL' | 'READ' | 'UNREAD';

interface NotificationFiltersProps {
  activeFilter: NotificationFilter;
  unreadCount?: number;
  onChange: (filter: NotificationFilter) => void;
}

export function NotificationFilters({ activeFilter, unreadCount, onChange }: NotificationFiltersProps) {
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
      <span className="student-count">{unreadCount ?? '—'} chưa đọc</span>
    </div>
  );
}
