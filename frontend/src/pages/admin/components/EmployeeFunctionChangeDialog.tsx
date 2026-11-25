import { useState } from 'react';
import type { AdminEmployeeDetail, EmployeeJobFunction } from '../../../services/adminEmployeeApi';
import { employeeErrorMessage, employeeName, jobFunctionMeta } from '../employeeFormat';
import { AdminModal } from './AdminUi';
import { EmployeeHandoverFields } from './EmployeeHandover';
import { useEmployeeHandover } from './useEmployeeHandover';

interface Props {
  employee: Pick<AdminEmployeeDetail, 'employeeId' | 'email' | 'firstName' | 'lastName' | 'jobFunction'>;
  nextFunction: EmployeeJobFunction;
  onClose: () => void;
  /** Saves the edited employee; called once the open work has been handed over. */
  onConfirm: () => Promise<void>;
}

export function EmployeeFunctionChangeDialog({ employee, nextFunction, onClose, onConfirm }: Props) {
  const handover = useEmployeeHandover(employee.employeeId, true);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const transferring = handover.openCount > 0;

  const submit = async () => {
    setTried(true);
    if (handover.incomplete) return;
    setBusy(true);
    setError(null);
    try {
      await handover.transfer();
      await onConfirm();
    } catch (saveError) {
      setError(employeeErrorMessage(saveError));
      setBusy(false);
      handover.reload();
    }
  };

  return (
    <AdminModal
      title="Đổi chức năng?"
      subtitle={employeeName(employee)}
      size={transferring ? 'md' : 'sm'}
      onClose={onClose}
      footer={
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" onClick={onClose}>
            Huỷ
          </button>
          <button
            className="admin-button"
            type="button"
            disabled={busy || handover.loading || Boolean(handover.loadError) || handover.blocked}
            onClick={() => void submit()}
          >
            {transferring ? 'Chuyển việc và lưu' : 'Lưu thay đổi'}
          </button>
        </div>
      }
    >
      <div className="admin-modal-pad admin-stack-gap">
        {error && (
          <div className="admin-alert admin-alert-danger" role="alert">
            {error}
          </div>
        )}
        <div className="admin-function-change">
          <span className={`admin-badge admin-badge-${jobFunctionMeta[employee.jobFunction].tone}`}>
            {jobFunctionMeta[employee.jobFunction].label}
          </span>
          <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
          <span className={`admin-badge admin-badge-${jobFunctionMeta[nextFunction].tone}`}>
            {jobFunctionMeta[nextFunction].label}
          </span>
        </div>
        <EmployeeHandoverFields handover={handover} tried={tried} />
      </div>
    </AdminModal>
  );
}
