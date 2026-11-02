import { useCallback, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getEmployees, type EmployeeJobFunction, type EmployeeStatus } from '../../services/adminEmployeeApi';
import { AdminAvatar, AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { employeeJobFunctions, employeeName, employeeStatusMeta, jobFunctionMeta } from './employeeFormat';
import { formatDate } from './adminFormat';

type FunctionFilter = EmployeeJobFunction | 'ALL';
type StatusFilter = Exclude<EmployeeStatus, 'BLOCKED'> | 'ALL';

const functions: FunctionFilter[] = ['ALL', ...employeeJobFunctions];
const statuses: StatusFilter[] = ['ALL', 'ACTIVE', 'INACTIVE'];

function pick<T extends string>(options: readonly T[], value: string | null) {
  return options.find((option) => option === value);
}

export function AdminEmployeesPage() {
  const [params, setParams] = useSearchParams();
  const jobFunction = pick(functions, params.get('jobFunction')) ?? 'ALL';
  const status = pick(statuses, params.get('status')) ?? 'ALL';
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);

  const load = useCallback(
    (signal: AbortSignal) =>
      getEmployees(
        {
          jobFunction: jobFunction === 'ALL' ? undefined : jobFunction,
          status: status === 'ALL' ? undefined : status,
          keyword: keyword || undefined,
          page
        },
        signal
      ),
    [jobFunction, keyword, page, status]
  );
  const { data, error, isLoading } = useResource(load);

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
    const nextStatus = String(form.get('status') ?? 'ALL');
    update({
      keyword: String(form.get('keyword') ?? '').trim(),
      status: nextStatus === 'ALL' ? undefined : nextStatus,
      page: undefined
    });
  };

  return (
    <div className="admin-page">
      <div className="admin-section-heading">
        <h1 className="admin-title">Nhân viên</h1>
        <Link className="admin-button" to="/admin/employees/new">
          Thêm nhân viên
        </Link>
      </div>
      <FilterChips<FunctionFilter>
        label="Chức năng"
        value={jobFunction}
        options={functions.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : jobFunctionMeta[value].label
        }))}
        onChange={(value) => update({ jobFunction: value === 'ALL' ? undefined : value, page: undefined })}
      />
      <section className="admin-card">
        <form key={params.toString()} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder="Tên, email hoặc số điện thoại" />
          </label>
          <label>
            Trạng thái
            <select name="status" defaultValue={status}>
              {statuses.map((value) => (
                <option key={value} value={value}>
                  {value === 'ALL' ? 'Tất cả trạng thái' : employeeStatusMeta[value].label}
                </option>
              ))}
            </select>
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
          <AdminState empty="Không có nhân viên phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Nhân viên</th>
                  <th>Chức năng</th>
                  <th>Số điện thoại</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((employee) => {
                  const name = employeeName(employee);
                  return (
                    <tr
                      key={employee.employeeId}
                      className={employee.status === 'INACTIVE' ? 'admin-row-muted' : undefined}
                    >
                      <td>
                        <div className="admin-person">
                          <AdminAvatar name={name} />
                          <div className="admin-stack">
                            <strong>{name}</strong>
                            <span>{employee.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <StatusBadge meta={jobFunctionMeta[employee.jobFunction]} />
                      </td>
                      <td className="admin-nowrap">{employee.phone ?? '—'}</td>
                      <td>
                        <StatusBadge meta={employeeStatusMeta[employee.status]} />
                      </td>
                      <td className="admin-nowrap">{formatDate(employee.createdAt)}</td>
                      <td className="admin-cell-action">
                        <div className="admin-button-row">
                          <Link
                            className="admin-button small secondary"
                            to={`/admin/employees/${employee.employeeId}/edit`}
                          >
                            Sửa
                          </Link>
                          <Link className="admin-button small secondary" to={`/admin/employees/${employee.employeeId}`}>
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
              unit="nhân viên"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
    </div>
  );
}
