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
import { StudentLayout } from './pages/student/StudentLayout';
import { StudentDashboardPage } from './pages/student/StudentDashboardPage';
import { StudentStudyingRequestsPage } from './pages/student/StudentStudyingRequestsPage';
import { StudentStudyingRequestDetailPage } from './pages/student/StudentStudyingRequestDetailPage';
import { StudentTeachingRequestsPage } from './pages/student/StudentTeachingRequestsPage';
import { StudentApplicationsPage } from './pages/student/StudentApplicationsPage';
import { StudentContractsPage } from './pages/student/StudentContractsPage';
import { StudentContractDetailPage } from './pages/student/StudentContractDetailPage';
import { StudentNotificationsPage } from './pages/student/StudentNotificationsPage';
import { StudentWalletPage } from './pages/student/StudentWalletPage';
import { StudentProfilePage } from './pages/student/StudentProfilePage';

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
            <Route element={<RoleGuard roles={['STUDENT']} />}>
              <Route path="/student" element={<StudentLayout />}>
                <Route index element={<StudentDashboardPage />} />
                <Route path="dashboard" element={<StudentDashboardPage />} />
                <Route path="studying-requests" element={<StudentStudyingRequestsPage />} />
                <Route path="studying-requests/:requestId" element={<StudentStudyingRequestDetailPage />} />
                <Route path="teaching-requests" element={<StudentTeachingRequestsPage />} />
                <Route path="applications" element={<StudentApplicationsPage />} />
                <Route path="contracts" element={<StudentContractsPage />} />
                <Route path="contracts/:contractId" element={<StudentContractDetailPage />} />
                <Route path="notifications" element={<StudentNotificationsPage />} />
                <Route path="wallet" element={<StudentWalletPage />} />
                <Route path="profile" element={<StudentProfilePage />} />
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
