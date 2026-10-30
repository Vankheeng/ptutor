import { useState } from 'react';
import {
  reactivateUserAccount,
  suspendUserAccount,
  type AdminUserAccount,
  type SuspensionType
} from '../../../services/adminUserAccountApi';
import {
  REASON_MAX,
  REASON_MIN,
  accountErrorMessage,
  daysFromNow,
  suspensionPresets,
  toLocalInputValue
} from '../accountFormat';
import { formatDateTime, fullName } from '../adminFormat';
import { AdminModal, ReasonField } from './AdminUi';

export type AccountAction = 'suspend' | 'reactivate';

interface Props {
  action: AccountAction;
  account: AdminUserAccount;
  onClose: () => void;
  onDone: (message: string) => void;
}

const suspensionTypes: { value: SuspensionType; label: string }[] = [
  { value: 'TEMPORARY', label: 'Tạm thời' },
  { value: 'PERMANENT', label: 'Vĩnh viễn' }
];

export function AccountActionDialog({ action, account, onClose, onDone }: Props) {
  const suspending = action === 'suspend';
  const [type, setType] = useState<SuspensionType>('TEMPORARY');
  const [until, setUntil] = useState(() => daysFromNow(7));
  const [minUntil] = useState(() => toLocalInputValue(new Date()));
  const [untilInvalid, setUntilInvalid] = useState(false);
  const [reason, setReason] = useState('');
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reasonInvalid = reason.trim().length < REASON_MIN;

  const submit = async () => {
    setTried(true);
    const untilDate = until ? new Date(until) : null;
    const untilPast = !untilDate || Number.isNaN(untilDate.getTime()) || untilDate.getTime() <= Date.now();
    const needsUntil = suspending && type === 'TEMPORARY';
    setUntilInvalid(needsUntil && untilPast);
    if (reasonInvalid || (needsUntil && untilPast)) return;
    setBusy(true);
    setError(null);
    try {
      if (suspending) {
        await suspendUserAccount(account.userId, {
          type,
          reason: reason.trim(),
          suspendedUntil: type === 'TEMPORARY' && untilDate ? untilDate.toISOString() : null
        });
        onDone('Đã khóa tài khoản.');
      } else {
        await reactivateUserAccount(account.userId, reason.trim());
        onDone('Đã mở khóa tài khoản.');
      }
    } catch (actionError) {
      setError(accountErrorMessage(actionError));
      setBusy(false);
    }
  };

  return (
    <AdminModal
      title={suspending ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
      subtitle={`${fullName(account.firstName, account.lastName, account.email)} · ${account.email}`}
      size="sm"
      onClose={onClose}
      footer={
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" onClick={onClose}>
            Huỷ
          </button>
          <button
            className={`admin-button${suspending ? ' danger' : ''}`}
            type="button"
            disabled={busy}
            onClick={() => void submit()}
          >
            {suspending ? 'Khóa tài khoản' : 'Mở khóa'}
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
        {suspending ? (
          <>
            <fieldset className="admin-choice-group">
              <legend>Hình thức khóa</legend>
              <div className="admin-choices">
                {suspensionTypes.map((option) => (
                  <label key={option.value} className={type === option.value ? 'active' : undefined}>
                    <input
                      type="radio"
                      name="suspension-type"
                      checked={type === option.value}
                      onChange={() => setType(option.value)}
                    />
                    <strong>{option.label}</strong>
                  </label>
                ))}
              </div>
            </fieldset>
            {type === 'TEMPORARY' && (
              <div className="admin-field">
                <label htmlFor="suspension-until">
                  Khóa đến <span className="admin-required">*</span>
                </label>
                <input
                  id="suspension-until"
                  type="datetime-local"
                  value={until}
                  min={minUntil}
                  aria-invalid={untilInvalid}
                  onChange={(event) => {
                    setUntil(event.target.value);
                    setUntilInvalid(false);
                  }}
                />
                <div className="admin-presets">
                  <div role="group" aria-label="Chọn nhanh">
                    {suspensionPresets.map((preset) => (
                      <button
                        key={preset.days}
                        type="button"
                        onClick={() => {
                          setUntil(daysFromNow(preset.days));
                          setUntilInvalid(false);
                        }}
                      >
                        {preset.label}
                      </button>
                    ))}
                  </div>
                </div>
                {untilInvalid && <span className="admin-field-error">Thời điểm mở khóa phải sau hiện tại.</span>}
              </div>
            )}
          </>
        ) : (
          account.status === 'BLOCKED' && (
            <div className={`admin-alert admin-alert-${account.suspensionType === 'TEMPORARY' ? 'warning' : 'danger'}`}>
              <strong>{account.suspensionType === 'TEMPORARY' ? 'Khóa tạm thời' : 'Khóa vĩnh viễn'}</strong>
              {account.suspendedUntil && <span>đến {formatDateTime(account.suspendedUntil)}</span>}
            </div>
          )
        )}
        <ReasonField
          id="account-action-reason"
          label={suspending ? 'Lý do khóa' : 'Lý do mở khóa'}
          value={reason}
          minLength={REASON_MIN}
          maxLength={REASON_MAX}
          showError={tried}
          emptyMessage="Vui lòng nhập lý do."
          onChange={setReason}
        />
      </div>
    </AdminModal>
  );
}
