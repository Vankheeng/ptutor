import type { FinancialTransaction } from '../../../services/studentApi';
import { dateTimeLabel, moneyLabel, transactionLabel } from '../../../utils/labels';

interface FinancialTransactionListProps {
  transactions?: FinancialTransaction[];
  isLoading: boolean;
  error: string | null;
}

export function FinancialTransactionList({ transactions, isLoading, error }: FinancialTransactionListProps) {
  if (error)
    return (
      <div className="student-error" role="alert">
        {error}
      </div>
    );

  if (!transactions?.length) {
    return isLoading ? null : (
      <div className="student-empty">
        <h3>Chưa có giao dịch nào</h3>
      </div>
    );
  }

  return (
    <div className="student-list">
      {transactions.map((item) => (
        <div className="student-list-row" key={item.id}>
          <div>
            <h3>{item.description || transactionLabel(item.type)}</h3>
            <p>
              {transactionLabel(item.source)} · {transactionLabel(item.method)} · {dateTimeLabel(item.occurredAt)}
            </p>
          </div>
          <span className="student-status">{formatMoney(item.amount)}</span>
        </div>
      ))}
    </div>
  );
}

function formatMoney(value: number) {
  return moneyLabel(value);
}
