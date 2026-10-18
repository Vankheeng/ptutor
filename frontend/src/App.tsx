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
import { StudentTutorOfferDetailPage } from './pages/student/StudentTutorOfferDetailPage';
import { StudentStudyingRequestCreatePage } from './pages/student/StudentStudyingRequestCreatePage';
import { StudentStudyingRequestEditPage } from './pages/student/StudentStudyingRequestEditPage';
import { StudentTeachingRequestsPage } from './pages/student/StudentTeachingRequestsPage';
import { StudentApplicationsPage } from './pages/student/StudentApplicationsPage';
import { StudentContractsPage } from './pages/student/StudentContractsPage';
import { StudentContractDetailPage } from './pages/student/StudentContractDetailPage';
import { StudentContractCreatePage } from './pages/student/StudentContractCreatePage';
import { NotificationsPage } from './pages/common-user/NotificationsPage';
import { WalletPage } from './pages/common-user/WalletPage';
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
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="wallet" element={<WalletPage />} />
                <Route path="sections/:section" element={<TutorComingSoonPage />} />
              </Route>
            </Route>
            <Route element={<RoleGuard roles={['STUDENT']} />}>
              <Route path="/student" element={<StudentLayout />}>
                <Route index element={<StudentDashboardPage />} />
                <Route path="dashboard" element={<StudentDashboardPage />} />
                <Route path="studying-requests" element={<StudentStudyingRequestsPage />} />
                <Route path="studying-requests/new" element={<StudentStudyingRequestCreatePage />} />
                <Route
                  path="studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId"
                  element={<StudentTutorOfferDetailPage />}
                />
                <Route path="studying-requests/:requestId" element={<StudentStudyingRequestDetailPage />} />
                <Route path="studying-requests/:requestId/edit" element={<StudentStudyingRequestEditPage />} />
                <Route path="teaching-requests" element={<StudentTeachingRequestsPage />} />
                <Route path="applications" element={<StudentApplicationsPage />} />
                <Route path="contracts" element={<StudentContractsPage />} />
                <Route path="contracts/:contractId" element={<StudentContractDetailPage />} />
                <Route
                  path="studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new"
                  element={<StudentContractCreatePage />}
                />
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="wallet" element={<WalletPage />} />
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
