import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import { Icon, type IconName } from '../../components/ui/Icon';
import '../student/student-layout.css';
import '../student/student.css';

type Section = 'WORKSPACE' | 'LEARNING' | 'ACCOUNT';
type NavigationItem = { section: Section; to: string; label: string; icon: IconName };

const navigation: NavigationItem[] = [
  { section: 'WORKSPACE', to: '/student/dashboard', label: 'Tổng quan', icon: 'home' },
  { section: 'LEARNING', to: '/student/teaching-requests', label: 'Tìm kiếm gia sư', icon: 'search' },
  { section: 'LEARNING', to: '/student/tutor-recommendations', label: 'Gợi ý gia sư', icon: 'match' },
  { section: 'LEARNING', to: '/student/studying-requests', label: 'Yêu cầu tìm gia sư', icon: 'book' },
  { section: 'LEARNING', to: '/student/applications', label: 'Ứng tuyển của tôi', icon: 'edit' },
  { section: 'LEARNING', to: '/student/tutor-offers', label: 'Đề nghị từ gia sư', icon: 'send' },
  { section: 'LEARNING', to: '/student/contracts', label: 'Hợp đồng', icon: 'contract' },
  { section: 'LEARNING', to: '/student/calendar', label: 'Lịch học', icon: 'calendar' },
  { section: 'LEARNING', to: '/student/refund-requests', label: 'Yêu cầu hoàn tiền', icon: 'refund' },
  { section: 'LEARNING', to: '/student/complaints', label: 'Khiếu nại', icon: 'shield' },
  { section: 'ACCOUNT', to: '/student/notifications', label: 'Thông báo', icon: 'bell' },
  { section: 'ACCOUNT', to: '/student/wallet', label: 'Ví tiền & thanh toán', icon: 'wallet' },
  { section: 'ACCOUNT', to: '/student/wallet/transactions', label: 'Lịch sử giao dịch', icon: 'history' },
  { section: 'ACCOUNT', to: '/student/wallet/analytics', label: 'Phân tích thu chi', icon: 'dashboard' },
  { section: 'ACCOUNT', to: '/student/profile', label: 'Hồ sơ của tôi', icon: 'user' }
];

const sectionLabels: Record<Section, string> = {
  WORKSPACE: 'KHÔNG GIAN LÀM VIỆC',
  LEARNING: 'HỌC TẬP',
  ACCOUNT: 'TÀI KHOẢN'
};

export function CommonUserLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const fullName =
    [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || user?.email || 'Học viên';
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
          {(['WORKSPACE', 'LEARNING', 'ACCOUNT'] as Section[]).map((section) => (
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
                      <Icon name={item.icon} size={18} />
                    </span>
                    {item.label}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="student-sidebar-bottom">
          <NavLink className="student-nav-link" to="/public">
            <span aria-hidden="true">
              <Icon name="home" size={18} />
            </span>
            Trang chủ
          </NavLink>
          <button
            className="student-nav-link student-logout"
            type="button"
            onClick={() => {
              void logout();
              navigate('/');
            }}
          >
            <span aria-hidden="true">
              <Icon name="logout" size={18} />
            </span>
            Đăng xuất
          </button>
        </div>
      </aside>
      <main className="student-main">
        <header className="student-topbar">
          <div>
            <span className="student-topbar-kicker">PTUTOR / KHÔNG GIAN HỌC VIÊN</span>
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
