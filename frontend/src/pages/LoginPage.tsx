import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../services/httpClient';
import { useAuth } from '../auth/useAuth';
import { Toast } from '../components/ui/Toast';
import { PasswordField } from '../components/auth/PasswordField';
import './AuthPage.css';

interface LoginLocationState {
  email?: string;
  message?: string;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const state = (location.state ?? {}) as LoginLocationState;
  const [email, setEmail] = useState(state.email ?? '');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState(state.message ?? '');

  useEffect(() => {
    if (!successMessage) return;
    const timeout = window.setTimeout(() => setSuccessMessage(''), 4000);
    return () => window.clearTimeout(timeout);
  }, [successMessage]);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    const errors: Record<string, string> = {};
    if (!email.trim()) errors.email = 'Vui lòng nhập email.';
    else if (!/^\S+@\S+\.\S+$/.test(email)) errors.email = 'Email không hợp lệ.';
    if (!password) errors.password = 'Vui lòng nhập mật khẩu.';
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setIsSubmitting(true);
    try {
      const authenticatedUser = await login({ email, password });
      navigate(
        authenticatedUser.role === 'TUTOR'
          ? '/tutor/dashboard'
          : authenticatedUser.role === 'STUDENT'
            ? '/student/dashboard'
            : '/',
        {
          replace: true,
          state: { message: 'Đăng nhập thành công.' }
        }
      );
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors);
        setError(caught.message);
      } else {
        setError('Không thể đăng nhập. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      {successMessage && <Toast message={successMessage} />}
      <section className="auth-card" aria-labelledby="login-title">
        <Link className="auth-back" to="/">
          ← Về trang chủ
        </Link>
        <div className="auth-heading">
          <h1 id="login-title">Đăng nhập Ptutor</h1>
          <p>Tiếp tục hành trình kết nối tri thức của bạn.</p>
        </div>
        <form className="auth-form" onSubmit={submit} noValidate>
          <label className="auth-field">
            <span>Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              placeholder="you@example.com"
            />
            {fieldErrors.email && <small>{fieldErrors.email}</small>}
          </label>
          <PasswordField
            label="Mật khẩu"
            value={password}
            onChange={setPassword}
            autoComplete="current-password"
            showPassword={showPassword}
            onToggle={() => setShowPassword((value) => !value)}
            error={fieldErrors.password && <small>{fieldErrors.password}</small>}
          />
          {error && (
            <div className="auth-form-error" role="alert">
              {error}
            </div>
          )}
          <button className="button auth-submit" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
          <Link className="forgot-password-link" to="/forgot-password">
            Quên mật khẩu?
          </Link>
        </form>
        <p className="auth-switch">
          Chưa có tài khoản? <Link to="/register">Đăng ký miễn phí</Link>
        </p>
      </section>
    </main>
  );
}
