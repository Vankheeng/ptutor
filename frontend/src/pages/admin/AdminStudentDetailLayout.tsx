import { useCallback, useState } from 'react';
import { Link, NavLink, Outlet, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminStudentProfile } from '../../services/adminStudentApi';
import { getAdminUserAccount } from '../../services/adminUserAccountApi';
import { accountStatusMeta } from './accountFormat';
import { formatDate, fullName } from './adminFormat';
import type { AdminStudentContext } from './adminStudentContext';
import { AccountActionDialog, type AccountAction } from './components/AccountActionDialog';
import { AdminAvatar, AdminState, StatusBadge } from './components/AdminUi';

const tabs = [
  { to: '', label: 'Hồ sơ', end: true },
  { to: 'learning-history', label: 'Lịch sử học' },
  { to: 'contracts', label: 'Hợp đồng' },
  { to: 'transactions', label: 'Giao dịch' },
  { to: 'complaints', label: 'Khiếu nại' }
];

export function AdminStudentDetailLayout() {
  const { userId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<AccountAction | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      const [account, profile] = await Promise.all([
        getAdminUserAccount(userId, signal),
        getAdminStudentProfile(userId, signal)
      ]);
      return { account, profile };
    },
    [userId]
  );
  const { data, error, isLoading } = useResource(load, revision);

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/students">
        <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24">
          <path d="M15 18l-6-6 6-6" />
        </svg>
        Danh sách học viên
      </Link>
      {!data ? (
        <AdminState loading={isLoading} error={error} />
      ) : (
        <>
          <section className="admin-card admin-tutor-header">
            <div className="admin-tutor-identity">
              <AdminAvatar
                size="lg"
                name={fullName(data.account.firstName, data.account.lastName, data.account.email)}
                url={data.profile.avatarUrl}
              />
              <div>
                <div className="admin-tutor-name">
                  <h1>{fullName(data.account.firstName, data.account.lastName, data.account.email)}</h1>
                  <StatusBadge meta={accountStatusMeta(data.account)} />
                </div>
                <div className="admin-tutor-meta">
                  <span>{data.account.email}</span>
                  {data.account.phone && <span>{data.account.phone}</span>}
                  <span>Đăng ký {formatDate(data.account.createdAt)}</span>
                </div>
              </div>
              <div className="admin-button-row admin-header-actions">
                {data.account.status === 'ACTIVE' && (
                  <button className="admin-button danger-outline" type="button" onClick={() => setDialog('suspend')}>
                    Khóa tài khoản
                  </button>
                )}
                {data.account.status === 'BLOCKED' && (
                  <button className="admin-button" type="button" onClick={() => setDialog('reactivate')}>
                    Mở khóa
                  </button>
                )}
              </div>
            </div>
            <nav className="admin-tutor-tabs" aria-label="Thông tin học viên">
              {tabs.map((tab) => (
                <NavLink key={tab.label} to={tab.to} end={tab.end}>
                  {tab.label}
                </NavLink>
              ))}
            </nav>
          </section>
          {message && (
            <div className="admin-alert admin-alert-success" role="status">
              {message}
            </div>
          )}
          <Outlet context={data satisfies AdminStudentContext} />
          {dialog && (
            <AccountActionDialog
              action={dialog}
              account={data.account}
              onClose={() => setDialog(null)}
              onDone={(text) => {
                setDialog(null);
                setMessage(text);
                setRevision((value) => value + 1);
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
