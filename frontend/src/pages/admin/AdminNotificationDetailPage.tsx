import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getNotificationCampaign,
  getNotificationCampaignStatistics,
  type NotificationCampaign,
  type NotificationCampaignStatistics
} from '../../services/adminNotificationApi';
import { AdminState, StatusBadge } from './components/AdminUi';
import { NotificationActionDialog, type NotificationAction } from './components/NotificationActionDialog';
import { formatDateTime } from './adminFormat';
import {
  audienceLabels,
  canCancel,
  canCancelSchedule,
  canEdit,
  canRetract,
  canSchedule,
  canSend,
  categoryLabels,
  notificationErrorMessage,
  notificationStatusMeta,
  readRate
} from './notificationFormat';

/** How often a notification being distributed is reloaded until it settles. */
const PROCESSING_POLL_MS = 5000;

interface LocationState {
  action?: NotificationAction;
  message?: string;
}

export function AdminNotificationDetailPage() {
  const { notificationId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<NotificationAction | null>(
    () => (location.state as LocationState | null)?.action ?? null
  );
  const [message, setMessage] = useState<string | null>(
    () => (location.state as LocationState | null)?.message ?? null
  );

  const load = useCallback((signal: AbortSignal) => getNotificationCampaign(notificationId, signal), [notificationId]);
  const { data, error, isLoading } = useResource(load, revision);

  // Drop the one-shot state so a reload does not reopen the dialog or repeat the message.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  const processing = data?.status === 'PROCESSING';
  useEffect(() => {
    if (!processing) return;
    const timer = window.setInterval(() => setRevision((value) => value + 1), PROCESSING_POLL_MS);
    return () => window.clearInterval(timer);
  }, [processing]);

  if (!data) {
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }

  const { status } = data;
  const sendAction: NotificationAction = status === 'FAILED' ? 'retry' : 'send';

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/notifications">
        ‹ Thông báo
      </Link>
      <header className="admin-card admin-card-pad admin-account-header">
        <div className="admin-stack-gap admin-notification-heading">
          <h1 className="admin-title">{data.title}</h1>
          <div className="admin-tags">
            <StatusBadge meta={notificationStatusMeta[status]} />
            <span>{categoryLabels[data.category]}</span>
            <span>{audienceLabels[data.audience]}</span>
          </div>
        </div>
        <div className="admin-button-row">
          {canCancel(status) && (
            <button className="admin-button danger-outline" type="button" onClick={() => setDialog('cancel')}>
              Hủy thông báo
            </button>
          )}
          {canRetract(status) && (
            <button className="admin-button danger-outline" type="button" onClick={() => setDialog('retract')}>
              Thu hồi
            </button>
          )}
          {canEdit(status) && (
            <Link className="admin-button secondary" to={`/admin/notifications/${data.id}/edit`}>
              Sửa
            </Link>
          )}
          {canCancelSchedule(status) && (
            <button className="admin-button secondary" type="button" onClick={() => setDialog('cancel-schedule')}>
              Hủy lịch
            </button>
          )}
          {canSchedule(status) && (
            <button className="admin-button outline" type="button" onClick={() => setDialog('schedule')}>
              {status === 'SCHEDULED' ? 'Đổi lịch' : 'Lên lịch gửi'}
            </button>
          )}
          {canSend(status) && (
            <button className="admin-button" type="button" onClick={() => setDialog(sendAction)}>
              {status === 'FAILED' ? 'Gửi lại' : 'Gửi ngay'}
            </button>
          )}
        </div>
      </header>

      {message && (
        <div className="admin-alert admin-alert-success" role="status">
          {message}
        </div>
      )}
      {status === 'FAILED' && data.failureReason && (
        <div className="admin-alert admin-alert-danger">
          <strong>Lý do lỗi:</strong>
          <code className="admin-notification-error">{data.failureReason}</code>
        </div>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          {status === 'SENT' && <NotificationStatistics key={revision} campaign={data} />}
          <section className="admin-card admin-card-pad">
            <h2>Nội dung</h2>
            <p className="admin-prose">{data.content}</p>
          </section>
        </div>
        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Thông tin</h2>
            <div className="admin-key-values">
              <div>
                <span>Loại</span>
                <strong>{categoryLabels[data.category]}</strong>
              </div>
              <div>
                <span>Gửi đến</span>
                <strong>{audienceLabels[data.audience]}</strong>
              </div>
              <div>
                <span>Tạo lúc</span>
                <strong>{formatDateTime(data.createdAt)}</strong>
              </div>
              <div>
                <span>Cập nhật lúc</span>
                <strong>{formatDateTime(data.updatedAt)}</strong>
              </div>
            </div>
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Tiến trình</h2>
            <NotificationTimeline campaign={data} />
          </section>
        </aside>
      </div>

      {dialog && (
        <NotificationActionDialog
          action={dialog}
          campaign={data}
          onClose={() => setDialog(null)}
          onDone={(text) => {
            setDialog(null);
            setMessage(text);
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}

function NotificationStatistics({ campaign }: { campaign: NotificationCampaign }) {
  const [stats, setStats] = useState<Omit<NotificationCampaignStatistics, 'notificationId'>>(campaign);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = async () => {
    setBusy(true);
    setError(null);
    try {
      setStats(await getNotificationCampaignStatistics(campaign.id));
    } catch (refreshError) {
      setError(notificationErrorMessage(refreshError));
    } finally {
      setBusy(false);
    }
  };

  const rate = readRate(stats.readCount, stats.recipientCount);
  return (
    <section className="admin-card admin-card-pad">
      <div className="admin-section-heading">
        <h2>Thống kê</h2>
        <button className="admin-button small secondary" type="button" disabled={busy} onClick={() => void refresh()}>
          Làm mới
        </button>
      </div>
      {error && (
        <div className="admin-alert admin-alert-danger" role="alert">
          {error}
        </div>
      )}
      <div className="admin-stats admin-notification-stats">
        <div className="admin-stat">
          Người nhận
          <strong>{stats.recipientCount.toLocaleString('vi-VN')}</strong>
        </div>
        <div className="admin-stat admin-stat-brand">
          Đã đọc
          <strong>{stats.readCount.toLocaleString('vi-VN')}</strong>
        </div>
        <div className="admin-stat">
          Chưa đọc
          <strong>{stats.unreadCount.toLocaleString('vi-VN')}</strong>
        </div>
        <div className="admin-stat">
          Tỉ lệ đọc
          <strong>{rate.toLocaleString('vi-VN')}%</strong>
        </div>
      </div>
      <div
        className="admin-meter"
        role="img"
        aria-label={`Đã đọc ${stats.readCount} trên ${stats.recipientCount} người nhận`}
      >
        <span style={{ width: `${rate}%` }} />
      </div>
    </section>
  );
}

function NotificationTimeline({ campaign }: { campaign: NotificationCampaign }) {
  const steps: { label: string; at: string | null; done: boolean }[] = [
    { label: 'Tạo nháp', at: campaign.createdAt, done: true },
    { label: 'Hẹn gửi', at: campaign.scheduledAt, done: campaign.scheduledAt !== null },
    { label: 'Bắt đầu gửi', at: campaign.processingStartedAt, done: campaign.processingStartedAt !== null },
    { label: 'Đã gửi', at: campaign.sentAt, done: campaign.sentAt !== null }
  ];
  if (campaign.status === 'FAILED') steps.push({ label: 'Gửi thất bại', at: campaign.updatedAt, done: true });
  if (campaign.status === 'CANCELLED') steps.push({ label: 'Đã hủy', at: campaign.updatedAt, done: true });
  if (campaign.status === 'RETRACTED') steps.push({ label: 'Đã thu hồi', at: campaign.updatedAt, done: true });

  return (
    <ol className="admin-notification-timeline">
      {steps.map((step) => (
        <li key={step.label} className={step.done ? 'done' : undefined}>
          <strong>{step.label}</strong>
          <span>{step.done ? formatDateTime(step.at) : '—'}</span>
        </li>
      ))}
    </ol>
  );
}
