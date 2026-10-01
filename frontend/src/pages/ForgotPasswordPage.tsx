import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PasswordField } from '../components/auth/PasswordField';
import { ApiError } from '../services/httpClient';
import { resetPassword, sendPasswordResetOtp, verifyPasswordResetOtp } from '../services/forgotPasswordApi';
import './AuthPage.css';

type Step = 1 | 2 | 3;
type FieldErrors = Record<string, string>;

const OTP_LIFETIME_SECONDS = 5 * 60;

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(OTP_LIFETIME_SECONDS);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState('');

  useEffect(() => {
    if (step !== 2 || secondsLeft <= 0) return;
    const timer = window.setInterval(() => setSecondsLeft((seconds) => Math.max(seconds - 1, 0)), 1000);
    return () => window.clearInterval(timer);
  }, [secondsLeft, step]);

  const clearError = (field: string) => {
    setErrors((current) => ({ ...current, [field]: '' }));
    setFormError('');
  };

  const handleApiError = (caught: unknown, fallback: string) => {
    if (caught instanceof ApiError) {
      setErrors(caught.fieldErrors);
      setFormError(caught.message);
    } else {
      setFormError(fallback);
    }
  };

  const requestOtp = async () => {
    const nextErrors: FieldErrors = {};
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) nextErrors.email = 'Email không hợp lệ.';
    setErrors(nextErrors);
    setFormError('');
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await sendPasswordResetOtp(email.trim());
      setOtp('');
      setSecondsLeft(OTP_LIFETIME_SECONDS);
      setStep(2);
    } catch (caught) {
      handleApiError(caught, 'Không thể gửi OTP. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const verifyOtp = async () => {
    const nextErrors: FieldErrors = {};
    if (!/^\d{6}$/.test(otp)) nextErrors.otp = 'OTP phải gồm đúng 6 chữ số.';
    setErrors(nextErrors);
    setFormError('');
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const result = await verifyPasswordResetOtp(email.trim(), otp);
      if (!result.valid) {
        setFormError('OTP không hợp lệ hoặc đã hết hạn.');
        return;
      }
      setStep(3);
    } catch (caught) {
      handleApiError(caught, 'Không thể xác thực OTP. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitReset = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: FieldErrors = {};
    if (newPassword.length < 8 || newPassword.length > 72) nextErrors.newPassword = 'Mật khẩu phải từ 8 đến 72 ký tự.';
    if (newPassword !== confirmNewPassword) nextErrors.confirmNewPassword = 'Mật khẩu xác nhận không khớp.';
    setErrors(nextErrors);
    setFormError('');
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await resetPassword(email.trim(), otp, newPassword, confirmNewPassword);
      navigate('/login', {
        replace: true,
        state: { email, message: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.' }
      });
    } catch (caught) {
      handleApiError(caught, 'Không thể đặt lại mật khẩu. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const resendOtp = () => {
    void requestOtp();
  };

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="forgot-password-title">
        <Link className="auth-back" to="/login">
          ← Quay lại đăng nhập
        </Link>
        <div className="auth-heading">
          <h1 id="forgot-password-title">Quên mật khẩu?</h1>
          <p>
            {step === 1
              ? 'Nhập email đã đăng ký để nhận mã OTP.'
              : step === 2
                ? 'Nhập mã OTP gồm 6 chữ số đã được gửi tới email.'
                : 'Tạo mật khẩu mới cho tài khoản của bạn.'}
          </p>
        </div>

        {step === 1 && (
          <form
            className="auth-form"
            onSubmit={(event) => {
              event.preventDefault();
              void requestOtp();
            }}
            noValidate
          >
            <label className="auth-field">
              <span>Email</span>
              <input
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  clearError('email');
                }}
                autoComplete="email"
                placeholder="you@example.com"
              />
              {errors.email && <small>{errors.email}</small>}
            </label>
            {formError && (
              <div className="auth-form-error" role="alert">
                {formError}
              </div>
            )}
            <button className="button auth-submit" type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Đang gửi OTP...' : 'Gửi mã OTP'}
            </button>
          </form>
        )}

        {step === 2 && (
          <form
            className="auth-form"
            onSubmit={(event) => {
              event.preventDefault();
              void verifyOtp();
            }}
            noValidate
          >
            <div className="otp-email">
              Mã OTP đã được gửi tới <strong>{email}</strong>
            </div>
            <label className="auth-field">
              <span>Mã OTP</span>
              <input
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(event) => {
                  setOtp(event.target.value.replace(/\D/g, ''));
                  clearError('otp');
                }}
                placeholder="123456"
              />
              {errors.otp && <small>{errors.otp}</small>}
            </label>
            <div className={secondsLeft === 0 ? 'otp-expiry expired' : 'otp-expiry'}>
              {secondsLeft > 0
                ? `Mã có hiệu lực trong ${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')}`
                : 'Mã OTP đã hết hạn'}
            </div>
            {formError && (
              <div className="auth-form-error" role="alert">
                {formError}
              </div>
            )}
            <button className="button auth-submit" type="submit" disabled={isSubmitting || secondsLeft === 0}>
              {isSubmitting ? 'Đang xác thực...' : 'Xác thực OTP'}
            </button>
            <button className="text-button" type="button" onClick={resendOtp} disabled={isSubmitting}>
              Gửi lại OTP
            </button>
            <button
              className="text-button secondary"
              type="button"
              onClick={() => {
                setStep(1);
                setFormError('');
                setErrors({});
              }}
            >
              Đổi email
            </button>
          </form>
        )}

        {step === 3 && (
          <form className="auth-form" onSubmit={submitReset} noValidate>
            <PasswordField
              label="Mật khẩu mới"
              value={newPassword}
              onChange={(value) => {
                setNewPassword(value);
                clearError('newPassword');
              }}
              autoComplete="new-password"
              showPassword={showNewPassword}
              onToggle={() => setShowNewPassword((visible) => !visible)}
              error={errors.newPassword && <small>{errors.newPassword}</small>}
            />
            <PasswordField
              label="Xác nhận mật khẩu mới"
              value={confirmNewPassword}
              onChange={(value) => {
                setConfirmNewPassword(value);
                clearError('confirmNewPassword');
              }}
              autoComplete="new-password"
              showPassword={showConfirmPassword}
              onToggle={() => setShowConfirmPassword((visible) => !visible)}
              error={errors.confirmNewPassword && <small>{errors.confirmNewPassword}</small>}
            />
            {formError && (
              <div className="auth-form-error" role="alert">
                {formError}
              </div>
            )}
            <button className="button auth-submit" type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Đang đặt lại mật khẩu...' : 'Đặt lại mật khẩu'}
            </button>
          </form>
        )}
        <p className="auth-switch">
          Nhớ mật khẩu? <Link to="/login">Đăng nhập</Link>
        </p>
      </section>
    </main>
  );
}
