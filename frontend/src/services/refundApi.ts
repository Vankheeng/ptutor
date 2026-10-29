import { get, patch, post } from './httpClient';
import type { PageResponse } from './studentApi';

export type RefundRequestStatus = 'PENDING' | 'IN_REVIEW' | 'REFUNDED' | 'REJECTED' | 'CANCELLED';
/** What happened in a disputed lesson: claimed by the student, answered by the tutor, decided by the reviewer. */
export type LessonOutcome = 'TAUGHT' | 'TUTOR_ABSENT' | 'STUDENT_ABSENT';
export type AbsenceOutcome = Exclude<LessonOutcome, 'TAUGHT'>;
export type RefundBlocker = 'TUTOR_RESPONSE_WINDOW_OPEN' | 'CONTRACT_HAS_OPEN_COMPLAINT' | 'NOTHING_TO_REFUND';
export type LessonStatus = 'SCHEDULED' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

export interface EvidenceInput {
  fileUrl: string;
  fileType: string;
}

export interface RefundEvidence extends EvidenceInput {
  id: string;
  uploadedByUserId: string | null;
}

export interface DisputedLesson {
  lessonId: string;
  title: string | null;
  date: string;
  startTime: string;
  endTime: string;
  markedTaughtAt: string | null;
  claimedOutcome: AbsenceOutcome;
  tutorPosition: LessonOutcome | null;
  decision: LessonOutcome | null;
}

export interface RefundRequest {
  id: string;
  contractId: string;
  subjectName: string | null;
  gradeName: string | null;
  studentUserId: string;
  studentName: string;
  tutorUserId: string;
  tutorName: string;
  status: RefundRequestStatus;
  description: string;
  disputedLessons: DisputedLesson[];
  evidences: RefundEvidence[];
  tutorResponse: string | null;
  tutorRespondedAt: string | null;
  tutorResponseDeadline: string;
  refundAmount: number | null;
  tutorShareAmount: number | null;
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ContractLesson {
  id: string;
  contractId: string;
  title: string | null;
  date: string;
  startTime: string;
  endTime: string;
  status: LessonStatus;
  note: string | null;
  markedTaughtAt: string | null;
}

export interface AdminRefundSummary {
  id: string;
  studentUserId: string;
  studentName: string;
  studentEmail: string;
  contractId: string;
  subjectName: string | null;
  gradeName: string | null;
  tutorName: string;
  status: RefundRequestStatus;
  disputedLessonCount: number;
  awaitingTutorResponse: boolean;
  hasOpenComplaint: boolean;
  assignedEmployeeId: string | null;
  assignedEmployeeName: string | null;
  createdAt: string;
}

export interface RefundCalculation {
  unitPrice: number;
  tutorAbsentRate: number;
  studentAbsentRate: number;
  paidAmount: number;
  previouslyRefundedAmount: number;
  refundableAmount: number;
  refundAmount: number;
  tutorShareAmount: number;
  undecidedLessons: number;
  blockers: RefundBlocker[];
}

export interface AdminRefundDetail {
  id: string;
  contractId: string;
  status: RefundRequestStatus;
  description: string;
  disputedLessons: DisputedLesson[];
  evidences: RefundEvidence[];
  tutorResponse: string | null;
  tutorRespondedAt: string | null;
  tutorResponseDeadline: string;
  refundAmount: number | null;
  tutorShareAmount: number | null;
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
  student: { userId: string; name: string; email: string };
  tutor: { userId: string; name: string; email: string };
  reviewer: { employeeId: string; userId: string; name: string } | null;
  contract: {
    id: string;
    subjectName: string | null;
    gradeName: string | null;
    teachingMode: string;
    price: number;
    paymentPeriod: 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE';
    totalLessons: number;
    startDate: string;
    endDate: string;
    status: string;
  };
  payments: {
    id: string;
    paymentInstallmentId: string | null;
    paymentMethod: string;
    status: string;
    amount: number;
    refundedAmount: number;
    paidAt: string | null;
  }[];
  complaints: { complaintId: string; title: string; status: string; faultParty: string | null }[];
  calculation: RefundCalculation;
  note: string | null;
}

export interface RefundCreateInput {
  contractId: string;
  description: string;
  lessons: { lessonId: string; claimedOutcome: AbsenceOutcome }[];
  evidences: EvidenceInput[];
}

export interface TutorResponseInput {
  message: string;
  lessonPositions: { lessonId: string; position: LessonOutcome }[];
  evidences: EvidenceInput[];
}

export interface RefundApprovalInput {
  expectedAmount: number;
  lessonDecisions: { lessonId: string; decision: LessonOutcome }[];
  note: string | null;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const studentPath = '/api/v1/students/me/refund-requests';
const tutorPath = '/api/v1/tutors/me/refund-requests';
const adminPath = '/api/v1/admin/refund-requests';

export const getStudentContractLessons = (contractId: string, signal?: AbortSignal) =>
  get<ContractLesson[]>(`/api/v1/students/me/contracts/${contractId}/lessons`, { signal });

export const confirmContractLesson = (lessonId: string) =>
  post<ContractLesson>(`/api/v1/students/me/lessons/${lessonId}/confirm`);

export const createRefundRequest = (input: RefundCreateInput) => post<RefundRequest>(studentPath, input);
export const getMyRefundRequests = (page: number, signal?: AbortSignal) =>
  get<PageResponse<RefundRequest>>(`${studentPath}${query({ page, size: 20 })}`, { signal });
export const getMyRefundRequest = (id: string, signal?: AbortSignal) =>
  get<RefundRequest>(`${studentPath}/${id}`, { signal });
export const cancelRefundRequest = (id: string) => patch<RefundRequest>(`${studentPath}/${id}/cancel`);

export const getTutorRefundRequests = (page: number, signal?: AbortSignal) =>
  get<PageResponse<RefundRequest>>(`${tutorPath}${query({ page, size: 20 })}`, { signal });
export const getTutorRefundRequest = (id: string, signal?: AbortSignal) =>
  get<RefundRequest>(`${tutorPath}/${id}`, { signal });
export const respondToRefundRequest = (id: string, input: TutorResponseInput) =>
  post<RefundRequest>(`${tutorPath}/${id}/response`, input);

export function getAdminRefundRequests(
  filters: { status: RefundRequestStatus; keyword?: string; page: number },
  signal?: AbortSignal
) {
  return get<PageResponse<AdminRefundSummary>>(`${adminPath}${query({ ...filters, size: 20 })}`, { signal });
}
export const getAdminRefundRequest = (id: string, signal?: AbortSignal) =>
  get<AdminRefundDetail>(`${adminPath}/${id}`, { signal });
export const startRefundReview = (id: string) => patch<AdminRefundDetail>(`${adminPath}/${id}/start-review`);
export const approveRefundRequest = (id: string, input: RefundApprovalInput) =>
  patch<AdminRefundDetail>(`${adminPath}/${id}/approve`, input);
export const rejectRefundRequest = (id: string, reason: string) =>
  patch<AdminRefundDetail>(`${adminPath}/${id}/reject`, { reason });
