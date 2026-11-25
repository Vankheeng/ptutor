import './App.css';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { GlobalToastViewport } from './components/ui/GlobalToastViewport';
import { GuestRoute, ProtectedRoute, RoleGuard } from './auth/RouteGuards';
import HomePage from './pages/HomePage';
import AdminStatisticsPage from './pages/AdminStatisticsPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import { TutorDashboardPage } from './pages/tutor/TutorDashboardPage';
import { TutorLayout } from './pages/tutor/TutorLayout';
import { TutorComingSoonPage } from './pages/tutor/TutorComingSoonPage';
import { TutorStudyingRequestPage } from './pages/tutor/TutorStudyingRequestPage';
import { TutorStudentSearchPage } from './pages/tutor/TutorStudentSearchPage';
import { TutorProposalsPage } from './pages/tutor/TutorProposalsPage';
import { TutorMyApplicationsPage } from './pages/tutor/TutorMyApplicationsPage';
import { TutorContractsPage } from './pages/tutor/TutorContractsPage';
import { TutorContractDetailPage } from './pages/tutor/TutorContractDetailPage';
import { TutorStudentRecommendationsPage } from './pages/tutor/TutorStudentRecommendationsPage';
import { HomeLanding } from './components/home/HomeLanding';
import { StudentLayout } from './pages/student/StudentLayout';
import { StudentDashboardPage } from './pages/student/StudentDashboardPage';
import { StudentStudyingRequestsPage } from './pages/student/StudentStudyingRequestsPage';
import { StudentStudyingRequestDetailPage } from './pages/student/StudentStudyingRequestDetailPage';
import { OffersPage } from './pages/common-user/OffersPage';
import { OfferDetailPage } from './pages/student/OfferDetailPage';
import { StudentStudyingRequestCreatePage } from './pages/student/StudentStudyingRequestCreatePage';
import { StudentStudyingRequestEditPage } from './pages/student/StudentStudyingRequestEditPage';
import { StudentTeachingRequestsPage } from './pages/student/StudentTeachingRequestsPage';
import { StudentTeachingRequestDetailPage } from './pages/student/StudentTeachingRequestDetailPage';
import { StudentApplicationsPage } from './pages/student/StudentApplicationsPage';
import { StudentApplicationDetailPage } from './pages/student/StudentApplicationDetailPage';
import { ContractsPage } from './pages/common-user/ContractsPage';
import { StudentContractDetailPage } from './pages/student/StudentContractDetailPage';
import { StudentContractCreatePage } from './pages/student/StudentContractCreatePage';
import { TutorContractCreatePage } from './pages/tutor/TutorContractCreatePage';
import { NotificationsPage } from './pages/common-user/NotificationsPage';
import { WalletPage } from './pages/common-user/WalletPage';
import { WalletTransactionsPage } from './pages/common-user/WalletTransactionsPage';
import { WalletTransactionDetailPage } from './pages/common-user/WalletTransactionDetailPage';
import { WalletAnalyticsPage } from './pages/common-user/WalletAnalyticsPage';
import { MyTeachingRequestsPage } from './pages/tutor/MyTeachingRequestsPage';
import { TeachingRequestDetailPage } from './pages/tutor/TeachingRequestDetailPage';
import { ProfileWorkspacePage } from './pages/common-user/ProfileWorkspacePage';
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
import { AdminWithdrawalsPage } from './pages/admin/AdminWithdrawalsPage';
import { AdminWithdrawalDetailPage } from './pages/admin/AdminWithdrawalDetailPage';
import { StudentContractLessonsPage } from './pages/student/StudentContractLessonsPage';
import { TutorCalendarPage } from './pages/tutor/TutorCalendarPage';
import { StudentCalendarPage } from './pages/student/StudentCalendarPage';
import { StudentRefundRequestCreatePage } from './pages/student/StudentRefundRequestCreatePage';
import { StudentRefundRequestsPage } from './pages/student/StudentRefundRequestsPage';
import { ComplaintsPage } from './pages/common-user/ComplaintsPage';
import { ComplaintCreatePage } from './pages/common-user/ComplaintCreatePage';
import { ComplaintDetailPage } from './pages/common-user/ComplaintDetailPage';
import { CommonRefundRequestDetailPage } from './pages/common-user/CommonRefundRequestDetailPage';
import { AdminRequestReviewPage } from './pages/admin/AdminRequestReviewPage';
import { AdminComplaintsPage } from './pages/admin/AdminComplaintsPage';
import { AdminComplaintDetailPage } from './pages/admin/AdminComplaintDetailPage';
import { AdminUserAccountDetailPage } from './pages/admin/AdminUserAccountDetailPage';
import { AdminStudentsPage } from './pages/admin/AdminStudentsPage';
import { AdminStudentDetailLayout } from './pages/admin/AdminStudentDetailLayout';
import { AdminStudentProfilePage } from './pages/admin/AdminStudentProfilePage';
import { AdminStudentLearningHistoryPage } from './pages/admin/AdminStudentLearningHistoryPage';
import { AdminStudentContractsPage } from './pages/admin/AdminStudentContractsPage';
import { AdminContractsPage } from './pages/admin/AdminContractsPage';
import { AdminStudentTransactionsPage } from './pages/admin/AdminStudentTransactionsPage';
import { AdminTransactionsPage } from './pages/admin/AdminTransactionsPage';
import { AdminWalletsPage } from './pages/admin/AdminWalletsPage';
import { AdminTransactionDetailPage } from './pages/admin/AdminTransactionDetailPage';
import { AdminStudentComplaintsPage } from './pages/admin/AdminStudentComplaintsPage';
import { AdminNotificationsPage } from './pages/admin/AdminNotificationsPage';
import { AdminNotificationFormPage } from './pages/admin/AdminNotificationFormPage';
import { AdminNotificationDetailPage } from './pages/admin/AdminNotificationDetailPage';
import { KbDocumentListPage } from './pages/admin/assistant/KbDocumentListPage';
import { KbDocumentEditorPage } from './pages/admin/assistant/KbDocumentEditorPage';
import { UnansweredQuestionsPage } from './pages/admin/assistant/UnansweredQuestionsPage';
import { AssistantStatsPage } from './pages/admin/assistant/AssistantStatsPage';
import { SupportRequestQueuePage } from './pages/admin/support-requests/SupportRequestQueuePage';
import { SupportRequestDetailPage as AdminSupportRequestDetailPage } from './pages/admin/support-requests/SupportRequestDetailPage';
import { SupportRequestsPage } from './pages/common-user/SupportRequestsPage';
import { SupportRequestDetailPage } from './pages/common-user/SupportRequestDetailPage';
import { AdminEmployeesPage } from './pages/admin/AdminEmployeesPage';
import { AdminRecommendationWeightsPage } from './pages/admin/AdminRecommendationWeightsPage';
import { AdminEmployeeDetailPage } from './pages/admin/AdminEmployeeDetailPage';
import { AdminEmployeeFormPage } from './pages/admin/AdminEmployeeFormPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <GlobalToastViewport />
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
                <Route path="contracts" element={<AdminContractsPage />} />
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
                <Route path="withdrawals" element={<AdminWithdrawalsPage />} />
                <Route path="withdrawals/:withdrawalId" element={<AdminWithdrawalDetailPage />} />
                <Route path="transactions" element={<AdminTransactionsPage />} />
                <Route path="wallets" element={<AdminWalletsPage />} />
                <Route path="transactions/:source/:transactionId" element={<AdminTransactionDetailPage />} />
                <Route path="complaints" element={<AdminComplaintsPage />} />
                <Route path="complaints/:complaintId" element={<AdminComplaintDetailPage />} />
                <Route path="support-requests" element={<SupportRequestQueuePage />} />
                <Route path="support-requests/:requestId" element={<AdminSupportRequestDetailPage />} />
                <Route path="accounts" element={<Navigate replace to="/admin/students" />} />
                <Route path="accounts/:userId" element={<AdminUserAccountDetailPage />} />
                <Route path="notifications" element={<AdminNotificationsPage />} />
                <Route path="recommendation-weights" element={<AdminRecommendationWeightsPage />} />
                <Route path="notifications/new" element={<AdminNotificationFormPage />} />
                <Route path="notifications/:notificationId" element={<AdminNotificationDetailPage />} />
                <Route path="notifications/:notificationId/edit" element={<AdminNotificationFormPage />} />
                <Route element={<RoleGuard roles={['ADMIN']} />}>
                  <Route path="assistant/documents" element={<KbDocumentListPage />} />
                  <Route path="assistant/documents/new" element={<KbDocumentEditorPage />} />
                  <Route path="assistant/documents/:documentId" element={<KbDocumentEditorPage />} />
                  <Route path="assistant/unanswered" element={<UnansweredQuestionsPage />} />
                  <Route path="assistant/stats" element={<AssistantStatsPage />} />
                  <Route path="statistics" element={<AdminStatisticsPage />} />
                  <Route path="employees" element={<AdminEmployeesPage />} />
                  <Route path="employees/new" element={<AdminEmployeeFormPage />} />
                  <Route path="employees/:employeeId" element={<AdminEmployeeDetailPage />} />
                  <Route path="employees/:employeeId/edit" element={<AdminEmployeeFormPage />} />
                </Route>
              </Route>
            </Route>
            <Route element={<RoleGuard roles={['TUTOR']} />}>
              <Route path="/tutor" element={<TutorLayout />}>
                <Route index element={<TutorDashboardPage />} />
                <Route path="dashboard" element={<TutorDashboardPage />} />
                <Route path="calendar" element={<TutorCalendarPage />} />
                <Route path="sections/calendar" element={<TutorCalendarPage />} />
                <Route path="profile" element={<ProfileWorkspacePage subjectRole="tutor" own />} />
                <Route path="certificates" element={<Navigate replace to="/tutor/profile?tab=certificates" />} />
                <Route path="students/:profileId/profile" element={<ProfileWorkspacePage subjectRole="student" />} />
                <Route path="students" element={<TutorStudentSearchPage />} />
                <Route path="studying-requests/:requestId" element={<TutorStudyingRequestPage />} />
                <Route path="proposals" element={<TutorProposalsPage />} />
                <Route path="applications" element={<TutorMyApplicationsPage />} />
                <Route path="student-recommendations" element={<TutorStudentRecommendationsPage />} />
                <Route path="sections/contracts" element={<TutorContractsPage />} />
                <Route path="sections/contracts/:contractId" element={<TutorContractDetailPage />} />
                <Route path="refund-requests" element={<Navigate replace to="/tutor/complaints/received" />} />
                <Route
                  path="refund-requests/:refundRequestId"
                  element={<Navigate replace to="/tutor/complaints/received" />}
                />
                <Route path="complaints" element={<ComplaintsPage basePath="/tutor/complaints" role="tutor" />} />
                <Route path="complaints/new" element={<ComplaintCreatePage basePath="/tutor/complaints" />} />
                <Route
                  path="complaints/received"
                  element={<ComplaintsPage mode="received" basePath="/tutor/complaints" role="tutor" />}
                />
                <Route
                  path="complaints/received/:complaintId"
                  element={<ComplaintDetailPage mode="received" basePath="/tutor/complaints" role="tutor" />}
                />
                <Route
                  path="complaints/:complaintId"
                  element={<ComplaintDetailPage basePath="/tutor/complaints" role="tutor" />}
                />
                <Route path="support-requests" element={<SupportRequestsPage />} />
                <Route path="support-requests/:requestId" element={<SupportRequestDetailPage />} />
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="wallet" element={<WalletPage />} />
                <Route path="wallet/transactions" element={<WalletTransactionsPage />} />
                <Route path="wallet/analytics" element={<WalletAnalyticsPage />} />
                <Route path="wallet/transactions/:source/:transactionId" element={<WalletTransactionDetailPage />} />
                <Route path="sections/teaching-requests" element={<MyTeachingRequestsPage />} />
                <Route path="sections/teaching-requests/:requestId" element={<TeachingRequestDetailPage />} />
                <Route
                  path="sections/teaching-requests/:requestId/student-requests/:applicationId/contracts/new"
                  element={<TutorContractCreatePage />}
                />
                <Route path="sections/:section" element={<TutorComingSoonPage />} />
              </Route>
            </Route>
            <Route element={<RoleGuard roles={['STUDENT']} />}>
              <Route path="/student" element={<StudentLayout />}>
                <Route index element={<StudentDashboardPage />} />
                <Route path="dashboard" element={<StudentDashboardPage />} />
                <Route path="calendar" element={<StudentCalendarPage />} />
                <Route path="studying-requests" element={<StudentStudyingRequestsPage />} />
                <Route path="studying-requests/new" element={<StudentStudyingRequestCreatePage />} />
                <Route
                  path="studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId"
                  element={<OfferDetailPage />}
                />
                <Route path="studying-requests/:requestId" element={<StudentStudyingRequestDetailPage />} />
                <Route path="studying-requests/:requestId/edit" element={<StudentStudyingRequestEditPage />} />
                <Route path="teaching-requests" element={<StudentTeachingRequestsPage />} />
                <Route path="tutor-recommendations" element={<Navigate to="/student/teaching-requests" replace />} />
                <Route path="teaching-requests/:requestId" element={<StudentTeachingRequestDetailPage />} />
                <Route path="applications" element={<StudentApplicationsPage />} />
                <Route path="tutor-offers" element={<OffersPage />} />
                <Route path="applications/:applicationId" element={<StudentApplicationDetailPage />} />
                <Route path="contracts" element={<ContractsPage role="student" />} />
                <Route path="contracts/:contractId" element={<StudentContractDetailPage />} />
                <Route
                  path="studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new"
                  element={<StudentContractCreatePage />}
                />
                <Route path="contracts/:contractId/lessons" element={<StudentContractLessonsPage />} />
                <Route path="contracts/:contractId/refund-request" element={<StudentRefundRequestCreatePage />} />
                <Route path="refund-requests" element={<StudentRefundRequestsPage />} />
                <Route path="refund-requests/new" element={<StudentRefundRequestCreatePage />} />
                <Route path="refund-requests/:refundRequestId" element={<CommonRefundRequestDetailPage />} />
                <Route path="complaints" element={<ComplaintsPage />} />
                <Route path="complaints/new" element={<ComplaintCreatePage />} />
                <Route path="complaints/received" element={<ComplaintsPage mode="received" />} />
                <Route path="complaints/received/:complaintId" element={<ComplaintDetailPage mode="received" />} />
                <Route path="complaints/:complaintId" element={<ComplaintDetailPage />} />
                <Route path="support-requests" element={<SupportRequestsPage />} />
                <Route path="support-requests/:requestId" element={<SupportRequestDetailPage />} />
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="wallet" element={<WalletPage />} />
                <Route path="wallet/transactions" element={<WalletTransactionsPage />} />
                <Route path="wallet/analytics" element={<WalletAnalyticsPage />} />
                <Route path="wallet/transactions/:source/:transactionId" element={<WalletTransactionDetailPage />} />
                <Route path="profile" element={<ProfileWorkspacePage subjectRole="student" own />} />
                <Route path="tutors/:profileId/profile" element={<ProfileWorkspacePage subjectRole="tutor" />} />
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
