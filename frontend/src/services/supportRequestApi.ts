// Yêu cầu hỗ trợ gửi nhân viên (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5).
// Người dùng: /api/v1/users/me/support-requests. Nhân viên CUSTOMER_SERVICE / ADMIN: /api/v1/admin/support-requests.
import { get, patch, post } from './httpClient';
import type { PageResponse } from './studentApi';
import type {
  AdminSupportRequestDetail,
  AdminSupportRequestSummary,
  SupportRequest,
  SupportRequestCreate,
  SupportRequestStats,
  SupportRequestStatus
} from '../types/supportRequest';

const MINE = '/api/v1/users/me/support-requests';
const ADMIN = '/api/v1/admin/support-requests';

function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export const createSupportRequest = (body: SupportRequestCreate) => post<SupportRequest>(MINE, body);

export const getMySupportRequests = (page: number, status: SupportRequestStatus | '', signal?: AbortSignal) =>
  get<PageResponse<SupportRequest>>(`${MINE}${query({ page, size: 20, status })}`, { signal });

export const getMySupportRequest = (id: string, signal?: AbortSignal) =>
  get<SupportRequest>(`${MINE}/${id}`, { signal });

export const cancelSupportRequest = (id: string) => post<SupportRequest>(`${MINE}/${id}/cancel`);

export const closeSupportRequest = (id: string) => post<SupportRequest>(`${MINE}/${id}/close`);

export interface SupportQueueFilters {
  status?: SupportRequestStatus | '';
  assignedToMe?: boolean;
  role?: 'STUDENT' | 'TUTOR' | '';
  page: number;
}

export const getSupportQueue = (filters: SupportQueueFilters, signal?: AbortSignal) =>
  get<PageResponse<AdminSupportRequestSummary>>(`${ADMIN}${query({ ...filters, size: 20 })}`, { signal });

export const getSupportRequestForStaff = (id: string, signal?: AbortSignal) =>
  get<AdminSupportRequestDetail>(`${ADMIN}/${id}`, { signal });

export const assignSupportRequest = (id: string) => patch<AdminSupportRequestDetail>(`${ADMIN}/${id}/assign`);

export const releaseSupportRequest = (id: string) => patch<AdminSupportRequestDetail>(`${ADMIN}/${id}/release`);

export const answerSupportRequest = (id: string, answer: string) =>
  patch<AdminSupportRequestDetail>(`${ADMIN}/${id}/answer`, { answer });

export const getSupportRequestStats = (signal?: AbortSignal) => get<SupportRequestStats>(`${ADMIN}/stats`, { signal });
