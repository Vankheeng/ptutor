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
  if (!currentUserId) return null;
  const pending = contract.status === 'PENDING';
  const createdByYou = contract.createdByUserId === currentUserId;
  const signingDeadline = new Date(new Date(contract.createdAt).getTime() + 7 * 24 * 60 * 60 * 1000);
  const expired = pending && isContractExpired(contract.endDate, signingDeadline);
  const studentSigned = createdByYou || Boolean(contract.signedByUserId && !createdByYou);
  const tutorSigned = !createdByYou || Boolean(contract.signedByUserId && createdByYou);

  return (
    <div className="student-contract-actions-panel">
      <div className="student-contract-actions-copy">
        <h2>
          <Icon name="contract" size={15} />{' '}
          {expired
            ? 'Hợp đồng đã hết hạn'
            : pending
              ? createdByYou
                ? 'Đang chờ gia sư ký'
                : 'Yêu cầu ký hợp đồng'
              : 'Điều khoản hợp đồng'}
        </h2>
        <p>
          {!pending
            ? 'Hợp đồng đã được ký hoặc hoàn tất. Bạn có thể xem và in bản PDF.'
            : expired
              ? 'Hợp đồng đã hết hạn ký.'
              : createdByYou
                ? 'Hợp đồng đã gửi cho gia sư và đang chờ ký.'
                : 'Gia sư đã tạo hợp đồng. Vui lòng kiểm tra điều khoản trước khi ký.'}
        </p>
        {pending && (
          <p className="student-contract-signing-deadline">
            Hạn ký:{' '}
            {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(signingDeadline)}
          </p>
        )}
        <span className="student-contract-terms-link">
          <ContractPdfLink
            studentName={[contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên'}
            studentEmail={contract.studentEmail}
            studentAddress={contract.teachingMode === 'ONLINE' ? null : contract.studentDetailAddress}
            tutorName={[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'}
            tutorEmail={contract.tutorEmail}
            subject={contract.subjectName}
            grade={contract.gradeName}
            teachingMode={contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
            studentSigned={studentSigned}
            tutorSigned={tutorSigned}
            studentSignedAt={createdByYou ? contract.createdAt : contract.signedByUserId ? contract.signedAt : null}
            tutorSignedAt={createdByYou ? (contract.signedByUserId ? contract.signedAt : null) : contract.createdAt}
            price={contract.price}
            paymentPeriod={contract.paymentPeriod}
            startDate={contract.startDate}
            endDate={contract.endDate}
            schedules={contract.schedules}
          />
        </span>
        <ul className="student-contract-terms-summary">
          <li>Hai bên đồng ý với các điều khoản đã ghi trong hợp đồng.</li>
          <li>Nghỉ học cần báo trước; hai bên có thể đổi lịch hoặc đề nghị hoàn tiền theo thỏa thuận.</li>
          <li>Buổi học cần được thanh toán trước khi bắt đầu.</li>
          <li>Học phí của buổi đã được xác nhận tham gia không được hoàn lại.</li>
          <li>Hai bên có quyền khiếu nại hoặc yêu cầu dừng hợp đồng theo thỏa thuận.</li>
        </ul>
      </div>
      {pending && !expired && (
        <div className="student-contract-actions-buttons">
          {!createdByYou ? (
            <>
              <button className="student-button danger" disabled={busy} type="button" onClick={onReject}>
                Từ chối hợp đồng
              </button>
              <button className="student-button success" disabled={busy} type="button" onClick={onSign}>
                Ký hợp đồng
              </button>
            </>
          ) : (
            <button className="student-button danger" disabled={busy} type="button" onClick={onCancel}>
              Hủy hợp đồng
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function isContractExpired(endDate: string, signingDeadline: Date) {
  if (signingDeadline.getTime() <= Date.now()) return true;
  const [year, month, day] = endDate.split('-').map(Number);
  const end = new Date(year, month - 1, day);
  const today = new Date();
  const currentDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return end <= currentDay;
}
