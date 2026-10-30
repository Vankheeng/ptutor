import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminUserAccount } from '../../services/adminUserAccountApi';
import { AdminAvatar, AdminState, DefinitionGrid, StatusBadge } from './components/AdminUi';
import { AccountActionDialog, type AccountAction } from './components/AccountActionDialog';
import { accountStatusMeta, actorName, daysLeft, reactivationReasonLabel, roleLabels } from './accountFormat';
import { formatDateTime, fullName } from './adminFormat';

export function AdminUserAccountDetailPage() {
  const { userId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<AccountAction | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback((signal: AbortSignal) => getAdminUserAccount(userId, signal), [userId]);
  const { data, error, isLoading } = useResource(load, revision);

  if (!data) {
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }

  const name = fullName(data.firstName, data.lastName, data.email);
  const blocked = data.status === 'BLOCKED';
  const temporary = data.suspensionType === 'TEMPORARY';
  const remaining = temporary ? daysLeft(data.suspendedUntil) : null;

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/accounts">
        ‹ Tài khoản người dùng
      </Link>
      <header className="admin-card admin-card-pad admin-account-header">
        <div className="admin-account-identity">
          <AdminAvatar name={name} size="lg" />
          <div className="admin-stack-gap">
            <h1 className="admin-title">{name}</h1>
            <div className="admin-tags">
              <span className={`admin-badge admin-badge-${data.role === 'TUTOR' ? 'brand' : 'neutral'}`}>
                {roleLabels[data.role]}
              </span>
              <StatusBadge meta={accountStatusMeta(data)} />
            </div>
          </div>
        </div>
        <div className="admin-button-row">
          {data.role === 'TUTOR' && (
            <Link className="admin-button secondary" to={`/admin/tutor-profiles/${data.userId}`}>
              Xem hồ sơ gia sư
            </Link>
          )}
          {data.status === 'ACTIVE' && (
            <button className="admin-button danger" type="button" onClick={() => setDialog('suspend')}>
              Khóa tài khoản
            </button>
          )}
          {blocked && (
            <button className="admin-button" type="button" onClick={() => setDialog('reactivate')}>
              Mở khóa
            </button>
          )}
        </div>
      </header>

      {message && (
        <div className="admin-alert admin-alert-success" role="status">
          {message}
        </div>
      )}

      {blocked && (
        <section
          className={`admin-card admin-card-pad admin-suspension admin-suspension-${temporary ? 'temporary' : 'permanent'}`}
        >
          <div className="admin-section-heading">
            <h2>{temporary ? 'Đang khóa tạm thời' : 'Đang khóa vĩnh viễn'}</h2>
            {remaining !== null && <span>Còn {remaining} ngày</span>}
          </div>
          <DefinitionGrid
            columns={3}
            items={[
              ['Khóa lúc', formatDateTime(data.suspendedAt)],
              ['Khóa đến', temporary ? formatDateTime(data.suspendedUntil) : 'Không thời hạn'],
              ['Người thực hiện', data.suspendedByUserId ? actorName(data.suspendedByName) : '—']
            ]}
          />
          <div className="admin-stack">
            <strong>Lý do</strong>
            <p className="admin-prose">{data.suspensionReason ?? '—'}</p>
          </div>
        </section>
      )}

      <section className="admin-card admin-card-pad">
        <h2>Thông tin tài khoản</h2>
        <DefinitionGrid
          columns={3}
          items={[
            ['Email', data.email],
            ['Số điện thoại', data.phone],
            ['Số lần bị khóa', data.suspensionCount],
            ['Ngày tạo', formatDateTime(data.createdAt)],
            ['Cập nhật lần cuối', formatDateTime(data.updatedAt)]
          ]}
        />
      </section>

      <section className="admin-card admin-card-pad">
        <h2>Lần mở khóa gần nhất</h2>
        {data.reactivatedAt ? (
          <>
            <DefinitionGrid
              columns={3}
              items={[
                ['Mở khóa lúc', formatDateTime(data.reactivatedAt)],
                [
                  'Người thực hiện',
                  data.reactivatedByUserId ? actorName(data.reactivatedByName) : 'Tự động khi hết hạn'
                ]
              ]}
            />
            {data.reactivationReason && (
              <div className="admin-stack">
                <strong>Lý do</strong>
                <p className="admin-prose">{reactivationReasonLabel(data.reactivationReason)}</p>
              </div>
            )}
          </>
        ) : (
          <p className="admin-account-empty">Chưa có</p>
        )}
      </section>

      {dialog && (
        <AccountActionDialog
          action={dialog}
          account={data}
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
