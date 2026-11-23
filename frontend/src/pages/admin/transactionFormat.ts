import { transactionTypeLabels } from './studentFormat';

export const transactionPurposeLabels: Record<string, string> = {
  ...transactionTypeLabels,
  TOP_UP: 'Nạp ví',
  TUTOR_EARNING: 'Thu nhập gia sư',
  CONTRACT_REFUND: 'Hoàn học phí',
  TEACHING_REQUEST_REFUND: 'Hoàn phí đăng tin dạy',
  STUDYING_REQUEST_REFUND: 'Hoàn phí đăng yêu cầu học',
  WITHDRAWAL_REVERSAL: 'Hoàn tiền rút ví'
};

export const transactionReferenceLabels: Record<string, string> = {
  CONTRACT: 'Hợp đồng',
  LESSON: 'Buổi học',
  PAYMENT: 'Thanh toán',
  WALLET_TRANSACTION: 'Giao dịch ví',
  STUDYING_REQUEST: 'Yêu cầu học',
  TEACHING_REQUEST: 'Tin dạy',
  WITHDRAWAL_REQUEST: 'Yêu cầu rút tiền',
  REFUND_REQUEST: 'Yêu cầu hoàn tiền'
};
