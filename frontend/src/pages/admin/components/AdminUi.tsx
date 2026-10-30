import { useEffect, useId, useRef, type ReactNode } from 'react';
import { getInitials } from '../../../utils/formatters';
import { displayableUrl, type StatusMeta } from '../adminFormat';

export function StatusBadge({ meta }: { meta: StatusMeta }) {
  return <span className={`admin-badge admin-badge-${meta.tone}`}>{meta.label}</span>;
}

export function AdminAvatar({ name, url, size = 'md' }: { name: string; url?: string | null; size?: 'md' | 'lg' }) {
  const source = displayableUrl(url);
  return (
    <span className={`admin-avatar admin-avatar-${size}`} aria-hidden="true">
      {source ? <img src={source} alt="" /> : getInitials(name)}
    </span>
  );
}

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  unit: string;
  onChange: (page: number) => void;
}

export function AdminPagination({ page, totalPages, totalElements, unit, onChange }: PaginationProps) {
  return (
    <div className="admin-pagination">
      <span>
        {totalElements} {unit}
      </span>
      {totalPages > 1 && (
        <div className="admin-pagination-buttons">
          <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>
            Trước
          </button>
          <span aria-current="page">
            {page + 1} / {totalPages}
          </span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
            Sau
          </button>
        </div>
      )}
    </div>
  );
}

export function AdminState({ loading, error, empty }: { loading?: boolean; error?: string | null; empty?: string }) {
  if (loading) return <div className="admin-state">Đang tải…</div>;
  if (error)
    return (
      <div className="admin-alert admin-alert-danger" role="alert">
        {error}
      </div>
    );
  if (empty) return <div className="admin-state">{empty}</div>;
  return null;
}

interface ModalProps {
  title: ReactNode;
  subtitle?: ReactNode;
  size?: 'sm' | 'md' | 'xl';
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

export function AdminModal({ title, subtitle, size = 'md', onClose, children, footer }: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', handleKey);
    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);

  return (
    <div className="admin-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div
        ref={dialogRef}
        className={`admin-modal admin-modal-${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <header className="admin-modal-header">
          <div>
            <h2 id={titleId}>{title}</h2>
            {subtitle && <div className="admin-modal-subtitle">{subtitle}</div>}
          </div>
          <button className="admin-icon-button" type="button" aria-label="Đóng" onClick={onClose}>
            <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </header>
        <div className="admin-modal-body">{children}</div>
        {footer && <footer className="admin-modal-footer">{footer}</footer>}
      </div>
    </div>
  );
}

interface ReasonFieldProps {
  id: string;
  label: string;
  value: string;
  minLength: number;
  maxLength: number;
  showError: boolean;
  placeholder?: string;
  emptyMessage?: string;
  onChange: (value: string) => void;
}

export function ReasonField({
  id,
  label,
  value,
  minLength,
  maxLength,
  showError,
  placeholder,
  emptyMessage = 'Vui lòng nhập lý do từ chối.',
  onChange
}: ReasonFieldProps) {
  const length = value.trim().length;
  const invalid = showError && length < minLength;
  const hint = minLength > 1 ? `Từ ${minLength} đến ${maxLength} ký tự.` : `Tối đa ${maxLength} ký tự.`;
  const error =
    length === 0 ? emptyMessage : `Lý do cần ít nhất ${minLength} ký tự (hiện có ${length}).`;
  return (
    <div className="admin-field">
      <label htmlFor={id}>
        {label} <span className="admin-required">*</span>
      </label>
      <textarea
        id={id}
        rows={5}
        maxLength={maxLength}
        value={value}
        placeholder={placeholder}
        aria-invalid={invalid}
        aria-describedby={`${id}-help`}
        onChange={(event) => onChange(event.target.value)}
      />
      <div id={`${id}-help`} className="admin-field-help">
        <span className={invalid ? 'admin-field-error' : undefined}>{invalid ? error : hint}</span>
        <span>
          {value.length}/{maxLength}
        </span>
      </div>
    </div>
  );
}

export function DefinitionGrid({ items, columns = 2 }: { items: [string, ReactNode][]; columns?: 2 | 3 }) {
  return (
    <dl className={`admin-definitions admin-definitions-${columns}`}>
      {items.map(([term, value]) => (
        <div key={term}>
          <dt>{term}</dt>
          <dd>{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

export function FilterChips<T extends string | number>({
  label,
  options,
  value,
  onChange
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="admin-chips" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          className={option.value === value ? 'active' : undefined}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
