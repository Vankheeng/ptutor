import { useCallback } from 'react';
import { NavLink } from 'react-router-dom';
import { useResource } from '../../../hooks/useResource';
import { getAdminCertificates } from '../../../services/adminTutorApi';

export function AdminReviewTabs({ refreshKey }: { refreshKey?: unknown }) {
  const load = useCallback(
    async (signal: AbortSignal) => (await getAdminCertificates({ status: 'PENDING', page: 0 }, signal)).totalElements,
    []
  );
  const { data: pendingCertificates } = useResource(load, refreshKey);

  return (
    <>
      <h1 className="admin-title">Duyệt hồ sơ gia sư</h1>
      <nav className="admin-tabs" aria-label="Loại cần duyệt">
        <NavLink end to="/admin/tutor-profiles">
          Hồ sơ gia sư
        </NavLink>
        <NavLink to="/admin/tutor-profiles/certificates">
          Chứng chỉ nộp bổ sung
          {pendingCertificates ? <span className="admin-count">{pendingCertificates}</span> : null}
        </NavLink>
      </nav>
    </>
  );
}
