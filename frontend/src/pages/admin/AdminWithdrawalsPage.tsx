import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminWithdrawals, type WithdrawalStatus } from '../../services/adminWithdrawalApi';
import { AdminPagination, AdminState, FilterChips } from './components/AdminUi';
import { formatWithdrawalDate, formatWithdrawalMoney, withdrawalLabels } from './withdrawalFormat';

export function AdminWithdrawalsPage() {
  const [params, setParams] = useSearchParams();
  const statuses: WithdrawalStatus[] = ['PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED'];
  const status = statuses.find((item) => item === params.get('status')) ?? 'PENDING';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const load = useCallback((signal: AbortSignal) => getAdminWithdrawals(status, page, signal), [status, page]);
  const { data, error, isLoading } = useResource(load);
  const update = (nextStatus: WithdrawalStatus, nextPage = 0) => {
    const next = new URLSearchParams();
    if (nextStatus !== 'PENDING') next.set('status', nextStatus);
    if (nextPage) next.set('page', String(nextPage));
    setParams(next);
  };

  return (
    <div className="admin-page">
      <h1 className="admin-title">Yêu cầu rút tiền</h1>
      <FilterChips
        label="Trạng thái"
        value={status}
        options={statuses.map((value) => ({ value, label: withdrawalLabels[value] }))}
        onChange={(value) => update(value)}
      />
      <section className="admin-card">
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có yêu cầu rút tiền." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Người yêu cầu</th>
                  <th>Số tiền</th>
                  <th>Ngân hàng</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((item) => (
                  <tr key={item.withdrawal.withdrawalId}>
                    <td>
                      <div className="admin-stack">
                        <strong>{item.userName || item.userEmail}</strong>
                        <span>{item.userEmail}</span>
                      </div>
                    </td>
                    <td>{formatWithdrawalMoney(item.withdrawal.amount)}</td>
                    <td>
                      <div className="admin-stack">
                        <strong>{item.withdrawal.bankName}</strong>
                        <span>{item.withdrawal.maskedAccountNumber}</span>
                      </div>
                    </td>
                    <td>{withdrawalLabels[item.withdrawal.status]}</td>
                    <td>{formatWithdrawalDate(item.withdrawal.createdAt)}</td>
                    <td>
                      <Link
                        className="admin-button small secondary"
                        to={`/admin/withdrawals/${item.withdrawal.withdrawalId}`}
                      >
                        Xem
                      </Link>
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
              unit="yêu cầu"
              onChange={(next) => update(status, next)}
            />
          </div>
        )}
      </section>
    </div>
  );
}
