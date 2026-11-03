import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminCertificates, type CertificateStatus } from '../../services/adminTutorApi';
import { certificateStatusMeta, formatDate, formatDateTime } from './adminFormat';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { AdminReviewTabs } from './components/AdminReviewTabs';
import { CertificateReviewModal } from './components/CertificateReviewModal';

const statuses: CertificateStatus[] = ['PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'];

export function AdminCertificateQueuePage() {
  const [params, setParams] = useSearchParams();
  const status = (statuses.find((value) => value === params.get('status')) ?? 'PENDING') as CertificateStatus;
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [openId, setOpenId] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  const load = useCallback(
    (signal: AbortSignal) => getAdminCertificates({ status, keyword: keyword || undefined, page }, signal),
    [keyword, page, status]
  );
  const { data, error, isLoading } = useResource(load, revision);

  const update = (changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    setParams(next);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    update({ keyword: String(new FormData(event.currentTarget).get('keyword') ?? '').trim(), page: undefined });
  };

  return (
    <div className="admin-page">
      <AdminReviewTabs refreshKey={revision} />
      <FilterChips<CertificateStatus>
        label="Trạng thái chứng chỉ"
        value={status}
        options={statuses.map((value) => ({ value, label: certificateStatusMeta[value].label }))}
        onChange={(value) => update({ status: value === 'PENDING' ? undefined : value, page: undefined })}
      />
      <section className="admin-card">
        <form key={keyword} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input
              name="keyword"
              type="search"
              defaultValue={keyword}
              placeholder="Tên chứng chỉ, tên hoặc email gia sư"
            />
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Tìm
            </button>
          </div>
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có chứng chỉ nào." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Chứng chỉ</th>
                  <th>Gia sư</th>
                  <th>Ngày cấp</th>
                  <th>Hết hạn</th>
                  <th>Gửi lúc</th>
                  <th>Trạng thái</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((certificate) => (
                  <tr key={certificate.id}>
                    <td>
                      <div className="admin-stack">
                        <strong>{certificate.name}</strong>
                        <span>{certificate.issuingOrganization || '—'}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-stack">
                        <Link className="admin-link" to={`/admin/tutor-profiles/${certificate.tutorUserId}`}>
                          {certificate.tutorName}
                        </Link>
                        <span>{certificate.tutorEmail}</span>
                      </div>
                    </td>
                    <td>{formatDate(certificate.issueDate)}</td>
                    <td>{certificate.expiryDate ? formatDate(certificate.expiryDate) : 'Không thời hạn'}</td>
                    <td>{formatDateTime(certificate.createdAt)}</td>
                    <td>
                      <StatusBadge meta={certificateStatusMeta[certificate.status]} />
                    </td>
                    <td className="admin-cell-action">
                      <button
                        className={`admin-button small${certificate.status === 'PENDING' ? '' : ' secondary'}`}
                        type="button"
                        onClick={() => setOpenId(certificate.id)}
                      >
                        {certificate.status === 'PENDING' ? 'Xem & xác minh' : 'Xem'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="admin-card-footer">
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit="chứng chỉ"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
      {openId && (
        <CertificateReviewModal
          certificateId={openId}
          showTutor
          onClose={() => setOpenId(null)}
          onReviewed={() => {
            setOpenId(null);
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
