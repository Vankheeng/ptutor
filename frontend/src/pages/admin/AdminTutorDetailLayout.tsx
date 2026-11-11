import { useCallback, useState } from 'react';
import { Link, NavLink, Outlet, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminTutor } from '../../services/adminTutorApi';
import { accountStatusMeta, formatDate, fullName, profileStatusMeta } from './adminFormat';
import type { AdminTutorContext } from './adminTutorContext';
import { AccountActionDialog, type AccountAction } from './components/AccountActionDialog';
import { AdminAvatar, AdminState, StatusBadge } from './components/AdminUi';

const tabs = [
  { to: '', label: 'Hồ sơ & chứng chỉ', end: true },
  { to: 'contracts', label: 'Hợp đồng' },
  { to: 'ratings', label: 'Đánh giá' },
  { to: 'teaching-history', label: 'Lịch sử dạy' },
  { to: 'score', label: 'Điểm đề xuất' }
];

/** `review` is opened from the profile review queue; `manage` from the Gia sư menu, with account actions. */
export function AdminTutorDetailLayout({ mode = 'review' }: { mode?: 'review' | 'manage' }) {
  const { userId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<AccountAction | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getAdminTutor(userId, signal), [userId]);
  const { data: tutor, error, isLoading } = useResource(load, revision);
  const reload = useCallback(() => setRevision((value) => value + 1), []);

  return (
    <div className="admin-page">
      <Link className="admin-back" to={mode === 'review' ? '/admin/tutor-profiles' : '/admin/tutors'}>
        <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24">
          <path d="M15 18l-6-6 6-6" />
        </svg>
        {mode === 'review' ? 'Danh sách hồ sơ' : 'Danh sách gia sư'}
      </Link>
      {!tutor ? (
        <AdminState loading={isLoading} error={error} />
      ) : (
        <>
          <section className="admin-card admin-tutor-header">
            <div className="admin-tutor-identity">
              <AdminAvatar
                size="lg"
                name={fullName(tutor.firstName, tutor.lastName, tutor.email)}
                url={tutor.avatarUrl}
              />
              <div>
                <div className="admin-tutor-name">
                  <h1>{fullName(tutor.firstName, tutor.lastName, tutor.email)}</h1>
                  <StatusBadge meta={profileStatusMeta[tutor.profileStatus]} />
                  <StatusBadge meta={accountStatusMeta[tutor.accountStatus]} />
                </div>
                <div className="admin-tutor-meta">
                  <span>{tutor.email}</span>
                  {tutor.phone && <span>{tutor.phone}</span>}
                  <span>Đăng ký {formatDate(tutor.createdAt)}</span>
                  <span>Cập nhật {formatDate(tutor.updatedAt)}</span>
                </div>
              </div>
              {mode === 'manage' && (
                <div className="admin-button-row admin-header-actions">
                  {tutor.accountStatus === 'ACTIVE' && (
                    <button className="admin-button danger-outline" type="button" onClick={() => setDialog('suspend')}>
                      Khóa tài khoản
                    </button>
                  )}
                  {tutor.accountStatus === 'BLOCKED' && (
                    <button className="admin-button" type="button" onClick={() => setDialog('reactivate')}>
                      Mở khóa
                    </button>
                  )}
                </div>
              )}
            </div>
            <nav className="admin-tutor-tabs" aria-label="Thông tin gia sư">
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
          <Outlet context={{ tutor, reload } satisfies AdminTutorContext} />
          {dialog && (
            <AccountActionDialog
              action={dialog}
              account={{ ...tutor, status: tutor.accountStatus }}
              onClose={() => setDialog(null)}
              onDone={(text) => {
                setDialog(null);
                setMessage(text);
                reload();
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
