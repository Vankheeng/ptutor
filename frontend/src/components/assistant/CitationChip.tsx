// Chip trích dẫn [n]: bấm / rê chuột → tên tài liệu và mục (docs/support-chatbot/08-giao-dien.md mục 8.1).
import { useState } from 'react';
import type { AssistantCitation } from '../../types/assistant';

export function CitationChip({ citation }: { citation: AssistantCitation }) {
  const [open, setOpen] = useState(false);
  const label = `${citation.documentTitle} › ${citation.section}`;
  return (
    <span className="assistant-citation">
      <button
        type="button"
        className="assistant-citation-chip"
        aria-label={`Nguồn ${citation.index}: ${label}`}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        onBlur={() => setOpen(false)}
      >
        {citation.index}
      </button>
      {open && (
        <span role="tooltip" className="assistant-citation-tooltip">
          {label}
        </span>
      )}
    </span>
  );
}
