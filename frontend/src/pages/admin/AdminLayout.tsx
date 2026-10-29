import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import './admin.css';

const navigation = [
  { to: '/admin/tutor-profiles', label: 'Duyệt hồ sơ gia sư' },
  { to: '/admin/refund-requests', label: 'Yêu cầu hoàn tiền' }
];

export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <NavLink className="admin-brand" to="/admin/tutor-profiles">
          <img src={logo} alt="" />
          <span>PTutor Admin</span>
        </NavLink>
        <nav className="admin-nav" aria-label="Menu quản trị">
          {navigation.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
              {item.label}
            </NavLink>
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
        <Outlet />
      </main>
    </div>
  );
}
