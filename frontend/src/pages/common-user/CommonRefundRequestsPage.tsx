import { useAuth } from '../../auth/useAuth';
import { StudentRefundRequestsPage } from '../student/StudentRefundRequestsPage';
import { TutorRefundRequestsPage } from '../tutor/TutorRefundRequestsPage';

/** Shared entry point for the refund-request tab in both workspaces. */
export function CommonRefundRequestsPage() {
  const { user } = useAuth();
  return user?.role === 'TUTOR' ? <TutorRefundRequestsPage /> : <StudentRefundRequestsPage />;
}
