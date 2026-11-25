import { get, post, put } from './httpClient';
import type { PageResponse } from './studentApi';

export type ComplaintStatus = 'PENDING' | 'IN_REVIEW' | 'AWAITING_EVIDENCE' | 'RESOLVED' | 'REJECTED' | 'CANCELLED';
export type ComplaintCategory =
  'ABSENCE' | 'LATE_OR_SHORT' | 'TEACHING_QUALITY' | 'MISCONDUCT' | 'SERIOUS_MISCONDUCT' | 'OTHER';
export type ComplaintFaultParty = 'RESPONDENT' | 'COMPLAINANT' | 'BOTH' | 'NONE';

export interface ComplaintEvidence {
  id?: string;
  fileUrl: string;
  fileType: string;
  uploadedByUserId?: string | null;
}

export interface StudentComplaint {
  id: string;
  userId: string;
  contractId: string;
  title: string;
  content: string;
  category: ComplaintCategory;
  status: ComplaintStatus;
  resolution: string | null;
  faultParty: ComplaintFaultParty | null;
  resolvedAt: string | null;
  respondentResponse: string | null;
  respondentRespondedAt: string | null;
  evidences: ComplaintEvidence[];
  createdAt: string;
  updatedAt: string;
}

export interface ComplaintEvidenceInput {
  fileUrl: string;
  fileType: string;
}

export interface ComplaintEvidenceUploadResponse {
  fileUrl: string;
  previewUrl: string;
  fileType: string;
}

export interface ComplaintCreateInput {
  contractId: string;
  category: ComplaintCategory;
  title: string;
  content: string;
  evidences?: ComplaintEvidenceInput[];
}

export type ComplaintUpdateInput = Omit<ComplaintCreateInput, 'contractId'>;

export interface ComplaintRespondentResponseInput {
  response: string;
  evidences?: ComplaintEvidenceInput[];
}

const path = '/api/v1/users/me/complaints';

function query(status: ComplaintStatus | undefined, page: number, size: number) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return `?${params.toString()}`;
}

export const getMyComplaints = (status?: ComplaintStatus, page = 0, size = 20, signal?: AbortSignal) =>
  get<PageResponse<StudentComplaint>>(`${path}${query(status, page, size)}`, { signal });

export const getMyComplaint = (id: string, signal?: AbortSignal) => get<StudentComplaint>(`${path}/${id}`, { signal });

export const createComplaint = (request: ComplaintCreateInput) => post<StudentComplaint>(path, request);

export const uploadComplaintEvidence = (file: File) => {
  const body = new FormData();
  body.append('file', file);
  return post<ComplaintEvidenceUploadResponse>(`${path}/evidence-upload`, body);
};

export const updateComplaint = (id: string, request: ComplaintUpdateInput) =>
  put<StudentComplaint>(`${path}/${id}`, request);

export const cancelComplaint = (id: string) => post<StudentComplaint>(`${path}/${id}/cancel`);

export const submitComplaintEvidence = (id: string, evidences: ComplaintEvidenceInput[]) =>
  post<StudentComplaint>(`${path}/${id}/evidences`, { evidences });

export const getComplaintsAgainstMe = (status?: ComplaintStatus, page = 0, size = 20, signal?: AbortSignal) =>
  get<PageResponse<StudentComplaint>>(`${path}/against-me${query(status, page, size)}`, { signal });

export const getComplaintAgainstMe = (id: string, signal?: AbortSignal) =>
  get<StudentComplaint>(`${path}/against-me/${id}`, { signal });

export const respondToComplaint = (id: string, request: ComplaintRespondentResponseInput) =>
  post<StudentComplaint>(`${path}/against-me/${id}/responses`, request);
