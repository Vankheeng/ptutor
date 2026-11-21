import { useEffect, useState, type FormEvent } from 'react';
import {
  getTutorContractLessons,
  getTutorContracts,
  getTutorLessons,
  type TutorLesson,
  type TutorTeachingProposal
} from '../../../services/tutorApi';

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
const times = Array.from(
  { length: 96 },
  (_, index) =>
    `${String(Math.floor((index * 15) / 60)).padStart(2, '0')}:${String((index * 15) % 60).padStart(2, '0')}`
);
type AvailableTime = { day: string; start: string; end: string };

export interface TutorApplicationValues {
  proposedPrice: number;
  teachingMode: 'ONLINE' | 'OFFLINE';
  preferredSchedule: string;
  preferredProvinceId: string | null;
  preferredDistrictId: string | null;
  message: string;
}

interface TutorApplicationFormProps {
  subjectName: string | null;
  requestMode: 'ONLINE' | 'OFFLINE';
  requestProvinceId?: string | null;
  requestDistrictId?: string | null;
  proposal?: TutorTeachingProposal;
  editing: boolean;
  sending: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (values: TutorApplicationValues) => void;
}

export function TutorApplicationForm({
  requestMode,
  proposal,
  editing,
  sending,
  error,
  onClose,
  onSubmit
}: TutorApplicationFormProps) {
  const [availableTimes, setAvailableTimes] = useState<AvailableTime[]>(() =>
    parseAvailableTimes(proposal?.preferredSchedule)
  );
  const [teachingMode, setTeachingMode] = useState<'ONLINE' | 'OFFLINE'>(proposal?.teachingMode ?? requestMode);
  const [validationError, setValidationError] = useState('');
  const [scheduleWarnings, setScheduleWarnings] = useState<string[]>([]);
  const invalidSchedule = availableTimes.some((slot) => slot.start >= slot.end);

  useEffect(() => {
    const controller = new AbortController();
    loadTutorLessons(controller.signal)
      .then((result) => {
        const conflicts = result.content.filter((lesson) => {
          if (lesson.status === 'CANCELLED') return false;
          const day = weekdayFromDate(lesson.date);
          const slot = availableTimes.find((item) => item.day === day);
          return Boolean(slot && slot.start < lesson.endTime.slice(0, 5) && slot.end > lesson.startTime.slice(0, 5));
        });
        if (!conflicts.length) {
          setScheduleWarnings([]);
          return;
        }
        setScheduleWarnings([
          ...conflicts.map(
            (conflict) =>
              `Bạn đang có buổi học "${conflict.title || 'Buổi học'}" trùng lịch (${formatLessonConflict(conflict)}).`
          ),
          'Bạn vẫn có thể gửi đề xuất vì chưa xác định ngày bắt đầu hợp đồng.'
        ]);
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [availableTimes]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    if (invalidSchedule) {
      setValidationError('Giờ kết thúc phải sau giờ bắt đầu.');
      return;
    }
    setValidationError('');
    onSubmit({
      proposedPrice: Number(data.get('proposedPrice')),
      teachingMode,
      preferredSchedule: availableTimes.map((slot) => `${slot.day}, ${slot.start}–${slot.end}`).join('; '),
      preferredProvinceId: null,
      preferredDistrictId: null,
      message: String(data.get('message') ?? '')
    });
  };

  const addDay = () =>
    setAvailableTimes((current) => {
      const lastSlot = current[current.length - 1] ?? { day: 'Monday', start: '16:00', end: '17:00' };
      const lastDayIndex = weekdays.indexOf(lastSlot.day);
      const nextDay =
        Array.from(
          { length: weekdays.length },
          (_, offset) => weekdays[(lastDayIndex + offset + 1) % weekdays.length]
        ).find((day) => !current.some((slot) => slot.day === day)) || 'Monday';
      return [...current, { day: nextDay, start: lastSlot.start, end: lastSlot.end }];
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
      <section className="tutor-apply-modal" role="dialog" aria-modal="true" aria-labelledby="tutor-apply-title">
        <button className="tutor-modal-close" type="button" aria-label="Đóng" onClick={onClose}>
          ×
        </button>
        <h2 id="tutor-apply-title">{editing ? 'Chỉnh sửa chi tiết ứng tuyển' : 'Chi tiết ứng tuyển'}</h2>
        {(error || validationError) && (
          <div className="tutor-error" role="alert">
            {error || validationError}
          </div>
        )}
        <form className="tutor-proposal-form" onSubmit={submit}>
          <label>
            Học phí đề xuất (VNĐ)
            <input name="proposedPrice" type="number" min="0" defaultValue={proposal?.proposedPrice ?? ''} required />
          </label>
          <label>
            Hình thức dạy
            <select
              name="teachingMode"
              value={teachingMode}
              onChange={(event) => {
                const mode = event.target.value as 'ONLINE' | 'OFFLINE';
                setTeachingMode(mode);
              }}
            >
              <option value="ONLINE">Trực tuyến</option>
              <option value="OFFLINE">Trực tiếp</option>
            </select>
          </label>
          <div className="tutor-availability-editor">
            <div className="tutor-availability-editor-heading">
              <strong>Thời gian có thể dạy</strong>
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
              <div className="tutor-availability-row" key={index}>
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
                      <option key={time}>{time}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Đến
                  <select value={slot.end} onChange={(event) => updateTime(index, 'end', event.target.value)}>
                    {times.map((time) => (
                      <option key={time}>{time}</option>
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
            {scheduleWarnings.length > 0 && (
              <div className="tutor-schedule-warning" role="status">
                <strong>Lịch có thể bị trùng</strong>
                {scheduleWarnings.map((warning) => (
                  <div key={warning}>{warning}</div>
                ))}
              </div>
            )}
            {invalidSchedule && (
              <div className="tutor-schedule-time-warning" role="alert">
                Giờ kết thúc phải sau giờ bắt đầu. Vui lòng điều chỉnh trước khi tiếp tục.
              </div>
            )}
          </div>
          <label>
            Lời nhắn cho học viên
            <textarea
              name="message"
              rows={4}
              maxLength={10000}
              defaultValue={proposal?.message ?? ''}
              placeholder="Chia sẻ kinh nghiệm và cách bạn có thể hỗ trợ học viên."
            />
          </label>
          <div className="tutor-form-actions">
            <button className="tutor-button secondary" type="button" onClick={onClose}>
              Hủy
            </button>
            <button className="tutor-button" type="submit" disabled={sending || invalidSchedule}>
              {sending
                ? 'Đang lưu…'
                : editing
                  ? 'Lưu thay đổi'
                  : proposal?.status === 'CANCELLED'
                    ? 'Gửi lại đề xuất'
                    : 'Gửi đề xuất'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function parseAvailableTimes(schedule?: string | null): AvailableTime[] {
  const pattern =
    /(Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday)\s*,?\s*(\d{2}:\d{2})\s*[–-]\s*(\d{2}:\d{2})/gi;
  const parsed = [...(schedule ?? '').matchAll(pattern)].map((match) => ({
    day: match[1],
    start: match[2],
    end: match[3]
  }));
  return parsed.length ? parsed : [{ day: 'Monday', start: '16:00', end: '17:00' }];
}

async function loadTutorLessons(signal: AbortSignal) {
  try {
    return await getTutorLessons(signal);
  } catch {
    const contracts = await getTutorContracts(signal);
    const pages = await Promise.all(contracts.content.map((contract) => getTutorContractLessons(contract.id, signal)));
    return {
      content: pages.flatMap((page) => page.content)
    };
  }
}

function weekdayFromDate(value: string) {
  return weekdays[new Date(`${value}T12:00:00`).getDay()];
}

function formatLessonConflict(lesson: TutorLesson) {
  const [year, month, day] = lesson.date.split('-');
  return `${day}/${month}/${year} ${lesson.startTime.slice(0, 5)} - ${lesson.endTime.slice(0, 5)}`;
}
