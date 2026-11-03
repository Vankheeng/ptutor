import { useCallback, useState } from 'react';
import { ApiError } from '../../services/httpClient';
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

export function NotificationsPage() {
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
      setError(caught instanceof ApiError ? caught.message : 'Could not update this notification.');
    }
  };
  const readAll = async () => {
    setError('');
    try {
      await markAllNotificationsRead();
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not mark notifications as read.');
    }
  };
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">STAY UPDATED</span>
          <h1>Notifications</h1>
          <p>Updates about your requests, contracts, payments, and account.</p>
        </div>
        <button className="student-button secondary" type="button" onClick={() => void readAll()}>
          Mark all as read
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
