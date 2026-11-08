import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Contract, Installment, WalletBalance } from '../../services/studentApi';

type PaymentMethod = 'VNPAY' | 'WALLET';

interface Props {
  contract: Contract;
  installment: Installment;
  walletBalance: WalletBalance | null;
  walletLoading: boolean;
  walletError: string;
  busy: boolean;
  error: string;
  onClose: () => void;
  onPayVnpay: () => void;
  onPayWallet: () => void;
}

export function StudentPaymentMethodDialog({
  contract,
  installment,
  walletBalance,
  walletLoading,
  walletError,
  busy,
  error,
  onClose,
  onPayVnpay,
  onPayWallet
}: Props) {
  const [method, setMethod] = useState<PaymentMethod>('VNPAY');
  const lessonNotReady = installment.paymentPeriod === 'PER_LESSON' && !installment.lessonId;
  const walletBalanceValue = walletBalance?.balance ?? 0;
  const shortfall = Math.max(0, installment.amount - walletBalanceValue);
  const walletCanPay = Boolean(walletBalance) && shortfall === 0 && !walletLoading && !walletError;
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';

  return (
    <div className="student-payment-dialog-backdrop" role="presentation">
      <section
        className="student-payment-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="student-payment-dialog-title"
      >
        <header className="student-payment-dialog-header">
          <div>
            <span className="student-eyebrow">CHI TIẾT THANH TOÁN</span>
            <h2 id="student-payment-dialog-title">Thanh toán kỳ {installment.sequenceNumber}</h2>
          </div>
          <button
            className="student-payment-dialog-close"
            type="button"
            aria-label="Đóng"
            disabled={busy}
            onClick={onClose}
          >
            ×
          </button>
        </header>

        <div className="student-payment-dialog-body">
          <dl className="student-payment-summary">
            <div>
              <dt>Hợp đồng</dt>
              <dd>{contract.subjectName || 'Môn học'}</dd>
            </div>
            <div>
              <dt>Gia sư</dt>
              <dd>{tutorName}</dd>
            </div>
            <div>
              <dt>Số kỳ</dt>
              <dd>Kỳ {installment.sequenceNumber}</dd>
            </div>
            <div>
              <dt>Hạn thanh toán</dt>
              <dd>{formatDate(installment.dueDate)}</dd>
            </div>
            <div>
              <dt>Chu kỳ</dt>
              <dd>{formatPaymentPeriod(installment.paymentPeriod)}</dd>
            </div>
            <div>
              <dt>Trạng thái</dt>
              <dd>{installment.status === 'PENDING' ? 'Đang chờ thanh toán' : installment.status}</dd>
            </div>
            <div className="student-payment-summary-total">
              <dt>Tổng cần thanh toán</dt>
              <dd>{formatMoney(installment.amount)}</dd>
            </div>
          </dl>

          <p className="student-payment-dialog-note">Thanh toán trước 3 ngày so với buổi hoặc kỳ học.</p>

          {lessonNotReady && (
            <div className="student-payment-dialog-warning" role="alert">
              Kỳ thanh toán này chưa được gắn với buổi học. Vui lòng chờ gia sư xếp lịch trước khi thanh toán.
            </div>
          )}
          {error && (
            <div className="student-error student-payment-dialog-error" role="alert">
              {error}
            </div>
          )}

          <fieldset className="student-payment-methods" disabled={busy || lessonNotReady}>
            <legend>Chọn phương thức thanh toán</legend>
            <label className={`student-payment-method ${method === 'VNPAY' ? 'selected' : ''}`}>
              <input
                type="radio"
                name="student-payment-method"
                value="VNPAY"
                checked={method === 'VNPAY'}
                onChange={() => setMethod('VNPAY')}
              />
              <span>
                <strong>Thanh toán bằng VNPay</strong>
                <small>Thanh toán qua cổng VNPay.</small>
              </span>
            </label>

            <label
              className={`student-payment-method ${method === 'WALLET' ? 'selected' : ''} ${!walletCanPay ? 'unavailable' : ''}`}
            >
              <input
                type="radio"
                name="student-payment-method"
                value="WALLET"
                checked={method === 'WALLET'}
                disabled={!walletCanPay}
                onChange={() => setMethod('WALLET')}
              />
              <span>
                <strong>Thanh toán bằng Ví PTutor</strong>
                {walletLoading ? (
                  <small>Đang tải số dư ví…</small>
                ) : walletError ? (
                  <small>{walletError}</small>
                ) : walletBalance ? (
                  <small>
                    Số dư: {formatMoney(walletBalance.balance)}
                    {shortfall > 0 ? ` · Còn thiếu ${formatMoney(shortfall)}` : ''}
                  </small>
                ) : (
                  <small>Không thể tải số dư ví.</small>
                )}
              </span>
            </label>
          </fieldset>

          {!walletCanPay && !walletLoading && !walletError && shortfall > 0 && (
            <p className="student-payment-wallet-shortfall">
              Ví không đủ số dư để thanh toán.{' '}
              <Link to="/student/wallet" onClick={onClose}>
                Nạp tiền vào ví
              </Link>
            </p>
          )}
        </div>

        <footer className="student-payment-dialog-actions">
          <button className="student-button secondary" type="button" disabled={busy} onClick={onClose}>
            Đóng
          </button>
          <button
            className="student-button payment"
            type="button"
            disabled={busy || lessonNotReady || (method === 'WALLET' && !walletCanPay)}
            onClick={method === 'VNPAY' ? onPayVnpay : onPayWallet}
          >
            {busy ? 'Đang xử lý…' : method === 'VNPAY' ? 'Thanh toán bằng VNPay' : 'Thanh toán bằng Ví'}
          </button>
        </footer>
      </section>
    </div>
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

function formatPaymentPeriod(value: Installment['paymentPeriod']) {
  return {
    PER_LESSON: 'Mỗi buổi',
    WEEKLY: 'Hàng tuần',
    MONTHLY: 'Hàng tháng',
    PACKAGE: 'Theo gói'
  }[value];
}
