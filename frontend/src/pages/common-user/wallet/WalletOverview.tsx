import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { BankAccount, FinancialTransaction, WalletBalance, Withdrawal } from '../../../services/studentApi';

const formatMoney = (value: number, currency = 'VND') =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);

interface Props {
  balance: WalletBalance | null;
  bank: BankAccount | null;
  transactions: FinancialTransaction[];
  withdrawals: Withdrawal[];
  isLoading: boolean;
  bankLoading: boolean;
  onTopUp: () => void;
  onWithdraw: () => void;
  onBank: () => void;
  onCancelWithdrawal: (withdrawalId: string) => void;
  cancellingWithdrawalId: string | null;
  withdrawalError: string;
  transactionsTo: string;
  analyticsTo: string;
}

export function WalletOverview({
  balance,
  bank,
  transactions,
  withdrawals,
  isLoading,
  bankLoading,
  onTopUp,
  onWithdraw,
  onBank,
  onCancelWithdrawal,
  cancellingWithdrawalId,
  withdrawalError,
  transactionsTo,
  analyticsTo
}: Props) {
  const waitingWithdrawals = withdrawals.filter((item) =>
    ['PENDING', 'PROCESSING', 'UNDER_REVIEW'].includes(item.status)
  );
  const currentMonth = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  const monthlyCredits = transactions.filter(
    (item) =>
      item.occurredAt.slice(0, 7) === currentMonth && item.source === 'WALLET_TRANSACTION' && item.type === 'CREDIT'
  );
  const completedIncome = monthlyCredits
    .filter((item) => ['COMPLETED', 'SUCCESS', 'SUCCEEDED'].includes(item.status.toUpperCase()))
    .reduce((total, item) => total + Number(item.amount), 0);
  const pendingIncome = monthlyCredits
    .filter((item) => ['PENDING', 'PROCESSING'].includes(item.status.toUpperCase()))
    .reduce((total, item) => total + Number(item.amount), 0);
  const monthlyIncome = completedIncome + pendingIncome;
  const paidRatio = monthlyIncome > 0 ? Math.round((completedIncome / monthlyIncome) * 100) : 0;
  const activityCount = monthlyCredits.length;

  return (
    <>
      <section className="wallet-metric-grid">
        <article className="wallet-metric wallet-metric-primary">
          <div className="wallet-metric-card-heading">
            <div className="wallet-metric-label">
              <span aria-hidden="true">◈</span> SỐ DƯ KHẢ DỤNG
            </div>
            <Link to={transactionsTo}>▤ Lịch sử</Link>
          </div>
          <strong>{isLoading ? 'Đang tải…' : balance ? formatMoney(balance.balance, balance.currency) : '—'}</strong>
          <p>Sẵn sàng sử dụng hoặc yêu cầu rút về ngân hàng.</p>
          <div className="wallet-metric-actions">
            <button type="button" onClick={onTopUp}>
              ＋ Nạp VNPay
            </button>
            <button type="button" className="wallet-translucent" onClick={onWithdraw}>
              ↗ Rút tiền
            </button>
          </div>
        </article>

        <article className="wallet-metric">
          <div className="wallet-metric-label">
            <span className="wallet-amber">◷</span> SỐ DƯ TẠM GIỮ
          </div>
          <strong>
            {isLoading ? 'Đang tải…' : balance ? formatMoney(balance.pendingBalance, balance.currency) : '—'}
          </strong>
          <p>Khoản tiền đang chờ xử lý hoặc giải ngân.</p>
          <div className="wallet-pending-breakdown">
            <div>
              <span>Tiền trong ví đang chờ xử lý</span>
              <b>{formatMoney(balance?.pendingBalance ?? 0, balance?.currency)}</b>
            </div>
            <div>
              <span>Lệnh rút chờ Admin duyệt</span>
              <b className="wallet-amber-text">{waitingWithdrawals.length} lệnh</b>
            </div>
          </div>
        </article>

        <article className="wallet-metric">
          <div className="wallet-metric-label">
            <span className="wallet-green">↗</span> TỔNG THU NHẬP THÁNG NÀY
          </div>
          <div className="wallet-income-summary">
            <div>
              <strong className="wallet-green-text">{formatMoney(monthlyIncome)}</strong>
              <p>
                Đã thanh toán: <b>{formatMoney(completedIncome)}</b>
              </p>
              <p>
                Chờ quyết toán: <b className="wallet-purple-text">{formatMoney(pendingIncome)}</b>
              </p>
            </div>
            <div className="wallet-income-ring" style={{ '--wallet-progress': `${paidRatio}%` } as CSSProperties}>
              <b>{paidRatio}%</b>
              <span>đã nhận</span>
            </div>
          </div>
          <div className="wallet-metric-footer">
            <span>{activityCount} khoản thu trong tháng</span>
            <b>{new Date().toLocaleDateString('vi-VN', { month: 'long', year: 'numeric' })}</b>
          </div>
          <div className="wallet-analytics-link-row">
            <Link to={analyticsTo}>
              ▥ Xem biểu đồ thu – chi <span aria-hidden="true">→</span>
            </Link>
          </div>
        </article>

        <article className="wallet-metric wallet-bank-metric">
          <div className="wallet-metric-label">
            <span>▤</span> TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG
          </div>
          {bankLoading ? (
            <strong className="wallet-bank-placeholder">Đang tải…</strong>
          ) : bank ? (
            <>
              <strong className="wallet-bank-name">{bank.bankName}</strong>
              <div className="wallet-bank-number">{bank.maskedAccountNumber}</div>
              <p>
                Chủ tài khoản: <b>{bank.accountHolderName}</b>
              </p>
            </>
          ) : (
            <>
              <strong className="wallet-bank-name">Chưa liên kết</strong>
              <p>Thêm tài khoản ngân hàng để gửi yêu cầu rút tiền.</p>
            </>
          )}
          <div className="wallet-bank-status">
            {!bank && <span className="is-unverified">○ Chưa liên kết ngân hàng</span>}
            <button type="button" onClick={onBank}>
              {bank ? 'Thay đổi ngân hàng' : 'Liên kết ngân hàng'} <span aria-hidden="true">→</span>
            </button>
          </div>
        </article>
      </section>
      {withdrawals.length > 0 && (
        <section className="wallet-withdrawal-requests">
          <div className="wallet-withdrawal-heading">
            <div>
              <h2>Yêu cầu rút tiền</h2>
              <p>Các yêu cầu đang chờ xử lý</p>
            </div>
            <span>{waitingWithdrawals.length} đang chờ</span>
          </div>
          {withdrawalError && (
            <div className="wallet-error" role="alert">
              {withdrawalError}
            </div>
          )}
          <div className="wallet-withdrawal-list">
            {withdrawals.map((item) => (
              <article className="wallet-withdrawal-item" key={item.withdrawalId}>
                <div className="wallet-withdrawal-main">
                  <strong>{formatMoney(item.amount)}</strong>
                  <span>
                    {item.bankName} · {item.maskedAccountNumber}
                  </span>
                  <small>{new Date(item.createdAt).toLocaleString('vi-VN')}</small>
                </div>
                <div className="wallet-withdrawal-actions">
                  <span className="wallet-withdrawal-status">
                    {item.status === 'PENDING' ? 'Chờ xử lý' : item.status}
                  </span>
                  {item.status === 'PENDING' && (
                    <button
                      type="button"
                      disabled={cancellingWithdrawalId !== null}
                      onClick={() => onCancelWithdrawal(item.withdrawalId)}
                    >
                      {cancellingWithdrawalId === item.withdrawalId ? 'Đang thu hồi…' : 'Thu hồi yêu cầu'}
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </>
  );
}
