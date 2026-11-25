import { get, patch } from './httpClient';
import type { PageResponse } from './studentApi';

export type ManagedUserRole = 'STUDENT' | 'TUTOR';
export type UserAccountStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
export type SuspensionType = 'TEMPORARY' | 'PERMANENT';

export interface AdminUserAccount {
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  role: ManagedUserRole;
  status: UserAccountStatus;
  suspensionType: SuspensionType | null;
  suspensionReason: string | null;
  suspendedAt: string | null;
  suspendedUntil: string | null;
  suspendedByUserId: string | null;
  suspendedByName: string | null;
  reactivatedAt: string | null;
  reactivatedByUserId: string | null;
  reactivatedByName: string | null;
  reactivationReason: string | null;
  suspensionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface UserAccountFilters {
  role?: ManagedUserRole;
  status?: UserAccountStatus;
  keyword?: string;
  page: number;
}

export interface SuspendAccountPayload {
  type: SuspensionType;
  reason: string;
  /** ISO instant; required for TEMPORARY, must be null for PERMANENT. */
  suspendedUntil: string | null;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const basePath = '/api/v1/admin/users';

export const getAdminUserAccounts = (filters: UserAccountFilters, signal?: AbortSignal) =>
  get<PageResponse<AdminUserAccount>>(`${basePath}${query({ ...filters, size: 20 })}`, { signal });
export const getAdminUserAccount = (userId: string, signal?: AbortSignal) =>
  get<AdminUserAccount>(`${basePath}/${userId}`, { signal });
export const suspendUserAccount = (userId: string, payload: SuspendAccountPayload) =>
  patch<AdminUserAccount>(`${basePath}/${userId}/suspend`, payload);
export const reactivateUserAccount = (userId: string, reason: string) =>
  patch<AdminUserAccount>(`${basePath}/${userId}/reactivate`, { reason });
