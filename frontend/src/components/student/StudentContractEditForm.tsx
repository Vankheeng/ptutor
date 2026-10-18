import { useState, type FormEvent } from 'react';
import type { Contract, ContractSchedule } from '../../services/studentApi';

const days = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
const times = Array.from(
  { length: 48 },
  (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`
);

export function StudentContractEditForm({
  contract,
  loading,
  onSubmit
}: {
  contract: Contract;
  loading: boolean;
  onSubmit: (values: { price: number; preferredSchedule?: string; schedules?: ContractSchedule[] }) => Promise<void>;
}) {
  const [price, setPrice] = useState(String(contract.price));
  const [preferredSchedule, setPreferredSchedule] = useState(contract.preferredSchedule);
  const [schedules, setSchedules] = useState<ContractSchedule[]>(contract.schedules?.length ? contract.schedules : []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (schedules.length) {
      await onSubmit({ price: Number(price), schedules });
    } else {
      await onSubmit({ price: Number(price), preferredSchedule });
    }
  };

  const update = (index: number, patch: Partial<ContractSchedule>) => {
    setSchedules((current) => current.map((schedule, row) => (row === index ? { ...schedule, ...patch } : schedule)));
  };

  return (
    <form className="student-form-grid" onSubmit={(event) => void submit(event)}>
      <label>
        Giá
        <input value={price} type="number" min="0" onChange={(event) => setPrice(event.target.value)} required />
      </label>
      {!schedules.length && (
        <label>
          Lịch học
          <input value={preferredSchedule} onChange={(event) => setPreferredSchedule(event.target.value)} required />
        </label>
      )}
      {!!schedules.length && (
        <div className="student-field-full student-contract-schedule-editor">
          <div className="student-contract-schedule-heading">
            <span>Lịch học</span>
            <small>Cập nhật các khung giờ học hàng tuần.</small>
          </div>
          {schedules.map((schedule, index) => (
            <div className="student-contract-schedule-row" key={`${index}-${schedule.dayOfWeek}`}>
              <label>
                Ngày
                <select
                  value={schedule.dayOfWeek}
                  onChange={(event) => update(index, { dayOfWeek: Number(event.target.value) })}
                >
                  {days.map((day, dayIndex) => (
                    <option key={day} value={dayIndex + 1}>
                      {day}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Từ
                <select
                  value={schedule.startTime}
                  onChange={(event) => update(index, { startTime: event.target.value })}
                >
                  {times.map((time) => (
                    <option key={time}>{time}</option>
                  ))}
                </select>
              </label>
              <label>
                Đến
                <select value={schedule.endTime} onChange={(event) => update(index, { endTime: event.target.value })}>
                  {times.map((time) => (
                    <option key={time}>{time}</option>
                  ))}
                </select>
              </label>
              <button
                className="student-button schedule-remove"
                type="button"
                disabled={loading}
                onClick={() => setSchedules((current) => current.filter((_, row) => row !== index))}
              >
                Xóa
              </button>
            </div>
          ))}
          <button
            className="student-button schedule-add"
            type="button"
            disabled={loading}
            onClick={() =>
              setSchedules((current) => [...current, { dayOfWeek: 1, startTime: '16:00', endTime: '17:00' }])
            }
          >
            Thêm khung giờ
          </button>
        </div>
      )}
      <div className="student-form-actions student-field-full">
        <button className="student-button" disabled={loading} type="submit">
          Lưu thay đổi
        </button>
      </div>
    </form>
  );
}
