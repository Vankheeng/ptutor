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
  if (contract.status !== 'PENDING') return null;
  const createdByYou = contract.createdByUserId === currentUserId;

  return (
    <div className="student-contract-actions-panel">
      <div className="student-contract-actions-copy">
        <h2>
          <Icon name="contract" size={15} /> Thao tác
        </h2>
        <p>
          {createdByYou
            ? 'Bạn có thể hủy hợp đồng này khi hợp đồng đang ở trạng thái Đang chờ.'
            : 'Chỉ khả dụng khi hợp đồng ở trạng thái Đang chờ và bạn là bên nhận.'}
        </p>
      </div>
      <div className="student-contract-actions-buttons">
        {!createdByYou && (
          <>
            <button className="student-button success" disabled={busy} type="button" onClick={onSign}>
              Ký hợp đồng
            </button>
            <button className="student-button danger" disabled={busy} type="button" onClick={onReject}>
              Từ chối hợp đồng
            </button>
          </>
        )}
        {createdByYou && (
          <button className="student-button danger" disabled={busy} type="button" onClick={onCancel}>
            Hủy hợp đồng
          </button>
        )}
      </div>
    </div>
  );
}
