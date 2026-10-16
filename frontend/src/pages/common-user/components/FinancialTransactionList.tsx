import type { FinancialTransaction } from '../../../services/studentApi';

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
        <h3>No financial activity</h3>
        <p>Your payments and wallet transactions will appear here.</p>
      </div>
    );
  }

  return (
    <div className="student-list">
      {transactions.map((item) => (
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
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}
