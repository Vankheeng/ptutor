import { get, patch, post, put } from './httpClient';
import type { PageResponse } from './studentApi';

export type EmployeeJobFunction = 'CUSTOMER_SERVICE' | 'ACCOUNTANT';
export type EmployeeStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
export type EmployeeGender = 'MALE' | 'FEMALE' | 'OTHER';

export interface AdminEmployeeSummary {
  employeeId: string;
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  jobFunction: EmployeeJobFunction;
  status: EmployeeStatus;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeAddress {
  detailAddress: string | null;
  districtId: string | null;
  districtName: string | null;
  provinceId: string | null;
  provinceName: string | null;
}

export interface AdminEmployeeDetail extends AdminEmployeeSummary {
  dateOfBirth: string | null;
  gender: EmployeeGender | null;
  maskedCitizenId: string | null;
  address: EmployeeAddress | null;
  systemRole: 'ADMIN' | 'EMPLOYEE';
  accessRevocationReason: string | null;
  accessRevokedAt: string | null;
  accessRevokedByUserId: string | null;
  reactivationReason: string | null;
  reactivatedAt: string | null;
  reactivatedByUserId: string | null;
}

export interface EmployeeFilters {
  status?: EmployeeStatus;
  jobFunction?: EmployeeJobFunction;
  keyword?: string;
  page: number;
}

export interface EmployeePayload {
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  /** yyyy-MM-dd */
  dateOfBirth: string;
  gender: EmployeeGender;
  /** 12 digits; optional on update, where null keeps the current value. */
  citizenId: string | null;
  provinceId: string;
  districtId: string;
  detailAddress: string | null;
  jobFunction: EmployeeJobFunction;
}

export interface CreateEmployeePayload extends EmployeePayload {
  citizenId: string;
  initialPassword: string;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const basePath = '/api/v1/admin/employees';

export const getEmployees = (filters: EmployeeFilters, signal?: AbortSignal) =>
  get<PageResponse<AdminEmployeeSummary>>(`${basePath}${query({ ...filters, size: 20 })}`, { signal });
export const getEmployee = (employeeId: string, signal?: AbortSignal) =>
  get<AdminEmployeeDetail>(`${basePath}/${employeeId}`, { signal });
export const createEmployee = (payload: CreateEmployeePayload) => post<AdminEmployeeDetail>(basePath, payload);
export const updateEmployee = (employeeId: string, payload: EmployeePayload) =>
  put<AdminEmployeeDetail>(`${basePath}/${employeeId}`, payload);
/** Revokes access and signs the employee out everywhere. Fails while complaints or refunds are still assigned. */
export const deactivateEmployee = (employeeId: string, reason: string) =>
  patch<AdminEmployeeDetail>(`${basePath}/${employeeId}/deactivate`, { reason });
export const reactivateEmployee = (employeeId: string, reason: string) =>
  patch<AdminEmployeeDetail>(`${basePath}/${employeeId}/reactivate`, { reason });

export interface EmployeeOpenComplaint {
  id: string;
  title: string;
  complainantName: string | null;
  status: string;
  createdAt: string;
}

export interface EmployeeOpenRefundRequest {
  id: string;
  studentName: string | null;
  subjectName: string | null;
  createdAt: string;
}

export interface EmployeeOpenAssignments {
  complaints: EmployeeOpenComplaint[];
  refundRequests: EmployeeOpenRefundRequest[];
}

export const getEmployeeOpenAssignments = (employeeId: string, signal?: AbortSignal) =>
  get<EmployeeOpenAssignments>(`${basePath}/${employeeId}/open-assignments`, { signal });
