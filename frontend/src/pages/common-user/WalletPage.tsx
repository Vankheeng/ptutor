import { useCallback, useState } from 'react';
import { ApiError } from '../../services/httpClient';
import { createWalletTopUp, getFinancialTransactions, getWalletBalance } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { FinancialTransactionList } from './components/FinancialTransactionList';
import { WalletBalanceCard } from './components/WalletBalanceCard';
import { WalletTopUpForm } from './components/WalletTopUpForm';
import '../student/student.css';

export function WalletPage() {
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
        <WalletBalanceCard balance={balance.data} isLoading={balance.isLoading} />
        <WalletTopUpForm amount={amount} onAmountChange={setAmount} onSubmit={() => void topUp()} />
      </div>
      <div className="student-section-heading">
        <div>
          <span className="student-eyebrow">RECENT ACTIVITY</span>
          <h2>Transactions</h2>
        </div>
      </div>
      <FinancialTransactionList
        transactions={transactions.data?.content}
        isLoading={transactions.isLoading}
        error={transactions.error}
      />
    </>
  );
}
