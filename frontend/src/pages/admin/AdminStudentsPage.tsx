import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminUserAccounts,
  type AdminUserAccount,
  type UserAccountStatus
} from '../../services/adminUserAccountApi';
import { AdminAvatar, AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { AccountActionDialog, type AccountAction } from './components/AccountActionDialog';
import { accountStatusFilterLabels, accountStatusMeta } from './accountFormat';
import { formatDate, formatDateTime, fullName } from './adminFormat';

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

  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminUserAccounts(
        { role: 'STUDENT', status: status === 'ALL' ? undefined : status, keyword: keyword || undefined, page },
        signal
      ),
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
    const form = new FormData(event.currentTarget);
    update({ keyword: String(form.get('keyword') ?? '').trim(), page: undefined });
  };

  return (
    <div className="admin-page">
      <h1 className="admin-title">Học viên</h1>
      <FilterChips<StatusFilter>
        label="Trạng thái tài khoản"
        value={status}
        options={statuses.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : accountStatusFilterLabels[value]
        }))}
        onChange={(value) => update({ status: value === 'ALL' ? undefined : value, page: undefined })}
      />
      {message && (
        <div className="admin-alert admin-alert-success" role="status">
          {message}
        </div>
      )}
      <section className="admin-card">
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
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có học viên phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Số điện thoại</th>
                  <th>Trạng thái</th>
                  <th>Số lần khóa</th>
                  <th>Ngày tạo</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((account) => {
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
                      <td className="admin-nowrap">{account.phone ?? '—'}</td>
                      <td>
                        <div className="admin-stack">
                          <div>
                            <StatusBadge meta={accountStatusMeta(account)} />
                          </div>
                          {account.status === 'BLOCKED' && account.suspendedUntil && (
                            <span>Đến {formatDateTime(account.suspendedUntil)}</span>
                          )}
                        </div>
                      </td>
                      <td>{account.suspensionCount}</td>
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
                            Xem
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
        {data && (
          <div className="admin-card-footer">
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
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
