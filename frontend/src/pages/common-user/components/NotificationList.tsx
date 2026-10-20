import type { Notification } from '../../../services/studentApi';

interface NotificationListProps {
  notifications?: Notification[];
  isLoading: boolean;
  error: string | null;
  onRead: (notification: Notification) => void;
  /** The notification leads to a page: clicking it opens that page. */
  canOpen?: (notification: Notification) => boolean;
}

export function NotificationList({ notifications, isLoading, error, onRead, canOpen }: NotificationListProps) {
  if (isLoading) {
    return (
      <div className="student-empty">
        <h3>Đang tải thông báo…</h3>
      </div>
    );
  }

  if (error)
    return (
      <div className="student-error" role="alert">
        {error}
      </div>
    );

  if (!notifications?.length) {
    return (
      <div className="student-empty">
        <h3>Chưa có thông báo</h3>
        <p>Bạn đã xem hết thông báo.</p>
      </div>
    );
  }

  return (
    <div className="student-list">
      {notifications.map((item) => (
        <button
          className="student-list-row"
          style={{
            textAlign: 'left',
            border: item.isRead ? undefined : '1px solid #c7b4ff',
            cursor: item.isRead && !canOpen?.(item) ? 'default' : 'pointer'
          }}
          key={item.id}
          type="button"
          onClick={() => onRead(item)}
        >
          <div>
            <h3>{item.title}</h3>
            <p>
              {item.content}
              <br />
              <span className="student-muted">{new Date(item.createdAt).toLocaleString('vi-VN')}</span>
              {canOpen?.(item) && (
                <>
                  {' · '}
                  <span className="student-muted">Bấm để xem</span>
                </>
              )}
            </p>
          </div>
          <span className={`student-status ${item.isRead ? 'completed' : 'unread'}`}>
            {item.isRead ? 'Đã đọc' : 'Chưa đọc'}
          </span>
        </button>
      ))}
    </div>
  );
}
