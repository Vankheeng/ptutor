import { get } from './httpClient';
import type {
  AdminTutorAddress,
  AdminTutorContract,
  ContractStatus,
  LessonStatus,
  TeachingMode
} from './adminTutorApi';
import type { ComplaintStatus } from './complaintApi';
import type { PageResponse } from './studentApi';

export type TransactionSource = 'PAYMENT' | 'WALLET_TRANSACTION' | 'WITHDRAWAL';
export type ComplaintRelation = 'ALL' | 'SUBMITTED' | 'RECEIVED';

export interface AdminStudentProfile {
  userId: string;
  studentId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  dateOfBirth: string | null;
  avatarUrl: string | null;
  address: AdminTutorAddress | null;
  introduction: string | null;
  learningStyle: string | null;
  personalityTags: string | null;
  goalsDescription: string | null;
  currentLevel: string | null;
  weakPoints: string | null;
}

export interface AdminStudentLesson {
  lessonId: string;
  contractId: string;
  contractStatus: ContractStatus;
  subjectName: string;
  gradeName: string;
  tutorUserId: string;
  tutorName: string;
  tutorEmail: string;
  title: string | null;
  date: string;
  startTime: string;
  endTime: string;
  teachingMode: TeachingMode;
  status: LessonStatus;
  note: string | null;
}

export interface AdminStudentLearningHistory {
  summary: {
    total: number;
    scheduled: number;
    pendingConfirmation: number;
    confirmed: number;
    completed: number;
    cancelled: number;
  };
  lessons: PageResponse<AdminStudentLesson>;
}

export interface AdminStudentContract extends AdminTutorContract {
  tutorId: string;
  tutorFirstName: string | null;
  tutorLastName: string | null;
  tutorEmail: string;
}

export interface AdminStudentTransaction {
  id: string;
  source: TransactionSource;
  type: string;
  method: string;
  status: string;
  amount: number;
  referenceId: string | null;
  description: string | null;
  occurredAt: string;
}

export interface AdminStudentComplaint {
  complaintId: string;
  relation: Exclude<ComplaintRelation, 'ALL'>;
  complainantUserId: string;
  complainantName: string | null;
  complainantEmail: string | null;
  contractId: string | null;
  title: string;
  status: ComplaintStatus;
  assignedEmployeeId: string | null;
  assignedEmployeeName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionFilters {
  source?: TransactionSource;
  type?: string;
  method?: string;
  status?: string;
  page: number;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const studentPath = (userId: string) => `/api/v1/admin/users/${userId}`;

export function getAdminStudentProfile(userId: string, signal?: AbortSignal) {
  return get<AdminStudentProfile>(`${studentPath(userId)}/student-profile`, { signal });
}

export function getAdminStudentLearningHistory(
  userId: string,
  filters: { lessonStatus?: LessonStatus; contractStatus?: ContractStatus; page: number },
  signal?: AbortSignal
) {
  return get<AdminStudentLearningHistory>(`${studentPath(userId)}/learning-history${query(filters)}`, { signal });
}

export function getAdminStudentLesson(userId: string, lessonId: string, signal?: AbortSignal) {
  return get<AdminStudentLesson>(`${studentPath(userId)}/learning-history/${lessonId}`, { signal });
}

export function getAdminStudentContracts(
  userId: string,
  status: ContractStatus | undefined,
  page: number,
  signal?: AbortSignal
) {
  return get<PageResponse<AdminStudentContract>>(`${studentPath(userId)}/contracts${query({ status, page })}`, {
    signal
  });
}

export function getAdminStudentContract(userId: string, contractId: string, signal?: AbortSignal) {
  return get<AdminStudentContract>(`${studentPath(userId)}/contracts/${contractId}`, { signal });
}

export function getAdminStudentTransactions(userId: string, filters: TransactionFilters, signal?: AbortSignal) {
  return get<PageResponse<AdminStudentTransaction>>(`${studentPath(userId)}/transactions${query({ ...filters })}`, {
    signal
  });
}

export function getAdminStudentComplaints(
  userId: string,
  filters: { relation: ComplaintRelation; status?: ComplaintStatus; page: number },
  signal?: AbortSignal
) {
  return get<PageResponse<AdminStudentComplaint>>(`${studentPath(userId)}/complaints${query({ ...filters })}`, {
    signal
  });
}
