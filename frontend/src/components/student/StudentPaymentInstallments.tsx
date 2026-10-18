import type { Installment } from '../../services/studentApi';
import { StudentContractStatusBadge } from './StudentContractStatusBadge';

export function StudentPaymentInstallments({
  installments,
  loading,
  onPay
}: {
  installments: Installment[];
  loading: boolean;
  onPay: (installmentId: string) => void;
}) {
  return (
    <section className="student-panel student-contract-section">
      <h2 className="student-contract-panel-heading">Payment installments</h2>
      {installments.length ? (
        <div className="student-installment-table" role="table" aria-label="Payment installments">
          <div className="student-installment-row student-installment-header" role="row">
            <span role="columnheader">#</span>
            <span role="columnheader">Due date</span>
            <span role="columnheader">Amount</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Action</span>
          </div>
          {installments.map((item) => (
            <div className="student-installment-row" role="row" key={item.id}>
              <span role="cell">{item.sequenceNumber}</span>
              <span role="cell">{formatDate(item.dueDate)}</span>
              <span role="cell">{formatMoney(item.amount)}</span>
              <span role="cell" className="student-installment-status">
                <StudentContractStatusBadge status={item.status} />
              </span>
              <span role="cell" className="student-installment-action">
                {item.status === 'PENDING' && (
                  <button
                    className="student-button student-installment-pay"
                    type="button"
                    disabled={loading}
                    onClick={() => onPay(item.id)}
                  >
                    Pay
                  </button>
                )}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <p className="student-muted">No installments are available.</p>
      )}
    </section>
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value));
}
