// API quản trị kho tri thức của trợ lý (docs/support-chatbot/07-api.md mục 7.3). Chỉ ADMIN.
import { del, get, post, put } from './httpClient';

const BASE = '/api/v1/admin/assistant';

export type KbType = 'policy' | 'guide' | 'faq' | 'glossary' | 'error';
export type KbAudience = 'PUBLIC' | 'STUDENT' | 'TUTOR';
export type KbDocumentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type KbVersionState = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED';

export interface KbDocumentRow {
  id: string;
  docKey: string;
  status: KbDocumentStatus;
  title: string;
  type: KbType;
  audience: KbAudience[];
  features: string[];
  hasDraft: boolean;
  openTodo: number;
  publishedVersion: number | null;
  publishedWithOpenTodo: boolean;
  editedAt: string;
}

export interface KbDocumentPage {
  content: KbDocumentRow[];
  totalElements: number;
  page: number;
  size: number;
}

export interface KbVersion {
  id: string;
  version: number;
  state: KbVersionState;
  title: string;
  type: KbType;
  audience: KbAudience[];
  features: string[];
  routes: string[];
  keywords: string[];
  errorCodes: string[];
  sourceRefs: unknown[];
  todo: string[];
  origin: 'IMPORT' | 'ADMIN';
  editedAt: string;
  publishedAt: string | null;
  restoredFromVersion: number | null;
  publishedWithOpenTodo: boolean;
  contentMd?: string;
}

export interface KbDocumentDetail {
  id: string;
  docKey: string;
  status: KbDocumentStatus;
  published: KbVersion | null;
  draft: KbVersion | null;
  versions: KbVersion[];
}

export interface KbDocumentPayload {
  docKey?: string;
  title: string;
  type: KbType | '';
  audience: KbAudience[];
  features: string[];
  routes: string[];
  keywords: string[];
  errorCodes: string[];
  sourceRefs: unknown[];
  todo: string[];
  contentMd: string;
}

export interface ChunkPreview {
  ordinal: number;
  headingPath: string;
  tokenCount: number;
}

export interface ValidationResult {
  ok: boolean;
  errors: string[];
  warnings: string[];
  chunks: ChunkPreview[];
  tokenCounter?: string;
}

export interface UnansweredItem {
  question: string;
  rewrittenQuery: string | null;
  grounded: string;
  fallbackReason: string | null;
  intent: string | null;
  feedbackReason: string | null;
  rating: number | null;
  createdAt: string;
  messageId: string;
}

export interface AssistantStats {
  turns: number;
  grounded: Record<string, number>;
  fallbackReasons: Record<string, number>;
  intents: Record<string, number>;
  feedback: { up: number; down: number };
  latencyMs: { p50: number; p95: number };
  tokensPerTurn: number;
}

export interface DocumentFilters {
  status?: string;
  type?: string;
  feature?: string;
  audience?: string;
  q?: string;
  page?: number;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export const listDocuments = (filters: DocumentFilters, signal?: AbortSignal) =>
  get<KbDocumentPage>(`${BASE}/documents${query({ ...filters, size: 200 })}`, { signal });
export const getDocument = (id: string, signal?: AbortSignal) =>
  get<KbDocumentDetail>(`${BASE}/documents/${id}`, { signal });
export const createDocument = (payload: KbDocumentPayload) => post<KbDocumentDetail>(`${BASE}/documents`, payload);
export const saveDraft = (id: string, payload: KbDocumentPayload) =>
  put<KbDocumentDetail>(`${BASE}/documents/${id}/draft`, payload);
export const discardDraft = (id: string) => del<KbDocumentDetail>(`${BASE}/documents/${id}/draft`);
export const validateDraft = (id: string) => post<ValidationResult>(`${BASE}/documents/${id}/draft/validate`);
export const previewDocument = (payload: KbDocumentPayload) =>
  post<ValidationResult>(`${BASE}/documents/preview`, payload);
export const publishDocument = (id: string) => post<KbDocumentDetail>(`${BASE}/documents/${id}/publish`);
export const archiveDocument = (id: string) => post<KbDocumentDetail>(`${BASE}/documents/${id}/archive`);
export const getVersion = (id: string, version: number) =>
  get<KbVersion>(`${BASE}/documents/${id}/versions/${version}`);
export const restoreVersion = (id: string, version: number) =>
  post<KbDocumentDetail>(`${BASE}/documents/${id}/versions/${version}/restore`);
export const getUnanswered = (days: number, signal?: AbortSignal) =>
  get<{ items: UnansweredItem[] }>(`${BASE}/unanswered?days=${days}`, { signal });
export const getAssistantStats = (signal?: AbortSignal) => get<AssistantStats>(`${BASE}/stats`, { signal });
export const addScopeExemplar = (text: string, label: 'IN' | 'OUT') => post(`${BASE}/scope-exemplars`, { text, label });
