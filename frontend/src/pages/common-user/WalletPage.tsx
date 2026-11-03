import { useCallback, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  getFinancialTransactions,
  getMyBankAccount,
  getMyWithdrawals,
  getWalletBalance,
  cancelMyWithdrawal,
  type BankAccount,
  type FinancialTransaction,
  type WalletBalance,
  type Withdrawal
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import { WalletOverview } from './wallet/WalletOverview';
import { WalletDialogs } from './wallet/WalletDialogs';
import { WalletPageHeader } from './wallet/WalletPageHeader';
import { WalletPolicy } from './wallet/WalletPolicy';
import './wallet.css';

type Dialog = 'topup' | 'withdraw' | 'bank' | null;

export function WalletPage() {
  const location = useLocation();
  const transactionsTo = `${location.pathname.startsWith('/tutor/') ? '/tutor' : '/student'}/wallet/transactions`;
  const analyticsTo = `${location.pathname.startsWith('/tutor/') ? '/tutor' : '/student'}/wallet/analytics`;
  const [dialog, setDialog] = useState<Dialog>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [notice, setNotice] = useState('');
  const [withdrawalError, setWithdrawalError] = useState('');
  const [cancellingWithdrawalId, setCancellingWithdrawalId] = useState<string | null>(null);
  const [confirmedBank, setConfirmedBank] = useState<BankAccount | null>(null);
  const loadBalance = useCallback((signal: AbortSignal) => getWalletBalance(signal), []);
  const loadTransactions = useCallback((signal: AbortSignal) => getFinancialTransactions(0, 100, signal), []);
  const loadWithdrawals = useCallback((signal: AbortSignal) => getMyWithdrawals(0, 20, signal), []);
  const loadBank = useCallback(async (signal: AbortSignal) => {
    const account = await getMyBankAccount(signal);
    setConfirmedBank(null);
    return account;
  }, []);
  const balance = useResource(loadBalance, refreshKey);
  const transactions = useResource(loadTransactions, refreshKey);
  const withdrawals = useResource(loadWithdrawals, refreshKey);
  const bank = useResource(loadBank, refreshKey);

  const refresh = () => {
    setNotice('');
    setRefreshKey((key) => key + 1);
  };
  const onWithdrawalCreated = () => {
    setDialog(null);
    setNotice('Yêu cầu rút tiền đã được gửi và đang chờ PTutor xét duyệt.');
    refresh();
  };
  const onBankUpdated = (account: BankAccount) => {
    setConfirmedBank(account);
    setDialog(null);
    setNotice(`Đã cập nhật tài khoản nhận tiền ${account.bankName}.`);
    refresh();
  };
  const onCancelWithdrawal = async (withdrawalId: string) => {
    setCancellingWithdrawalId(withdrawalId);
    setWithdrawalError('');
    try {
      await cancelMyWithdrawal(withdrawalId);
      setRefreshKey((key) => key + 1);
      setNotice('Đã thu hồi yêu cầu rút tiền. Số tiền đã được hoàn vào số dư khả dụng.');
    } catch (error) {
      setWithdrawalError(
        error instanceof ApiError ? error.message : 'Không thể thu hồi yêu cầu rút tiền. Vui lòng thử lại.'
      );
    } finally {
      setCancellingWithdrawalId(null);
    }
  };

  return (
    <div className="wallet-page">
      <WalletPageHeader />
      {notice && (
        <div className="wallet-success" role="status">
          {notice}
        </div>
      )}
      {balance.error && (
        <div className="wallet-error" role="alert">
          {balance.error}
        </div>
      )}

      <WalletOverview
        balance={balance.data}
        bank={confirmedBank ?? bank.data}
        transactions={transactions.data?.content ?? []}
        withdrawals={withdrawals.data?.content ?? []}
        isLoading={balance.isLoading}
        bankLoading={bank.isLoading}
        onTopUp={() => setDialog('topup')}
        onWithdraw={() => setDialog('withdraw')}
        onBank={() => setDialog('bank')}
        onCancelWithdrawal={onCancelWithdrawal}
        cancellingWithdrawalId={cancellingWithdrawalId}
        withdrawalError={withdrawalError}
        transactionsTo={transactionsTo}
        analyticsTo={analyticsTo}
      />

      <WalletPolicy />
      <WalletDialogs
        key={dialog ?? 'closed'}
        active={dialog}
        balance={balance.data}
        bank={confirmedBank ?? bank.data}
        onClose={() => setDialog(null)}
        onWithdrawalCreated={onWithdrawalCreated}
        onBankUpdated={onBankUpdated}
      />
    </div>
  );
}

export type { WalletBalance, FinancialTransaction, Withdrawal };
