import { useEffect, useMemo, useState, type FormEvent } from 'react';
import type { ContractSchedule, ContractTerms, PaymentPeriod } from '../../../services/studentApi';
import { ContractPdfLink, type ContractPdfDetails } from './ContractPdfLink';
import { quarterHourTimes } from '../../../components/ui/quarterHourTimes';
import './contract-lesson-actions.css';

const days = [
  ['1', 'Thứ Hai'],
  ['2', 'Thứ Ba'],
  ['3', 'Thứ Tư'],
  ['4', 'Thứ Năm'],
  ['5', 'Thứ Sáu'],
  ['6', 'Thứ Bảy'],
  ['7', 'Chủ Nhật']
] as const;

const paymentPeriodLabels: Record<PaymentPeriod, string> = {
  PER_LESSON: 'Theo buổi',
  WEEKLY: 'Hàng tuần',
  MONTHLY: 'Hàng tháng',
  PACKAGE: 'Theo gói'
};

const timeOptions = quarterHourTimes;

interface Props {
  initial?: Partial<ContractTerms>;
  submitLabel: string;
  loading: boolean;
  onCancel?: () => void;
  onSubmit: (terms: ContractTerms) => Promise<void>;
  onCheckSchedule?: (terms: ContractTerms) => Promise<string[]>;
  includePackage?: boolean;
  includeAgreement?: boolean;
  signingExisting?: boolean;
  editingExisting?: boolean;
  showPdf?: boolean;
  cancelLabel?: string;
  pdfDetails?: ContractPdfDetails;
  editableScheduleList?: boolean;
  hideLessonPreview?: boolean;
}

export function ContractTermsForm({
  initial,
  submitLabel,
  loading,
  onCancel,
  onSubmit,
  onCheckSchedule,
  includePackage = true,
  includeAgreement = false,
  signingExisting = false,
  editingExisting = false,
  showPdf = false,
  cancelLabel = 'Hủy',
  pdfDetails,
  editableScheduleList = false,
  hideLessonPreview = false
}: Props) {
  const [price, setPrice] = useState(initial?.price === undefined ? '' : String(initial.price));
  const [paymentPeriod, setPaymentPeriod] = useState<PaymentPeriod>(initial?.paymentPeriod ?? 'MONTHLY');
  const [startDate, setStartDate] = useState(initial?.startDate ?? '');
  const [endDate, setEndDate] = useState(initial?.endDate ?? '');
  const [schedules, setSchedules] = useState<ContractSchedule[]>(
    initial?.schedules?.length
      ? initial.schedules.map((schedule) => ({
          ...schedule,
          startTime: schedule.startTime.slice(0, 5),
          endTime: schedule.endTime.slice(0, 5)
        }))
      : [{ dayOfWeek: 0, startTime: '', endTime: '' }]
  );
  const [error, setError] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [previewExpanded, setPreviewExpanded] = useState(true);
  const [scheduleWarnings, setScheduleWarnings] = useState<string[]>([]);
  const [checkingSchedule, setCheckingSchedule] = useState(false);
  const [editingLessonList, setEditingLessonList] = useState(false);
  const [lessonDrafts, setLessonDrafts] = useState<LessonPreview[] | null>(null);
  const lessons = useMemo(() => buildLessonPreview(schedules, startDate, endDate), [endDate, schedules, startDate]);
  const previewLessons = editingLessonList && lessonDrafts ? lessonDrafts : lessons;
  const invalidSchedule = schedules.some((schedule) =>
    Boolean(schedule.startTime && schedule.endTime && schedule.endTime <= schedule.startTime)
  );
  const schedulePayload = useMemo<ContractTerms | null>(() => {
    if (
      !startDate ||
      !endDate ||
      endDate <= startDate ||
      !schedules.length ||
      schedules.some(
        (schedule) =>
          !schedule.dayOfWeek || !schedule.startTime || !schedule.endTime || schedule.endTime <= schedule.startTime
      ) ||
      !lessons.length
    )
      return null;
    return { price: Number(price || 0), paymentPeriod, schedules, startDate, endDate };
  }, [endDate, lessons.length, paymentPeriod, price, schedules, startDate]);

  useEffect(() => {
    if (!onCheckSchedule || !schedulePayload) {
      window.setTimeout(() => {
        setScheduleWarnings([]);
        setCheckingSchedule(false);
      }, 0);
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setCheckingSchedule(true);
      void onCheckSchedule(schedulePayload)
        .then((messages) => {
          if (active) setScheduleWarnings(messages);
        })
        .catch(() => {
          if (active) setScheduleWarnings([]);
        })
        .finally(() => {
          if (active) setCheckingSchedule(false);
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [onCheckSchedule, schedulePayload, signingExisting]);
  const updateSchedule = (index: number, patch: Partial<ContractSchedule>) => {
    setSchedules((current) => current.map((schedule, row) => (row === index ? { ...schedule, ...patch } : schedule)));
  };
  const updateLessonSchedule = (lesson: LessonPreview, patch: Partial<LessonPreview>) => {
    setLessonDrafts((current) => current?.map((item) => (item === lesson ? { ...item, ...patch } : item)) ?? current);
  };

  const removeLessonSchedule = (lesson: LessonPreview) => {
    setLessonDrafts((current) => current?.filter((item) => item !== lesson) ?? current);
  };

  const addLessonSchedule = () => {
    const base = lessonDrafts?.[lessonDrafts.length - 1] ?? lessons[lessons.length - 1];
    setLessonDrafts((current) => [
      ...(current ?? lessons),
      {
        date: '',
        dayOfWeek: 0,
        startTime: base?.startTime || '19:00',
        endTime: base?.endTime || '20:00'
      }
    ]);
  };

  const toggleLessonList = () => {
    if (editingLessonList) {
      const drafts = lessonDrafts ?? [];
      if (!drafts.length || drafts.some((lesson) => !lesson.date || !lesson.dayOfWeek)) {
        setError('Vui lòng chọn ngày cho tất cả buổi học trước khi lưu.');
        return;
      }
      if (drafts.some((lesson) => lesson.date < startDate || lesson.date > endDate)) {
        setError('Ngày học phải nằm trong khoảng ngày bắt đầu và ngày kết thúc hợp đồng.');
        return;
      }
      if (drafts.some((lesson) => !lesson.startTime || !lesson.endTime || lesson.endTime <= lesson.startTime)) {
        setError('Giờ kết thúc phải sau giờ bắt đầu cho tất cả buổi học.');
        return;
      }
      const nextSchedules = drafts
        .map(({ dayOfWeek, startTime, endTime }) => ({ dayOfWeek, startTime, endTime }))
        .filter(
          (schedule, index, all) =>
            all.findIndex(
              (item) =>
                item.dayOfWeek === schedule.dayOfWeek &&
                item.startTime === schedule.startTime &&
                item.endTime === schedule.endTime
            ) === index
        );
      setSchedules(nextSchedules);
      setLessonDrafts(null);
      setEditingLessonList(false);
      setError('');
      return;
    }
    setLessonDrafts(lessons);
    setEditingLessonList(true);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    if (signingExisting) {
      if (!agreed) {
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
      return;
    }
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
    if (!editingExisting && startDate < todayValue) {
      setError('Ngày bắt đầu không được ở trong quá khứ.');
      return;
    }
    if (
      !schedules.length ||
      schedules.some((schedule) => !schedule.dayOfWeek || !schedule.startTime || !schedule.endTime)
    ) {
      setError('Vui lòng thêm ít nhất một khung giờ dạy.');
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
    if (scheduleWarnings.length) {
      setError('Lịch học đang bị trùng. Vui lòng điều chỉnh ngày hoặc giờ trước khi tiếp tục.');
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
        Giá mỗi buổi học (VNĐ/buổi)
        {signingExisting ? (
          <span className="contract-signing-value">{new Intl.NumberFormat('vi-VN').format(Number(price))} VNĐ</span>
        ) : (
          <input value={price} type="number" min="0" onChange={(event) => setPrice(event.target.value)} required />
        )}
      </label>
      <label>
        Chu kỳ thanh toán
        {signingExisting ? (
          <span className="contract-signing-value">{paymentPeriodLabels[paymentPeriod]}</span>
        ) : (
          <select value={paymentPeriod} onChange={(event) => setPaymentPeriod(event.target.value as PaymentPeriod)}>
            {initial?.paymentPeriod === 'PER_LESSON' && (
              <option value="PER_LESSON" hidden>
                Theo buổi
              </option>
            )}
            {initial?.paymentPeriod === 'WEEKLY' && (
              <option value="WEEKLY" hidden>
                Hàng tuần
              </option>
            )}
            <option value="MONTHLY">Hàng tháng</option>
            {includePackage && <option value="PACKAGE">Theo gói</option>}
          </select>
        )}
      </label>
      <div className="student-field-full student-contract-schedule-editor">
        <div className="student-contract-schedule-heading">
          <span>Lịch học</span>
          <small>
            {signingExisting
              ? 'Lịch học đã thỏa thuận trong hợp đồng.'
              : 'Chọn một hoặc nhiều khung giờ học hàng tuần.'}
          </small>
        </div>
        {schedules.map((schedule, index) => (
          <div className="student-contract-schedule-row" key={`${index}-${schedule.dayOfWeek}`}>
            <label>
              Ngày
              {signingExisting ? (
                <span className="contract-signing-value">
                  {days.find(([value]) => Number(value) === schedule.dayOfWeek)?.[1] ?? 'Chưa chọn ngày'}
                </span>
              ) : (
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
              )}
            </label>
            <label>
              Từ
              {signingExisting ? (
                <span className="contract-signing-value">{schedule.startTime || '—'}</span>
              ) : (
                <select
                  value={schedule.startTime}
                  onChange={(event) => updateSchedule(index, { startTime: event.target.value })}
                >
                  <option value="">Giờ bắt đầu</option>
                  {schedule.startTime && !timeOptions.includes(schedule.startTime) && (
                    <option value={schedule.startTime}>{schedule.startTime}</option>
                  )}
                  {timeOptions.map((time) => (
                    <option key={time} value={time}>
                      {time}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label>
              Đến
              {signingExisting ? (
                <span className="contract-signing-value">{schedule.endTime || '—'}</span>
              ) : (
                <select
                  value={schedule.endTime}
                  onChange={(event) => updateSchedule(index, { endTime: event.target.value })}
                >
                  <option value="">Giờ kết thúc</option>
                  {schedule.endTime && !timeOptions.includes(schedule.endTime) && (
                    <option value={schedule.endTime}>{schedule.endTime}</option>
                  )}
                  {timeOptions.map((time) => (
                    <option key={time} value={time}>
                      {time}
                    </option>
                  ))}
                </select>
              )}
            </label>
            {!signingExisting && (
              <button
                className="student-button schedule-remove"
                type="button"
                disabled={loading}
                onClick={() => setSchedules((current) => current.filter((_, row) => row !== index))}
              >
                Xóa
              </button>
            )}
          </div>
        ))}
        {!signingExisting && (
          <button
            className="student-button schedule-add"
            type="button"
            disabled={loading}
            onClick={() => setSchedules((current) => [...current, { dayOfWeek: 0, startTime: '', endTime: '' }])}
          >
            Thêm khung giờ
          </button>
        )}
        {invalidSchedule && (
          <p className="contract-schedule-time-warning" role="alert">
            Giờ kết thúc phải sau giờ bắt đầu.
          </p>
        )}
      </div>
      <label>
        Ngày bắt đầu
        {signingExisting ? (
          <span className="contract-signing-value">{formatContractDate(startDate)}</span>
        ) : (
          <input
            value={startDate}
            min={editingExisting ? undefined : todayValue()}
            type="date"
            onChange={(event) => setStartDate(event.target.value)}
            required
          />
        )}
      </label>
      <label>
        Ngày kết thúc
        {signingExisting ? (
          <span className="contract-signing-value">{formatContractDate(endDate)}</span>
        ) : (
          <input
            value={endDate}
            min={editingExisting ? undefined : startDate || todayValue()}
            type="date"
            onChange={(event) => setEndDate(event.target.value)}
            required
          />
        )}
      </label>
      {!hideLessonPreview && (
        <div className="student-field-full contract-lesson-preview">
          <div className="contract-lesson-preview-header">
            <strong>
              {signingExisting && initial?.totalLessons
                ? `${initial.totalLessons} buổi học trong hợp đồng`
                : previewLessons.length > 0
                  ? `Dự kiến ${previewLessons.length} buổi học`
                  : 'Thêm lịch học để hiển thị số buổi dự kiến'}
            </strong>
            {editableScheduleList && !signingExisting && (lessons.length > 0 || editingLessonList) && (
              <button
                className="student-button contract-lesson-edit-button"
                type="button"
                disabled={loading}
                onClick={toggleLessonList}
              >
                {editingLessonList ? 'Lưu các buổi học' : 'Sửa các buổi học'}
              </button>
            )}
          </div>
          {previewLessons.length > 0 || editingLessonList ? (
            <details open={previewExpanded} onToggle={(event) => setPreviewExpanded(event.currentTarget.open)}>
              <summary>
                {previewExpanded ? 'Thu gọn danh sách buổi học dự kiến' : 'Mở rộng để xem danh sách buổi học dự kiến'}
              </summary>
              {editingLessonList ? (
                <div className="contract-lesson-table-wrap">
                  <table className="contract-lesson-table">
                    <thead>
                      <tr>
                        <th>Ngày</th>
                        <th>Bắt đầu</th>
                        <th>Kết thúc</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {previewLessons.map((lesson, index) => (
                        <tr key={`${lesson.date}-${lesson.startTime}-${index}`}>
                          <td>
                            <input
                              type="date"
                              value={lesson.date}
                              min={startDate}
                              max={endDate}
                              onChange={(event) => {
                                const date = event.target.value;
                                if (!date) return;
                                const nextDate = new Date(`${date}T12:00:00`);
                                updateLessonSchedule(lesson, {
                                  date,
                                  dayOfWeek: nextDate.getDay() === 0 ? 7 : nextDate.getDay()
                                });
                              }}
                            />
                          </td>
                          <td>
                            <select
                              value={lesson.startTime}
                              onChange={(event) => updateLessonSchedule(lesson, { startTime: event.target.value })}
                            >
                              {timeOptions.map((time) => (
                                <option key={time} value={time}>
                                  {time}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <select
                              value={lesson.endTime}
                              onChange={(event) => updateLessonSchedule(lesson, { endTime: event.target.value })}
                            >
                              {timeOptions.map((time) => (
                                <option key={time} value={time}>
                                  {time}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <button
                              className="student-button contract-lesson-remove-button"
                              type="button"
                              disabled={loading}
                              onClick={() => removeLessonSchedule(lesson)}
                            >
                              Xóa
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <button
                    className="student-button contract-lesson-add-button"
                    type="button"
                    disabled={loading}
                    onClick={addLessonSchedule}
                  >
                    + Thêm buổi học
                  </button>
                </div>
              ) : (
                <ol>
                  {previewLessons.map((lesson) => (
                    <li key={`${lesson.date}-${lesson.startTime}`}>{formatLesson(lesson)}</li>
                  ))}
                </ol>
              )}
            </details>
          ) : null}
        </div>
      )}
      {checkingSchedule && <p className="student-form-hint student-field-full">Đang kiểm tra trùng lịch…</p>}
      {scheduleWarnings.length > 0 && (
        <div className="contract-schedule-warning student-field-full" role="alert">
          <strong>Lịch học bị trùng</strong>
          {scheduleWarnings.map((message) => (
            <div key={message}>{message}</div>
          ))}
        </div>
      )}
      {(includeAgreement || showPdf) && (
        <div className="student-field-full contract-create-terms-note">
          <strong>Điều khoản hợp đồng</strong>
          <ul>
            <li>Đồng ý với các điều khoản đã ghi trong hợp đồng.</li>
            <li>Nghỉ học phải thông báo trước; hai bên có thể đổi lịch hoặc trao đổi về hoàn tiền.</li>
            <li>
              Các buổi học cần được thanh toán trước khi bắt đầu. Với chu kỳ thanh toán theo tháng, học phí phải được
              thanh toán trước khi buổi học đầu tiên của tháng đó bắt đầu.
            </li>
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
          {includeAgreement && (
            <label className="contract-agreement-checkbox">
              <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
              <span>Tôi đồng ý với các điều khoản đã ghi trong hợp đồng.</span>
            </label>
          )}
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
            {cancelLabel}
          </button>
        )}
        <button
          className="student-button"
          disabled={
            loading ||
            checkingSchedule ||
            invalidSchedule ||
            scheduleWarnings.length > 0 ||
            (!signingExisting && lessons.length < 1) ||
            (includeAgreement && !agreed)
          }
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

function formatContractDate(value: string) {
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : '—';
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
  return `${new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(`${lesson.date}T12:00:00`))}  ${lesson.startTime}-${lesson.endTime}`;
}
