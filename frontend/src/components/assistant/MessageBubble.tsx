// Một bong bóng tin nhắn. Markdown được hiển thị bằng phần tử React (KHÔNG dùng dangerouslySetInnerHTML):
// nội dung câu trả lời / tài liệu không bao giờ thành HTML chạy được (docs/support-chatbot/09 T11 — XSS).
// Chỉ hỗ trợ phần Markdown trợ lý dùng: **đậm**, danh sách "- " và "1. ", xuống dòng, chip trích dẫn [n].
import type { ReactNode } from 'react';
import type { AssistantCitation } from '../../types/assistant';
import { CitationChip } from './CitationChip';

interface Props {
  text: string;
  citations?: AssistantCitation[];
}

const INLINE = /(\*\*[^*]+\*\*|\[\d+\])/g;

function renderInline(line: string, citations: AssistantCitation[], keyPrefix: string): ReactNode[] {
  return line
    .split(INLINE)
    .filter((part) => part !== '')
    .map((part, index) => {
      const key = `${keyPrefix}-${index}`;
      if (part.startsWith('**') && part.endsWith('**')) return <strong key={key}>{part.slice(2, -2)}</strong>;
      const citation = /^\[(\d+)\]$/.exec(part);
      if (citation) {
        const found = citations.find((item) => item.index === Number(citation[1]));
        return found ? <CitationChip key={key} citation={found} /> : null;
      }
      return <span key={key}>{part}</span>;
    });
}

function renderSafeMarkdown(text: string, citations: AssistantCitation[] = []): ReactNode[] {
  const blocks: ReactNode[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;

  const flushList = () => {
    if (!list) return;
    const items = list.items.map((item, index) => <li key={index}>{renderInline(item, citations, `li${index}`)}</li>);
    blocks.push(list.ordered ? <ol key={`b${blocks.length}`}>{items}</ol> : <ul key={`b${blocks.length}`}>{items}</ul>);
    list = null;
  };

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trimEnd();
    const bullet = /^\s*[-*]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      const ordered = Boolean(numbered);
      if (list && list.ordered !== ordered) flushList();
      if (!list) list = { ordered, items: [] };
      list.items.push((bullet ?? numbered)![1]);
      continue;
    }
    flushList();
    if (line.trim() === '') continue;
    const content = line.replace(/^#{1,6}\s+/, '');
    blocks.push(<p key={`b${blocks.length}`}>{renderInline(content, citations, `p${blocks.length}`)}</p>);
  }
  flushList();
  return blocks;
}

export function MessageBubble({ text, citations = [] }: Props) {
  return <div className="assistant-markdown">{renderSafeMarkdown(text, citations)}</div>;
}
