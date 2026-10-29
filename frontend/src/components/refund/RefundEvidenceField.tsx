import { useId, useState } from 'react';
import type { EvidenceInput } from '../../services/refundApi';
import { evidenceName, guessFileType } from './refundFormat';

interface Props {
  value: EvidenceInput[];
  onChange: (value: EvidenceInput[]) => void;
  max?: number;
}

export function RefundEvidenceField({ value, onChange, max = 5 }: Props) {
  const id = useId();
  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);

  const add = () => {
    const trimmed = url.trim();
    if (!/^https?:\/\/\S+$/i.test(trimmed)) {
      setError('Nhập đường dẫn bắt đầu bằng http:// hoặc https://');
      return;
    }
    onChange([...value, { fileUrl: trimmed, fileType: guessFileType(trimmed) }]);
    setUrl('');
    setError(null);
  };

  return (
    <div className="rf-field">
      <label htmlFor={id}>Minh chứng</label>
      {value.length > 0 && (
        <ul className="rf-files">
          {value.map((item, index) => (
            <li key={`${item.fileUrl}-${index}`}>
              <a href={item.fileUrl} target="_blank" rel="noreferrer">
                {evidenceName(item)}
              </a>
              <button
                type="button"
                aria-label={`Xoá ${evidenceName(item)}`}
                onClick={() => onChange(value.filter((_, position) => position !== index))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {value.length < max && (
        <div className="rf-inline">
          <input
            id={id}
            type="url"
            value={url}
            placeholder="Đường dẫn ảnh, video hoặc tài liệu"
            aria-invalid={Boolean(error)}
            onChange={(event) => setUrl(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault();
                add();
              }
            }}
          />
          <button className="rf-button secondary" type="button" onClick={add}>
            Thêm
          </button>
        </div>
      )}
      {error && <span className="rf-error">{error}</span>}
    </div>
  );
}

export function RefundEvidenceList({ items }: { items: { id: string; fileUrl: string; fileType: string }[] }) {
  if (items.length === 0) return null;
  return (
    <ul className="rf-files">
      {items.map((item) => (
        <li key={item.id}>
          <a href={item.fileUrl} target="_blank" rel="noreferrer">
            {evidenceName(item)}
          </a>
        </li>
      ))}
    </ul>
  );
}
