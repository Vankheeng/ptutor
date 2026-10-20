import type { WalletBalance } from '../../../services/studentApi';
import { moneyLabel } from '../../../utils/labels';

interface WalletBalanceCardProps {
  balance: WalletBalance | null;
  isLoading: boolean;
}

export function WalletBalanceCard({ balance, isLoading }: WalletBalanceCardProps) {
  return (
    <section className="student-balance">
      <small>Số dư khả dụng</small>
      <strong>{isLoading ? '—' : balance ? formatMoney(balance.balance) : 'Không có dữ liệu'}</strong>
      <small>Đang chờ: {balance ? formatMoney(balance.pendingBalance) : '—'}</small>
    </section>
  );
}

function formatMoney(value: number) {
  return moneyLabel(value);
}
