import { useCallback, useState } from 'react';
import { ApiError } from '../../services/httpClient';
import { createWalletTopUp, getFinancialTransactions, getWalletBalance } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';

export function StudentWalletPage() {
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const loadBalance = useCallback((signal: AbortSignal) => getWalletBalance(signal), []);
  const loadTransactions = useCallback((signal: AbortSignal) => getFinancialTransactions(0, 20, signal), []);
  const balance = useResource(loadBalance);
  const transactions = useResource(loadTransactions);
  const topUp = async () => {
    setError('');
    try {
      const payment = await createWalletTopUp(Number(amount));
      window.open(payment.paymentUrl, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not create the payment.');
    }
  };
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">MONEY & PAYMENTS</span>
          <h1>Wallet & Payments</h1>
          <p>Review your balance and financial activity.</p>
        </div>
      </div>
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <div className="student-detail-layout">
        <section className="student-balance">
          <small>Available balance</small>
          <strong>{balance.isLoading ? '—' : balance.data ? formatMoney(balance.data.balance) : 'Unavailable'}</strong>
          <small>
            {balance.data?.currency || 'VND'} · Pending {balance.data ? formatMoney(balance.data.pendingBalance) : '—'}
          </small>
        </section>
        <section className="student-panel">
          <h2>Top up wallet</h2>
          <div className="student-form-grid">
            <label>
              Amount (VND)
              <input
                type="number"
                min="10000"
                step="1"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="100000"
              />
            </label>
            <div className="student-form-actions">
              <button className="student-button" type="button" disabled={!amount} onClick={() => void topUp()}>
                Open VNPay
              </button>
            </div>
          </div>
        </section>
      </div>
      <div className="student-section-heading">
        <div>
          <span className="student-eyebrow">RECENT ACTIVITY</span>
          <h2>Transactions</h2>
        </div>
      </div>
      {transactions.error && (
        <div className="student-error" role="alert">
          {transactions.error}
        </div>
      )}
      {transactions.data?.content.length ? (
        <div className="student-list">
          {transactions.data.content.map((item) => (
            <div className="student-list-row" key={item.id}>
              <div>
                <h3>{item.description || item.type}</h3>
                <p>
                  {item.source} · {item.method} · {new Date(item.occurredAt).toLocaleString('en-US')}
                </p>
              </div>
              <span className="student-status">{formatMoney(item.amount)}</span>
            </div>
          ))}
        </div>
      ) : (
        !transactions.isLoading && (
          <div className="student-empty">
            <h3>No financial activity</h3>
            <p>Your payments and wallet transactions will appear here.</p>
          </div>
        )
      )}
    </>
  );
}
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
