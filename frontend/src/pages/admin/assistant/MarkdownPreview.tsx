// Xem trước tài liệu Markdown ở trang soạn. Hiển thị bằng phần tử React, KHÔNG dùng dangerouslySetInnerHTML
// (docs/support-chatbot/09 T11 — XSS qua nội dung tài liệu). Hỗ trợ: tiêu đề #, ##, ###; danh sách; **đậm**.
import type { ReactNode } from 'react';

function inline(text: string, key: string): ReactNode[] {
  return text
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((part, index) =>
      part.startsWith('**') && part.endsWith('**') ? (
        <strong key={`${key}-${index}`}>{part.slice(2, -2)}</strong>
      ) : (
        <span key={`${key}-${index}`}>{part}</span>
      )
    );
}

export function MarkdownPreview({ markdown }: { markdown: string }) {
  const blocks: ReactNode[] = [];
  let list: string[] = [];
  const flush = () => {
    if (list.length === 0) return;
    blocks.push(
      <ul key={`list-${blocks.length}`}>
        {list.map((item, index) => (
          <li key={index}>{inline(item, `li-${blocks.length}-${index}`)}</li>
        ))}
      </ul>
    );
    list = [];
  };
  markdown.split('\n').forEach((raw, index) => {
    const line = raw.trimEnd();
    const item = /^\s*(?:[-*]|\d+[.)])\s+(.*)$/.exec(line);
    if (item) {
      list.push(item[1]);
      return;
    }
    flush();
    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      const content = inline(heading[2], `h-${index}`);
      blocks.push(
        level === 1 ? (
          <h2 key={index}>{content}</h2>
        ) : level === 2 ? (
          <h3 key={index}>{content}</h3>
        ) : (
          <h4 key={index}>{content}</h4>
        )
      );
    } else if (line.trim()) {
      blocks.push(<p key={index}>{inline(line, `p-${index}`)}</p>);
    }
  });
  flush();
  return <div className="admin-prose kb-markdown-preview">{blocks}</div>;
}
