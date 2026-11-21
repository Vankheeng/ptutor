import { useAuth } from '../../auth/useAuth';
import { StudentRefundRequestDetailPage } from '../student/StudentRefundRequestDetailPage';
import { TutorRefundRequestDetailPage } from '../tutor/TutorRefundRequestDetailPage';

/** Shared detail route; actions remain role-specific inside each view. */
export function CommonRefundRequestDetailPage() {
  const { user } = useAuth();
  return user?.role === 'TUTOR' ? <TutorRefundRequestDetailPage /> : <StudentRefundRequestDetailPage />;
}
