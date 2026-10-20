import type { Availability } from '../../services/studentApi';

interface Props {
  values: Availability[];
  onChange: (values: Availability[]) => void;
}

export function StudentAvailabilityEditor({ values, onChange }: Props) {
  const slots = values.length ? values : [{ dayOfWeek: 0, startTime: '', endTime: '' }];
  const update = (index: number, key: keyof Availability, value: string) => {
    const next = slots.map((slot) => ({ ...slot }));
    next[index] = { ...next[index], [key]: key === 'dayOfWeek' ? Number(value) : value };
    onChange(next);
  };
  const remove = (index: number) => onChange(slots.filter((_, currentIndex) => currentIndex !== index));

  return (
    <div className="student-field-full student-availability-editor">
      <span>Lịch rảnh (không bắt buộc)</span>
      <p className="student-muted">Thêm một hoặc vài khung giờ bạn học được, hoặc để trống.</p>
      {slots.map((slot, index) => (
        <div className="student-availability-row" key={index}>
          <select
            aria-label={`Thứ của khung giờ ${index + 1}`}
            value={slot.dayOfWeek || ''}
            onChange={(event) => update(index, 'dayOfWeek', event.target.value)}
          >
            <option value="">Chọn thứ</option>
            {['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'].map((day, dayIndex) => (
              <option key={day} value={dayIndex + 1}>
                {day}
              </option>
            ))}
          </select>
          <input
            aria-label={`Giờ bắt đầu ${index + 1}`}
            value={slot.startTime}
            onChange={(event) => update(index, 'startTime', event.target.value)}
            type="time"
          />
          <input
            aria-label={`Giờ kết thúc ${index + 1}`}
            value={slot.endTime}
            onChange={(event) => update(index, 'endTime', event.target.value)}
            type="time"
          />
          <button className="student-button secondary" type="button" onClick={() => remove(index)}>
            Xoá
          </button>
        </div>
      ))}
      <button
        className="student-button availability-add-button"
        type="button"
        onClick={() => onChange([...slots, { dayOfWeek: 0, startTime: '', endTime: '' }])}
      >
        Thêm khung giờ
      </button>
    </div>
  );
}
