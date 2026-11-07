import type { ReactNode } from 'react';

export interface ApplicationSourceModel {
  eyebrow: string;
  subject: string;
  title: string;
  grade: string;
  highlights: Array<{ icon: string; label: string; value: string }>;
  authorLabel: string;
  authorName: string;
  createdText: string;
  onOpen?: () => void;
}

export interface ApplicationModel {
  heading: string;
  statusClass: string;
  statusLabel: string;
  price: number;
  summary: Array<{ label: string; value: string }>;
  notes?: Array<{ label: string; value: string }>;
  footerText: string;
  actions?: ReactNode;
}

interface ApplicationSplitCardProps {
  source: ApplicationSourceModel;
  application: ApplicationModel;
  onCardOpen?: () => void;
  className?: string;
}

export function ApplicationSplitCard({ source, application, onCardOpen, className }: ApplicationSplitCardProps) {
  const sourceInteractive = Boolean(source.onOpen);
  const cardInteractive = Boolean(onCardOpen);

  return (
    <article
      className={`tutor-panel tutor-proposal-card tutor-proposal-split application-split-card${cardInteractive ? ' application-card-clickable' : ''}${className ? ` ${className}` : ''}`}
      role={cardInteractive ? 'link' : undefined}
      tabIndex={cardInteractive ? 0 : undefined}
      onClick={onCardOpen}
      onKeyDown={
        onCardOpen
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onCardOpen();
              }
            }
          : undefined
      }
    >
      <section
        className="tutor-proposal-request-side"
        aria-label={sourceInteractive ? 'Mở chi tiết yêu cầu' : undefined}
        role={sourceInteractive ? 'button' : undefined}
        tabIndex={sourceInteractive ? 0 : undefined}
        onClick={source.onOpen}
        onKeyDown={
          source.onOpen
            ? (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  source.onOpen?.();
                }
              }
            : undefined
        }
      >
        <div className="tutor-proposal-side-heading">
          <span className="tutor-eyebrow">{source.eyebrow}</span>
          <span className="tutor-subject-tag">{source.subject}</span>
        </div>
        <h2>{source.title}</h2>
        <p className="tutor-proposal-grade">{source.grade}</p>
        <div className="tutor-highlight-grid">
          {source.highlights.map((highlight, index) => (
            <ApplicationHighlight key={`${highlight.label}-${index}`} {...highlight} />
          ))}
        </div>
        <div className="tutor-request-author">
          <span>
            {source.authorLabel} <strong>{source.authorName}</strong>
          </span>
          <span>{source.createdText}</span>
        </div>
      </section>

      <section className="tutor-proposal-my-side" aria-label={application.heading}>
        <div className="tutor-proposal-application-heading">
          <span className="tutor-proposal-application-icon" aria-hidden="true">
            ↗
          </span>
          <div>
            <h3>{application.heading}</h3>
            <span className={`tutor-cert-status ${application.statusClass}`}>{application.statusLabel}</span>
          </div>
        </div>

        <div className="tutor-proposal-offer-price">
          <small>Giá đề xuất</small>
          <strong>
            {new Intl.NumberFormat('vi-VN').format(application.price)} <span>VNĐ</span>
          </strong>
        </div>

        <div className="tutor-proposal-summary tutor-proposal-my-summary">
          {application.summary.map((item, index) => (
            <span key={`${item.label}-${index}`}>
              <small>{item.label}</small>
              <strong>{item.value}</strong>
            </span>
          ))}
        </div>

        {application.notes?.map((note, index) => (
          <div className="tutor-application-note tutor-proposal-message" key={`${note.label}-${index}`}>
            <span className="tutor-application-note-tag">{note.label}</span>
            <p>{note.value}</p>
          </div>
        ))}

        <div className="tutor-proposal-footer">
          <small>{application.footerText}</small>
          {application.actions && (
            <div
              className="tutor-proposal-actions"
              onClick={(event) => event.stopPropagation()}
              onKeyDown={(event) => event.stopPropagation()}
            >
              {application.actions}
            </div>
          )}
        </div>
      </section>
    </article>
  );
}

function ApplicationHighlight({ icon, label, value }: { icon: string; label: string; value: string }) {
  return (
    <div className="tutor-proposal-highlight">
      <span aria-hidden="true">{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </div>
  );
}
