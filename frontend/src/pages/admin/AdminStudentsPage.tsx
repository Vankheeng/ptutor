import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminUserAccounts,
  type AdminUserAccount,
  type UserAccountStatus
} from '../../services/adminUserAccountApi';
import { AdminAvatar, AdminPagination, AdminState, StatusBadge } from './components/AdminUi';
import { AccountActionDialog, type AccountAction } from './components/AccountActionDialog';
import { accountStatusFilterLabels, accountStatusMeta } from './accountFormat';
import { formatDate, fullName } from './adminFormat';

type StatusFilter = UserAccountStatus | 'ALL';

const statuses: StatusFilter[] = ['ALL', 'ACTIVE', 'BLOCKED', 'INACTIVE'];

export function AdminStudentsPage() {
  const [params, setParams] = useSearchParams();
  const status = statuses.find((value) => value === params.get('status')) ?? 'ALL';
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [revision, setRevision] = useState(0);
  const [action, setAction] = useState<{ type: AccountAction; account: AdminUserAccount } | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async (signal: AbortSignal) => {
    const base = { role: 'STUDENT' as const, keyword: keyword || undefined };
    const [students, all, active, blocked, inactive] = await Promise.all([
      getAdminUserAccounts({ ...base, status: status === 'ALL' ? undefined : status, page }, signal),
      getAdminUserAccounts({ ...base, page: 0 }, signal),
      getAdminUserAccounts({ ...base, status: 'ACTIVE', page: 0 }, signal),
      getAdminUserAccounts({ ...base, status: 'BLOCKED', page: 0 }, signal),
      getAdminUserAccounts({ ...base, status: 'INACTIVE', page: 0 }, signal)
    ]);
    return { students, counts: { ALL: all.totalElements, ACTIVE: active.totalElements, BLOCKED: blocked.totalElements, INACTIVE: inactive.totalElements } };
  }, [keyword, page, status]);
  const { data, error, isLoading } = useResource(load, revision);
  const students = data?.students;

  const update = (changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    setParams(next);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    update({ keyword: String(form.get('keyword') ?? '').trim(), page: undefined });
  };

  return (
    <div className="admin-page">
      <div className="admin-student-page-heading">
        <div>
          <span>QUẢN LÝ HỌC VIÊN</span>
          <h1>Hồ sơ học viên</h1>
          <p>Theo dõi thông tin, lịch sử học, hợp đồng và giao dịch của từng học viên.</p>
        </div>
      </div>
      <div className="admin-student-overview" aria-label="Tổng quan học viên">
        {statuses.map((value) => (
          <button
            key={value}
            type="button"
            className={status === value ? 'is-active' : ''}
            aria-pressed={status === value}
            onClick={() => update({ status: value === 'ALL' ? undefined : value, page: undefined })}
          >
            <span>{value === 'ALL' ? 'Tất cả học viên' : accountStatusFilterLabels[value]}</span>
            <strong>{data ? data.counts[value].toLocaleString('vi-VN') : '—'}</strong>
          </button>
        ))}
      </div>
      {message && (
        <div className="admin-alert admin-alert-success" role="status">
          {message}
        </div>
      )}
      <section className="admin-card">
        <div className="admin-student-list-heading">
          <div>
            <h2>Danh sách học viên</h2>
            <span>{status === 'ALL' ? 'Tất cả trạng thái' : accountStatusFilterLabels[status]}</span>
          </div>
          {students && <strong>{students.totalElements.toLocaleString('vi-VN')} học viên</strong>}
        </div>
        <form key={params.toString()} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder="Tên, email hoặc số điện thoại" />
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Tìm
            </button>
          </div>
        </form>
        {!students ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : students.content.length === 0 ? (
          <AdminState empty="Không có học viên phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Liên hệ</th>
                  <th>Trạng thái</th>
                  <th>Ngày tham gia</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {students.content.map((account) => {
                  const name = fullName(account.firstName, account.lastName, account.email);
                  return (
                    <tr key={account.userId}>
                      <td>
                        <div className="admin-person">
                          <AdminAvatar name={name} />
                          <div className="admin-stack">
                            <strong>{name}</strong>
                            <span>{account.email}</span>
                          </div>
                        </div>
                      </td>
                      <td className="admin-nowrap">{account.phone ?? 'Chưa cập nhật'}</td>
                      <td><StatusBadge meta={accountStatusMeta(account)} /></td>
                      <td className="admin-nowrap">{formatDate(account.createdAt)}</td>
                      <td className="admin-cell-action">
                        <div className="admin-button-row">
                          {account.status === 'ACTIVE' && (
                            <button
                              className="admin-button small danger-outline"
                              type="button"
                              onClick={() => setAction({ type: 'suspend', account })}
                            >
                              Khóa
                            </button>
                          )}
                          {account.status === 'BLOCKED' && (
                            <button
                              className="admin-button small"
                              type="button"
                              onClick={() => setAction({ type: 'reactivate', account })}
                            >
                              Mở khóa
                            </button>
                          )}
                          <Link className="admin-button small secondary" to={`/admin/students/${account.userId}`}>
                            Xem hồ sơ
                          </Link>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {students && (
          <div className="admin-card-footer">
            <AdminPagination
              page={students.page}
              totalPages={students.totalPages}
              totalElements={students.totalElements}
              unit="học viên"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
      {action && (
        <AccountActionDialog
          action={action.type}
          account={action.account}
          onClose={() => setAction(null)}
          onDone={(text) => {
            setAction(null);
            setMessage(text);
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
