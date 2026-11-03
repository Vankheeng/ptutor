// Khi nào hiện nút "Gửi câu hỏi cho nhân viên" (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.2):
// trợ lý từ chối (grounded = NONE) hoặc trả lời một phần (PARTIAL), hoặc người dùng bấm 👎, hoặc mở từ menu.
// KHÔNG hiện cho câu ngoài phạm vi (OUT_OF_SCOPE) — không thuộc nền tảng nên nhân viên cũng không hỗ trợ.
import type { ChatItem } from '../../types/assistant';

export type SupportSource = 'ASSISTANT_ABSTAIN' | 'ASSISTANT_PARTIAL' | 'ASSISTANT_FEEDBACK' | 'MENU';

export function supportSourceFor(item: ChatItem): SupportSource | null {
  if (item.sender !== 'assistant' || item.failed || item.intent === 'OUT_OF_SCOPE') return null;
  if (item.grounded === 'NONE' && item.intent !== 'CHITCHAT') return 'ASSISTANT_ABSTAIN';
  if (item.grounded === 'PARTIAL') return 'ASSISTANT_PARTIAL';
  return null;
}
