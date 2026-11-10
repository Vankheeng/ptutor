import { useState, type FormEvent } from 'react';
import type { LearningMode, StudentApplication } from '../../services/studentApi';
import type { Reference } from '../../types/api';

const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const weekdayLabels: Record<string, string> = {
  Sunday: 'Chủ nhật',
  Monday: 'Thứ Hai',
  Tuesday: 'Thứ Ba',
  Wednesday: 'Thứ Tư',
  Thursday: 'Thứ Năm',
  Friday: 'Thứ Sáu',
  Saturday: 'Thứ Bảy'
};
const weekdayAliases: Record<string, string> = {
  'chủ nhật': 'Sunday',
  'thứ hai': 'Monday',
  'thứ ba': 'Tuesday',
  'thứ tư': 'Wednesday',
  'thứ năm': 'Thursday',
  'thứ sáu': 'Friday',
  'thứ bảy': 'Saturday'
};
const times = Array.from(
  { length: 48 },
  (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`
);

type AvailableTime = { day: string; start: string; end: string };

export interface StudentApplicationFormValues {
  gradeId: string;
  proposedPrice: number;
  learningMode: LearningMode;
  preferredSchedule: string;
  message: string;
}

interface Props {
  application?: StudentApplication;
  initialValues?: Partial<StudentApplicationFormValues>;
  grades: Reference[] | null;
  gradeLoadError?: string;
  saving: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (values: StudentApplicationFormValues) => void;
  title?: string;
  submitLabel?: string;
}

export function StudentApplicationForm({
  application,
  initialValues,
  grades,
  gradeLoadError,
  saving,
  error,
  onClose,
  onSubmit,
  title = 'Chỉnh sửa chi tiết ứng tuyển',
  submitLabel = 'Lưu thay đổi'
}: Props) {
  const [validationError, setValidationError] = useState('');
  const [learningMode, setLearningMode] = useState<LearningMode>(
    application?.learningMode ?? initialValues?.learningMode ?? 'ONLINE'
  );
  const [availableTimes, setAvailableTimes] = useState<AvailableTime[]>(() =>
    parseAvailableTimes(application?.preferredSchedule ?? initialValues?.preferredSchedule)
  );
  const availableGrades = grades ?? [];
  const selectedGradeId = application?.gradeId ?? initialValues?.gradeId ?? '';
  const gradeIsAvailable = availableGrades.some((grade) => grade.id === selectedGradeId);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const gradeId = String(data.get('gradeId') ?? '').trim();
    const proposedPrice = Number(data.get('proposedPrice'));
    const message = String(data.get('message') ?? '').trim();

    if (!gradeId || !availableGrades.some((grade) => grade.id === gradeId)) {
      setValidationError('Vui lòng chọn một lớp học mà gia sư đã đăng ký dạy.');
      return;
    }
    if (!Number.isFinite(proposedPrice) || proposedPrice < 0) {
      setValidationError('Vui lòng nhập học phí đề xuất hợp lệ.');
      return;
    }
    if (availableTimes.length === 0 || availableTimes.some((slot) => !slot.day || !slot.start || !slot.end)) {
      setValidationError('Vui lòng điền đầy đủ thứ, giờ bắt đầu và giờ kết thúc.');
      return;
    }
    if (new Set(availableTimes.map((slot) => slot.day)).size !== availableTimes.length) {
      setValidationError('Mỗi thứ trong tuần chỉ được chọn một lần.');
      return;
    }
    if (availableTimes.some((slot) => slot.start >= slot.end)) {
      setValidationError('Giờ kết thúc phải sau giờ bắt đầu.');
      return;
    }
    if (message.length > 10000) {
      setValidationError('Lời nhắn tối đa 10.000 ký tự.');
      return;
    }

    setValidationError('');
    onSubmit({
      gradeId,
      proposedPrice,
      learningMode,
      preferredSchedule: availableTimes.map((slot) => `${slot.day}, ${slot.start}–${slot.end}`).join('; '),
      message
    });
  };

  const addDay = () =>
    setAvailableTimes((current) => {
      const nextDay = weekdays.find((day) => !current.some((slot) => slot.day === day));
      return nextDay ? [...current, { day: nextDay, start: '16:00', end: '17:00' }] : current;
    });

  const updateTime = (index: number, field: keyof AvailableTime, value: string) => {
    setAvailableTimes((current) =>
      current.map((slot, slotIndex) => (slotIndex === index ? { ...slot, [field]: value } : slot))
    );
  };

  return (
    <div
      className="tutor-modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="tutor-apply-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="student-application-edit-title"
      >
        <button className="tutor-modal-close" type="button" aria-label="Đóng" onClick={onClose}>
          ×
        </button>
        <h2 id="student-application-edit-title">{title}</h2>
        {(error || validationError) && (
          <div className="tutor-error" role="alert">
            {error || validationError}
          </div>
        )}
        <form className="tutor-proposal-form" onSubmit={submit}>
          <label>
            Lớp học
            <select
              name="gradeId"
              defaultValue={gradeIsAvailable ? selectedGradeId : ''}
              required
              disabled={grades === null || availableGrades.length === 0}
            >
              <option value="">
                {gradeLoadError
                  ? 'Không thể tải danh sách lớp'
                  : grades === null
                    ? 'Đang tải lớp học…'
                    : availableGrades.length
                      ? 'Chọn lớp học'
                      : 'Không có lớp học khả dụng'}
              </option>
              {availableGrades.map((grade) => (
                <option key={grade.id} value={grade.id}>
                  {grade.name}
                </option>
              ))}
            </select>
            {gradeLoadError && (
              <span className="tutor-error" role="alert">
                {gradeLoadError}
              </span>
            )}
          </label>
          <label>
            Học phí đề xuất (VNĐ)
            <input
              name="proposedPrice"
              type="number"
              min="0"
              defaultValue={application?.proposedPrice ?? initialValues?.proposedPrice ?? ''}
              required
            />
          </label>
          <label>
            Hình thức học
            <select
              name="learningMode"
              value={learningMode}
              onChange={(event) => setLearningMode(event.target.value as LearningMode)}
            >
              <option value="ONLINE">Trực tuyến</option>
              <option value="OFFLINE">Trực tiếp</option>
            </select>
          </label>
          <div className="tutor-availability-editor">
            <div className="tutor-availability-editor-heading">
              <strong>Thời gian có thể học</strong>
              <button
                className="tutor-detail-link"
                type="button"
                disabled={availableTimes.length >= weekdays.length}
                onClick={addDay}
              >
                + Thêm ngày
              </button>
            </div>
            {availableTimes.map((slot, index) => (
              <div className="tutor-availability-row" key={`${slot.day}-${index}`}>
                <label>
                  Thứ
                  <select value={slot.day} onChange={(event) => updateTime(index, 'day', event.target.value)}>
                    {weekdays.map((day) => (
                      <option
                        key={day}
                        value={day}
                        disabled={availableTimes.some((other, otherIndex) => otherIndex !== index && other.day === day)}
                      >
                        {weekdayLabels[day]}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Từ
                  <select value={slot.start} onChange={(event) => updateTime(index, 'start', event.target.value)}>
                    {times.map((time) => (
                      <option key={time} value={time}>
                        {time}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Đến
                  <select value={slot.end} onChange={(event) => updateTime(index, 'end', event.target.value)}>
                    {times.map((time) => (
                      <option key={time} value={time}>
                        {time}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  className="tutor-availability-remove"
                  type="button"
                  aria-label={`Xóa ${weekdayLabels[slot.day]}`}
                  disabled={availableTimes.length === 1}
                  onClick={() => setAvailableTimes((current) => current.filter((_, slotIndex) => slotIndex !== index))}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
          <label>
            Lời nhắn cho gia sư
            <textarea
              name="message"
              rows={4}
              maxLength={10000}
              defaultValue={application?.message ?? initialValues?.message ?? ''}
            />
          </label>
          <div className="tutor-form-actions">
            <button className="tutor-button secondary" type="button" onClick={onClose}>
              Hủy
            </button>
            <button
              className="tutor-button"
              type="submit"
              disabled={saving || Boolean(gradeLoadError) || grades === null || !availableGrades.length}
            >
              {saving ? 'Đang lưu…' : submitLabel}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function parseAvailableTimes(schedule?: string | null): AvailableTime[] {
  const pattern =
    /(Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Chủ nhật|Thứ Hai|Thứ Ba|Thứ Tư|Thứ Năm|Thứ Sáu|Thứ Bảy)\s*,?\s*(\d{1,2}:\d{2})\s*(?:–|-|to)\s*(\d{1,2}:\d{2})/gi;
  const parsed = [...(schedule ?? '').matchAll(pattern)]
    .map((match) => ({
      day: weekdayAliases[match[1].toLowerCase()] ?? match[1],
      start: normalizeTime(match[2]),
      end: normalizeTime(match[3])
    }))
    .filter((slot) => weekdays.includes(slot.day));

  return parsed.length ? parsed : [{ day: 'Monday', start: '16:00', end: '17:00' }];
}

function normalizeTime(value: string) {
  const [hours, minutes] = value.split(':');
  return `${hours.padStart(2, '0')}:${minutes}`;
}
