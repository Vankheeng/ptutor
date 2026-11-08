import { useId, useRef, useState } from 'react';
import type { ComplaintEvidenceInput } from '../../../services/studentComplaintApi';
import { uploadComplaintEvidence } from '../../../services/studentComplaintApi';
import { complaintErrorMessage } from './studentComplaintFormat';

const acceptedFiles = '.pdf,.jpg,.jpeg,.png,.webp,.mp4,.webm,.txt,.doc,.docx';

function fileLabel(fileType: string, index: number) {
  const extension = fileType.split('/').at(-1)?.toUpperCase() || 'FILE';
  return `Tệp minh chứng ${index + 1} · ${extension}`;
}

export function StudentComplaintEvidenceField({
  value,
  onChange,
  max = 5,
  label = 'Minh chứng',
  addLabel = 'Thêm minh chứng'
}: {
  value: ComplaintEvidenceInput[];
  onChange: (value: ComplaintEvidenceInput[]) => void;
  max?: number;
  label?: string;
  addLabel?: string;
}) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const upload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const selectedFiles = Array.from(files).slice(0, Math.max(0, max - value.length));
    if (selectedFiles.length === 0) return;

    setUploading(true);
    setError('');
    try {
      const uploaded: ComplaintEvidenceInput[] = [];
      for (const file of selectedFiles) {
        const result = await uploadComplaintEvidence(file);
        uploaded.push({ fileUrl: result.fileUrl, fileType: result.fileType });
      }
      onChange([...value, ...uploaded]);
    } catch (caught) {
      setError(complaintErrorMessage(caught));
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  return (
    <div className="rf-field student-complaint-upload-field">
      <label htmlFor={id}>{label}</label>
      {value.length > 0 && (
        <ul className="rf-files">
          {value.map((item, index) => (
            <li key={`${item.fileUrl}-${index}`}>
              <span>{fileLabel(item.fileType, index)}</span>
              <button
                type="button"
                aria-label={`Xóa ${fileLabel(item.fileType, index)}`}
                onClick={() => onChange(value.filter((_, position) => position !== index))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {value.length < max && (
        <div className="rf-inline student-complaint-file-picker">
          <input
            ref={inputRef}
            id={id}
            className="student-complaint-file-input"
            type="file"
            accept={acceptedFiles}
            multiple
            disabled={uploading}
            onChange={(event) => void upload(event.target.files)}
          />
          <button
            className="rf-button secondary"
            type="button"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
          >
            <span aria-hidden="true">+</span> {uploading ? 'Đang tải lên…' : addLabel}
          </button>
        </div>
      )}
      {error && <span className="rf-error">{error}</span>}
    </div>
  );
}
