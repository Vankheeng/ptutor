import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { errorMessage } from '../../services/errorMessages';
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type Notification
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { NotificationFilters } from './components/NotificationFilters';
import { NotificationList } from './components/NotificationList';
import { NotificationPagination } from './components/NotificationPagination';
import '../student/student.css';

/** Where a notification leads, when it points to something the user can open. */
function targetOf(notification: Notification, role: string | undefined): string | null {
  if (notification.eventType !== 'RECOMMENDATION_MATCH_FOUND' || !notification.referenceId) return null;
  if (role === 'STUDENT' && notification.referenceType === 'TEACHING_REQUEST') {
    return `/student/teaching-requests?posting=${encodeURIComponent(notification.referenceId)}`;
  }
  if (role === 'TUTOR' && notification.referenceType === 'STUDYING_REQUEST') {
    return `/tutor/studying-requests/${encodeURIComponent(notification.referenceId)}`;
  }
  return null;
}

export function NotificationsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [filter, setFilter] = useState<'ALL' | 'READ' | 'UNREAD'>('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const load = useCallback(
    (signal: AbortSignal) => getNotifications(filter === 'ALL' ? undefined : filter, page, 20, signal),
    [filter, page]
  );
  const loadUnread = useCallback((signal: AbortSignal) => getUnreadCount(signal), []);
  const query = useResource(load, revision);
  const unread = useResource(loadUnread, revision);
  const read = async (notification: Notification) => {
    setError('');
    try {
      if (!notification.isRead) await markNotificationRead(notification.id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(errorMessage(caught, 'Chưa cập nhật được thông báo này.'));
    }
    const target = targetOf(notification, user?.role);
    if (target) navigate(target);
  };
  const readAll = async () => {
    setError('');
    try {
      await markAllNotificationsRead();
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(errorMessage(caught, 'Chưa đánh dấu được các thông báo là đã đọc.'));
    }
  };
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">CẬP NHẬT</span>
          <h1>Thông báo</h1>
          <p>Tin về đơn, hợp đồng, thanh toán, tài khoản và các gợi ý mới phù hợp với bạn.</p>
        </div>
        <button className="student-button secondary" type="button" onClick={() => void readAll()}>
          Đánh dấu tất cả đã đọc
        </button>
      </div>
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <NotificationFilters
        activeFilter={filter}
        unreadCount={unread.data?.unreadCount}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
      />
      <NotificationList
        notifications={query.data?.content}
        isLoading={query.isLoading}
        error={query.error}
        onRead={(notification) => void read(notification)}
        canOpen={(notification) => targetOf(notification, user?.role) !== null}
      />
      {query.data && (
        <NotificationPagination
          page={query.data.page}
          totalPages={query.data.totalPages}
          first={query.data.first}
          last={query.data.last}
          onPageChange={setPage}
        />
      )}
    </>
  );
}
