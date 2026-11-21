import type { SelectHTMLAttributes } from 'react';
import { quarterHourTimes } from './quarterHourTimes';

export function TimeSelect({ children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props}>
      {children}
      <option value="">Chọn giờ</option>
      {quarterHourTimes.map((time) => (
        <option key={time} value={time}>
          {time}
        </option>
      ))}
    </select>
  );
}
