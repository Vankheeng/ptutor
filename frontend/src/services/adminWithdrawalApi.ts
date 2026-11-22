import { get, patch } from './httpClient';
import type { PageResponse } from './studentApi';

export type WithdrawalStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';

export interface AdminWithdrawal {
  withdrawal: {
    withdrawalId: string;
    amount: number;
    bankCode: string;
    bankName: string;
    maskedAccountNumber: string;
    accountHolderName: string;
    note: string | null;
    status: WithdrawalStatus;
    reviewedAt: string | null;
    rejectionReason: string | null;
    completedAt: string | null;
    transferReference: string | null;
    createdAt: string;
  };
  userId: string;
  userName: string;
  userEmail: string;
  accountNumber: string | null;
}

const base = '/api/v1/admin/withdrawals';

export function getAdminWithdrawals(status: WithdrawalStatus | '', page: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (status) query.set('status', status);
  return get<PageResponse<AdminWithdrawal>>(`${base}?${query}`, { signal });
}

export const getAdminWithdrawal = (id: string, signal?: AbortSignal) =>
  get<AdminWithdrawal>(`${base}/${id}`, { signal });
export const approveWithdrawal = (id: string) => patch<AdminWithdrawal>(`${base}/${id}/approve`);
export const rejectWithdrawal = (id: string, reason: string) =>
  patch<AdminWithdrawal>(`${base}/${id}/reject`, { reason });
export const completeWithdrawal = (id: string, transferReference: string) =>
  patch<AdminWithdrawal>(`${base}/${id}/complete`, { transferReference });
