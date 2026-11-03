import type { WalletBalance } from '../../../services/studentApi';

interface WalletBalanceCardProps {
  balance: WalletBalance | null;
  isLoading: boolean;
}

export function WalletBalanceCard({ balance, isLoading }: WalletBalanceCardProps) {
  return (
    <section className="student-balance">
      <small>Available balance</small>
      <strong>{isLoading ? '—' : balance ? formatMoney(balance.balance) : 'Unavailable'}</strong>
      <small>
        {balance?.currency || 'VND'} · Pending {balance ? formatMoney(balance.pendingBalance) : '—'}
      </small>
    </section>
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
