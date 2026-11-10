import { useOutletContext } from 'react-router-dom';
import type { AdminStudentProfile } from '../../services/adminStudentApi';
import type { AdminUserAccount } from '../../services/adminUserAccountApi';

export interface AdminStudentContext {
  account: AdminUserAccount;
  profile: AdminStudentProfile;
}

export function useAdminStudent() {
  return useOutletContext<AdminStudentContext>();
}
