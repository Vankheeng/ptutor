import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import type { TransactionSource } from '../../services/adminStudentApi';
import { getAdminTransaction } from '../../services/adminTransactionApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { AdminState, StatusBadge } from './components/AdminUi';
import {
  transactionMethodLabels,
  transactionSourceLabels,
  transactionStatus,
  transactionTypeLabels
} from './studentFormat';
import { transactionPurposeLabels, transactionReferenceLabels } from './transactionFormat';
import { flowLabels } from './transactionFlow';

const sources: TransactionSource[] = ['PAYMENT', 'WALLET_TRANSACTION', 'WITHDRAWAL'];

export function AdminTransactionDetailPage() {
  const { source, transactionId } = useParams();
  const validSource = sources.find((value) => value === source);
  const load = useCallback(
    (signal: AbortSignal) => {
      if (!validSource || !transactionId) return Promise.reject(new Error('Mã giao dịch không hợp lệ.'));
      return getAdminTransaction(validSource, transactionId, signal);
    },
    [validSource, transactionId]
  );
  const { data, error, isLoading } = useResource(load);
  const hasReference = Boolean(
    data &&
    (data.transaction.referenceType || data.transaction.referenceId || data.transactionCode || data.transferReference)
  );

  return (
    <div className="admin-page admin-transaction-detail">
      <Link className="admin-back" to="/admin/transactions">
        ← Lịch sử giao dịch
      </Link>
      {!data ? (
        <section className="admin-card admin-card-pad">
          <AdminState loading={isLoading} error={error} />
        </section>
      ) : (
        <>
          <header className={`admin-transaction-hero admin-transaction-hero-${data.flow.toLowerCase()}`}>
            <div className="admin-transaction-hero-main">
              <span className="admin-transaction-eyebrow">Chi tiết giao dịch</span>
              <h1>{transactionPurposeLabels[data.purpose] ?? data.purpose}</h1>
              <div className="admin-transaction-hero-meta">
                <span>{formatDateTime(data.transaction.occurredAt)}</span>
                <StatusBadge meta={transactionStatus(data.transaction.status)} />
              </div>
            </div>
            <div className="admin-transaction-amount">
              <span>{flowLabels[data.flow]}</span>
              <strong>{formatCurrency(data.transaction.amount)}</strong>
            </div>
          </header>

          <div className={`admin-transaction-detail-grid ${hasReference ? 'has-reference' : 'no-reference'}`}>
            <section className="admin-card admin-transaction-detail-card admin-transaction-detail-info">
              <h2>Thông tin giao dịch</h2>
              <dl className="admin-transaction-facts">
                <div className="admin-transaction-fact-wide">
                  <dt>Mã giao dịch</dt>
                  <dd className="admin-transaction-code">{data.transaction.id}</dd>
                </div>
                <div>
                  <dt>Nguồn</dt>
                  <dd>{transactionSourceLabels[data.transaction.source]}</dd>
                </div>
                <div>
                  <dt>Loại</dt>
                  <dd>{transactionTypeLabels[data.transaction.type] ?? data.transaction.type}</dd>
                </div>
                <div>
                  <dt>Phương thức</dt>
                  <dd>{transactionMethodLabels[data.transaction.method] ?? data.transaction.method}</dd>
                </div>
                <div>
                  <dt>Phân loại dòng tiền</dt>
                  <dd>{flowLabels[data.flow]}</dd>
                </div>
              </dl>
            </section>

            {hasReference && (
              <section className="admin-card admin-transaction-detail-card admin-transaction-detail-reference">
                <h2>Tham chiếu</h2>
                <dl className="admin-transaction-facts admin-transaction-facts-single">
                  {data.transactionCode && (
                    <div>
                      <dt>Mã thanh toán</dt>
                      <dd className="admin-transaction-code">{data.transactionCode}</dd>
                    </div>
                  )}
                  {data.transferReference && (
                    <div>
                      <dt>Mã chuyển khoản</dt>
                      <dd className="admin-transaction-code">{data.transferReference}</dd>
                    </div>
                  )}
                  {data.transaction.referenceType && (
                    <div>
                      <dt>Loại tham chiếu</dt>
                      <dd>
                        {transactionReferenceLabels[data.transaction.referenceType] ?? data.transaction.referenceType}
                      </dd>
                    </div>
                  )}
                  {data.transaction.referenceId && (
                    <div>
                      <dt>Mã tham chiếu</dt>
                      <dd className="admin-transaction-code">{data.transaction.referenceId}</dd>
                    </div>
                  )}
                </dl>
              </section>
            )}

            <section className="admin-card admin-transaction-detail-card admin-transaction-detail-user">
              <h2>Người liên quan</h2>
              <strong className="admin-transaction-user-name">{data.userName || data.userEmail}</strong>
              <span className="admin-transaction-user-email">{data.userEmail}</span>
            </section>

            {data.transaction.description && (
              <section className="admin-card admin-transaction-detail-card admin-transaction-detail-note">
                <h2>Ghi chú</h2>
                <p className="admin-transaction-description">{data.transaction.description}</p>
              </section>
            )}
          </div>
        </>
      )}
    </div>
  );
}
