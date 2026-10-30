import { useState } from 'react';
import {
  cancelNotificationSchedule,
  deleteNotificationCampaign,
  retryNotificationCampaign,
  scheduleNotificationCampaign,
  sendNotificationCampaign,
  type NotificationCampaign
} from '../../../services/adminNotificationApi';
import { toLocalInputValue } from '../accountFormat';
import { formatDateTime } from '../adminFormat';
import {
  SCHEDULE_LEAD_MS,
  audienceLabels,
  categoryLabels,
  inMinutes,
  notificationErrorMessage,
  notificationStatusMeta,
  tomorrowAt
} from '../notificationFormat';
import { AdminModal } from './AdminUi';

export type NotificationAction = 'send' | 'retry' | 'schedule' | 'cancel-schedule' | 'cancel' | 'retract';

interface Props {
  action: NotificationAction;
  campaign: NotificationCampaign;
  onClose: () => void;
  onDone: (message: string, updated: NotificationCampaign) => void;
}

const titles: Record<NotificationAction, string> = {
  send: 'Gửi thông báo ngay?',
  retry: 'Gửi lại thông báo?',
  schedule: 'Lên lịch gửi',
  'cancel-schedule': 'Hủy lịch gửi?',
  cancel: 'Hủy thông báo?',
  retract: 'Thu hồi thông báo?'
};

const confirmLabels: Record<NotificationAction, string> = {
  send: 'Gửi ngay',
  retry: 'Gửi lại',
  schedule: 'Lên lịch',
  'cancel-schedule': 'Hủy lịch',
  cancel: 'Hủy thông báo',
  retract: 'Thu hồi'
};

const successMessages: Record<NotificationAction, string> = {
  send: 'Đã gửi thông báo.',
  retry: 'Đã gửi lại thông báo.',
  schedule: 'Đã lên lịch gửi.',
  'cancel-schedule': 'Đã hủy lịch gửi.',
  cancel: 'Đã hủy thông báo.',
  retract: 'Đã thu hồi thông báo.'
};

const presets = [
  { label: 'Sau 1 giờ', value: () => inMinutes(60) },
  { label: 'Sáng mai 08:00', value: () => tomorrowAt(8) },
  { label: 'Tối mai 20:00', value: () => tomorrowAt(20) }
];

export function NotificationActionDialog({ action, campaign, onClose, onDone }: Props) {
  const [when, setWhen] = useState(() =>
    campaign.scheduledAt ? toLocalInputValue(new Date(campaign.scheduledAt)) : inMinutes(60)
  );
  const [minWhen] = useState(() => inMinutes(5));
  const [whenInvalid, setWhenInvalid] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const destructive = action === 'cancel' || action === 'retract';

  const run = () => {
    switch (action) {
      case 'send':
        return sendNotificationCampaign(campaign.id);
      case 'retry':
        return retryNotificationCampaign(campaign.id);
      case 'schedule':
        return scheduleNotificationCampaign(campaign.id, new Date(when).toISOString());
      case 'cancel-schedule':
        return cancelNotificationSchedule(campaign.id);
      default:
        return deleteNotificationCampaign(campaign.id);
    }
  };

  const submit = async () => {
    if (action === 'schedule') {
      const time = new Date(when).getTime();
      const tooSoon = Number.isNaN(time) || time < Date.now() + SCHEDULE_LEAD_MS;
      setWhenInvalid(tooSoon);
      if (tooSoon) return;
    }
    setBusy(true);
    setError(null);
    try {
      onDone(successMessages[action], await run());
    } catch (actionError) {
      setError(notificationErrorMessage(actionError));
      setBusy(false);
    }
  };

  return (
    <AdminModal
      title={titles[action]}
      subtitle={`${campaign.title} · ${notificationStatusMeta[campaign.status].label}`}
      size="sm"
      onClose={onClose}
      footer={
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" onClick={onClose}>
            Không
          </button>
          <button
            className={`admin-button${destructive ? ' danger' : ''}`}
            type="button"
            disabled={busy}
            onClick={() => void submit()}
          >
            {confirmLabels[action]}
          </button>
        </div>
      }
    >
      <div className="admin-modal-pad admin-stack-gap">
        {error && (
          <div className="admin-alert admin-alert-danger" role="alert">
            {error}
          </div>
        )}
        {(action === 'send' || action === 'retry') && (
          <div className="admin-key-values">
            <div>
              <span>Loại</span>
              <strong>{categoryLabels[campaign.category]}</strong>
            </div>
            <div>
              <span>Gửi đến</span>
              <strong>{audienceLabels[campaign.audience]}</strong>
            </div>
          </div>
        )}
        {action === 'schedule' && (
          <div className="admin-field">
            <label htmlFor="notification-schedule">
              Thời điểm gửi <span className="admin-required">*</span>
            </label>
            <input
              id="notification-schedule"
              type="datetime-local"
              value={when}
              min={minWhen}
              aria-invalid={whenInvalid}
              onChange={(event) => {
                setWhen(event.target.value);
                setWhenInvalid(false);
              }}
            />
            <div className="admin-presets">
              <div role="group" aria-label="Chọn nhanh">
                {presets.map((preset) => (
                  <button
                    key={preset.label}
                    type="button"
                    onClick={() => {
                      setWhen(preset.value());
                      setWhenInvalid(false);
                    }}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>
            {whenInvalid && <span className="admin-field-error">Tối thiểu 5 phút sau hiện tại.</span>}
          </div>
        )}
        {action === 'cancel-schedule' && (
          <div className="admin-alert admin-alert-warning">
            Hẹn gửi {formatDateTime(campaign.scheduledAt)}. Thông báo sẽ trở về nháp.
          </div>
        )}
        {action === 'cancel' && <div className="admin-alert admin-alert-danger">Không thể hoàn tác.</div>}
        {action === 'retract' && (
          <div className="admin-alert admin-alert-danger">
            Thông báo sẽ bị gỡ khỏi hộp thư của {campaign.recipientCount.toLocaleString('vi-VN')} người nhận.
          </div>
        )}
      </div>
    </AdminModal>
  );
}
