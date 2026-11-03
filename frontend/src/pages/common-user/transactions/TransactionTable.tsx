import type { FinancialTransaction } from '../../../services/studentApi';
import { useNavigate } from 'react-router-dom';
import { transactionLabels, transactionStatusLabels } from './transactionLabels';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);

export function TransactionTable({
  transactions,
  isLoading,
  basePath,
  cancellingWithdrawalId,
  onCancelWithdrawal
}: {
  transactions: FinancialTransaction[];
  isLoading: boolean;
  basePath: string;
  cancellingWithdrawalId: string | null;
  onCancelWithdrawal: (withdrawalId: string) => void;
}) {
  const navigate = useNavigate();
  if (isLoading) return <div className="transactions-table-message">Đang tải giao dịch từ PTutor…</div>;
  if (!transactions.length)
    return (
      <div className="transactions-table-message">
        <b>Không tìm thấy giao dịch</b>
        <span>Thử thay đổi bộ lọc hoặc khoảng thời gian.</span>
      </div>
    );
  return (
    <div className="transactions-table-wrap">
      <table className="transactions-table">
        <thead>
          <tr>
            <th>MÃ GIAO DỊCH</th>
            <th>THỜI GIAN</th>
            <th>LOẠI GIAO DỊCH</th>
            <th>NỘI DUNG</th>
            <th>PHƯƠNG THỨC</th>
            <th>TRẠNG THÁI</th>
            <th className="is-amount">SỐ TIỀN</th>
            <th>TÙY CHỌN</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((item) => {
            const isTopUp =
              item.type === 'WALLET_TOP_UP' ||
              (item.source === 'WALLET_TRANSACTION' &&
                item.type === 'CREDIT' &&
                item.description?.toLowerCase().includes('top-up'));
            const credit = (item.source === 'WALLET_TRANSACTION' && item.type === 'CREDIT') || isTopUp;
            const status = item.status.toUpperCase();
            const statusLabel =
              item.source === 'WITHDRAWAL' && status === 'PENDING'
                ? 'Chờ duyệt'
                : (transactionStatusLabels[status] ?? status.replaceAll('_', ' '));
            const detailPath = `${basePath}/wallet/transactions/${item.source}/${item.id}`;
            return (
              <tr
                key={`${item.source}-${item.id}`}
                role="link"
                tabIndex={0}
                aria-label={`Xem chi tiết giao dịch ${item.id}`}
                onClick={() => navigate(detailPath)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    navigate(detailPath);
                  }
                }}
              >
                <td>
                  <span className="transactions-id">TX-{item.id.slice(0, 8).toUpperCase()}</span>
                  <small>{item.source.replaceAll('_', ' ')}</small>
                </td>
                <td>
                  {new Date(item.occurredAt).toLocaleString('vi-VN', {
                    day: '2-digit',
                    month: '2-digit',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                  })}
                </td>
                <td>
                  <span className={`transactions-type ${credit ? 'is-credit' : ''}`}>
                    {isTopUp
                      ? 'Nạp tiền'
                      : (transactionLabels[item.type.toUpperCase()] ?? item.type.replaceAll('_', ' '))}
                  </span>
                </td>
                <td className="transactions-description">
                  {item.description || transactionLabels[item.type.toUpperCase()] || item.type.replaceAll('_', ' ')}
                </td>
                <td>
                  <span className="transactions-method">
                    {item.method === 'VNPAY'
                      ? '▣ VNPay'
                      : item.method === 'WALLET'
                        ? '◈ Ví PTutor'
                        : item.method.replaceAll('_', ' ')}
                  </span>
                </td>
                <td>
                  <span className={`transactions-status status-${status.toLowerCase()}`}>{statusLabel}</span>
                </td>
                <td className={`is-amount transactions-amount ${credit ? 'is-credit' : ''}`}>
                  {credit ? '+' : '−'}
                  {money(Number(item.amount))}
                </td>
                <td>
                  {item.source === 'WITHDRAWAL' && status === 'PENDING' && (
                    <button
                      className="transactions-cancel-button"
                      type="button"
                      disabled={cancellingWithdrawalId !== null}
                      onClick={(event) => {
                        event.stopPropagation();
                        onCancelWithdrawal(item.id);
                      }}
                      onKeyDown={(event) => event.stopPropagation()}
                    >
                      {cancellingWithdrawalId === item.id ? 'Đang thu hồi…' : 'Thu hồi'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
