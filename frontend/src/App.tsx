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
import { AdminLayout } from './pages/admin/AdminLayout';
import { AdminTutorProfilesPage } from './pages/admin/AdminTutorProfilesPage';
import { AdminCertificateQueuePage } from './pages/admin/AdminCertificateQueuePage';
import { AdminTutorDetailLayout } from './pages/admin/AdminTutorDetailLayout';
import { AdminTutorReviewPage } from './pages/admin/AdminTutorReviewPage';
import { AdminTutorContractsPage } from './pages/admin/AdminTutorContractsPage';
import { AdminTutorRatingsPage } from './pages/admin/AdminTutorRatingsPage';
import { AdminTutorTeachingHistoryPage } from './pages/admin/AdminTutorTeachingHistoryPage';
import { AdminTutorScorePage } from './pages/admin/AdminTutorScorePage';
import { AdminRefundRequestsPage } from './pages/admin/AdminRefundRequestsPage';
import { AdminRefundRequestDetailPage } from './pages/admin/AdminRefundRequestDetailPage';
import { StudentContractLessonsPage } from './pages/student/StudentContractLessonsPage';
import { StudentRefundRequestCreatePage } from './pages/student/StudentRefundRequestCreatePage';
import { StudentRefundRequestsPage } from './pages/student/StudentRefundRequestsPage';
import { StudentRefundRequestDetailPage } from './pages/student/StudentRefundRequestDetailPage';
import { TutorRefundRequestsPage } from './pages/tutor/TutorRefundRequestsPage';
import { TutorRefundRequestDetailPage } from './pages/tutor/TutorRefundRequestDetailPage';
import { AdminRequestReviewPage } from './pages/admin/AdminRequestReviewPage';
import { AdminComplaintsPage } from './pages/admin/AdminComplaintsPage';
import { AdminComplaintDetailPage } from './pages/admin/AdminComplaintDetailPage';
import { AdminUserAccountsPage } from './pages/admin/AdminUserAccountsPage';
import { AdminUserAccountDetailPage } from './pages/admin/AdminUserAccountDetailPage';
import { AdminStudentsPage } from './pages/admin/AdminStudentsPage';
import { AdminStudentDetailLayout } from './pages/admin/AdminStudentDetailLayout';
import { AdminStudentProfilePage } from './pages/admin/AdminStudentProfilePage';
import { AdminStudentLearningHistoryPage } from './pages/admin/AdminStudentLearningHistoryPage';
import { AdminStudentContractsPage } from './pages/admin/AdminStudentContractsPage';
import { AdminStudentTransactionsPage } from './pages/admin/AdminStudentTransactionsPage';
import { AdminStudentComplaintsPage } from './pages/admin/AdminStudentComplaintsPage';
import { AdminNotificationsPage } from './pages/admin/AdminNotificationsPage';
import { AdminNotificationFormPage } from './pages/admin/AdminNotificationFormPage';
import { AdminNotificationDetailPage } from './pages/admin/AdminNotificationDetailPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/public" element={<HomeLanding />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<RoleGuard roles={['ADMIN', 'EMPLOYEE']} />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<Navigate replace to="tutor-profiles" />} />
                <Route path="tutor-profiles" element={<AdminTutorProfilesPage />} />
                <Route path="tutor-profiles/certificates" element={<AdminCertificateQueuePage />} />
                <Route path="teaching-requests" element={<AdminRequestReviewPage key="teaching" kind="teaching" />} />
                <Route path="studying-requests" element={<AdminRequestReviewPage key="studying" kind="studying" />} />
                <Route path="tutor-profiles/:userId" element={<AdminTutorDetailLayout />}>
                  <Route index element={<AdminTutorReviewPage />} />
                  <Route path="contracts" element={<AdminTutorContractsPage />} />
                  <Route path="ratings" element={<AdminTutorRatingsPage />} />
                  <Route path="teaching-history" element={<AdminTutorTeachingHistoryPage />} />
                  <Route path="score" element={<AdminTutorScorePage />} />
                </Route>
                <Route path="students" element={<AdminStudentsPage />} />
                <Route path="students/:userId" element={<AdminStudentDetailLayout />}>
                  <Route index element={<AdminStudentProfilePage />} />
                  <Route path="learning-history" element={<AdminStudentLearningHistoryPage />} />
                  <Route path="contracts" element={<AdminStudentContractsPage />} />
                  <Route path="transactions" element={<AdminStudentTransactionsPage />} />
                  <Route path="complaints" element={<AdminStudentComplaintsPage />} />
                </Route>
                <Route path="tutors" element={<AdminTutorProfilesPage key="manage" mode="manage" />} />
                <Route path="tutors/:userId" element={<AdminTutorDetailLayout key="manage" mode="manage" />}>
                  <Route index element={<AdminTutorReviewPage />} />
                  <Route path="contracts" element={<AdminTutorContractsPage />} />
                  <Route path="ratings" element={<AdminTutorRatingsPage />} />
                  <Route path="teaching-history" element={<AdminTutorTeachingHistoryPage />} />
                  <Route path="score" element={<AdminTutorScorePage />} />
                </Route>
                <Route path="refund-requests" element={<AdminRefundRequestsPage />} />
                <Route path="refund-requests/:refundRequestId" element={<AdminRefundRequestDetailPage />} />
                <Route path="complaints" element={<AdminComplaintsPage />} />
                <Route path="complaints/:complaintId" element={<AdminComplaintDetailPage />} />
                <Route path="accounts" element={<AdminUserAccountsPage />} />
                <Route path="accounts/:userId" element={<AdminUserAccountDetailPage />} />
                <Route path="notifications" element={<AdminNotificationsPage />} />
                <Route path="notifications/new" element={<AdminNotificationFormPage />} />
                <Route path="notifications/:notificationId" element={<AdminNotificationDetailPage />} />
                <Route path="notifications/:notificationId/edit" element={<AdminNotificationFormPage />} />
              </Route>
            </Route>
            <Route element={<RoleGuard roles={['TUTOR']} />}>
              <Route path="/tutor" element={<TutorLayout />}>
                <Route index element={<TutorDashboardPage />} />
                <Route path="dashboard" element={<TutorDashboardPage />} />
                <Route path="profile" element={<TutorProfilePage />} />
                <Route path="certificates" element={<TutorCertificatesPage />} />
                <Route path="studying-requests/:requestId" element={<TutorStudyingRequestPage />} />
                <Route path="proposals" element={<TutorProposalsPage />} />
                <Route path="refund-requests" element={<TutorRefundRequestsPage />} />
                <Route path="refund-requests/:refundRequestId" element={<TutorRefundRequestDetailPage />} />
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
                <Route path="contracts/:contractId/lessons" element={<StudentContractLessonsPage />} />
                <Route path="contracts/:contractId/refund-request" element={<StudentRefundRequestCreatePage />} />
                <Route path="refund-requests" element={<StudentRefundRequestsPage />} />
                <Route path="refund-requests/:refundRequestId" element={<StudentRefundRequestDetailPage />} />
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
