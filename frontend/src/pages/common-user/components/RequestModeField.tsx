import type { LearningMode } from '../../../services/studentApi';

interface Props {
  value: LearningMode;
  onChange: (value: LearningMode) => void;
  label?: string;
}

export function RequestModeField({ value, onChange, label = 'Hình thức học' }: Props) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value as LearningMode)}>
        <option value="ONLINE">Trực tuyến</option>
        <option value="OFFLINE">Trực tiếp</option>
      </select>
    </label>
  );
}
