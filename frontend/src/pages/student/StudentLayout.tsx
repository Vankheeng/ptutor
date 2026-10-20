import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import { Icon } from '../../components/ui/Icon';
import './student-layout.css';
import './student.css';

const navigation = [
  { section: 'WORKSPACE', to: '/student/dashboard', label: 'Tổng quan', icon: '⌂' },
  { section: 'LEARNING', to: '/student/studying-requests', label: 'Tin tìm gia sư của tôi', icon: '▤' },
  { section: 'LEARNING', to: '/student/teaching-requests', label: 'Tìm gia sư', icon: '⌕' },
  { section: 'LEARNING', to: '/student/applications', label: 'Đơn đăng ký học', icon: '↗' },
  { section: 'LEARNING', to: '/student/contracts', label: 'Hợp đồng', icon: '▣' },
  { section: 'ACCOUNT', to: '/student/notifications', label: 'Thông báo', icon: 'bell' },
  { section: 'ACCOUNT', to: '/student/wallet', label: 'Ví và thanh toán', icon: '◈' },
  { section: 'ACCOUNT', to: '/student/profile', label: 'Hồ sơ của tôi', icon: '◎' }
];

const SECTION_LABELS = { WORKSPACE: 'CHUNG', LEARNING: 'HỌC TẬP', ACCOUNT: 'TÀI KHOẢN' } as const;

export function StudentLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const fullName =
    [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || user?.email || 'Học sinh';
  const initials = fullName
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="student-shell">
      <aside className="student-sidebar">
        <NavLink className="student-brand" to="/student/dashboard" aria-label="Trang tổng quan học sinh Ptutor">
          <img src={logo} alt="Ptutor" />
        </NavLink>
        <div className="student-identity">
          <span className="student-avatar">
            {user?.profile?.avatarUrl ? <img src={user.profile.avatarUrl} alt="" /> : initials}
          </span>
          <span>
            <strong>{fullName}</strong>
            <small>Học sinh</small>
          </span>
        </div>
        <nav className="student-nav" aria-label="Khu học sinh">
          {(['WORKSPACE', 'LEARNING', 'ACCOUNT'] as const).map((section) => (
            <div className="student-nav-group" key={section}>
              <span className="student-nav-label">{SECTION_LABELS[section]}</span>
              {navigation
                .filter((item) => item.section === section)
                .map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) => (isActive ? 'student-nav-link active' : 'student-nav-link')}
                  >
                    <span aria-hidden="true">{item.icon === 'bell' ? <Icon name="bell" size={18} /> : item.icon}</span>
                    {item.label}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="student-sidebar-bottom">
          <NavLink className="student-nav-link" to="/public">
            <span aria-hidden="true">↗</span>Trang chủ
          </NavLink>
          <button
            className="student-nav-link student-logout"
            type="button"
            onClick={() => {
              void logout();
              navigate('/');
            }}
          >
            <span aria-hidden="true">⇥</span>Đăng xuất
          </button>
        </div>
      </aside>
      <main className="student-main">
        <header className="student-topbar">
          <div>
            <span className="student-topbar-kicker">PTUTOR / KHU HỌC SINH</span>
            <strong>{fullName}</strong>
          </div>
          <span className="student-topbar-status">
            <i /> Tài khoản đang hoạt động
          </span>
        </header>
        <div className="student-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
