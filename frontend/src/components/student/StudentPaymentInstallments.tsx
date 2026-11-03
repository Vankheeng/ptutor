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
      <h2 className="student-contract-panel-heading">Các kỳ thanh toán</h2>
      <p className="student-installment-note">Thanh toán trước 3 ngày so với buổi hoặc kỳ học.</p>
      {installments.length ? (
        <div className="student-installment-table" role="table" aria-label="Các kỳ thanh toán">
          <div className="student-installment-row student-installment-header" role="row">
            <span role="columnheader">#</span>
            <span role="columnheader">Hạn thanh toán</span>
            <span role="columnheader">Số tiền</span>
            <span role="columnheader">Trạng thái</span>
            <span role="columnheader">Thao tác</span>
          </div>
          {installments.map((item) => (
            <div className="student-installment-row" role="row" key={item.id}>
              <span role="cell">{item.sequenceNumber}</span>
              <span role="cell">{formatDate(item.dueDate)}</span>
              <span role="cell">{formatMoney(item.amount)}</span>
              <span role="cell" className="student-installment-status">
                {isOverdue(item) ? (
                  <span className="student-status overdue">Quá hạn</span>
                ) : (
                  <StudentContractStatusBadge status={item.status} />
                )}
              </span>
              <span role="cell" className="student-installment-action">
                {item.status === 'PENDING' && (
                  <button
                    className="student-button student-installment-pay"
                    type="button"
                    disabled={loading}
                    onClick={() => onPay(item.id)}
                  >
                    Thanh toán
                  </button>
                )}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <p className="student-muted">Chưa có kỳ thanh toán nào.</p>
      )}
    </section>
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VND`;
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa lên lịch';
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value));
}

function isOverdue(installment: Installment) {
  if (installment.status !== 'PENDING' || !installment.dueDate) return false;
  const dueDate = new Date(`${installment.dueDate}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return dueDate < today;
}
