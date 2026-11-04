import type { AdminEmployeeSummary } from '../../../services/adminEmployeeApi';
import { formatDate } from '../adminFormat';
import { employeeName } from '../employeeFormat';
import { AdminState } from './AdminUi';
import type { EmployeeHandover } from './useEmployeeHandover';

export function EmployeeHandoverFields({ handover, tried }: { handover: EmployeeHandover; tried: boolean }) {
  if (handover.loading || handover.loadError) {
    return <AdminState loading={handover.loading} error={handover.loadError} />;
  }
  const { loaded, complaints, refunds } = handover;
  if (!loaded) return null;

  const targetSelect = (
    id: string,
    candidates: AdminEmployeeSummary[],
    value: string,
    onChange: (value: string) => void,
    missing: boolean
  ) => (
    <div className="admin-field">
      <label htmlFor={id}>
        Chuyển cho <span className="admin-required">*</span>
      </label>
      <select id={id} value={value} aria-invalid={tried && missing} onChange={(event) => onChange(event.target.value)}>
        <option value="">Chọn nhân viên</option>
        {candidates.map((candidate) => (
          <option key={candidate.employeeId} value={candidate.employeeId}>
            {employeeName(candidate)} · {candidate.email}
          </option>
        ))}
      </select>
      {tried && missing && <span className="admin-field-error">Vui lòng chọn người nhận.</span>}
    </div>
  );

  return (
    <>
      {complaints.length > 0 && (
        <section className="admin-handover">
          <h3>Khiếu nại đang xử lý ({complaints.length})</h3>
          <ul>
            {complaints.map((complaint) => (
              <li key={complaint.id}>
                <strong>{complaint.title}</strong>
                <span>
                  {complaint.complainantName ?? '—'} · {formatDate(complaint.createdAt)}
                </span>
              </li>
            ))}
          </ul>
          {handover.noComplaintCandidate ? (
            <div className="admin-alert admin-alert-danger">
              Không có nhân viên chăm sóc khách hàng khác đang hoạt động.
            </div>
          ) : (
            targetSelect(
              'handover-complaints',
              loaded.complaintTargets,
              handover.complaintTarget,
              handover.setComplaintTarget,
              handover.missingComplaintTarget
            )
          )}
        </section>
      )}
      {refunds.length > 0 && (
        <section className="admin-handover">
          <h3>Yêu cầu hoàn tiền đang xử lý ({refunds.length})</h3>
          <ul>
            {refunds.map((refund) => (
              <li key={refund.id}>
                <strong>{refund.studentName ?? '—'}</strong>
                <span>
                  {refund.subjectName ?? '—'} · {formatDate(refund.createdAt)}
                </span>
              </li>
            ))}
          </ul>
          {handover.noRefundCandidate ? (
            <div className="admin-alert admin-alert-danger">Không có kế toán khác đang hoạt động.</div>
          ) : (
            targetSelect(
              'handover-refunds',
              loaded.refundTargets,
              handover.refundTarget,
              handover.setRefundTarget,
              handover.missingRefundTarget
            )
          )}
        </section>
      )}
    </>
  );
}
