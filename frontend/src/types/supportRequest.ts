// Kiểu dữ liệu yêu cầu hỗ trợ — theo docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.4, 14.5.
import type { SupportSource } from '../components/assistant/supportSource';

export type SupportRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'ANSWERED' | 'CLOSED' | 'CANCELLED';

export interface SupportRequestCreate {
  question: string;
  conversationId: string | null;
  messageId: string | null;
  attachConversation: boolean;
  source: SupportSource;
  pagePath: string;
}

/** createdAt theo múi giờ backend (JVM); answeredAt, closedAt theo UTC — xem supportFormat.ts. */
export interface SupportRequest {
  id: string;
  code: string;
  question: string;
  source: SupportSource;
  status: SupportRequestStatus;
  answer: string | null;
  attachConversation: boolean;
  previousRequestId: string | null;
  previousRequestCode: string | null;
  createdAt: string;
  answeredAt: string | null;
  closedAt: string | null;
}

export interface AdminSupportRequestSummary {
  id: string;
  code: string;
  question: string;
  userRole: 'STUDENT' | 'TUTOR';
  requesterName: string;
  source: SupportSource;
  status: SupportRequestStatus;
  assignedEmployeeId: string | null;
  assignedEmployeeName: string | null;
  attachConversation: boolean;
  createdAt: string;
  answeredAt: string | null;
}

export interface ExcerptTurn {
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface AdminSupportRequestDetail {
  id: string;
  code: string;
  question: string;
  userRole: 'STUDENT' | 'TUTOR';
  requester: { userId: string; fullName: string; email: string };
  source: SupportSource;
  status: SupportRequestStatus;
  pagePath: string | null;
  attachConversation: boolean;
  conversationExcerpt: ExcerptTurn[] | null;
  answer: string | null;
  assignedEmployeeId: string | null;
  assignedEmployeeName: string | null;
  assignedToMe: boolean;
  previousRequestId: string | null;
  history: { id: string; code: string; question: string; status: SupportRequestStatus; createdAt: string }[];
  createdAt: string;
  assignedAt: string | null;
  answeredAt: string | null;
  closedAt: string | null;
}

export interface SupportRequestStats {
  byStatus: Record<SupportRequestStatus, number>;
  bySource: Partial<Record<SupportSource, number>>;
  windowDays: number;
  minutesToAssignP50: number | null;
  minutesToAssignP95: number | null;
  minutesToAnswerP50: number | null;
  minutesToAnswerP95: number | null;
  /** Tỉ lệ yêu cầu / lượt trợ lý từ chối (14.9); null khi không lấy được số lượt từ assistant-service. */
  refusalFollowUp: RefusalFollowUp | null;
}

export interface RefusalFollowUp {
  abstainTurns: number;
  partialTurns: number;
  requestsAfterAbstain: number;
  requestsAfterPartial: number;
  abstainRequestRate: number | null;
  partialRequestRate: number | null;
}
