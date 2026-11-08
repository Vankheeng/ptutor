export interface ReferenceNavigationTarget {
  to: string;
  label: string;
}

export function referenceNavigation(
  basePath: '/student' | '/tutor',
  referenceType: string | null | undefined,
  referenceId: string | null | undefined
): ReferenceNavigationTarget | null {
  if (!referenceType || !referenceId) return null;

  const id = encodeURIComponent(referenceId);
  const tutor = basePath === '/tutor';

  switch (referenceType) {
    case 'CONTRACT':
      return tutor
        ? { to: '/tutor/sections/contracts', label: 'Xem hợp đồng' }
        : { to: `/student/contracts/${id}`, label: 'Xem chi tiết hợp đồng' };
    case 'STUDYING_REQUEST':
      return { to: `${basePath}/studying-requests/${id}`, label: 'Xem chi tiết yêu cầu học' };
    case 'TEACHING_REQUEST':
      return tutor
        ? { to: `/tutor/sections/teaching-requests/${id}`, label: 'Xem chi tiết yêu cầu dạy' }
        : { to: '/student/teaching-requests', label: 'Xem yêu cầu dạy' };
    case 'REFUND_REQUEST':
      return { to: `${basePath}/refund-requests/${id}`, label: 'Xem yêu cầu hoàn tiền' };
    case 'WITHDRAWAL_REQUEST':
      return { to: `${basePath}/wallet/transactions/WITHDRAWAL/${id}`, label: 'Xem yêu cầu rút tiền' };
    case 'WALLET_TRANSACTION':
      return { to: `${basePath}/wallet/transactions/WALLET_TRANSACTION/${id}`, label: 'Xem giao dịch' };
    case 'PAYMENT':
      return { to: `${basePath}/wallet/transactions/PAYMENT/${id}`, label: 'Xem giao dịch' };
    case 'TUTOR_STUDENT_REQUEST':
      return { to: tutor ? '/tutor/proposals' : '/student/applications', label: 'Xem yêu cầu' };
    case 'STUDENT_TUTOR_REQUEST':
      return { to: tutor ? '/tutor/students' : '/student/applications', label: 'Xem yêu cầu' };
    default:
      return null;
  }
}
