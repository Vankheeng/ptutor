// Nhãn hiển thị cho trang quản trị kho tri thức (docs/support-chatbot/03 mục 3.3–3.4, 3.7).
import type { StatusMeta } from '../adminFormat';
import type { KbAudience, KbDocumentStatus, KbType, KbVersionState } from '../../../services/adminAssistantApi';

export const kbTypes: KbType[] = ['policy', 'guide', 'faq', 'glossary', 'error'];
export const kbTypeLabels: Record<KbType, string> = {
  policy: 'Quy định',
  guide: 'Hướng dẫn',
  faq: 'Hỏi đáp',
  glossary: 'Giải nghĩa trạng thái',
  error: 'Lỗi thường gặp'
};

export const kbAudiences: KbAudience[] = ['PUBLIC', 'STUDENT', 'TUTOR'];
export const kbAudienceLabels: Record<KbAudience, string> = {
  PUBLIC: 'Mọi người',
  STUDENT: 'Học viên',
  TUTOR: 'Gia sư'
};

export const kbFeatures = [
  'account',
  'studying-request',
  'teaching-request',
  'matching',
  'contract',
  'payment',
  'wallet',
  'withdrawal',
  'tutor-profile',
  'certificate',
  'lesson',
  'refund',
  'complaint',
  'review',
  'notification'
];

export const documentStatusMeta: Record<KbDocumentStatus, StatusMeta> = {
  DRAFT: { label: 'Nháp', tone: 'pending' },
  PUBLISHED: { label: 'Đang công bố', tone: 'success' },
  ARCHIVED: { label: 'Đã lưu trữ', tone: 'neutral' }
};

export const versionStateMeta: Record<KbVersionState, StatusMeta> = {
  DRAFT: { label: 'Nháp', tone: 'pending' },
  PUBLISHED: { label: 'Đang dùng', tone: 'success' },
  SUPERSEDED: { label: 'Bản cũ', tone: 'neutral' },
  ARCHIVED: { label: 'Đã lưu trữ', tone: 'neutral' }
};

export const fallbackLabels: Record<string, string> = {
  NO_CONTEXT: 'Không có tài liệu liên quan',
  VERIFY_FAILED: 'Không qua kiểm chứng',
  VERIFY_PARTIAL: 'Một phần không có căn cứ',
  SCHEMA_INVALID: 'Trả lời sai định dạng',
  BUDGET: 'Hết ngân sách lượt',
  TIMEOUT: 'Quá giờ',
  MODEL_ERROR: 'Lỗi model',
  NO_CLAIMS: 'Không có khẳng định',
  OUT_OF_SCOPE_RULE: 'Ngoài phạm vi (luật)',
  OUT_OF_SCOPE_EMBEDDING: 'Ngoài phạm vi (câu mẫu)',
  OUT_OF_SCOPE_MODEL: 'Ngoài phạm vi (model)'
};
