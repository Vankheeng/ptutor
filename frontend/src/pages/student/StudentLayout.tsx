import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import { Icon } from '../../components/ui/Icon';
import './student-layout.css';
import './student.css';

const navigation = [
  { section: 'WORKSPACE', to: '/student/dashboard', label: 'Tổng quan', icon: '⌂' },
  { section: 'LEARNING', to: '/student/teaching-requests', label: 'Tìm kiếm gia sư', icon: '⌕' },
  { section: 'LEARNING', to: '/student/studying-requests', label: 'Yêu cầu tìm gia sư', icon: '▤' },
  { section: 'LEARNING', to: '/student/applications', label: 'Ứng tuyển của tôi', icon: '↗' },
  { section: 'LEARNING', to: '/student/contracts', label: 'Hợp đồng', icon: '▣' },
  { section: 'LEARNING', to: '/student/calendar', label: 'Lịch học', icon: 'calendar' },
  { section: 'LEARNING', to: '/student/refund-requests', label: 'Yêu cầu hoàn tiền', icon: '↺' },
  { section: 'ACCOUNT', to: '/student/notifications', label: 'Thông báo', icon: 'bell' },
  { section: 'ACCOUNT', to: '/student/wallet', label: 'Ví tiền & thanh toán', icon: '◈' },
  { section: 'ACCOUNT', to: '/student/wallet/transactions', label: 'Lịch sử giao dịch', icon: '▤' },
  { section: 'ACCOUNT', to: '/student/wallet/analytics', label: 'Phân tích thu chi', icon: '▥' },
  { section: 'ACCOUNT', to: '/student/profile', label: 'Hồ sơ của tôi', icon: '◎' }
];

const sectionLabels = {
  WORKSPACE: 'KHÔNG GIAN LÀM VIỆC',
  LEARNING: 'HỌC TẬP',
  ACCOUNT: 'TÀI KHOẢN'
} as const;

export function StudentLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const fullName =
    [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || user?.email || 'Student';
  const initials = fullName
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="student-shell">
      <aside className="student-sidebar">
        <NavLink className="student-brand" to="/student/dashboard" aria-label="Bảng điều khiển học viên Ptutor">
          <img src={logo} alt="Ptutor" />
        </NavLink>
        <div className="student-identity">
          <span className="student-avatar">
            {user?.profile?.avatarUrl ? <img src={user.profile.avatarUrl} alt="" /> : initials}
          </span>
          <span>
            <strong>{fullName}</strong>
            <small>Học viên</small>
          </span>
        </div>
        <nav className="student-nav" aria-label="Không gian học viên">
          {(['WORKSPACE', 'LEARNING', 'ACCOUNT'] as const).map((section) => (
            <div className="student-nav-group" key={section}>
              <span className="student-nav-label">{sectionLabels[section]}</span>
              {navigation
                .filter((item) => item.section === section)
                .map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/student/wallet'}
                    className={({ isActive }) => (isActive ? 'student-nav-link active' : 'student-nav-link')}
                  >
                    <span aria-hidden="true">
                      {item.icon === 'bell' || item.icon === 'calendar' ? (
                        <Icon name={item.icon} size={18} />
                      ) : (
                        item.icon
                      )}
                    </span>
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
            <span className="student-topbar-kicker">PTUTOR / STUDENT WORKSPACE</span>
            <strong>{fullName}</strong>
          </div>
          <span className="student-topbar-status">
            <i /> Account active
          </span>
        </header>
        <div className="student-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
