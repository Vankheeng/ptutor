import type { TransactionFlow } from '../../services/adminTransactionApi';

export const flowLabels: Record<TransactionFlow, string> = {
  IN: 'Tiền vào',
  OUT: 'Tiền ra',
  INTERNAL: 'Nội bộ',
  NONE: 'Chưa ghi nhận'
};
