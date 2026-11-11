import { HomeLanding } from '../components/home/HomeLanding';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export default function HomePage() {
  const { user } = useAuth();
  if (user?.role === 'TUTOR') return <Navigate replace to="/tutor/dashboard" />;
  if (user?.role === 'STUDENT') return <Navigate replace to="/student/dashboard" />;
  if (user?.role === 'ADMIN' || user?.role === 'EMPLOYEE') return <Navigate replace to="/admin/tutor-profiles" />;
  return <HomeLanding />;
}
