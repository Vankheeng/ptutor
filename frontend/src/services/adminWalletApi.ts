import { get } from './httpClient';
import type { PageResponse } from './studentApi';

export interface AdminWalletSummary {
  walletCount: number;
  availableBalance: number;
  pendingWithdrawalBalance: number;
  heldContractCount: number;
  heldTuition: number;
}

export interface AdminWallet {
  walletId: string;
  userId: string;
  userName: string;
  userEmail: string;
  userType: 'STUDENT' | 'TUTOR' | 'OTHER';
  balance: number;
  pendingBalance: number;
}

export interface AdminHeldTuition {
  contractId: string;
  studentUserId: string;
  studentName: string;
  studentEmail: string;
  tutorUserId: string;
  tutorName: string;
  paidAmount: number;
  refundedAmount: number;
  tutorPaidAmount: number;
  heldAmount: number;
  lastPaidAt: string | null;
}

export interface AdminContractTransaction {
  id: string;
  type: 'TUITION_PAYMENT' | 'TUITION_REFUND' | 'TUTOR_PAYOUT';
  userName: string;
  amount: number;
  status: string;
  occurredAt: string;
  transactionCode: string | null;
}

const base = '/api/v1/admin/wallets';

export const getAdminWalletSummary = (signal?: AbortSignal) =>
  get<AdminWalletSummary>(`${base}/summary`, { signal });

export const getAdminWallets = (search: string, page: number, signal?: AbortSignal) => {
  const params = new URLSearchParams({ search, page: String(page), size: '20' });
  return get<PageResponse<AdminWallet>>(`${base}?${params}`, { signal });
};

export const getAdminHeldTuition = (search: string, page: number, signal?: AbortSignal) => {
  const params = new URLSearchParams({ search, page: String(page), size: '20' });
  return get<PageResponse<AdminHeldTuition>>(`${base}/tuition?${params}`, { signal });
};

export const getAdminContractTransactions = (contractId: string, page: number, signal?: AbortSignal) =>
  get<PageResponse<AdminContractTransaction>>(`${base}/contracts/${contractId}/transactions?page=${page}&size=20`, { signal });
