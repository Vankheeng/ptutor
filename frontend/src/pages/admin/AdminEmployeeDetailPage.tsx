import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getEmployee } from '../../services/adminEmployeeApi';
import { AdminAvatar, AdminState, DefinitionGrid, StatusBadge } from './components/AdminUi';
import { EmployeeAccessDialog, type EmployeeAccessAction } from './components/EmployeeAccessDialog';
import { modulesForFunction } from './adminAccess';
import { employeeName, employeeStatusMeta, jobFunctionMeta } from './employeeFormat';
import { formatDate, formatDateTime, genderLabels } from './adminFormat';

export function AdminEmployeeDetailPage() {
  const { employeeId = '' } = useParams();
  const location = useLocation();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<EmployeeAccessAction | null>(null);
  const [message, setMessage] = useState<string | null>(
    () => (location.state as { message?: string } | null)?.message ?? null
  );

  const load = useCallback((signal: AbortSignal) => getEmployee(employeeId, signal), [employeeId]);
  const { data, error, isLoading } = useResource(load, revision);

  if (!data) {
    return (
      <div className="admin-page">
        <Link className="admin-back" to="/admin/employees">
          ‹ Nhân viên
        </Link>
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }

  const name = employeeName(data);
  const inactive = data.status === 'INACTIVE';
  const address = data.address;
  const addressText =
    [address?.detailAddress, address?.districtName, address?.provinceName].filter(Boolean).join(', ') || '—';

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/employees">
        ‹ Nhân viên
      </Link>
      <header className="admin-card admin-card-pad admin-account-header">
        <div className="admin-account-identity">
          <AdminAvatar name={name} size="lg" />
          <div className="admin-stack-gap">
            <h1 className="admin-title">{name}</h1>
            <div className="admin-tags">
              <StatusBadge meta={jobFunctionMeta[data.jobFunction]} />
              <StatusBadge meta={employeeStatusMeta[data.status]} />
            </div>
          </div>
        </div>
        <div className="admin-button-row">
          <Link className="admin-button secondary" to={`/admin/employees/${data.employeeId}/edit`}>
            Sửa thông tin
          </Link>
          {data.status === 'ACTIVE' && (
            <button className="admin-button danger-outline" type="button" onClick={() => setDialog('deactivate')}>
              Vô hiệu hóa
            </button>
          )}
          {inactive && (
            <button className="admin-button" type="button" onClick={() => setDialog('reactivate')}>
              Kích hoạt lại
            </button>
          )}
        </div>
      </header>

      {message && (
        <div className="admin-alert admin-alert-success" role="status">
          {message}
        </div>
      )}

      {inactive && (
        <section className="admin-card admin-card-pad admin-employee-revoked">
          <h2>Đã vô hiệu hóa · {formatDateTime(data.accessRevokedAt)}</h2>
          <p className="admin-prose">{data.accessRevocationReason ?? '—'}</p>
        </section>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          <section className="admin-card admin-card-pad">
            <h2>Thông tin cá nhân</h2>
            <DefinitionGrid
              columns={3}
              items={[
                ['Họ', data.lastName || '—'],
                ['Tên', data.firstName || '—'],
                ['Giới tính', data.gender ? genderLabels[data.gender] : '—'],
                ['Ngày sinh', formatDate(data.dateOfBirth)],
                ['Số CCCD', data.maskedCitizenId || '—'],
                ['Địa chỉ', addressText]
              ]}
            />
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Liên hệ</h2>
            <DefinitionGrid
              items={[
                ['Email', data.email],
                ['Số điện thoại', data.phone || '—']
              ]}
            />
          </section>
          {data.reactivatedAt && (
            <section className="admin-card admin-card-pad">
              <h2>Kích hoạt lại · {formatDateTime(data.reactivatedAt)}</h2>
              {data.reactivationReason && <p className="admin-prose">{data.reactivationReason}</p>}
            </section>
          )}
        </div>
        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Tài khoản</h2>
            <DefinitionGrid
              items={[
                ['Ngày tạo', formatDateTime(data.createdAt)],
                ['Cập nhật lúc', formatDateTime(data.updatedAt)]
              ]}
            />
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Quyền truy cập</h2>
            <ul className="admin-employee-access">
              {modulesForFunction(data.jobFunction).map((module) => (
                <li key={module}>
                  <svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24">
                    <path d="M5 12l5 5L20 7" />
                  </svg>
                  {module}
                </li>
              ))}
            </ul>
          </section>
        </aside>
      </div>

      {dialog && (
        <EmployeeAccessDialog
          action={dialog}
          employee={data}
          onClose={() => setDialog(null)}
          onDone={(text) => {
            setDialog(null);
            setMessage(text);
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
