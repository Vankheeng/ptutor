// Trạng thái khung chat (docs/support-chatbot/08-giao-dien.md mục 8.1 "Hành vi").
// - Giữ conversationId theo tab (sessionStorage); tải lại trang → nạp lại tin nhắn của phiên.
// - 409 CONVERSATION_ENDED → tự mở phiên mới và gửi lại câu hỏi một lần.
// - Quá 35 giây → báo lỗi, cho gửi lại. 429 / 503 → câu báo ngắn.
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../services/httpClient';
import {
  CLIENT_TIMEOUT_MS,
  endConversation,
  getConversationMessages,
  loadConversationId,
  saveConversationId,
  sendAssistantMessage
} from '../../services/assistantApi';
import type { AssistantReply, AssistantStoredMessage, ChatItem } from '../../types/assistant';

const NEW_SESSION_NOTICE = 'Đã bắt đầu cuộc trò chuyện mới';

let keySeed = 0;
const nextKey = () => `m${Date.now()}-${keySeed++}`;

function fromReply(reply: AssistantReply, notice?: string): ChatItem {
  return {
    key: nextKey(),
    sender: 'assistant',
    text: reply.answer,
    messageId: reply.messageId,
    citations: reply.citations,
    actions: reply.actions,
    grounded: reply.grounded,
    intent: reply.intent,
    rating: null,
    notice
  };
}

function fromStored(message: AssistantStoredMessage): ChatItem {
  return {
    key: nextKey(),
    sender: message.sender === 'USER' ? 'user' : 'assistant',
    text: message.content ?? '',
    messageId: message.id ?? undefined,
    citations: message.citations,
    actions: message.actions,
    grounded: message.grounded ?? undefined,
    intent: message.intent ?? undefined,
    rating: message.rating
  };
}

export function errorText(error: unknown): string {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return 'Trợ lý phản hồi quá lâu. Bạn gửi lại nhé.';
  }
  if (error instanceof ApiError) {
    if (error.status === 429) return 'Bạn gửi hơi nhanh, thử lại sau ít phút.';
    if (error.status === 503) return 'Trợ lý tạm thời không khả dụng.';
    if (error.code === 'INVALID_MESSAGE') return 'Câu hỏi cần từ 1 đến 1.000 ký tự.';
    if (error.code === 'CONVERSATION_BUSY') return 'Trợ lý đang trả lời câu trước, bạn đợi chút nhé.';
  }
  return 'Không gửi được câu hỏi. Bạn thử lại nhé.';
}

export function useAssistant(userId: string, pagePath: string) {
  const [items, setItems] = useState<ChatItem[]>([]);
  const [pending, setPending] = useState(false);
  const [suggestNew, setSuggestNew] = useState(false);
  const conversationRef = useRef<string | null>(loadConversationId(userId));

  const setConversation = useCallback(
    (conversationId: string | null) => {
      conversationRef.current = conversationId;
      saveConversationId(userId, conversationId);
    },
    [userId]
  );

  // Tải lại trang trong cùng tab: hiện lại các tin của phiên đang mở.
  useEffect(() => {
    const conversationId = conversationRef.current;
    if (!conversationId) return;
    let active = true;
    getConversationMessages(conversationId)
      .then((history) => {
        if (!active) return;
        if (history.ended) setConversation(null);
        else setItems(history.messages.map(fromStored));
      })
      .catch(() => {
        if (active) setConversation(null); // phiên đã xoá / không còn → bắt đầu phiên mới
      });
    return () => {
      active = false;
    };
  }, [setConversation]);

  const ask = useCallback(
    async (conversationId: string | null, text: string): Promise<AssistantReply> => {
      const controller = new AbortController();
      const timer = window.setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
      try {
        return await sendAssistantMessage(text, pagePath, conversationId, controller.signal);
      } finally {
        window.clearTimeout(timer);
      }
    },
    [pagePath]
  );

  const send = useCallback(
    async (rawText: string) => {
      const text = rawText.trim();
      if (!text || pending) return;
      setItems((current) => [...current, { key: nextKey(), sender: 'user', text }]);
      setPending(true);
      try {
        let notice: string | undefined;
        let reply: AssistantReply;
        try {
          reply = await ask(conversationRef.current, text);
        } catch (error) {
          if (!(error instanceof ApiError && error.code === 'CONVERSATION_ENDED')) throw error;
          setConversation(null);
          notice = NEW_SESSION_NOTICE;
          reply = await ask(null, text);
        }
        setConversation(reply.conversationId);
        setSuggestNew(reply.suggestNewConversation);
        setItems((current) => [...current, fromReply(reply, notice)]);
      } catch (error) {
        setItems((current) => [
          ...current,
          { key: nextKey(), sender: 'assistant', text: errorText(error), failed: true }
        ]);
      } finally {
        setPending(false);
      }
    },
    [ask, pending, setConversation]
  );

  const startNewConversation = useCallback(() => {
    const conversationId = conversationRef.current;
    if (conversationId) void endConversation(conversationId).catch(() => undefined);
    setConversation(null);
    setItems([]);
    setSuggestNew(false);
  }, [setConversation]);

  const setRating = useCallback((messageId: string, rating: 1 | -1) => {
    setItems((current) => current.map((item) => (item.messageId === messageId ? { ...item, rating } : item)));
  }, []);

  const lastQuestion = [...items].reverse().find((item) => item.sender === 'user')?.text ?? '';

  return {
    items,
    pending,
    suggestNew,
    send,
    startNewConversation,
    setRating,
    lastQuestion,
    conversationId: conversationRef.current
  };
}
