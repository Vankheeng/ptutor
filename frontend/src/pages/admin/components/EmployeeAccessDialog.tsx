import { useState } from 'react';
import { deactivateEmployee, reactivateEmployee, type AdminEmployeeDetail } from '../../../services/adminEmployeeApi';
import { REASON_MAX, REASON_MIN, employeeErrorMessage, employeeName, jobFunctionMeta } from '../employeeFormat';
import { AdminModal, ReasonField } from './AdminUi';
import { EmployeeHandoverFields } from './EmployeeHandover';
import { useEmployeeHandover } from './useEmployeeHandover';

export type EmployeeAccessAction = 'deactivate' | 'reactivate';

interface Props {
  action: EmployeeAccessAction;
  employee: Pick<AdminEmployeeDetail, 'employeeId' | 'email' | 'firstName' | 'lastName' | 'jobFunction'>;
  onClose: () => void;
  onDone: (message: string) => void;
}

export function EmployeeAccessDialog({ action, employee, onClose, onDone }: Props) {
  const deactivating = action === 'deactivate';
  const handover = useEmployeeHandover(employee.employeeId, deactivating);
  const [reason, setReason] = useState('');
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const transferring = deactivating && handover.openCount > 0;

  const submit = async () => {
    setTried(true);
    if (reason.trim().length < REASON_MIN || (deactivating && handover.incomplete)) return;
    setBusy(true);
    setError(null);
    try {
      if (deactivating) {
        await handover.transfer();
        await deactivateEmployee(employee.employeeId, reason.trim());
        onDone(
          transferring ? `Đã chuyển ${handover.openCount} việc và vô hiệu hóa nhân viên.` : 'Đã vô hiệu hóa nhân viên.'
        );
      } else {
        await reactivateEmployee(employee.employeeId, reason.trim());
        onDone('Đã kích hoạt lại nhân viên.');
      }
    } catch (actionError) {
      setError(employeeErrorMessage(actionError));
      setBusy(false);
      if (deactivating) handover.reload();
    }
  };

  return (
    <AdminModal
      title={deactivating ? 'Vô hiệu hóa nhân viên?' : 'Kích hoạt lại nhân viên?'}
      subtitle={`${employeeName(employee)} · ${jobFunctionMeta[employee.jobFunction].label}`}
      size={transferring ? 'md' : 'sm'}
      onClose={onClose}
      footer={
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" onClick={onClose}>
            Huỷ
          </button>
          <button
            className={`admin-button${deactivating ? ' danger' : ''}`}
            type="button"
            disabled={busy || (deactivating && (handover.loading || Boolean(handover.loadError) || handover.blocked))}
            onClick={() => void submit()}
          >
            {!deactivating ? 'Kích hoạt lại' : transferring ? 'Chuyển việc và vô hiệu hóa' : 'Vô hiệu hóa'}
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
        {deactivating && handover.loaded && handover.openCount === 0 && (
          <div className="admin-alert admin-alert-warning">Nhân viên sẽ bị đăng xuất khỏi mọi thiết bị.</div>
        )}
        {deactivating && <EmployeeHandoverFields handover={handover} tried={tried} />}
        <ReasonField
          id="employee-access-reason"
          label="Lý do"
          value={reason}
          minLength={REASON_MIN}
          maxLength={REASON_MAX}
          showError={tried}
          emptyMessage="Vui lòng nhập lý do."
          onChange={setReason}
        />
      </div>
    </AdminModal>
  );
}
