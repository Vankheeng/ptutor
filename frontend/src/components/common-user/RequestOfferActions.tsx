type OfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';

interface Props {
  role: 'tutor' | 'student';
  status: OfferStatus;
  busy?: boolean;
  canAct?: boolean;
  onAccept?: () => void;
  onReject?: () => void;
}

/** Shared offer actions. Each page owns the API calls and passes callbacks. */
export function RequestOfferActions({ role, status, busy = false, canAct = true, onAccept, onReject }: Props) {
  if (status !== 'PENDING' || !canAct) return null;
  const acceptClass = role === 'student' ? 'student-button success' : 'tutor-button';
  const rejectClass = role === 'student' ? 'student-button danger' : 'tutor-button secondary';
  return (
    <>
      {onAccept && (
        <button className={acceptClass} type="button" disabled={busy} onClick={onAccept}>
          Chấp nhận
        </button>
      )}
      {onReject && (
        <button className={rejectClass} type="button" disabled={busy} onClick={onReject}>
          Từ chối
        </button>
      )}
    </>
  );
}
