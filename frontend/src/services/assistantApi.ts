// Gọi API trợ lý qua backend (docs/support-chatbot/07-api.md mục 7.2). Dùng httpClient sẵn có: tự gắn token,
// tự làm mới token khi gặp 401. Frontend KHÔNG gọi thẳng assistant-service (QĐ2).
import { getStoredTokens } from '../auth/authStorage';
import { get, post, del } from './httpClient';
import type { AssistantConversationMessages, AssistantReply, FeedbackReason } from '../types/assistant';

const BASE = '/api/v1/assistant';
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
// Lớn hơn timeout 30 giây của backend (02 mục 2.7: 25 → 30 → 35 giây).
export const CLIENT_TIMEOUT_MS = 35_000;

export function isAssistantEnabled(): boolean {
  return (import.meta.env.VITE_ASSISTANT_ENABLED ?? 'true') !== 'false';
}

export function sendAssistantMessage(
  message: string,
  pagePath: string,
  conversationId: string | null,
  signal?: AbortSignal
): Promise<AssistantReply> {
  return post<AssistantReply>(`${BASE}/messages`, { conversationId, message, pagePath }, { signal });
}

export function getConversationMessages(conversationId: string): Promise<AssistantConversationMessages> {
  return get<AssistantConversationMessages>(`${BASE}/conversations/${conversationId}/messages`);
}

export function endConversation(conversationId: string): Promise<unknown> {
  return post(`${BASE}/conversations/${conversationId}/end`);
}

export function deleteConversation(conversationId: string): Promise<unknown> {
  return del(`${BASE}/conversations/${conversationId}`);
}

export function sendFeedback(
  messageId: string,
  rating: 1 | -1,
  reason?: FeedbackReason,
  comment?: string
): Promise<unknown> {
  return post(`${BASE}/messages/${messageId}/feedback`, { rating, reason: reason ?? null, comment: comment ?? null });
}

export function getSuggestions(pagePath: string): Promise<{ suggestions: string[] }> {
  return get<{ suggestions: string[] }>(`${BASE}/suggestions?pagePath=${encodeURIComponent(pagePath)}`);
}

// ---------------------------------------------------------------- mã phiên trong sessionStorage
// Phiên sống theo TAB (04 mục 4.10): chuyển trang / tải lại vẫn giữ; đóng tab thì trình duyệt tự xoá.
// Khoá theo userId để hai tài khoản đăng nhập lần lượt trong cùng tab không dùng chung phiên.

function storageKey(userId: string): string {
  return `ptutor.assistant.conversation.${userId}`;
}

export function loadConversationId(userId: string): string | null {
  try {
    return sessionStorage.getItem(storageKey(userId));
  } catch {
    return null; // trình duyệt chặn storage: vẫn chat được, chỉ không giữ phiên khi tải lại
  }
}

export function saveConversationId(userId: string, conversationId: string | null): void {
  try {
    if (conversationId) sessionStorage.setItem(storageKey(userId), conversationId);
    else sessionStorage.removeItem(storageKey(userId));
  } catch {
    // bỏ qua: xem loadConversationId
  }
}

/**
 * Gọi khi đăng xuất, TRƯỚC khi xoá token: kết thúc phiên trên server (đặt ended_at) và xoá mã phiên của tab.
 * "Bắn rồi quên": dùng fetch keepalive để request vẫn đi khi trang chuyển đi ngay sau đó.
 */
export function endAssistantConversationOnLogout(userId: string | undefined): void {
  if (!userId) return;
  const conversationId = loadConversationId(userId);
  saveConversationId(userId, null);
  const token = getStoredTokens()?.accessToken;
  if (!conversationId || !token) return;
  void fetch(`${apiBaseUrl}${BASE}/conversations/${conversationId}/end`, {
    method: 'POST',
    keepalive: true,
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' }
  }).catch(() => undefined);
}
