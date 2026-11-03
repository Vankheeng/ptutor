import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  createNotificationCampaign,
  getNotificationCampaign,
  updateNotificationCampaign,
  type NotificationCampaign,
  type NotificationCampaignPayload
} from '../../services/adminNotificationApi';
import { AdminState } from './components/AdminUi';
import type { NotificationAction } from './components/NotificationActionDialog';
import {
  CONTENT_MAX,
  CONTENT_MIN,
  TITLE_MAX,
  TITLE_MIN,
  audienceLabels,
  canEdit,
  canSchedule,
  canSend,
  categoryLabels,
  notificationAudiences,
  notificationCategories,
  notificationErrorMessage
} from './notificationFormat';

const emptyDraft: NotificationCampaignPayload = { title: '', content: '', category: 'GENERAL', audience: 'ALL' };

export function AdminNotificationFormPage() {
  const { notificationId } = useParams();
  const load = useCallback(
    (signal: AbortSignal) => (notificationId ? getNotificationCampaign(notificationId, signal) : Promise.resolve(null)),
    [notificationId]
  );
  const { data, error, isLoading } = useResource(load);

  if (notificationId && !data) {
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }
  if (data && !canEdit(data.status)) {
    return (
      <div className="admin-page">
        <Link className="admin-back" to={`/admin/notifications/${data.id}`}>
          ‹ {data.title}
        </Link>
        <AdminState error="Thông báo ở trạng thái này không thể sửa." />
      </div>
    );
  }
  return <NotificationForm key={data?.id ?? 'new'} campaign={data} />;
}

function NotificationForm({ campaign }: { campaign: NotificationCampaign | null }) {
  const navigate = useNavigate();
  const [draft, setDraft] = useState<NotificationCampaignPayload>(() =>
    campaign
      ? { title: campaign.title, content: campaign.content, category: campaign.category, audience: campaign.audience }
      : emptyDraft
  );
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sent = campaign?.status === 'SENT';
  const status = campaign?.status ?? 'DRAFT';
  const titleLength = draft.title.trim().length;
  const contentLength = draft.content.trim().length;
  const titleInvalid = titleLength < TITLE_MIN;
  const contentInvalid = contentLength < CONTENT_MIN;
  const backTo = campaign ? `/admin/notifications/${campaign.id}` : '/admin/notifications';

  const change = <K extends keyof NotificationCampaignPayload>(key: K, value: NotificationCampaignPayload[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const save = async (then?: NotificationAction) => {
    setTried(true);
    if (titleInvalid || contentInvalid) return;
    setBusy(true);
    setError(null);
    const payload = { ...draft, title: draft.title.trim(), content: draft.content.trim() };
    try {
      const saved = campaign
        ? await updateNotificationCampaign(campaign.id, payload)
        : await createNotificationCampaign(payload);
      navigate(`/admin/notifications/${saved.id}`, {
        state: then ? { action: then } : { message: campaign ? 'Đã lưu thay đổi.' : 'Đã lưu nháp.' }
      });
    } catch (saveError) {
      setError(notificationErrorMessage(saveError));
      setBusy(false);
    }
  };

  return (
    <div className="admin-page">
      <Link className="admin-back" to={backTo}>
        ‹ {campaign ? campaign.title : 'Thông báo'}
      </Link>
      <h1 className="admin-title">{campaign ? 'Sửa thông báo' : 'Tạo thông báo'}</h1>
      {error && (
        <div className="admin-alert admin-alert-danger" role="alert">
          {error}
        </div>
      )}
      {sent && (
        <div className="admin-alert admin-alert-warning">
          Thay đổi sẽ cập nhật cho {campaign.recipientCount.toLocaleString('vi-VN')} người đã nhận.
        </div>
      )}
      <form
        className="admin-columns"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <div className="admin-column-main">
          <section className="admin-card admin-card-pad">
            <div className="admin-field">
              <label htmlFor="notification-title">
                Tiêu đề <span className="admin-required">*</span>
              </label>
              <input
                id="notification-title"
                maxLength={TITLE_MAX}
                value={draft.title}
                aria-invalid={tried && titleInvalid}
                aria-describedby="notification-title-help"
                onChange={(event) => change('title', event.target.value)}
              />
              <div id="notification-title-help" className="admin-field-help">
                <span className={tried && titleInvalid ? 'admin-field-error' : undefined}>
                  {tried && titleInvalid ? `Tiêu đề cần ít nhất ${TITLE_MIN} ký tự.` : ''}
                </span>
                <span>
                  {draft.title.length}/{TITLE_MAX}
                </span>
              </div>
            </div>
            <div className="admin-field">
              <label htmlFor="notification-content">
                Nội dung <span className="admin-required">*</span>
              </label>
              <textarea
                id="notification-content"
                rows={9}
                maxLength={CONTENT_MAX}
                value={draft.content}
                aria-invalid={tried && contentInvalid}
                aria-describedby="notification-content-help"
                onChange={(event) => change('content', event.target.value)}
              />
              <div id="notification-content-help" className="admin-field-help">
                <span className={tried && contentInvalid ? 'admin-field-error' : undefined}>
                  {tried && contentInvalid ? `Nội dung cần ít nhất ${CONTENT_MIN} ký tự.` : ''}
                </span>
                <span>
                  {draft.content.length}/{CONTENT_MAX}
                </span>
              </div>
            </div>
          </section>
          <section className="admin-card admin-card-pad">
            <fieldset className="admin-choice-group">
              <legend>Loại thông báo</legend>
              <div className="admin-choices admin-choices-compact">
                {notificationCategories.map((value) => (
                  <label key={value} className={draft.category === value ? 'active' : undefined}>
                    <input
                      type="radio"
                      name="notification-category"
                      checked={draft.category === value}
                      onChange={() => change('category', value)}
                    />
                    <strong>{categoryLabels[value]}</strong>
                  </label>
                ))}
              </div>
            </fieldset>
          </section>
          <section className="admin-card admin-card-pad">
            <fieldset className="admin-choice-group">
              <legend>Gửi đến</legend>
              <div className="admin-choices admin-choices-compact">
                {notificationAudiences.map((value) => (
                  <label key={value} className={draft.audience === value ? 'active' : undefined}>
                    <input
                      type="radio"
                      name="notification-audience"
                      checked={draft.audience === value}
                      disabled={sent}
                      onChange={() => change('audience', value)}
                    />
                    <strong>{audienceLabels[value]}</strong>
                  </label>
                ))}
              </div>
            </fieldset>
          </section>
          <div className="admin-action-bar">
            <Link className="admin-button secondary" to={backTo}>
              Hủy
            </Link>
            <div className="admin-button-row">
              <button className={`admin-button${sent ? '' : ' secondary'}`} type="submit" disabled={busy}>
                {campaign ? 'Lưu thay đổi' : 'Lưu nháp'}
              </button>
              {canSchedule(status) && (
                <button
                  className="admin-button outline"
                  type="button"
                  disabled={busy}
                  onClick={() => void save('schedule')}
                >
                  Lên lịch gửi
                </button>
              )}
              {canSend(status) && (
                <button
                  className="admin-button"
                  type="button"
                  disabled={busy}
                  onClick={() => void save(status === 'FAILED' ? 'retry' : 'send')}
                >
                  {status === 'FAILED' ? 'Gửi lại' : 'Gửi ngay'}
                </button>
              )}
            </div>
          </div>
        </div>
        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Xem trước</h2>
            <div className="admin-notification-preview">
              <div className="admin-notification-preview-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24">
                  <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                  <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
                </svg>
              </div>
              <div className="admin-stack">
                <strong>{draft.title.trim() || 'Tiêu đề thông báo'}</strong>
                <p>{draft.content.trim() || 'Nội dung thông báo'}</p>
                <span>Vừa xong</span>
              </div>
            </div>
          </section>
        </aside>
      </form>
    </div>
  );
}
