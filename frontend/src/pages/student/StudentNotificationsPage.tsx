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

export function StudentNotificationsPage() {
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
      <div className="student-section-heading" style={{ marginTop: 0 }}>
        <div className="student-filter-tabs">
          {(['ALL', 'UNREAD', 'READ'] as const).map((item) => (
            <button
              key={item}
              className={filter === item ? 'active' : ''}
              type="button"
              onClick={() => {
                setFilter(item);
                setPage(0);
              }}
            >
              {item === 'ALL' ? 'All' : label(item)}
            </button>
          ))}
        </div>
        <span className="student-count">{unread.data?.unreadCount ?? '—'} unread</span>
      </div>
      {query.isLoading ? (
        <div className="student-empty">
          <h3>Loading notifications…</h3>
        </div>
      ) : query.error ? (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      ) : query.data?.content.length ? (
        <div className="student-list">
          {query.data.content.map((item) => (
            <button
              className="student-list-row"
              style={{
                textAlign: 'left',
                border: item.isRead ? undefined : '1px solid #c7b4ff',
                cursor: item.isRead ? 'default' : 'pointer'
              }}
              key={item.id}
              type="button"
              onClick={() => void read(item)}
            >
              <div>
                <h3>{item.title}</h3>
                <p>
                  {item.content}
                  <br />
                  <span className="student-muted">{new Date(item.createdAt).toLocaleString('en-US')}</span>
                </p>
              </div>
              <span className={`student-status ${item.isRead ? 'completed' : 'unread'}`}>
                {item.isRead ? 'Read' : 'Unread'}
              </span>
            </button>
          ))}
        </div>
      ) : (
        <div className="student-empty">
          <h3>No notifications</h3>
          <p>You are all caught up.</p>
        </div>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="student-pagination">
          <button type="button" disabled={query.data.first} onClick={() => setPage((value) => value - 1)}>
            Previous
          </button>
          <span>
            Page {query.data.page + 1} of {query.data.totalPages}
          </span>
          <button type="button" disabled={query.data.last} onClick={() => setPage((value) => value + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  );
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
