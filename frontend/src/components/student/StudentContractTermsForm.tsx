import { useMemo, useState, type FormEvent } from 'react';
import type { ContractSchedule, ContractTerms, PaymentPeriod } from '../../services/studentApi';

const days = [
  ['1', 'Thứ Hai'],
  ['2', 'Thứ Ba'],
  ['3', 'Thứ Tư'],
  ['4', 'Thứ Năm'],
  ['5', 'Thứ Sáu'],
  ['6', 'Thứ Bảy'],
  ['7', 'Chủ Nhật']
] as const;

const timeOptions = Array.from({ length: 48 }, (_, index) => {
  const hour = Math.floor(index / 2)
    .toString()
    .padStart(2, '0');
  const minute = index % 2 === 0 ? '00' : '30';
  return `${hour}:${minute}`;
});

interface Props {
  initial?: Partial<ContractTerms>;
  submitLabel: string;
  loading: boolean;
  onCancel?: () => void;
  onSubmit: (terms: ContractTerms) => Promise<void>;
  includePackage?: boolean;
}

export function StudentContractTermsForm({
  initial,
  submitLabel,
  loading,
  onCancel,
  onSubmit,
  includePackage = true
}: Props) {
  const [price, setPrice] = useState(initial?.price === undefined ? '' : String(initial.price));
  const [paymentPeriod, setPaymentPeriod] = useState<PaymentPeriod>(initial?.paymentPeriod ?? 'PER_LESSON');
  const [startDate, setStartDate] = useState(initial?.startDate ?? '');
  const [endDate, setEndDate] = useState(initial?.endDate ?? '');
  const [schedules, setSchedules] = useState<ContractSchedule[]>(
    initial?.schedules?.length ? initial.schedules : [{ dayOfWeek: 0, startTime: '', endTime: '' }]
  );
  const [error, setError] = useState('');
  const calculatedLessons = useMemo(() => countLessons(schedules, startDate, endDate), [schedules, startDate, endDate]);

  const updateSchedule = (index: number, patch: Partial<ContractSchedule>) => {
    setSchedules((current) => current.map((schedule, row) => (row === index ? { ...schedule, ...patch } : schedule)));
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    if (!price || Number(price) < 0) {
      setError('Giá không được là số âm.');
      return;
    }
    if (!startDate || !endDate || endDate <= startDate) {
      setError('Ngày kết thúc phải sau ngày bắt đầu.');
      return;
    }
    if (
      !schedules.length ||
      schedules.some((schedule) => !schedule.dayOfWeek || !schedule.startTime || !schedule.endTime)
    ) {
      setError('Vui lòng thêm ít nhất một khung giờ đầy đủ.');
      return;
    }
    if (schedules.some((schedule) => schedule.endTime <= schedule.startTime)) {
      setError('Thời gian kết thúc của mỗi khung phải sau thời gian bắt đầu.');
      return;
    }
    if (!calculatedLessons) {
      setError('Lịch đã chọn không có buổi học nào trong khoảng thời gian này.');
      return;
    }
    await onSubmit({
      price: Number(price),
      paymentPeriod,
      schedules,
      startDate,
      endDate,
      totalLessons: calculatedLessons
    });
  };

  return (
    <form className="student-form-grid" onSubmit={(event) => void submit(event)}>
      <label>
        Giá
        <input value={price} type="number" min="0" onChange={(event) => setPrice(event.target.value)} required />
      </label>
      <label>
        Chu kỳ thanh toán
        <select value={paymentPeriod} onChange={(event) => setPaymentPeriod(event.target.value as PaymentPeriod)}>
          <option value="PER_LESSON">Mỗi buổi</option>
          <option value="WEEKLY">Hàng tuần</option>
          <option value="MONTHLY">Hàng tháng</option>
          {includePackage && <option value="PACKAGE">Theo gói</option>}
        </select>
      </label>
      <div className="student-field-full student-contract-schedule-editor">
        <div className="student-contract-schedule-heading">
          <span>Lịch học</span>
          <small>Chọn một hoặc nhiều khung giờ học hàng tuần.</small>
        </div>
        {schedules.map((schedule, index) => (
          <div className="student-contract-schedule-row" key={`${index}-${schedule.dayOfWeek}`}>
            <label>
              Ngày
              <select
                value={schedule.dayOfWeek || ''}
                onChange={(event) => updateSchedule(index, { dayOfWeek: Number(event.target.value) })}
              >
                <option value="">Chọn ngày</option>
                {days.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Từ
              <select
                value={schedule.startTime}
                onChange={(event) => updateSchedule(index, { startTime: event.target.value })}
              >
                <option value="">Giờ bắt đầu</option>
                {timeOptions.map((time) => (
                  <option key={time} value={time}>
                    {time}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Đến
              <select
                value={schedule.endTime}
                onChange={(event) => updateSchedule(index, { endTime: event.target.value })}
              >
                <option value="">Giờ kết thúc</option>
                {timeOptions.map((time) => (
                  <option key={time} value={time}>
                    {time}
                  </option>
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
          onClick={() => setSchedules((current) => [...current, { dayOfWeek: 0, startTime: '', endTime: '' }])}
        >
          Thêm khung giờ
        </button>
      </div>
      <label>
        Ngày bắt đầu
        <input value={startDate} type="date" onChange={(event) => setStartDate(event.target.value)} required />
      </label>
      <label>
        Ngày kết thúc
        <input value={endDate} type="date" onChange={(event) => setEndDate(event.target.value)} required />
      </label>
      <div className="student-field-full student-contract-calculated-lessons">
        Tổng số buổi dự kiến: <strong>{calculatedLessons || '—'}</strong>
      </div>
      {error && (
        <p className="student-form-error student-field-full" role="alert">
          {error}
        </p>
      )}
      <div className="student-form-actions student-field-full">
        {onCancel && (
          <button className="student-button danger" disabled={loading} type="button" onClick={onCancel}>
            Hủy
          </button>
        )}
        <button className="student-button" disabled={loading} type="submit">
          {submitLabel}
        </button>
      </div>
    </form>
  );
}

function countLessons(schedules: ContractSchedule[], startDate: string, endDate: string) {
  if (
    !startDate ||
    !endDate ||
    endDate <= startDate ||
    schedules.some(
      (schedule) =>
        !schedule.dayOfWeek || !schedule.startTime || !schedule.endTime || schedule.endTime <= schedule.startTime
    )
  ) {
    return 0;
  }
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  let total = 0;
  for (const cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
    const isoDay = cursor.getDay() === 0 ? 7 : cursor.getDay();
    total += schedules.filter((schedule) => schedule.dayOfWeek === isoDay).length;
  }
  return total;
}
