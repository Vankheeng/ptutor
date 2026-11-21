import { Fragment } from 'react';
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import { adminNavigation, canOpenModule, firstOpenModule, moduleForPath } from './adminAccess';
import { AdminState } from './components/AdminUi';
import './admin.css';

const navIcons: Record<string, React.ReactNode> = {
  chart: <><path d="M4 19V5M4 19h16M8 15l4-4 3 2 5-6" /></>,
  student: <><circle cx="12" cy="8" r="3" /><path d="M5 20v-2a7 7 0 0 1 14 0v2" /></>,
  tutor: <><path d="M3 7 12 3l9 4-9 4-9-4ZM7 9v5c0 2 2.2 4 5 4s5-2 5-4V9M21 7v7" /></>,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" /></>,
  team: <><circle cx="9" cy="8" r="3" /><path d="M2 20v-2a7 7 0 0 1 14 0v2M17 5a3 3 0 0 1 0 6m1 4a6 6 0 0 1 4 5" /></>,
  verify: <><rect x="5" y="3" width="14" height="18" rx="2" /><path d="M9 9h6m-6 4 2 2 4-4" /></>,
  teach: <><rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 21h8m-4-5v5m-4-9 3-3 2 2 3-4" /></>,
  book: <><path d="M12 6c-3-2-6-2-9-1v14c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1Zm0 0v14" /></>,
  refund: <><path d="M4 8h12a5 5 0 0 1 0 10H7m0 0 3-3m-3 3 3 3M4 4h9" /></>,
  support: <><path d="M4 11a8 8 0 0 1 16 0v6a2 2 0 0 1-2 2h-2v-6h4M4 13h4v6H6a2 2 0 0 1-2-2v-4Zm4 6a4 4 0 0 0 4 2h2" /></>
};

export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const home = firstOpenModule(user);
  const current = moduleForPath(pathname);
  const allowed = !current || canOpenModule(user, current);
  const groups = adminNavigation
    .map((group) => ({ ...group, items: group.items.filter((item) => canOpenModule(user, item)) }))
    .filter((group) => group.items.length > 0);

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <NavLink className="admin-brand" to={home?.to ?? '/admin'}>
          <img src={logo} alt="PTutor" />
        </NavLink>
        <nav className="admin-nav" aria-label="Menu quản trị">
          {groups.map((group) => (
            <Fragment key={group.label}>
              <div className="admin-nav-label">{group.label}</div>
              <div className="admin-nav-group">
                {group.items.map((item) => (
                  <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                    <svg className="admin-nav-icon" viewBox="0 0 24 24" aria-hidden="true">{navIcons[item.icon]}</svg>
                    <span>{item.label}</span>
                  </NavLink>
                ))}
              </div>
            </Fragment>
          ))}
        </nav>
        <div className="admin-sidebar-footer">
          <span title={user?.email}>{user?.email}</span>
          <button
            type="button"
            onClick={() => {
              void logout();
              navigate('/');
            }}
          >
            Đăng xuất
          </button>
        </div>
      </aside>
      <main className="admin-main">
        {allowed ? (
          <Outlet />
        ) : home ? (
          <Navigate replace to={home.to} />
        ) : (
          <div className="admin-page">
            <AdminState error="Tài khoản chưa được phân quyền truy cập trang quản trị." />
          </div>
        )}
      </main>
    </div>
  );
}
