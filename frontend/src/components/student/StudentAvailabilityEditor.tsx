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
      <span>Availability (optional)</span>
      <p className="student-muted">Add one or more preferred time slots, or leave this section empty.</p>
      {slots.map((slot, index) => (
        <div className="student-availability-row" key={index}>
          <select
            aria-label={`Weekday ${index + 1}`}
            value={slot.dayOfWeek || ''}
            onChange={(event) => update(index, 'dayOfWeek', event.target.value)}
          >
            <option value="">Select a weekday</option>
            {['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map((day, dayIndex) => (
              <option key={day} value={dayIndex + 1}>
                {day}
              </option>
            ))}
          </select>
          <input
            aria-label={`Start time ${index + 1}`}
            value={slot.startTime}
            onChange={(event) => update(index, 'startTime', event.target.value)}
            type="time"
          />
          <input
            aria-label={`End time ${index + 1}`}
            value={slot.endTime}
            onChange={(event) => update(index, 'endTime', event.target.value)}
            type="time"
          />
          <button className="student-button secondary" type="button" onClick={() => remove(index)}>
            Remove
          </button>
        </div>
      ))}
      <button
        className="student-button availability-add-button"
        type="button"
        onClick={() => onChange([...slots, { dayOfWeek: 0, startTime: '', endTime: '' }])}
      >
        Add availability
      </button>
    </div>
  );
}
