import './App.css';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { GuestRoute, ProtectedRoute, RoleGuard } from './auth/RouteGuards';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import { TutorDashboardPage } from './pages/tutor/TutorDashboardPage';
import { TutorProfilePage } from './pages/tutor/TutorProfilePage';
import { TutorCertificatesPage } from './pages/tutor/TutorCertificatesPage';
import { TutorLayout } from './pages/tutor/TutorLayout';
import { TutorComingSoonPage } from './pages/tutor/TutorComingSoonPage';
import { TutorStudyingRequestPage } from './pages/tutor/TutorStudyingRequestPage';
import { TutorProposalsPage } from './pages/tutor/TutorProposalsPage';
import { HomeLanding } from './components/home/HomeLanding';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/public" element={<HomeLanding />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<RoleGuard roles={['TUTOR']} />}>
              <Route path="/tutor" element={<TutorLayout />}>
                <Route index element={<TutorDashboardPage />} />
                <Route path="dashboard" element={<TutorDashboardPage />} />
                <Route path="profile" element={<TutorProfilePage />} />
                <Route path="certificates" element={<TutorCertificatesPage />} />
                <Route path="studying-requests/:requestId" element={<TutorStudyingRequestPage />} />
                <Route path="proposals" element={<TutorProposalsPage />} />
                <Route path="sections/:section" element={<TutorComingSoonPage />} />
              </Route>
            </Route>
          </Route>
          <Route element={<GuestRoute />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          </Route>
          <Route path="*" element={<Navigate replace to="/" />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
