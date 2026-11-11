import type { Contract } from '../../services/studentApi';
import { Icon } from '../ui/Icon';

interface Props {
  contract: Contract;
  currentUserId?: string;
  busy: boolean;
  onSign: () => void;
  onReject: () => void;
  onCancel: () => void;
}

export function StudentContractActionBar({ contract, currentUserId, busy, onSign, onReject, onCancel }: Props) {
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
