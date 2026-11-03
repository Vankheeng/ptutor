// Nút nổi góc dưới phải + khung chat, gắn trong StudentLayout và TutorLayout (docs/support-chatbot/08 mục 8.1).
// Chỉ hiện cho học viên và gia sư đã đăng nhập; tắt bằng VITE_ASSISTANT_ENABLED=false.
import { useEffect, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { getSuggestions, isAssistantEnabled } from '../../services/assistantApi';
import { AssistantPanel } from './AssistantPanel';
import type { SupportSource } from './supportSource';
import { useAssistant } from './useAssistant';
import './assistant.css';

export interface SupportDraft {
  question: string;
  source: SupportSource;
  messageId?: string;
  conversationId: string | null;
}

interface Props {
  /** Mở hộp thoại gửi câu hỏi cho nhân viên (M17). */
  renderSupportDialog?: (draft: SupportDraft, close: () => void) => ReactNode;
}

function AssistantWidgetForUser({ userId, role, renderSupportDialog }: Props & { userId: string; role: string }) {
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [supportDraft, setSupportDraft] = useState<SupportDraft | null>(null);
  const assistant = useAssistant(userId, location.pathname);

  useEffect(() => {
    if (!open) return;
    let active = true;
    getSuggestions(location.pathname)
      .then((result) => active && setSuggestions(result.suggestions))
      .catch(() => active && setSuggestions([]));
    return () => {
      active = false;
    };
  }, [open, location.pathname]);

  return (
    <div className="assistant-widget">
      {open && (
        <AssistantPanel
          roleLabel={role === 'TUTOR' ? 'gia sư' : 'học viên'}
          items={assistant.items}
          pending={assistant.pending}
          suggestions={suggestions}
          suggestNew={assistant.suggestNew}
          onSend={(text) => void assistant.send(text)}
          onNewConversation={assistant.startNewConversation}
          onRated={assistant.setRating}
          onAskStaff={(question, source, messageId) =>
            setSupportDraft({ question, source, messageId, conversationId: assistant.conversationId })
          }
          onClose={() => setOpen(false)}
        />
      )}
      {supportDraft && renderSupportDialog?.(supportDraft, () => setSupportDraft(null))}
      <button
        type="button"
        className="assistant-fab"
        aria-label={open ? 'Đóng trợ lý' : 'Mở trợ lý PTutor'}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {open ? '×' : '?'}
      </button>
    </div>
  );
}

export function AssistantWidget(props: Props) {
  const { user } = useAuth();
  if (!isAssistantEnabled() || !user || (user.role !== 'STUDENT' && user.role !== 'TUTOR')) return null;
  // key = userId: đổi tài khoản trong cùng tab → dựng lại widget với phiên của tài khoản mới.
  return <AssistantWidgetForUser key={user.userId} userId={user.userId} role={user.role} {...props} />;
}
