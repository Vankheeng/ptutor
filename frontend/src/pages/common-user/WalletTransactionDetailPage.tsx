import { useCallback, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { cancelMyWithdrawal, getFinancialTransaction } from '../../services/studentApi';
import { ApiError } from '../../services/httpClient';
import { useResource } from '../../hooks/useResource';
import './wallet.css';
import './transactions/transactions.css';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);
const typeName = (type: string) =>
  ({
    WALLET_TOP_UP: 'Nạp tiền',
    TOP_UP: 'Nạp tiền',
    CREDIT: 'Tiền vào ví',
    DEBIT: 'Thanh toán',
    WITHDRAWAL: 'Rút tiền',
    TUITION_PAYMENT: 'Thanh toán học phí',
    STUDYING_REQUEST_FEE: 'Phí yêu cầu học',
    TEACHING_REQUEST_FEE: 'Phí yêu cầu dạy'
  })[type] ?? type.replaceAll('_', ' ');
const statusName = (status: string, source: string) =>
  source === 'WITHDRAWAL' && status === 'PENDING'
    ? 'Chờ duyệt'
    : ({
        PENDING: 'Đang xử lý',
        PROCESSING: 'Đang xét duyệt',
        PAID: 'Hoàn tất',
        COMPLETED: 'Hoàn tất',
        FAILED: 'Thất bại',
        CANCELLED: 'Đã hủy',
        REFUNDED: 'Đã hoàn tiền',
        REJECTED: 'Từ chối'
      }[status] ?? status.replaceAll('_', ' '));

export function WalletTransactionDetailPage() {
  const { source = '', transactionId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const basePath = location.pathname.startsWith('/tutor/') ? '/tutor' : '/student';
  const [refreshKey, setRefreshKey] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const [cancelMessage, setCancelMessage] = useState('');
  const [cancelError, setCancelError] = useState('');
  const load = useCallback(
    (signal: AbortSignal) => getFinancialTransaction(source, transactionId, signal),
    [source, transactionId]
  );
  const transaction = useResource(load, refreshKey);

  const cancelWithdrawal = async () => {
    setCancelling(true);
    setCancelError('');
    try {
      await cancelMyWithdrawal(transactionId);
      setCancelMessage('Đã thu hồi yêu cầu rút tiền. Số tiền đã được hoàn vào số dư khả dụng.');
      setRefreshKey((key) => key + 1);
    } catch (error) {
      setCancelError(
        error instanceof ApiError ? error.message : 'Không thể thu hồi yêu cầu rút tiền. Vui lòng thử lại.'
      );
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="wallet-page transactions-page transaction-detail-page">
      <div className="transactions-back-row">
        <button type="button" onClick={() => navigate(`${basePath}/wallet/transactions`)}>
          ← Quay lại lịch sử
        </button>
        <span>CHI TIẾT GIAO DỊCH</span>
      </div>
      {transaction.error ? (
        <div className="wallet-error" role="alert">
          {transaction.error}
        </div>
      ) : transaction.isLoading ? (
        <div className="transactions-table-message">Đang tải chi tiết giao dịch…</div>
      ) : (
        transaction.data && (
          <>
            <header className="transaction-detail-heading">
              <div>
                <span className="wallet-eyebrow">PTUTOR LEDGER HUB / TRANSACTION DETAIL</span>
                <h1>Chi tiết giao dịch</h1>
                <p>
                  Mã giao dịch <b>TX-{transaction.data.id.slice(0, 8).toUpperCase()}</b>
                </p>
              </div>
              <span className={`transactions-status status-${transaction.data.status.toLowerCase()}`}>
                {statusName(transaction.data.status, transaction.data.source)}
              </span>
            </header>
            <section className="transaction-detail-card">
              <span className="wallet-eyebrow">SỐ TIỀN GIAO DỊCH</span>
              <strong>{money(transaction.data.amount)}</strong>
              <span>{typeName(transaction.data.type)}</span>
            </section>
            <section className="transaction-detail-fields">
              <h2>Thông tin giao dịch</h2>
              <dl>
                <div>
                  <dt>Loại giao dịch</dt>
                  <dd>{typeName(transaction.data.type)}</dd>
                </div>
                <div>
                  <dt>Nguồn</dt>
                  <dd>{transaction.data.source.replaceAll('_', ' ')}</dd>
                </div>
                <div>
                  <dt>Phương thức</dt>
                  <dd>
                    {transaction.data.method === 'VNPAY'
                      ? 'VNPay'
                      : transaction.data.method === 'WALLET'
                        ? 'Ví PTutor'
                        : transaction.data.method}
                  </dd>
                </div>
                <div>
                  <dt>Trạng thái</dt>
                  <dd>{statusName(transaction.data.status, transaction.data.source)}</dd>
                </div>
                <div>
                  <dt>Thời gian</dt>
                  <dd>{new Date(transaction.data.occurredAt).toLocaleString('vi-VN')}</dd>
                </div>
                <div>
                  <dt>Mã tham chiếu</dt>
                  <dd>{transaction.data.referenceId ?? '—'}</dd>
                </div>
                <div className="is-full">
                  <dt>Nội dung</dt>
                  <dd>{transaction.data.description || '—'}</dd>
                </div>
              </dl>
              <div className="transaction-detail-actions">
                <button
                  type="button"
                  className="transactions-detail-back-button"
                  onClick={() => navigate(`${basePath}/wallet/transactions`)}
                >
                  ← Trở lại danh sách giao dịch
                </button>
                {source === 'WITHDRAWAL' && transaction.data.status === 'PENDING' && (
                  <button
                    className="transaction-cancel-detail-button"
                    type="button"
                    disabled={cancelling}
                    onClick={() => void cancelWithdrawal()}
                  >
                    {cancelling ? 'Đang thu hồi…' : 'Thu hồi yêu cầu rút tiền'}
                  </button>
                )}
              </div>
              <button
                type="button"
                className="transactions-detail-back-button"
                onClick={() => navigate(`${basePath}/wallet/transactions`)}
              >
                ← Trở lại danh sách giao dịch
              </button>
            </section>
            {source === 'WITHDRAWAL' && (transaction.data.status === 'PENDING' || cancelMessage !== '') && (
              <div className="transaction-withdrawal-cancel">
                {cancelMessage && (
                  <div className="wallet-success" role="status">
                    {cancelMessage}
                  </div>
                )}
                {cancelError && (
                  <div className="wallet-error" role="alert">
                    {cancelError}
                  </div>
                )}
                <button type="button" disabled={cancelling} onClick={() => void cancelWithdrawal()}>
                  {cancelling ? 'Đang thu hồi…' : 'Thu hồi yêu cầu rút tiền'}
                </button>
              </div>
            )}
          </>
        )
      )}
    </div>
  );
}
