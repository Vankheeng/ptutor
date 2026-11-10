import type { ReactNode } from 'react';

export interface ApplicationDetailField {
  label: string;
  value: string;
  className?: string;
}

interface ApplicationDetailPanelProps {
  heading: string;
  statusClass: string;
  statusLabel: string;
  priceLabel: string;
  price: number;
  fields: ApplicationDetailField[];
  notes?: Array<{ label: string; value: string }>;
  metadata?: ApplicationDetailField[];
  actions?: ReactNode;
}

export function ApplicationDetailPanel({
  heading,
  statusClass,
  statusLabel,
  priceLabel,
  price,
  fields,
  notes,
  metadata,
  actions
}: ApplicationDetailPanelProps) {
  return (
    <>
      <div className="tutor-application-panel-heading">
        <span className="tutor-proposal-application-icon" aria-hidden="true">
          ↗
        </span>
        <div>
          <h2>{heading}</h2>
          <span className={`tutor-cert-status ${statusClass}`}>{statusLabel}</span>
        </div>
      </div>

      <div className="tutor-apply-summary">
        <small>{priceLabel}:</small>
        <strong className="tutor-proposed-price-value">{new Intl.NumberFormat('vi-VN').format(price)} VNĐ</strong>
        {fields.map((field) => (
          <span key={field.label} className="application-detail-field">
            <small>{field.label}:</small>
            <strong className={field.className}>{field.value}</strong>
          </span>
        ))}
        {notes?.map((note) => (
          <span key={note.label} className="application-detail-field">
            <small>{note.label}:</small>
            <strong>{note.value}</strong>
          </span>
        ))}
        {metadata?.map((field) => (
          <span key={field.label} className="application-detail-field application-detail-metadata">
            <small>{field.label}:</small>
            <strong className={field.className}>{field.value}</strong>
          </span>
        ))}
      </div>

      {actions && <div className="tutor-my-application-actions">{actions}</div>}
    </>
  );
}
