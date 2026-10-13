import type { Availability } from '../../types/api';

interface AvailabilityEditorProps {
  value: Availability[];
  onChange: (value: Availability[]) => void;
}

/** ISO-8601 day numbers, as the API expects: 1 = Monday ... 7 = Sunday. */
const DAYS = [
  { value: 1, label: 'Thứ Hai' },
  { value: 2, label: 'Thứ Ba' },
  { value: 3, label: 'Thứ Tư' },
  { value: 4, label: 'Thứ Năm' },
  { value: 5, label: 'Thứ Sáu' },
  { value: 6, label: 'Thứ Bảy' },
  { value: 7, label: 'Chủ nhật' }
];

export function AvailabilityEditor({ value, onChange }: AvailabilityEditorProps) {
  const update = (index: number, changes: Partial<Availability>) =>
    onChange(value.map((slot, position) => (position === index ? { ...slot, ...changes } : slot)));

  return (
    <div className="rec-slots">
      {value.map((slot, index) => (
        <div className="rec-slot" key={index}>
          <select
            value={slot.dayOfWeek}
            aria-label="Ngày"
            onChange={(event) => update(index, { dayOfWeek: Number(event.target.value) })}
          >
            {DAYS.map((day) => (
              <option key={day.value} value={day.value}>
                {day.label}
              </option>
            ))}
          </select>
          <input
            type="time"
            value={slot.startTime.slice(0, 5)}
            aria-label="Từ"
            onChange={(event) => update(index, { startTime: event.target.value })}
          />
          <span>đến</span>
          <input
            type="time"
            value={slot.endTime.slice(0, 5)}
            aria-label="Đến"
            onChange={(event) => update(index, { endTime: event.target.value })}
          />
          <button
            type="button"
            className="rec-icon-button"
            aria-label="Bỏ khung giờ này"
            onClick={() => onChange(value.filter((_, position) => position !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <button
        type="button"
        className="rec-add"
        onClick={() => onChange([...value, { dayOfWeek: 2, startTime: '19:00', endTime: '21:00' }])}
      >
        + Thêm khung giờ rảnh
      </button>
    </div>
  );
}
