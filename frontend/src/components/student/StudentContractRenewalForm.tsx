import { useState } from 'react';
import type { Contract, ContractTerms } from '../../services/studentApi';
import { ContractTermsForm } from '../../pages/common-user/components/ContractTermsForm';
import { Icon } from '../ui/Icon';

export function StudentContractRenewalForm({
  contract,
  loading,
  onSubmit
}: {
  contract: Contract;
  loading: boolean;
  onSubmit: (terms: ContractTerms) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);

  return (
    <section className="student-panel student-contract-renewal">
      <div className="student-contract-renewal-callout">
        <div className="student-contract-renewal-copy">
          <span className="student-contract-renewal-icon" aria-hidden="true">
            <Icon name="contract" size={17} />
          </span>
          <div>
            <h2>Ã¯Â¿Â½? xu?t gia h?n</h2>
            <p>Ã¯Â¿Â½? xu?t gia h?n h?p d?ng nÃ¯Â¿Â½y khi h?p d?ng dang ho?t d?ng.</p>
          </div>
        </div>
        <button className="student-button" type="button" onClick={() => setOpen((value) => !value)}>
          {open ? 'Ã¯Â¿Â½Ã¯Â¿Â½ng bi?u m?u gia h?n' : 'Ã¯Â¿Â½? xu?t gia h?n'}
        </button>
      </div>
      {open && (
        <div className="student-contract-renewal-form">
          <ContractTermsForm
            loading={loading}
            submitLabel="G?i d? xu?t gia h?n"
            initial={{
              price: contract.price,
              paymentPeriod: contract.paymentPeriod,
              totalLessons: contract.totalLessons,
              preferredSchedule: contract.preferredSchedule,
              schedules: contract.schedules,
              startDate: contract.startDate,
              endDate: contract.endDate
            }}
            onSubmit={onSubmit}
          />
        </div>
      )}
    </section>
  );
}
