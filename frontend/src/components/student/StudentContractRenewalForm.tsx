import { useState } from 'react';
import type { Contract, ContractTerms } from '../../services/studentApi';
import { StudentContractTermsForm } from './StudentContractTermsForm';
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
            <h2>Renewal proposal</h2>
            <p>Propose a renewal for this contract when it is active.</p>
          </div>
        </div>
        <button className="student-button" type="button" onClick={() => setOpen((value) => !value)}>
          {open ? 'Close renewal form' : 'Propose renewal'}
        </button>
      </div>
      {open && (
        <div className="student-contract-renewal-form">
          <StudentContractTermsForm
            loading={loading}
            submitLabel="Submit renewal proposal"
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
