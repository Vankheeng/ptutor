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
            <h2>Đề xuất gia hạn</h2>
            <p>Đề xuất gia hạn hợp đồng này khi hợp đồng đang hoạt động.</p>
          </div>
        </div>
        <button className="student-button" type="button" onClick={() => setOpen((value) => !value)}>
          {open ? 'Đóng biểu mẫu gia hạn' : 'Đề xuất gia hạn'}
        </button>
      </div>
      {open && (
        <div className="student-contract-renewal-form">
          <StudentContractTermsForm
            loading={loading}
            submitLabel="Gửi đề xuất gia hạn"
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
