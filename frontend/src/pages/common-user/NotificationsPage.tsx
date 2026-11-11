import { useCallback, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type Notification
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { referenceNavigation } from '../../utils/referenceNavigation';
import { NotificationFilters } from './components/NotificationFilters';
import { NotificationList } from './components/NotificationList';
import { NotificationPagination } from './components/NotificationPagination';
import '../student/student.css';

const NOTIFICATION_PAGE_SIZE = 10;
type NotificationTypeFilter = 'ALL' | 'SYSTEM' | 'REQUEST' | 'CONTRACT' | 'PAYMENT' | 'COMPLAINT';

export function NotificationsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const basePath = location.pathname.startsWith('/tutor/') ? '/tutor' : '/student';
  const [filter, setFilter] = useState<'ALL' | 'READ' | 'UNREAD'>('ALL');
  const [type, setType] = useState<NotificationTypeFilter>('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const load = useCallback(
    (signal: AbortSignal) =>
      getNotifications(
        filter === 'ALL' ? undefined : filter,
        type === 'ALL' ? undefined : type,
        page,
        NOTIFICATION_PAGE_SIZE,
        signal
      ),
    [filter, page, type]
  );
  const loadUnread = useCallback((signal: AbortSignal) => getUnreadCount(signal), []);
  const query = useResource(load, revision);
  const unread = useResource(loadUnread, revision);
  const notifications = useMemo(
    () =>
      type === 'ALL' ? query.data?.content : query.data?.content.filter((notification) => notification.type === type),
    [query.data?.content, type]
  );
  const read = async (notification: Notification) => {
    setError('');
    const detailPath = referenceNavigation(basePath, notification.referenceType, notification.referenceId)?.to ?? null;
    try {
      if (!notification.isRead) await markNotificationRead(notification.id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể cập nhật thông báo.');
    } finally {
      if (detailPath) navigate(detailPath);
    }
  };
  const readAll = async () => {
    setError('');
    try {
      await markAllNotificationsRead();
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể đánh dấu thông báo đã đọc.');
    }
  };
  return (
    <main className="student-notifications-page">
      <div className="student-page-heading">
        <div>
          <h1>Thông báo</h1>
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
        notificationType={type}
        onTypeChange={(value) => {
          setType(value);
          setPage(0);
        }}
      />
      <NotificationList
        notifications={notifications}
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
    </main>
  );
}
