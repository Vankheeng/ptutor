import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import logo from '../../assets/ptutor-logo.svg';
import './tutor.css';
import './tutor-layout.css';

const navigation = [
  { section: 'WORKSPACE', to: '/tutor/dashboard', label: 'Dashboard', icon: '⌂' },
  { section: 'TEACHING', to: '/tutor/sections/teaching-requests', label: 'My Teaching Requests', icon: '▤' },
  { section: 'TEACHING', to: '/tutor/student-recommendations', label: 'Gợi ý học viên', icon: '✦' },
  { section: 'TEACHING', to: '/tutor/proposals', label: 'My requests to teach', icon: '↗' },
  { section: 'TEACHING', to: '/tutor/sections/contracts', label: 'Contracts', icon: '▣' },
  { section: 'TEACHING', to: '/tutor/sections/calendar', label: 'Calendar', icon: '▦' },
  { section: 'ACCOUNT', to: '/tutor/notifications', label: 'Notifications', icon: '♢' },
  { section: 'ACCOUNT', to: '/tutor/wallet', label: 'Wallet', icon: '◈' },
  { section: 'ACCOUNT', to: '/tutor/certificates', label: 'Certificates', icon: '▤' },
  { section: 'ACCOUNT', to: '/tutor/profile', label: 'My Profile', icon: '◉' }
];

export function TutorLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const fullName =
    [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(' ') || user?.email || 'Tutor';
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
          <span className="tutor-topbar-kicker">PTUTOR / TUTOR WORKSPACE</span>
          <strong>{fullName}</strong>
        </div>
        <span className="tutor-topbar-status">
          <i /> Account active
        </span>
      </header>
      <aside className="tutor-sidebar">
        <div className="tutor-identity">
          <span className="tutor-avatar">
            {user?.profile?.avatarUrl ? <img src={user.profile.avatarUrl} alt="" /> : initials}
          </span>
          <span>
            <strong>{fullName}</strong>
            <small>Tutor</small>
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
        <nav className="tutor-nav" aria-label="Tutor workspace">
          {(['WORKSPACE', 'TEACHING', 'ACCOUNT'] as const).map((section) => (
            <div className="tutor-nav-group" key={section}>
              <span className="tutor-nav-label">{section}</span>
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
            <span aria-hidden="true">↗</span>Public home
          </NavLink>
          <button
            className="tutor-nav-link tutor-logout"
            type="button"
            onClick={() => {
              void logout();
              navigate('/');
            }}
          >
            <span aria-hidden="true">⇥</span>Sign out
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
