// Khung chat: header, danh sách tin, ô nhập (docs/support-chatbot/08-giao-dien.md mục 8.1).
// Cách hiển thị theo bảng "Hành vi": từ chối / trả lời một phần có nút gửi nhân viên; câu ngoài phạm vi thì không.
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ChatItem } from '../../types/assistant';
import { ActionButtons } from './ActionButtons';
import { FeedbackButtons } from './FeedbackButtons';
import { MessageBubble } from './MessageBubble';
import { SuggestionChips } from './SuggestionChips';
import { supportSourceFor, type SupportSource } from './supportSource';

const MAX_CHARS = 1000;

interface Props {
  roleLabel: string;
  items: ChatItem[];
  pending: boolean;
  suggestions: string[];
  suggestNew: boolean;
  onSend: (text: string) => void;
  onNewConversation: () => void;
  onRated: (messageId: string, rating: 1 | -1) => void;
  onAskStaff: (question: string, source: SupportSource, messageId?: string) => void;
  onClose: () => void;
}

export function AssistantPanel(props: Props) {
  const { items, pending, suggestions, suggestNew, onSend } = props;
  const [draft, setDraft] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [items, pending]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!draft.trim() || pending) return;
    onSend(draft);
    setDraft('');
  };

  const questionBefore = (index: number) =>
    [...items.slice(0, index)].reverse().find((item) => item.sender === 'user')?.text ?? '';

  return (
    <section className="assistant-panel" role="dialog" aria-label="Trợ lý PTutor">
      <header className="assistant-header">
        <strong>Trợ lý PTutor</strong>
        <div className="assistant-header-tools">
          <button type="button" onClick={props.onNewConversation} title="Cuộc trò chuyện mới">
            Cuộc trò chuyện mới
          </button>
          <button type="button" aria-label="Thêm" aria-expanded={menuOpen} onClick={() => setMenuOpen((v) => !v)}>
            ⋯
          </button>
          <button type="button" aria-label="Đóng" onClick={props.onClose}>
            ×
          </button>
          {menuOpen && (
            <div className="assistant-menu" role="menu">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setMenuOpen(false);
                  props.onAskStaff(questionBefore(items.length), 'MENU');
                }}
              >
                Hỏi nhân viên hỗ trợ
              </button>
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setMenuOpen(false);
                  navigate(`/${props.roleLabel === 'gia sư' ? 'tutor' : 'student'}/support-requests`);
                }}
              >
                Yêu cầu hỗ trợ của tôi
              </button>
            </div>
          )}
        </div>
      </header>

      <div className="assistant-messages" ref={listRef}>
        {items.length === 0 && (
          <div className="assistant-welcome">
            <p>Chào bạn! Mình là trợ lý của PTutor dành cho {props.roleLabel}.</p>
            <SuggestionChips suggestions={suggestions} onPick={onSend} />
          </div>
        )}
        {items.map((item, index) => {
          const supportSource = supportSourceFor(item);
          return (
            <div
              key={item.key}
              className={[
                'assistant-message',
                item.sender,
                item.failed ? 'failed' : '',
                item.grounded === 'NONE' && item.intent !== 'CHITCHAT' && item.sender === 'assistant' ? 'abstain' : ''
              ].join(' ')}
            >
              {item.notice && <small className="assistant-notice">{item.notice}</small>}
              <MessageBubble text={item.text} citations={item.citations} />
              {item.grounded === 'PARTIAL' && (
                <small className="assistant-notice">Một phần câu hỏi chưa có thông tin chính thức</small>
              )}
              {item.actions && <ActionButtons actions={item.actions} />}
              {item.intent === 'OUT_OF_SCOPE' && <SuggestionChips suggestions={suggestions} onPick={onSend} />}
              {supportSource && (
                <button
                  type="button"
                  className="assistant-support-button"
                  onClick={() => props.onAskStaff(questionBefore(index), supportSource, item.messageId)}
                >
                  Gửi câu hỏi cho nhân viên
                </button>
              )}
              {item.sender === 'assistant' && item.messageId && !item.failed && (
                <FeedbackButtons
                  messageId={item.messageId}
                  rating={item.rating}
                  onRated={(rating) => props.onRated(item.messageId!, rating)}
                  onNegative={() => props.onAskStaff(questionBefore(index), 'ASSISTANT_FEEDBACK', item.messageId)}
                />
              )}
            </div>
          );
        })}
        {pending && (
          <div className="assistant-message assistant pending" aria-live="polite">
            đang soạn…
          </div>
        )}
        {suggestNew && (
          <button type="button" className="assistant-new-hint" onClick={props.onNewConversation}>
            Bắt đầu cuộc trò chuyện mới
          </button>
        )}
      </div>

      <form className="assistant-input" onSubmit={submit}>
        <textarea
          value={draft}
          maxLength={MAX_CHARS}
          rows={2}
          placeholder="Nhập câu hỏi về PTutor…"
          aria-label="Câu hỏi"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) submit(event);
          }}
        />
        <button type="submit" disabled={pending || !draft.trim()}>
          Gửi
        </button>
      </form>
      <small className="assistant-disclaimer">Trợ lý có thể sai. Thông tin chính thức xem tại trang tương ứng.</small>
    </section>
  );
}
