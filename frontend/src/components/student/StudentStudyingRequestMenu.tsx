import type { RequestStatus } from '../../services/studentApi';

export type StudyingRequestAction = 'cancel' | 'pay' | 'open' | 'close';

interface Props {
  status: RequestStatus;
  requestId: string;
  busy: boolean;
  onAction: (id: string, action: StudyingRequestAction) => void;
  onNavigate: (path: string) => void;
}

export function StudentStudyingRequestMenu({ status, requestId, busy, onAction, onNavigate }: Props) {
  const item = (label: string, action: () => void, className = '') => (
    <button
      className={className}
      type="button"
      disabled={busy}
      onClick={(event) => {
        event.stopPropagation();
        action();
      }}
    >
      {label}
    </button>
  );

  return (
    <div className="student-request-menu" onClick={(event) => event.stopPropagation()}>
      {(status === 'DRAFT' || status === 'OPEN') &&
        item('Chỉnh sửa thông tin', () => onNavigate(`/student/studying-requests/${requestId}/edit`), 'warning')}
      {status === 'DRAFT' && item('Thanh toán để đăng', () => onAction(requestId, 'pay'), 'payment')}
      {status === 'OPEN' && item('Đóng yêu cầu', () => onAction(requestId, 'close'))}
      {status === 'CLOSED' && item('Mở lại yêu cầu', () => onAction(requestId, 'open'))}
      {['DRAFT', 'OPEN', 'MATCHED', 'CLOSED'].includes(status) &&
        item('Hủy yêu cầu', () => onAction(requestId, 'cancel'), 'danger')}
    </div>
  );
}
