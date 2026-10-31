import { Fragment } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import './admin.css';

const navigation = [
  {
    label: 'Quản lý',
    items: [
      { to: '/admin/students', label: 'Học viên' },
      { to: '/admin/tutors', label: 'Gia sư' },
      { to: '/admin/notifications', label: 'Thông báo' }
    ]
  },
  {
    label: 'Kiểm duyệt',
    items: [
      { to: '/admin/tutor-profiles', label: 'Duyệt hồ sơ gia sư' },
      { to: '/admin/teaching-requests', label: 'Duyệt yêu cầu dạy' },
      { to: '/admin/studying-requests', label: 'Duyệt yêu cầu học' }
    ]
  },
  {
    label: 'Hỗ trợ',
    items: [
      { to: '/admin/refund-requests', label: 'Yêu cầu hoàn tiền' },
      { to: '/admin/complaints', label: 'Khiếu nại' },
      { to: '/admin/accounts', label: 'Tài khoản người dùng' }
    ]
  }
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
          {navigation.map((group) => (
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
        <Outlet />
      </main>
    </div>
  );
}
