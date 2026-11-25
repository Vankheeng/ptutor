import { useOutletContext } from 'react-router-dom';
import type { AdminTutorDetail } from '../../services/adminTutorApi';

export interface AdminTutorContext {
  tutor: AdminTutorDetail;
  reload: () => void;
}

export function useAdminTutor() {
  return useOutletContext<AdminTutorContext>();
}
