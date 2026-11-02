import { Fragment } from 'react';
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import { adminNavigation, canOpenModule, firstOpenModule, moduleForPath } from './adminAccess';
import { AdminState } from './components/AdminUi';
import './admin.css';

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
          <img src={logo} alt="" />
          <span>PTutor Admin</span>
        </NavLink>
        <nav className="admin-nav" aria-label="Menu quản trị">
          {groups.map((group) => (
            <Fragment key={group.label}>
              <div className="admin-nav-label">{group.label}</div>
              <div className="admin-nav-group">
                {group.items.map((item) => (
                  <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                    {item.label}
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
