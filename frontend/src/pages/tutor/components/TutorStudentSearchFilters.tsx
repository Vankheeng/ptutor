import { useState } from 'react';
import { Icon } from '../../../components/ui/Icon';
import { useLocationOptions } from '../../../hooks/useLocationOptions';
import { useStudyCatalogOptions } from '../../../hooks/useStudyCatalogOptions';

export interface TutorStudentFilters {
  search: string;
  learningMode: 'ALL' | 'ONLINE' | 'OFFLINE';
  provinceId: string;
  provinceName: string;
  districtId: string;
  subjectId: string;
  gradeId: string;
  minBudget: string;
  maxBudget: string;
  scheduleSlots: { dayOfWeek: string; startTime: string; endTime: string }[];
}

interface Props {
  filters: TutorStudentFilters;
  onChange: (next: TutorStudentFilters) => void;
  onSearch: () => void;
}

const weekdays = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];

export function TutorStudentSearchFilters({ filters, onChange, onSearch }: Props) {
  const [showAdvanced, setShowAdvanced] = useState(false);
  const { provinces, districts } = useLocationOptions(filters.provinceId);
  const { subjects, grades } = useStudyCatalogOptions();
  const update = (values: Partial<TutorStudentFilters>) => onChange({ ...filters, ...values });

  return (
    <div className="tutor-student-filter-panel">
      <div className="tutor-student-search-basic">
        <label className="tutor-search">
          <Icon name="search" size={18} />
          <input
            type="search"
            value={filters.search}
            onChange={(event) => update({ search: event.target.value })}
            placeholder="Tìm theo tên, môn học hoặc lớp"
            aria-label="Tìm theo tên học viên, môn học hoặc lớp"
          />
        </label>
        <label>
          Môn học
          <select value={filters.subjectId} onChange={(event) => update({ subjectId: event.target.value })}>
            <option value="">Tất cả môn học</option>
            {subjects.map((subject) => <option key={subject.id} value={subject.id}>{subject.name}</option>)}
          </select>
        </label>
        <label>
          Khối lớp
          <select value={filters.gradeId} onChange={(event) => update({ gradeId: event.target.value })}>
            <option value="">Tất cả khối lớp</option>
            {grades.map((grade) => <option key={grade.id} value={grade.id}>{grade.name}</option>)}
          </select>
        </label>
        <label>
          Hình thức học
          <select
            value={filters.learningMode}
            onChange={(event) => update({ learningMode: event.target.value as TutorStudentFilters['learningMode'] })}
          >
            <option value="ALL">Tất cả</option>
            <option value="ONLINE">Trực tuyến</option>
            <option value="OFFLINE">Trực tiếp</option>
          </select>
        </label>
        <label>
          Tỉnh / thành phố
          <select
            value={filters.provinceId}
            onChange={(event) => {
              const provinceId = event.target.value;
              update({
                provinceId,
                provinceName: provinces.find((province) => province.id === provinceId)?.name ?? '',
                districtId: ''
              });
            }}
          >
            <option value="">Tất cả tỉnh / thành</option>
            {provinces.map((province) => <option key={province.id} value={province.id}>{province.name}</option>)}
          </select>
        </label>
        <label>
          Quận / huyện
          <select
            value={filters.districtId}
            disabled={!filters.provinceId}
            onChange={(event) => update({ districtId: event.target.value })}
          >
            <option value="">Tất cả quận / huyện</option>
            {districts.map((district) => <option key={district.id} value={district.id}>{district.name}</option>)}
          </select>
        </label>
        <button
          className="tutor-student-advanced-toggle"
          type="button"
          aria-expanded={showAdvanced}
          onClick={() => setShowAdvanced((visible) => !visible)}
        >
          <Icon name="dashboard" size={15} />
          {showAdvanced ? 'Ẩn bộ lọc nâng cao' : 'Lọc nâng cao'}
        </button>
      </div>

      {showAdvanced && (
        <div className="tutor-student-search-advanced">
          <fieldset className="tutor-schedule-filter">
            <legend>Lịch mong muốn</legend>
            {filters.scheduleSlots.map((slot, index) => (
              <div className="tutor-schedule-filter-row" key={index}>
                <select
                  aria-label={`Ngày khung giờ ${index + 1}`}
                  value={slot.dayOfWeek}
                  onChange={(event) => update({
                    scheduleSlots: filters.scheduleSlots.map((item, current) => current === index ? { ...item, dayOfWeek: event.target.value } : item)
                  })}
                >
                  <option value="">Chọn ngày</option>
                  {weekdays.map((day, dayIndex) => <option key={day} value={String(dayIndex + 1)}>{day}</option>)}
                </select>
                <input
                  aria-label={`Giờ bắt đầu khung ${index + 1}`}
                  type="time"
                  value={slot.startTime}
                  onChange={(event) => update({
                    scheduleSlots: filters.scheduleSlots.map((item, current) => current === index ? { ...item, startTime: event.target.value } : item)
                  })}
                />
                <input
                  aria-label={`Giờ kết thúc khung ${index + 1}`}
                  type="time"
                  value={slot.endTime}
                  onChange={(event) => update({
                    scheduleSlots: filters.scheduleSlots.map((item, current) => current === index ? { ...item, endTime: event.target.value } : item)
                  })}
                />
                <button
                  className="tutor-schedule-filter-remove"
                  type="button"
                  aria-label={`Xóa khung giờ ${index + 1}`}
                  disabled={filters.scheduleSlots.length === 1}
                  onClick={() => update({ scheduleSlots: filters.scheduleSlots.filter((_, current) => current !== index) })}
                >
                  ×
                </button>
              </div>
            ))}
            <button
              className="tutor-schedule-filter-add"
              type="button"
              onClick={() => update({ scheduleSlots: [...filters.scheduleSlots, { dayOfWeek: '', startTime: '', endTime: '' }] })}
            >
              + Thêm ngày / giờ
            </button>
          </fieldset>
          <label>
            Giá từ
            <input type="number" min="0" value={filters.minBudget} onChange={(event) => update({ minBudget: event.target.value })} placeholder="₫ tối thiểu" />
          </label>
          <label>
            Giá đến
            <input type="number" min="0" value={filters.maxBudget} onChange={(event) => update({ maxBudget: event.target.value })} placeholder="₫ tối đa" />
          </label>
        </div>
      )}
      <div className="tutor-student-filter-actions">
        <button className="tutor-button" type="button" onClick={onSearch}>
          Tìm kiếm
        </button>
      </div>
    </div>
  );
}
