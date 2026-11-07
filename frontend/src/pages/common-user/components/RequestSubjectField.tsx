import type { Subject } from '../../../types/api';

interface Props {
  subjects: Subject[];
  value: string;
  customValue: string;
  onValueChange: (value: string) => void;
  onCustomValueChange: (value: string) => void;
  required?: boolean;
  customLabel?: string;
  customMaxLength?: number;
}

export function RequestSubjectField({
  subjects,
  value,
  customValue,
  onValueChange,
  onCustomValueChange,
  required = true,
  customLabel = 'Tên môn học khác',
  customMaxLength = 255
}: Props) {
  const custom = value === 'OTHER';
  return (
    <>
      <label>
        Môn học
        <select value={value} onChange={(event) => onValueChange(event.target.value)} required={required}>
          <option value="" disabled>
            Chọn môn học
          </option>
          {subjects
            .filter((subject) => subject.status === 'ACTIVE')
            .map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.name}
              </option>
            ))}
          <option value="OTHER">Môn học khác</option>
        </select>
      </label>
      {custom && (
        <label>
          {customLabel}
          <input
            value={customValue}
            onChange={(event) => onCustomValueChange(event.target.value)}
            maxLength={customMaxLength}
            required
          />
        </label>
      )}
    </>
  );
}
