// Kiểu dữ liệu của trợ lý hỗ trợ — theo docs/support-chatbot/07-api.md mục 7.2.

export type AssistantIntent = 'POLICY' | 'HOWTO' | 'LOOKUP' | 'CHITCHAT' | 'OUT_OF_SCOPE';
export type AssistantGrounded = 'FULL' | 'PARTIAL' | 'NONE';

export interface AssistantCitation {
  index: number;
  documentTitle: string;
  section: string;
  docKey: string;
  anchor: string;
}

export interface AssistantAction {
  label: string;
  route: string;
}

export interface AssistantReply {
  conversationId: string;
  messageId: string;
  answer: string;
  citations: AssistantCitation[];
  actions: AssistantAction[];
  suggestions: string[];
  intent: AssistantIntent;
  grounded: AssistantGrounded;
  createdAt: string;
  suggestNewConversation: boolean;
}

export interface AssistantStoredMessage {
  id: string | null;
  sender: 'USER' | 'ASSISTANT';
  content: string | null;
  intent: AssistantIntent | null;
  grounded: AssistantGrounded | null;
  citations: AssistantCitation[];
  actions: AssistantAction[];
  rating: 1 | -1 | null;
}

export interface AssistantConversationMessages {
  conversationId: string;
  ended: boolean;
  messages: AssistantStoredMessage[];
}

export type FeedbackReason = 'WRONG' | 'NOT_HELPFUL' | 'OUTDATED' | 'OTHER';

/** Một bong bóng trong khung chat. */
export interface ChatItem {
  key: string;
  sender: 'user' | 'assistant';
  text: string;
  messageId?: string;
  citations?: AssistantCitation[];
  actions?: AssistantAction[];
  grounded?: AssistantGrounded;
  intent?: AssistantIntent;
  rating?: 1 | -1 | null;
  notice?: string;
  failed?: boolean;
}
