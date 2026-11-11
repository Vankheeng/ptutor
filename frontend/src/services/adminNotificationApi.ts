import { del, get, post, put } from './httpClient';
import type { PageResponse } from './studentApi';

export type NotificationCategory = 'MAINTENANCE' | 'PROMOTION' | 'SERVICE_UPDATE' | 'GENERAL';
export type NotificationAudience = 'ALL' | 'STUDENT' | 'TUTOR';
export type NotificationCampaignStatus =
  'DRAFT' | 'SCHEDULED' | 'PROCESSING' | 'SENT' | 'FAILED' | 'CANCELLED' | 'RETRACTED';

export interface NotificationCampaign {
  id: string;
  title: string;
  content: string;
  category: NotificationCategory;
  audience: NotificationAudience;
  status: NotificationCampaignStatus;
  scheduledAt: string | null;
  processingStartedAt: string | null;
  sentAt: string | null;
  recipientCount: number;
  readCount: number;
  unreadCount: number;
  failureReason: string | null;
  createdByUserId: string | null;
  updatedByUserId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationCampaignStatistics {
  notificationId: string;
  recipientCount: number;
  readCount: number;
  unreadCount: number;
}

export interface NotificationCampaignFilters {
  status?: NotificationCampaignStatus;
  audience?: NotificationAudience;
  category?: NotificationCategory;
  keyword?: string;
  page: number;
}

export interface NotificationCampaignPayload {
  title: string;
  content: string;
  category: NotificationCategory;
  audience: NotificationAudience;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const basePath = '/api/v1/admin/notifications';

export const getNotificationCampaigns = (filters: NotificationCampaignFilters, signal?: AbortSignal) =>
  get<PageResponse<NotificationCampaign>>(`${basePath}${query({ ...filters, size: 20 })}`, { signal });
export const getNotificationCampaign = (id: string, signal?: AbortSignal) =>
  get<NotificationCampaign>(`${basePath}/${id}`, { signal });
export const getNotificationCampaignStatistics = (id: string, signal?: AbortSignal) =>
  get<NotificationCampaignStatistics>(`${basePath}/${id}/statistics`, { signal });
export const createNotificationCampaign = (payload: NotificationCampaignPayload) =>
  post<NotificationCampaign>(basePath, payload);
export const updateNotificationCampaign = (id: string, payload: NotificationCampaignPayload) =>
  put<NotificationCampaign>(`${basePath}/${id}`, payload);
/** Cancels an unsent notification, or retracts a sent one from every recipient's inbox. */
export const deleteNotificationCampaign = (id: string) => del<NotificationCampaign>(`${basePath}/${id}`);
export const sendNotificationCampaign = (id: string) => post<NotificationCampaign>(`${basePath}/${id}/send`);
export const retryNotificationCampaign = (id: string) => post<NotificationCampaign>(`${basePath}/${id}/retry`);
/** `scheduledAt` is an ISO instant at least 5 minutes in the future. */
export const scheduleNotificationCampaign = (id: string, scheduledAt: string) =>
  post<NotificationCampaign>(`${basePath}/${id}/schedule`, { scheduledAt });
export const cancelNotificationSchedule = (id: string) =>
  post<NotificationCampaign>(`${basePath}/${id}/cancel-schedule`);
