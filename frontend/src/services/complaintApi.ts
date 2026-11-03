import { get, patch } from './httpClient';
import type { PageResponse } from './studentApi';
import type { LessonStatus } from './refundApi';

export type ComplaintStatus = 'PENDING' | 'IN_REVIEW' | 'AWAITING_EVIDENCE' | 'RESOLVED' | 'REJECTED' | 'CANCELLED';
export type ComplaintCategory =
  'ABSENCE' | 'LATE_OR_SHORT' | 'TEACHING_QUALITY' | 'MISCONDUCT' | 'SERIOUS_MISCONDUCT' | 'OTHER';
export type ComplaintFaultParty = 'RESPONDENT' | 'COMPLAINANT' | 'BOTH' | 'NONE';
export type ComplaintPartyRole = 'STUDENT' | 'TUTOR';

export interface AdminComplaintSummary {
  id: string;
  complainantUserId: string;
  complainantName: string;
  complainantEmail: string;
  complainantRole: ComplaintPartyRole;
  contractId: string;
  title: string;
  category: ComplaintCategory;
  status: ComplaintStatus;
  faultParty: ComplaintFaultParty | null;
  assignedEmployeeId: string | null;
  assignedEmployeeName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminComplaintDetail {
  id: string;
  title: string;
  content: string;
  category: ComplaintCategory;
  status: ComplaintStatus;
  /** The evidence request while AWAITING_EVIDENCE, the verdict once RESOLVED or REJECTED. */
  resolution: string | null;
  faultParty: ComplaintFaultParty | null;
  resolvedAt: string | null;
  complainant: { userId: string; name: string; email: string; role: ComplaintPartyRole };
  respondent: {
    userId: string;
    name: string;
    email: string;
    role: ComplaintPartyRole;
    response: string | null;
    respondedAt: string | null;
  };
  reviewer: { employeeId: string; userId: string; name: string } | null;
  contract: {
    id: string;
    studentUserId: string;
    studentName: string;
    tutorUserId: string;
    tutorName: string;
    subjectName: string | null;
    gradeName: string | null;
    teachingMode: 'ONLINE' | 'OFFLINE';
    price: number;
    paymentPeriod: 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE';
    totalLessons: number;
    startDate: string;
    endDate: string;
    status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  };
  evidences: { id: string; fileUrl: string; fileType: string; uploadedByUserId: string | null; createdAt: string }[];
  lessons: {
    id: string;
    title: string | null;
    date: string;
    startTime: string;
    endTime: string;
    teachingMode: 'ONLINE' | 'OFFLINE';
    status: LessonStatus;
    note: string | null;
  }[];
  installments: {
    id: string;
    lessonId: string | null;
    sequenceNumber: number;
    paymentPeriod: string;
    amount: number;
    dueDate: string;
    status: 'PENDING' | 'PAID' | 'CANCELLED';
  }[];
  payments: {
    id: string;
    paymentInstallmentId: string | null;
    paymentType: string;
    paymentMethod: 'VNPAY' | 'WALLET';
    status: 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED';
    amount: number;
    transactionCode: string | null;
    providerTransactionNo: string | null;
    paidAt: string | null;
    createdAt: string;
  }[];
  createdAt: string;
  updatedAt: string;
}

export interface ComplaintFilters {
  status?: ComplaintStatus;
  category?: ComplaintCategory;
  faultParty?: ComplaintFaultParty;
  contractId?: string;
  keyword?: string;
  page: number;
}

export interface ComplaintHandler {
  employeeId: string;
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const adminPath = '/api/v1/admin/complaints';

export const getAdminComplaints = (filters: ComplaintFilters, signal?: AbortSignal) =>
  get<PageResponse<AdminComplaintSummary>>(`${adminPath}${query({ ...filters, size: 20 })}`, { signal });
export const getAdminComplaint = (id: string, signal?: AbortSignal) =>
  get<AdminComplaintDetail>(`${adminPath}/${id}`, { signal });
export const startComplaintReview = (id: string) => patch<AdminComplaintDetail>(`${adminPath}/${id}/start-review`);
export const requestComplaintEvidence = (id: string, message: string) =>
  patch<AdminComplaintDetail>(`${adminPath}/${id}/request-evidence`, { message });
export const reassignComplaint = (id: string, employeeId: string) =>
  patch<AdminComplaintDetail>(`${adminPath}/${id}/reassign`, { employeeId });
export const acceptComplaint = (id: string, resolution: string, faultParty: ComplaintFaultParty) =>
  patch<AdminComplaintDetail>(`${adminPath}/${id}/accept`, { resolution, faultParty });
export const rejectComplaint = (id: string, resolution: string) =>
  patch<AdminComplaintDetail>(`${adminPath}/${id}/reject`, { resolution });

/** Active complaint handlers an admin can hand a complaint to. The employee API is admin-only. */
export const getComplaintHandlers = (keyword: string, signal?: AbortSignal) =>
  get<PageResponse<ComplaintHandler>>(
    `/api/v1/admin/employees${query({ status: 'ACTIVE', jobFunction: 'COMPLAINT_HANDLER', keyword, size: 50 })}`,
    { signal }
  );
