import type { Contract } from '../../../services/studentApi';
import { Icon } from '../../../components/ui/Icon';
import { ContractPdfLink } from './ContractPdfLink';

interface Props {
  contract: Contract;
  currentUserId?: string;
  busy: boolean;
  onSign: () => void;
  onReject: () => void;
  onCancel: () => void;
}

export function ContractActionBar({ contract, currentUserId, busy, onSign, onReject, onCancel }: Props) {
  if (contract.status !== 'PENDING' || !currentUserId) return null;
  const createdByYou = contract.createdByUserId === currentUserId;
  const expired = isContractExpired(contract.endDate);

  return (
    <div className="student-contract-actions-panel">
      <div className="student-contract-actions-copy">
        <h2>
          <Icon name="contract" size={15} />{' '}
          {expired ? 'Hợp đồng đã hết hạn' : createdByYou ? 'Đang chờ gia sư ký' : 'Yêu cầu ký hợp đồng'}
        </h2>
        <p>
          {expired
            ? 'Hợp đồng đã hết hạn và không còn thao tác ký, từ chối hoặc hủy.'
            : createdByYou
              ? 'Hợp đồng đã được gửi cho gia sư và đang chờ ký.'
              : 'Gia sư đã tạo hợp đồng này. Vui lòng kiểm tra các điều khoản trước khi ký.'}
        </p>
        {!expired && !createdByYou && (
          <>
            <span className="student-contract-terms-link">
              <ContractPdfLink
                studentName={
                  [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên'
                }
                studentEmail={contract.studentEmail}
                studentAddress={contract.studentDetailAddress}
                tutorName={[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'}
                tutorEmail={contract.tutorEmail}
                subject={contract.subjectName}
                grade={contract.gradeName}
                teachingMode={contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
                studentSigned={contract.signedByUserId === contract.studentId || contract.status !== 'PENDING'}
                tutorSigned={contract.signedByUserId === contract.tutorId || contract.status !== 'PENDING'}
                studentSignedAt={
                  contract.createdByUserId === contract.studentId
                    ? contract.createdAt
                    : contract.signedByUserId === contract.studentId
                      ? contract.signedAt
                      : null
                }
                tutorSignedAt={
                  contract.createdByUserId === contract.tutorId
                    ? contract.createdAt
                    : contract.signedByUserId === contract.tutorId
                      ? contract.signedAt
                      : null
                }
                price={contract.price}
                paymentPeriod={contract.paymentPeriod}
                startDate={contract.startDate}
                endDate={contract.endDate}
                schedules={contract.schedules}
              />
            </span>
            <ul className="student-contract-terms-summary">
              <li>Đồng ý với các điều khoản đã ghi trong hợp đồng.</li>
              <li>Nghỉ học cần báo trước; hai bên có thể đổi lịch hoặc đề nghị hoàn tiền theo thỏa thuận.</li>
              <li>Học phí của buổi đã được hai bên xác nhận tham gia không được hoàn lại.</li>
              <li>Hai bên có quyền khiếu nại hoặc yêu cầu dừng hợp đồng khi đã thỏa thuận.</li>
              <li>Buổi học bị đánh dấu đã dạy nhưng thực tế không diễn ra có thể được yêu cầu hoàn tiền.</li>
            </ul>
          </>
        )}
      </div>
      {!expired && (
        <div className="student-contract-actions-buttons">
          {!createdByYou && (
            <>
              <button className="student-button danger" disabled={busy} type="button" onClick={onReject}>
                Từ chối hợp đồng
              </button>
              <button className="student-button success" disabled={busy} type="button" onClick={onSign}>
                Ký hợp đồng
              </button>
            </>
          )}
          {createdByYou && (
            <button className="student-button danger" disabled={busy} type="button" onClick={onCancel}>
              Hủy hợp đồng
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function isContractExpired(endDate: string) {
  const [year, month, day] = endDate.split('-').map(Number);
  const end = new Date(year, month - 1, day);
  const today = new Date();
  const currentDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return end <= currentDay;
}
