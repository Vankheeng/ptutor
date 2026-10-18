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
          <Icon name="contract" size={15} /> Actions
        </h2>
        <p>
          {createdByYou
            ? 'You can cancel this contract while it is Pending.'
            : 'Only available when contract is in Pending status and you are the recipient.'}
        </p>
      </div>
      <div className="student-contract-actions-buttons">
        {!createdByYou && (
          <>
            <button className="student-button success" disabled={busy} type="button" onClick={onSign}>
              Sign contract
            </button>
            <button className="student-button danger" disabled={busy} type="button" onClick={onReject}>
              Reject contract
            </button>
          </>
        )}
        {createdByYou && (
          <button className="student-button danger" disabled={busy} type="button" onClick={onCancel}>
            Cancel contract
          </button>
        )}
      </div>
    </div>
  );
}
