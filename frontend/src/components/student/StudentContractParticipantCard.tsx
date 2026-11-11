import type { ReactNode } from 'react';
import { Icon, type IconName } from '../ui/Icon';

interface ParticipantDetail {
  label: string;
  value: ReactNode;
}

interface Props {
  title: string;
  icon: IconName;
  details: ParticipantDetail[];
}

export function StudentContractParticipantCard({ title, icon, details }: Props) {
  return (
    <section className="student-panel student-contract-participant-card">
      <div className="student-contract-participant-heading">
        <span className="student-contract-participant-icon" aria-hidden="true">
          <Icon name={icon} size={18} />
        </span>
        <h2>{title}</h2>
      </div>
      <div className="student-contract-participant-details">
        {details.map((detail) => (
          <div key={detail.label}>
            <span>{detail.label}</span>
            <strong>{detail.value}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}
