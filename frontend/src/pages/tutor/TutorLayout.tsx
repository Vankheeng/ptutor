import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import './tutor.css';
import './tutor-layout.css';

const navigation = [
  { section: 'WORKSPACE', to: '/tutor/dashboard', label: 'Tổng quan', icon: '⌂' },
  { section: 'TEACHING', to: '/tutor/sections/teaching-requests', label: 'Yêu cầu dạy của tôi', icon: '▤' },
  { section: 'TEACHING', to: '/tutor/proposals', label: 'Đề xuất giảng dạy', icon: '↗' },
  { section: 'TEACHING', to: '/tutor/sections/contracts', label: 'Hợp đồng', icon: '▣' },
  { section: 'TEACHING', to: '/tutor/refund-requests', label: 'Yêu cầu hoàn tiền', icon: '↺' },
  { section: 'TEACHING', to: '/tutor/sections/calendar', label: 'Lịch dạy', icon: '▦' },
  { section: 'ACCOUNT', to: '/tutor/notifications', label: 'Thông báo', icon: '♢' },
  { section: 'ACCOUNT', to: '/tutor/wallet', label: 'Ví tiền', icon: '◈' },
  { section: 'ACCOUNT', to: '/tutor/certificates', label: 'Chứng chỉ', icon: '▤' },
  { section: 'ACCOUNT', to: '/tutor/profile', label: 'Hồ sơ của tôi', icon: '◉' }
];

const sectionLabels = { WORKSPACE: 'KHÔNG GIAN', TEACHING: 'GIẢNG DẠY', ACCOUNT: 'TÀI KHOẢN' } as const;

export function TutorLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const fullName =
    [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || user?.email || 'Gia sư';
  const initials = fullName
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className={`tutor-shell${sidebarCollapsed ? ' tutor-shell-collapsed' : ''}`}>
      <header className="tutor-topbar">
        <div className="tutor-topbar-brand">
          <NavLink className="tutor-brand" to="/tutor/dashboard" aria-label="Ptutor dashboard">
            <img src={logo} alt="Ptutor" />
          </NavLink>
        </div>
        <div className="tutor-topbar-workspace">
          <span className="tutor-topbar-kicker">PTUTOR / KHÔNG GIAN GIA SƯ</span>
          <strong>{fullName}</strong>
        </div>
        <span className="tutor-topbar-status">
          <i /> Tài khoản đang hoạt động
        </span>
      </header>
      <aside className="tutor-sidebar">
        <div className="tutor-identity">
          <span className="tutor-avatar">
            {user?.profile?.avatarUrl ? <img src={user.profile.avatarUrl} alt="" /> : initials}
          </span>
          <span>
            <strong>{fullName}</strong>
            <small>Gia sư</small>
          </span>
        </div>
        <button
          className="tutor-sidebar-toggle"
          type="button"
          aria-label={sidebarCollapsed ? 'Expand navigation' : 'Collapse navigation'}
          aria-pressed={sidebarCollapsed}
          onClick={() => setSidebarCollapsed((value) => !value)}
        >
          <svg aria-hidden="true" viewBox="0 0 16 16">
            <path d={sidebarCollapsed ? 'm6 4 4 4-4 4' : 'm10 4-4 4 4 4'} />
          </svg>
        </button>
        <nav className="tutor-nav" aria-label="Không gian gia sư">
          {(['WORKSPACE', 'TEACHING', 'ACCOUNT'] as const).map((section) => (
            <div className="tutor-nav-group" key={section}>
              <span className="tutor-nav-label">{sectionLabels[section]}</span>
              {navigation
                .filter((item) => item.section === section)
                .map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) => (isActive ? 'tutor-nav-link active' : 'tutor-nav-link')}
                  >
                    <span aria-hidden="true">{item.icon}</span>
                    {item.label}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="tutor-sidebar-bottom">
          <NavLink className="tutor-nav-link" to="/public">
            <span aria-hidden="true">↗</span>Trang chủ
          </NavLink>
          <button
            className="tutor-nav-link tutor-logout"
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
      <main className="tutor-main">
        <div className="tutor-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
