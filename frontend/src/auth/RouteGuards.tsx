import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './useAuth';
import type { UserRole } from '../types/api';

export function ProtectedRoute() {
  const { isLoading, isAuthenticated } = useAuth();
  if (isLoading) return <div className="route-loading">Đang khôi phục phiên đăng nhập...</div>;
  return isAuthenticated ? <Outlet /> : <Navigate replace to="/login" />;
}

export function GuestRoute() {
  const { isLoading, isAuthenticated } = useAuth();
  if (isLoading) return <div className="route-loading">Đang tải...</div>;
  return isAuthenticated ? <Navigate replace to="/" /> : <Outlet />;
}

export function RoleGuard({ roles }: { roles: UserRole[] }) {
  const { user } = useAuth();
  return user && roles.includes(user.role) ? <Outlet /> : <Navigate replace to="/" />;
}
