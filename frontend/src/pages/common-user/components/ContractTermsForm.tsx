import { useMemo, useState, type FormEvent } from 'react';
import type { ContractSchedule, ContractTerms, PaymentPeriod } from '../../../services/studentApi';
import { ContractPdfLink, type ContractPdfDetails } from './ContractPdfLink';

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
  includeAgreement?: boolean;
  pdfDetails?: ContractPdfDetails;
}

export function ContractTermsForm({
  initial,
  submitLabel,
  loading,
  onCancel,
  onSubmit,
  includePackage = true,
  includeAgreement = false,
  pdfDetails
}: Props) {
  const [price, setPrice] = useState(initial?.price === undefined ? '' : String(initial.price));
  const [paymentPeriod, setPaymentPeriod] = useState<PaymentPeriod>(
    initial?.paymentPeriod === 'PACKAGE' ? 'PACKAGE' : 'MONTHLY'
  );
  const [startDate, setStartDate] = useState(initial?.startDate ?? '');
  const [endDate, setEndDate] = useState(initial?.endDate ?? '');
  const [schedules, setSchedules] = useState<ContractSchedule[]>(
    initial?.schedules?.length ? initial.schedules : [{ dayOfWeek: 0, startTime: '', endTime: '' }]
  );
  const [error, setError] = useState('');
  const [agreed, setAgreed] = useState(false);
  const lessons = useMemo(() => buildLessonPreview(schedules, startDate, endDate), [endDate, schedules, startDate]);
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
    const today = new Date();
    const todayValue = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    if (startDate < todayValue) {
      setError('Ngày bắt đầu không được ở trong quá khứ.');
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
    if (!lessons.length) {
      setError('Khoảng thời gian đã chọn không có buổi học nào. Vui lòng kiểm tra lại ngày và lịch học.');
      return;
    }
    if (includeAgreement && !agreed) {
      setError('Vui lòng xác nhận đồng ý với các điều khoản hợp đồng.');
      return;
    }
    await onSubmit({
      price: Number(price),
      paymentPeriod,
      schedules,
      startDate,
      endDate
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
        <input
          value={startDate}
          min={todayValue()}
          type="date"
          onChange={(event) => setStartDate(event.target.value)}
          required
        />
      </label>
      <label>
        Ngày kết thúc
        <input
          value={endDate}
          min={startDate || todayValue()}
          type="date"
          onChange={(event) => setEndDate(event.target.value)}
          required
        />
      </label>
      <div className="student-field-full contract-lesson-preview">
        <strong>
          {lessons.length > 0 ? `Dự kiến ${lessons.length} buổi học` : 'Thêm lịch học để hiển thị số buổi dự kiến'}
        </strong>
        <details>
          <summary>Mở rộng để xem danh sách buổi học</summary>
          {lessons.length ? (
            <ol>
              {lessons.map((lesson) => (
                <li key={`${lesson.date}-${lesson.startTime}`}>{formatLesson(lesson)}</li>
              ))}
            </ol>
          ) : (
            <p>Chưa có buổi học phù hợp với khoảng ngày và lịch đã chọn.</p>
          )}
        </details>
      </div>
      {includeAgreement && (
        <div className="student-field-full contract-create-terms-note">
          <strong>Điều khoản hợp đồng</strong>
          <ul>
            <li>Đồng ý với các điều khoản đã ghi trong hợp đồng.</li>
            <li>Nghỉ học phải thông báo trước; hai bên có thể đổi lịch hoặc trao đổi về hoàn tiền.</li>
            <li>Học phí của buổi đã được xác nhận tham gia không được hoàn lại.</li>
            <li>Hai bên có thể khiếu nại hoặc yêu cầu dừng hợp đồng khi đã thỏa thuận.</li>
            <li>Buổi học bị đánh dấu đã dạy nhưng thực tế không diễn ra có thể được yêu cầu hoàn tiền.</li>
          </ul>
          {pdfDetails && (
            <ContractPdfLink
              {...pdfDetails}
              price={price}
              paymentPeriod={paymentPeriod}
              startDate={startDate}
              endDate={endDate}
              schedules={schedules}
            />
          )}
          <label className="contract-agreement-checkbox">
            <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
            <span>Tôi đồng ý với các điều khoản đã ghi trong hợp đồng.</span>
          </label>
        </div>
      )}
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
        <button
          className="student-button"
          disabled={loading || lessons.length < 1 || (includeAgreement && !agreed)}
          type="submit"
        >
          {submitLabel}
        </button>
      </div>
    </form>
  );
}

function todayValue() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
}

interface LessonPreview {
  date: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

function buildLessonPreview(schedules: ContractSchedule[], startDate: string, endDate: string): LessonPreview[] {
  if (!startDate || !endDate || endDate <= startDate) return [];
  if (
    schedules.some(
      (schedule) =>
        !schedule.dayOfWeek || !schedule.startTime || !schedule.endTime || schedule.endTime <= schedule.startTime
    )
  )
    return [];
  const result: LessonPreview[] = [];
  const cursor = new Date(`${startDate}T12:00:00`);
  const end = new Date(`${endDate}T12:00:00`);
  while (cursor <= end) {
    const dayOfWeek = cursor.getDay() === 0 ? 7 : cursor.getDay();
    const date = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`;
    schedules
      .filter((schedule) => schedule.dayOfWeek === dayOfWeek)
      .forEach((schedule) => {
        result.push({ date, dayOfWeek, startTime: schedule.startTime, endTime: schedule.endTime });
      });
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}

function formatLesson(lesson: LessonPreview) {
  return `${new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(`${lesson.date}T12:00:00`))} · ${lesson.startTime}–${lesson.endTime}`;
}
