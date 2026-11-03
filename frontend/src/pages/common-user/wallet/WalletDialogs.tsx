import { useState } from 'react';
import { ApiError } from '../../../services/httpClient';
import {
  confirmBankAccountOtp,
  confirmWithdrawalOtp,
  createWalletTopUp,
  requestBankAccountOtp,
  requestWithdrawalOtp,
  type BankAccount,
  type WalletBalance,
  type WalletSecurityChallenge
} from '../../../services/studentApi';

type ActiveDialog = 'topup' | 'withdraw' | 'bank' | null;
interface Props {
  active: ActiveDialog;
  balance: WalletBalance | null;
  bank: BankAccount | null;
  onClose: () => void;
  onWithdrawalCreated: () => void;
  onBankUpdated: (bank: BankAccount) => void;
}
const cash = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount);
const formatLocalExpiry = (value: string) => {
  const hasTimezone = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value);
  const date = new Date(hasTimezone ? value : `${value}Z`);
  return Number.isNaN(date.getTime())
    ? '--:--'
    : date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};
const getError = (error: unknown) =>
  error instanceof ApiError ? error.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
const BANK_OPTIONS = [
  { code: 'MB', name: 'MB Bank' },
  { code: 'VCB', name: 'Vietcombank' },
  { code: 'TCB', name: 'Techcombank' },
  { code: 'BIDV', name: 'BIDV' },
  { code: 'ACB', name: 'ACB' },
  { code: 'VPB', name: 'VPBank' },
  { code: 'TPB', name: 'TPBank' },
  { code: 'STB', name: 'Sacombank' },
  { code: 'CTG', name: 'VietinBank' }
];

export function WalletDialogs({ active, balance, bank, onClose, onWithdrawalCreated, onBankUpdated }: Props) {
  const [topUpAmount, setTopUpAmount] = useState('500000');
  const [amount, setAmount] = useState('50000');
  const [note, setNote] = useState('');
  const [otp, setOtp] = useState('');
  const [challenge, setChallenge] = useState<WalletSecurityChallenge | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolderName, setAccountHolderName] = useState(bank?.accountHolderName ?? '');
  const [bankChallenge, setBankChallenge] = useState<WalletSecurityChallenge | null>(null);
  const [bankOtp, setBankOtp] = useState('');

  const close = () => {
    setError('');
    setChallenge(null);
    setOtp('');
    setBankChallenge(null);
    setBankOtp('');
    setNote('');
    setTopUpAmount('500000');
    setAmount('50000');
    setAccountNumber('');
    setBankName('');
    setAccountHolderName('');
    onClose();
  };

  const submitTopUp = async () => {
    const value = Number(topUpAmount);
    if (!Number.isInteger(value) || value < 10_000 || value > 100_000_000) {
      setError('Số tiền nạp phải từ 10.000đ đến 100.000.000đ.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await createWalletTopUp(value);
      window.location.assign(result.paymentUrl);
    } catch (caught) {
      setError(getError(caught));
      setBusy(false);
    }
  };

  const requestOtp = async () => {
    const value = Number(amount);
    if (!Number.isInteger(value) || value < 50_000) {
      setError('Số tiền rút tối thiểu là 50.000đ.');
      return;
    }
    if (value > Number(balance?.balance ?? 0)) {
      setError('Số tiền rút vượt quá số dư khả dụng.');
      return;
    }
    const key = crypto.randomUUID();
    setBusy(true);
    setError('');
    try {
      const result = await requestWithdrawalOtp(value, note.trim(), key);
      setIdempotencyKey(key);
      setChallenge(result);
      setOtp('');
    } catch (caught) {
      setError(getError(caught));
    } finally {
      setBusy(false);
    }
  };

  const confirmOtp = async () => {
    if (!challenge || !/^\d{6}$/.test(otp)) {
      setError('Vui lòng nhập mã OTP gồm 6 chữ số.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await confirmWithdrawalOtp(challenge.challengeId, otp, idempotencyKey);
      onWithdrawalCreated();
      close();
    } catch (caught) {
      setError(getError(caught));
    } finally {
      setBusy(false);
    }
  };

  const submitBank = async () => {
    setBusy(true);
    setError('');
    try {
      const selectedBank = BANK_OPTIONS.find((option) => option.name === bankName);
      if (!selectedBank) {
        setError('Vui lòng chọn ngân hàng.');
        return;
      }
      const result = await requestBankAccountOtp({
        bankCode: selectedBank.code,
        bankName: selectedBank.name,
        accountNumber: accountNumber.trim(),
        accountHolderName: accountHolderName.trim()
      });
      setBankChallenge(result);
      setBankOtp('');
    } catch (caught) {
      setError(getError(caught));
    } finally {
      setBusy(false);
    }
  };

  const confirmBank = async () => {
    if (!bankChallenge || !/^\d{6}$/.test(bankOtp)) {
      setError('Vui lòng nhập mã OTP gồm 6 chữ số.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await confirmBankAccountOtp(bankChallenge.challengeId, bankOtp);
      onBankUpdated(result);
      close();
    } catch (caught) {
      setError(getError(caught));
    } finally {
      setBusy(false);
    }
  };

  if (!active) return null;
  return (
    <div
      className="wallet-modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      {active === 'topup' && (
        <section className="wallet-modal" role="dialog" aria-modal="true" aria-labelledby="wallet-topup-modal-title">
          <header className="wallet-modal-header">
            <div>
              <span className="wallet-modal-icon">▣</span>
              <div>
                <h2 id="wallet-topup-modal-title">Nạp Tiền Vào Ví PTutor</h2>
                <p>Thanh toán tức thì qua cổng VNPay</p>
              </div>
            </div>
            <button type="button" onClick={close} aria-label="Đóng">
              ×
            </button>
          </header>
          <div className="wallet-modal-body">
            <div className="wallet-modal-info">
              ⓘ{' '}
              <span>
                Nạp tiền tức thì qua cổng trực tuyến VNPay (quét mã VNPay-QR hoặc ứng dụng ngân hàng qua VNPay).
              </span>
            </div>
            <label className="wallet-modal-label">
              NHẬP SỐ TIỀN NẠP <small>(10.000đ - 100.000.000đ)</small>
              <div className="wallet-modal-money">
                <input
                  type="number"
                  min="10000"
                  max="100000000"
                  step="50000"
                  value={topUpAmount}
                  onChange={(event) => setTopUpAmount(event.target.value)}
                />
                <b>đ</b>
              </div>
            </label>
            <div className="wallet-amount-presets">
              {[200000, 500000, 1000000, 2000000].map((value) => (
                <button
                  type="button"
                  key={value}
                  className={Number(topUpAmount) === value ? 'selected' : ''}
                  onClick={() => setTopUpAmount(String(value))}
                >
                  {cash(value)} đ
                </button>
              ))}
            </div>
            {error && (
              <div className="wallet-error" role="alert">
                {error}
              </div>
            )}
            <button className="wallet-dialog-submit" type="button" disabled={busy} onClick={() => void submitTopUp()}>
              {busy ? 'Đang kết nối…' : 'Tiến hành thanh toán VNPay →'}
            </button>
          </div>
        </section>
      )}

      {active === 'withdraw' && (
        <section className="wallet-modal" role="dialog" aria-modal="true" aria-labelledby="wallet-withdraw-title">
          <header className="wallet-modal-header">
            <div>
              <span className="wallet-modal-icon">⇧</span>
              <div>
                <h2 id="wallet-withdraw-title">Lập Lệnh Rút Tiền Về Ngân Hàng</h2>
                <p>Yêu cầu được gửi chờ Admin đối soát và phê duyệt</p>
              </div>
            </div>
            <button type="button" onClick={close} aria-label="Đóng">
              ×
            </button>
          </header>
          <div className="wallet-modal-body">
            <div className="wallet-modal-warning">
              ⚠{' '}
              <span>
                <b>Chú ý:</b> Hệ thống không tự động chuyển tiền ngay. Yêu cầu rút sẽ được PTutor kiểm tra và giải ngân
                theo quy trình xét duyệt.
              </span>
            </div>
            {!challenge ? (
              <>
                <div className="wallet-modal-balance">
                  <b>SỐ DƯ KHẢ DỤNG HIỆN TẠI</b>
                  <strong>{balance ? `${cash(balance.balance)} đ` : '—'}</strong>
                </div>
                <label className="wallet-modal-label">
                  <span className="wallet-withdraw-amount-heading">
                    SỐ TIỀN MUỐN RÚT <small>Tối thiểu 50.000đ</small>
                  </span>
                  <div className="wallet-modal-money">
                    <input
                      type="number"
                      min="50000"
                      max={balance?.balance ?? 0}
                      step="10000"
                      value={amount}
                      onChange={(event) => setAmount(event.target.value)}
                    />
                    <b>đ</b>
                  </div>
                </label>
                <div className="wallet-linked-bank">
                  <small>NGÂN HÀNG THỤ HƯỞNG</small>
                  {bank ? (
                    <div>
                      <b>
                        {bank.bankName} · {bank.accountHolderName}
                      </b>
                      <strong>{bank.maskedAccountNumber}</strong>
                    </div>
                  ) : (
                    <p>Chưa có tài khoản ngân hàng. Hãy liên kết tài khoản trước khi rút tiền.</p>
                  )}
                </div>
                <label className="wallet-modal-label wallet-note-field">
                  GHI CHÚ ĐỐI SOÁT <small>(không bắt buộc)</small>
                  <input
                    className="wallet-text-input"
                    maxLength={500}
                    value={note}
                    onChange={(event) => setNote(event.target.value)}
                    placeholder="Ví dụ: Rút thu nhập dạy học tuần này…"
                  />
                </label>
              </>
            ) : (
              <>
                <div className="wallet-otp-step">
                  <span>1 · Lập lệnh rút tiền</span>
                  <b>2 · Xác thực Email Code</b>
                </div>
                <div className="wallet-modal-info">
                  Mã OTP 6 chữ số đã được gửi đến email của bạn. Mã hết hạn lúc {formatLocalExpiry(challenge.expiresAt)}
                  .
                </div>
                <div className="wallet-otp-entry-row">
                  <label className="wallet-modal-label">
                    MÃ OTP EMAIL
                    <input
                      className="wallet-otp-input"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      value={otp}
                      onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="••••••"
                    />
                  </label>
                  <button
                    className="wallet-otp-resend"
                    type="button"
                    disabled={busy}
                    onClick={() => void requestOtp()}
                    aria-label="Gửi lại mã OTP qua email"
                  >
                    {busy ? 'Đang gửi…' : '↻ Gửi lại mã OTP'}
                  </button>
                </div>
              </>
            )}
            {error && (
              <div className="wallet-error" role="alert">
                {error}
              </div>
            )}
            {!challenge ? (
              <button
                className="wallet-dialog-submit"
                type="button"
                disabled={busy || !bank}
                onClick={() => void requestOtp()}
              >
                {busy ? 'Đang gửi mã…' : 'Tiếp tục: Xác thực mã OTP Email →'}
              </button>
            ) : (
              <div className="wallet-dialog-actions">
                <button
                  className="wallet-dialog-secondary"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setChallenge(null);
                    setOtp('');
                    setError('');
                  }}
                >
                  Quay lại
                </button>
                <button
                  className="wallet-dialog-submit"
                  type="button"
                  disabled={busy}
                  onClick={() => void confirmOtp()}
                >
                  {busy ? 'Đang xác thực…' : 'Xác nhận gửi duyệt'}
                </button>
              </div>
            )}
          </div>
        </section>
      )}

      {active === 'bank' && (
        <section className="wallet-modal" role="dialog" aria-modal="true" aria-labelledby="wallet-bank-title">
          <header className="wallet-modal-header">
            <div>
              <span className="wallet-modal-icon">▤</span>
              <div>
                <h2 id="wallet-bank-title">Tài Khoản Ngân Hàng Thụ Hưởng</h2>
                <p>Xác thực thay đổi bằng mã OTP email</p>
              </div>
            </div>
            <button type="button" onClick={close} aria-label="Đóng">
              ×
            </button>
          </header>
          <div className="wallet-modal-body">
            <div className="wallet-modal-info">
              Tên chủ tài khoản cần khớp với hồ sơ xác minh của bạn. Số tài khoản được lưu an toàn và chỉ hiển thị dạng
              ẩn.
            </div>
            {!bankChallenge ? (
              <>
                {bank && (
                  <div className="wallet-linked-bank">
                    <small>TÀI KHOẢN HIỆN TẠI</small>
                    <div>
                      <b>
                        {bank.bankName} · {bank.accountHolderName}
                      </b>
                      <strong>{bank.maskedAccountNumber}</strong>
                    </div>
                  </div>
                )}
                <label className="wallet-modal-label">
                  NGÂN HÀNG THỤ HƯỞNG
                  <select
                    className="wallet-text-input wallet-bank-select"
                    value={bankName}
                    onChange={(event) => setBankName(event.target.value)}
                  >
                    <option value="">Chọn ngân hàng</option>
                    {BANK_OPTIONS.map((option) => (
                      <option key={option.code} value={option.name}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="wallet-modal-label">
                  SỐ TÀI KHOẢN
                  <input
                    className="wallet-text-input"
                    inputMode="numeric"
                    maxLength={34}
                    value={accountNumber}
                    onChange={(event) => setAccountNumber(event.target.value.replace(/\D/g, ''))}
                    placeholder="Nhập đầy đủ số tài khoản"
                  />
                </label>
                <label className="wallet-modal-label">
                  TÊN CHỦ TÀI KHOẢN
                  <input
                    className="wallet-text-input wallet-uppercase"
                    maxLength={150}
                    value={accountHolderName}
                    onChange={(event) => setAccountHolderName(event.target.value)}
                    placeholder="Tên in trên tài khoản"
                  />
                </label>
              </>
            ) : (
              <>
                <div className="wallet-modal-info">Mã xác thực đã được gửi đến email tài khoản PTutor.</div>
                <div className="wallet-otp-entry-row">
                  <label className="wallet-modal-label">
                    MÃ OTP EMAIL
                    <input
                      className="wallet-otp-input"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      value={bankOtp}
                      onChange={(event) => setBankOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="••••••"
                    />
                  </label>
                  <button
                    className="wallet-dialog-secondary"
                    type="button"
                    disabled={busy}
                    onClick={() => void submitBank()}
                  >
                    Gửi lại mã
                  </button>
                </div>
              </>
            )}
            {error && (
              <div className="wallet-error" role="alert">
                {error}
              </div>
            )}
            {!bankChallenge ? (
              <button
                className="wallet-dialog-submit"
                type="button"
                disabled={
                  busy ||
                  !BANK_OPTIONS.some((option) => option.name === bankName) ||
                  accountNumber.length < 6 ||
                  !accountHolderName
                }
                onClick={() => void submitBank()}
              >
                {busy ? 'Đang gửi mã…' : 'Gửi mã OTP để xác thực →'}
              </button>
            ) : (
              <div className="wallet-dialog-actions">
                <button
                  className="wallet-dialog-secondary"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setBankChallenge(null);
                    setBankOtp('');
                    setError('');
                  }}
                >
                  Quay lại
                </button>
                <button
                  className="wallet-dialog-submit"
                  type="button"
                  disabled={busy}
                  onClick={() => void confirmBank()}
                >
                  {busy ? 'Đang xác thực…' : 'Xác nhận tài khoản ngân hàng'}
                </button>
              </div>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
