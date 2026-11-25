import type { ReactNode } from 'react';
import { Icon } from '../ui/Icon';

interface PasswordFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: string;
  showPassword: boolean;
  onToggle: () => void;
  error?: ReactNode;
}

export function PasswordField({
  label,
  value,
  onChange,
  autoComplete,
  showPassword,
  onToggle,
  error
}: PasswordFieldProps) {
  return (
    <label className="auth-field">
      <span>{label}</span>
      <div className="password-input">
        <input
          type={showPassword ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
        />
        <button
          className="password-toggle"
          type="button"
          onClick={onToggle}
          aria-label={showPassword ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}
          aria-pressed={showPassword}
        >
          <Icon name={showPassword ? 'eyeOff' : 'eye'} size={18} />
        </button>
      </div>
      {error}
    </label>
  );
}
