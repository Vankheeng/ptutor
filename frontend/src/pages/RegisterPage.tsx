import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../services/httpClient';
import { register } from '../services/authApi';
import { getDistricts, getProvinces } from '../services/locationApi';
import type { District, Province } from '../types/api';
import type { RegisterRequest } from '../types/auth';
import { PasswordField } from '../components/auth/PasswordField';
import './AuthPage.css';

type FormValues = RegisterRequest;
type FormErrors = Record<string, string>;

const initialValues: FormValues = {
  email: '',
  password: '',
  role: 'STUDENT',
  firstName: '',
  lastName: '',
  phone: '',
  gender: 'MALE',
  dateOfBirth: '',
  citizenId: '',
  provinceId: '',
  districtId: ''
};

export default function RegisterPage() {
  const navigate = useNavigate();
  const [values, setValues] = useState<FormValues>(initialValues);
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState('');
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [locationsLoading, setLocationsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    getProvinces()
      .then((data) => {
        if (active) setProvinces(data);
      })
      .catch(() => {
        if (active) setFormError('Không thể tải danh sách tỉnh/thành phố.');
      })
      .finally(() => {
        if (active) setLocationsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!values.provinceId) {
      return;
    }
    let active = true;
    getDistricts(values.provinceId)
      .then((data) => {
        if (active) setDistricts(data);
      })
      .catch(() => {
        if (active) setFormError('Không thể tải danh sách quận/huyện.');
      });
    return () => {
      active = false;
    };
  }, [values.provinceId]);

  const maxDate = useMemo(() => new Date().toISOString().slice(0, 10), []);

  const update = <K extends keyof FormValues>(field: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: '' }));
    setFormError('');
  };

  const validate = (): FormErrors => {
    const next: FormErrors = {};
    if (!/^\S+@\S+\.\S+$/.test(values.email)) next.email = 'Email không hợp lệ.';
    if (values.password.length < 8 || values.password.length > 72) next.password = 'Mật khẩu phải từ 8 đến 72 ký tự.';
    if (values.password !== confirmPassword) next.confirmPassword = 'Mật khẩu xác nhận không khớp.';
    if (!values.firstName.trim()) next.firstName = 'Vui lòng nhập tên.';
    if (!values.lastName.trim()) next.lastName = 'Vui lòng nhập họ.';
    if (!values.phone.trim()) next.phone = 'Vui lòng nhập số điện thoại.';
    if (!values.dateOfBirth) next.dateOfBirth = 'Vui lòng chọn ngày sinh.';
    else if (values.dateOfBirth > maxDate) next.dateOfBirth = 'Ngày sinh không được ở tương lai.';
    if (!/^\d{12}$/.test(values.citizenId)) next.citizenId = 'CCCD phải gồm đúng 12 chữ số.';
    if (!values.provinceId) next.provinceId = 'Vui lòng chọn tỉnh/thành phố.';
    if (!values.districtId) next.districtId = 'Vui lòng chọn quận/huyện.';
    return next;
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors = validate();
    setErrors(nextErrors);
    setFormError('');
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await register(values);
      navigate('/login', {
        replace: true,
        state: { email: values.email, message: 'Đăng ký thành công. Vui lòng đăng nhập để tiếp tục.' }
      });
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.fieldErrors);
        setFormError(caught.message);
      } else {
        setFormError('Không thể đăng ký. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const field = (name: keyof FormErrors) => errors[name] && <small>{errors[name]}</small>;

  return (
    <main className="auth-page auth-page-register">
      <section className="auth-card auth-card-wide" aria-labelledby="register-title">
        <Link className="auth-back" to="/">
          ← Về trang chủ
        </Link>
        <div className="auth-heading">
          <h1 id="register-title">Tạo tài khoản</h1>
          <p>Điền thông tin để bắt đầu kết nối với cộng đồng Ptutor.</p>
        </div>
        <form className="auth-form register-form" onSubmit={submit} noValidate>
          <div className="role-picker" role="group" aria-label="Vai trò đăng ký">
            <button
              className={values.role === 'STUDENT' ? 'selected' : ''}
              type="button"
              onClick={() => update('role', 'STUDENT')}
            >
              Tôi là học viên
            </button>
            <button
              className={values.role === 'TUTOR' ? 'selected' : ''}
              type="button"
              onClick={() => update('role', 'TUTOR')}
            >
              Tôi là gia sư
            </button>
          </div>
          <div className="auth-form-grid">
            <label className="auth-field">
              <span>Họ</span>
              <input
                value={values.lastName}
                onChange={(e) => update('lastName', e.target.value)}
                autoComplete="family-name"
              />
              {field('lastName')}
            </label>
            <label className="auth-field">
              <span>Tên</span>
              <input
                value={values.firstName}
                onChange={(e) => update('firstName', e.target.value)}
                autoComplete="given-name"
              />
              {field('firstName')}
            </label>
            <label className="auth-field auth-field-wide">
              <span>Email</span>
              <input
                type="email"
                value={values.email}
                onChange={(e) => update('email', e.target.value)}
                autoComplete="email"
              />
              {field('email')}
            </label>
            <label className="auth-field">
              <span>Số CCCD</span>
              <input
                inputMode="numeric"
                maxLength={12}
                value={values.citizenId}
                onChange={(e) => update('citizenId', e.target.value)}
              />
              {field('citizenId')}
            </label>
            <label className="auth-field">
              <span>Số điện thoại</span>
              <input value={values.phone} onChange={(e) => update('phone', e.target.value)} autoComplete="tel" />
              {field('phone')}
            </label>
            <PasswordField
              label="Mật khẩu"
              value={values.password}
              onChange={(value) => update('password', value)}
              autoComplete="new-password"
              showPassword={showPassword}
              onToggle={() => setShowPassword((visible) => !visible)}
              error={field('password')}
            />
            <PasswordField
              label="Xác nhận mật khẩu"
              value={confirmPassword}
              onChange={(value) => {
                setConfirmPassword(value);
                setErrors((current) => ({ ...current, confirmPassword: '' }));
              }}
              autoComplete="new-password"
              showPassword={showConfirmPassword}
              onToggle={() => setShowConfirmPassword((visible) => !visible)}
              error={field('confirmPassword')}
            />
            <label className="auth-field">
              <span>Ngày sinh</span>
              <input
                type="date"
                max={maxDate}
                value={values.dateOfBirth}
                onChange={(e) => update('dateOfBirth', e.target.value)}
              />
              {field('dateOfBirth')}
            </label>
            <label className="auth-field">
              <span>Giới tính</span>
              <select value={values.gender} onChange={(e) => update('gender', e.target.value as FormValues['gender'])}>
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
                <option value="OTHER">Khác</option>
              </select>
              {field('gender')}
            </label>
            <label className="auth-field">
              <span>Tỉnh/thành phố</span>
              <select
                value={values.provinceId}
                disabled={locationsLoading}
                onChange={(e) => {
                  setDistricts([]);
                  update('provinceId', e.target.value);
                  update('districtId', '');
                }}
              >
                <option value="">Chọn tỉnh/thành phố</option>
                {provinces.map((province) => (
                  <option key={province.id} value={province.id}>
                    {province.name}
                  </option>
                ))}
              </select>
              {field('provinceId')}
            </label>
            <label className="auth-field">
              <span>Quận/huyện</span>
              <select
                value={values.districtId}
                disabled={!values.provinceId || locationsLoading}
                onChange={(e) => update('districtId', e.target.value)}
              >
                <option value="">Chọn quận/huyện</option>
                {districts.map((district) => (
                  <option key={district.id} value={district.id}>
                    {district.name}
                  </option>
                ))}
              </select>
              {field('districtId')}
            </label>
          </div>
          {formError && (
            <div className="auth-form-error" role="alert">
              {formError}
            </div>
          )}
          <button className="button auth-submit" type="submit" disabled={isSubmitting || locationsLoading}>
            {isSubmitting ? 'Đang tạo tài khoản...' : 'Đăng ký tài khoản'}
          </button>
        </form>
        <p className="auth-switch">
          Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
        </p>
      </section>
    </main>
  );
}
