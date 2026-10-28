import { useEffect, useState, type FormEvent } from 'react';
import { getDistricts, getProvinces } from '../../../services/locationApi';
import type { District, Province } from '../../../types/api';
import type { TutorTeachingProposal } from '../../../services/tutorApi';

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
  { length: 48 },
  (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`
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
  proposal?: TutorTeachingProposal;
  editing: boolean;
  sending: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (values: TutorApplicationValues) => void;
}

export function TutorApplicationForm({
  subjectName,
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
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [provinceId, setProvinceId] = useState(proposal?.preferredProvinceId ?? '');
  const [districtId, setDistrictId] = useState(proposal?.preferredDistrictId ?? '');
  const [locationLoading, setLocationLoading] = useState((proposal?.teachingMode ?? requestMode) === 'OFFLINE');
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (teachingMode !== 'OFFLINE') return;
    let active = true;
    getProvinces()
      .then((items) => {
        if (active) setProvinces(items);
      })
      .catch(() => {
        if (active) setProvinces([]);
      })
      .finally(() => {
        if (active) setLocationLoading(false);
      });
    return () => {
      active = false;
    };
  }, [teachingMode]);

  useEffect(() => {
    if (teachingMode !== 'OFFLINE' || !provinceId) return;
    let active = true;
    getDistricts(provinceId)
      .then((items) => {
        if (active) setDistricts(items);
      })
      .catch(() => {
        if (active) setDistricts([]);
      });
    return () => {
      active = false;
    };
  }, [teachingMode, provinceId]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    if (availableTimes.some((slot) => slot.start >= slot.end)) {
      setValidationError('Giờ kết thúc phải sau giờ bắt đầu.');
      return;
    }
    if (teachingMode === 'OFFLINE' && (!provinceId || !districtId)) {
      setValidationError('Vui lòng chọn tỉnh/thành và quận/huyện để dạy trực tiếp.');
      return;
    }
    setValidationError('');
    onSubmit({
      proposedPrice: Number(data.get('proposedPrice')),
      teachingMode,
      preferredSchedule: availableTimes.map((slot) => `${slot.day}, ${slot.start}–${slot.end}`).join('; '),
      preferredProvinceId: teachingMode === 'OFFLINE' ? provinceId : null,
      preferredDistrictId: teachingMode === 'OFFLINE' ? districtId : null,
      message: String(data.get('message') ?? '')
    });
  };

  const addDay = () =>
    setAvailableTimes((current) => {
      const nextDay = weekdays.find((day) => !current.some((slot) => slot.day === day)) || 'Monday';
      return [...current, { day: nextDay, start: '16:00', end: '17:00' }];
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
        <span className="tutor-eyebrow">ĐỀ XUẤT CỦA BẠN</span>
        <h2 id="tutor-apply-title">
          {editing ? 'Chỉnh sửa đề xuất' : `Ứng tuyển dạy ${subjectName || 'môn học này'}`}
        </h2>
        <p className="tutor-modal-intro">Gửi mức học phí và thời gian bạn có thể dạy cho học viên.</p>
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
                if (mode === 'OFFLINE' && provinces.length === 0) setLocationLoading(true);
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
          </div>
          {teachingMode === 'OFFLINE' && (
            <div className="tutor-offline-location">
              <strong>Địa điểm dạy</strong>
              <div className="tutor-location-fields">
                <label>
                  Tỉnh/thành phố
                  <select
                    value={provinceId}
                    onChange={(event) => {
                      setProvinceId(event.target.value);
                      setDistrictId('');
                      setDistricts([]);
                    }}
                    required
                  >
                    <option value="">{locationLoading ? 'Đang tải tỉnh/thành…' : 'Chọn tỉnh/thành'}</option>
                    {provinces.map((province) => (
                      <option key={province.id} value={province.id}>
                        {province.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Quận/huyện
                  <select
                    value={districtId}
                    onChange={(event) => setDistrictId(event.target.value)}
                    disabled={!provinceId || districts.length === 0}
                    required
                  >
                    <option value="">{provinceId ? 'Chọn quận/huyện' : 'Chọn tỉnh/thành trước'}</option>
                    {districts.map((district) => (
                      <option key={district.id} value={district.id}>
                        {district.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
          )}
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
            <button className="tutor-button" type="submit" disabled={sending}>
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
