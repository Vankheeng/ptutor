import { get } from './httpClient';
import type { PageResponse } from './studentApi';
import type { AdminStudentTransaction, TransactionSource } from './adminStudentApi';

export type TransactionFlow = 'IN' | 'OUT' | 'INTERNAL' | 'NONE';

export interface AdminTransaction {
  userId: string;
  userName: string;
  userEmail: string;
  purpose: string;
  flow: TransactionFlow;
  transactionCode: string | null;
  transferReference: string | null;
  transaction: AdminStudentTransaction;
}

export interface AdminTransactionSummary {
  moneyIn: number;
  moneyOut: number;
  net: number;
  internalCount: number;
}

export interface AdminTransactionFilters {
  page: number;
  source?: TransactionSource;
  flow?: TransactionFlow;
  type?: string;
  method?: string;
  status?: string;
  search?: string;
  fromDate?: string;
  toDate?: string;
}

const base = '/api/v1/admin/transactions';

function queryFilters(filters: AdminTransactionFilters) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (key !== 'page' && value) query.set(key, String(value));
  });
  return query;
}

export function getAdminTransactions(filters: AdminTransactionFilters, signal?: AbortSignal) {
  const query = queryFilters(filters);
  query.set('page', String(filters.page));
  query.set('size', '20');
  return get<PageResponse<AdminTransaction>>(`${base}?${query}`, { signal });
}

export function getAdminTransactionSummary(filters: AdminTransactionFilters, signal?: AbortSignal) {
  return get<AdminTransactionSummary>(`${base}/summary?${queryFilters(filters)}`, { signal });
}

export function getAdminTransaction(source: TransactionSource, id: string, signal?: AbortSignal) {
  return get<AdminTransaction>(`${base}/${source}/${id}`, { signal });
}
