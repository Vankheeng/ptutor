import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { getGrades } from '../../services/gradeApi';
import { getAllDistricts, getDistricts, getProvinces } from '../../services/locationApi';
import {
  getStudentProfile,
  type Availability,
  type LearningMode,
  type StudyingRequest,
  type StudyingRequestInput
} from '../../services/studentApi';
import { getSubjects } from '../../services/subjectApi';
import type { District, Grade, Province, Subject } from '../../types/api';
import { StudentAvailabilityEditor } from '../../components/student/StudentAvailabilityEditor';

type FormMode = 'create' | 'edit';

interface Props {
  mode: FormMode;
  initial?: StudyingRequest;
  onSubmit: (values: StudyingRequestInput, action: 'draft' | 'pay') => Promise<void>;
  onCancel: () => void;
}

interface FormState {
  subjectId: string;
  gradeId: string;
  title: string;
  description: string;
  note: string;
  provinceId: string;
  districtId: string;
  detailAddress: string;
  minPrice: string;
  maxPrice: string;
  learningGoals: string;
  learningMode: LearningMode;
  preferredSchedule: string;
  availabilities: Availability[];
}

const emptyState: FormState = {
  subjectId: '',
  gradeId: '',
  title: '',
  description: '',
  note: '',
  provinceId: '',
  districtId: '',
  detailAddress: '',
  minPrice: '',
  maxPrice: '',
  learningGoals: '',
  learningMode: 'ONLINE',
  preferredSchedule: '',
  availabilities: [{ dayOfWeek: 0, startTime: '', endTime: '' }]
};

export function StudentStudyingRequestForm({ mode, initial, onSubmit, onCancel }: Props) {
  const [state, setState] = useState<FormState>(() => (initial ? fromRequest(initial) : emptyState));
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districtCatalog, setDistrictCatalog] = useState<{ provinceId: string; items: District[] }>({
    provinceId: '',
    items: []
  });
  const [catalogError, setCatalogError] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const initializedProfile = useRef(false);

  useEffect(() => {
    let active = true;
    Promise.all([
      getSubjects(),
      getGrades(),
      getProvinces(),
      mode === 'edit' && initial?.districtId ? getAllDistricts() : Promise.resolve([] as District[])
    ])
      .then(([subjectData, gradeData, provinceData, allDistrictData]) => {
        if (!active) return;
        setSubjects(subjectData);
        setGrades(gradeData);
        setProvinces(provinceData);
        if (mode === 'edit' && initial?.districtId) {
          const district = allDistrictData.find((item) => item.id === initial.districtId);
          if (district)
            setState((current) => ({ ...current, provinceId: district.provinceId, districtId: district.id }));
        }
      })
      .catch((caught: unknown) => {
        if (active) setCatalogError(message(caught, 'Không tải được các lựa chọn của biểu mẫu.'));
      });
    if (mode === 'create' && !initializedProfile.current) {
      getStudentProfile()
        .then((profile) => {
          if (!active || initializedProfile.current) return;
          initializedProfile.current = true;
          setState((current) => ({
            ...current,
            provinceId: current.provinceId || profile.address?.provinceId || '',
            districtId: current.districtId || profile.address?.districtId || '',
            detailAddress: current.detailAddress || profile.address?.detailAddress || ''
          }));
        })
        .catch(() => {
          initializedProfile.current = true;
        });
    }
    return () => {
      active = false;
    };
  }, [mode, initial?.districtId]);

  useEffect(() => {
    if (!state.provinceId) return;
    let active = true;
    getDistricts(state.provinceId)
      .then((items) => {
        if (active) setDistrictCatalog({ provinceId: state.provinceId, items });
      })
      .catch((caught: unknown) => {
        if (active) setCatalogError(message(caught, 'Không tải được danh sách quận / huyện.'));
      });
    return () => {
      active = false;
    };
  }, [state.provinceId]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setState((current) => ({ ...current, [key]: value }));

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    const action = submitter?.value === 'pay' ? 'pay' : 'draft';
    const selectedSubject = state.subjectId;
    if (selectedSubject === 'OTHER' && !state.note.trim()) {
      setError('Hãy nhập tên môn học vào ô "Môn học khác".');
      return;
    }
    const minPrice = numberValue(state.minPrice);
    const maxPrice = numberValue(state.maxPrice);
    if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
      setError('Giá tối thiểu không được lớn hơn giá tối đa.');
      return;
    }
    const enteredSlots = state.availabilities.filter((slot) => slot.dayOfWeek || slot.startTime || slot.endTime);
    const invalidSlot = enteredSlots.find(
      (slot) => !slot.dayOfWeek || !slot.startTime || !slot.endTime || slot.endTime <= slot.startTime
    );
    if (invalidSlot) {
      setError('Mỗi khung giờ rảnh cần có thứ, giờ bắt đầu và giờ kết thúc sau giờ bắt đầu.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await onSubmit(
        {
          ...(selectedSubject && selectedSubject !== 'OTHER' ? { subjectId: selectedSubject } : {}),
          gradeId: state.gradeId,
          title: state.title.trim(),
          description: optional(state.description),
          note: optional(state.note),
          districtId: state.districtId || undefined,
          detailAddress: optional(state.detailAddress),
          minPrice,
          maxPrice,
          learningGoals: optional(state.learningGoals),
          learningMode: state.learningMode,
          preferredSchedule: optional(state.preferredSchedule),
          availabilities: enteredSlots
        },
        action
      );
    } catch (caught) {
      setError(message(caught, 'Chưa lưu được tin tìm gia sư.'));
    } finally {
      setLoading(false);
    }
  };

  const activeSubjects = subjects.filter((item) => item.status === 'ACTIVE');
  const activeGrades = grades.filter((item) => item.status === 'ACTIVE');
  const districts = districtCatalog.provinceId === state.provinceId ? districtCatalog.items : [];
  const showCustomNote = state.subjectId === 'OTHER' || (mode === 'edit' && !initial?.subjectId);

  return (
    <section className="student-panel">
      {catalogError && (
        <div className="student-error" role="alert">
          {catalogError}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <form className="student-form-grid" onSubmit={(event) => void submit(event)}>
        <label className="student-field-full">
          Tiêu đề
          <input value={state.title} onChange={(event) => set('title', event.target.value)} maxLength={255} required />
        </label>
        <label className="student-field-full">
          Mô tả
          <textarea value={state.description} onChange={(event) => set('description', event.target.value)} rows={3} />
        </label>
        <label>
          Môn học
          <select
            value={state.subjectId}
            onChange={(event) => {
              const value = event.target.value;
              setState((current) => ({ ...current, subjectId: value, note: value === 'OTHER' ? current.note : '' }));
            }}
            required={mode === 'create'}
          >
            {mode === 'create' && (
              <option value="" disabled>
                Chọn môn học
              </option>
            )}
            {mode === 'edit' && !initial?.subjectId && <option value="OTHER">Môn khác (tự nhập)</option>}
            {activeSubjects.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
            {mode === 'create' && <option value="OTHER">Môn khác</option>}
          </select>
        </label>
        {showCustomNote && (
          <label>
            {mode === 'create' ? 'Môn học khác' : 'Môn học khác / ghi chú'}
            <input value={state.note} onChange={(event) => set('note', event.target.value)} maxLength={255} required />
          </label>
        )}
        <label>
          Lớp
          <select value={state.gradeId} onChange={(event) => set('gradeId', event.target.value)} required>
            <option value="" disabled>
              Chọn lớp
            </option>
            {activeGrades.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hình thức học
          <select
            value={state.learningMode}
            onChange={(event) => set('learningMode', event.target.value as LearningMode)}
          >
            <option value="ONLINE">Online</option>
            <option value="OFFLINE">Trực tiếp</option>
          </select>
        </label>
        <label>
          Tỉnh / thành
          <select
            value={state.provinceId}
            onChange={(event) =>
              setState((current) => ({ ...current, provinceId: event.target.value, districtId: '' }))
            }
          >
            <option value="">Chọn tỉnh / thành</option>
            {provinces.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Quận / huyện
          <select
            value={state.districtId}
            onChange={(event) => set('districtId', event.target.value)}
            disabled={!state.provinceId}
          >
            <option value="">Chọn quận / huyện</option>
            {districts.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="student-field-full">
          Địa chỉ chi tiết
          <input
            value={state.detailAddress}
            onChange={(event) => set('detailAddress', event.target.value)}
            maxLength={255}
          />
        </label>
        <label>
          Học phí tối thiểu (đồng)
          <input
            value={state.minPrice}
            onChange={(event) => set('minPrice', event.target.value)}
            type="number"
            min="0"
            step="1000"
          />
        </label>
        <label>
          Học phí tối đa (đồng)
          <input
            value={state.maxPrice}
            onChange={(event) => set('maxPrice', event.target.value)}
            type="number"
            min="0"
            step="1000"
          />
        </label>
        <label className="student-field-full">
          Mục tiêu học
          <textarea
            value={state.learningGoals}
            onChange={(event) => set('learningGoals', event.target.value)}
            rows={3}
          />
        </label>
        <label className="student-field-full">
          Lịch học mong muốn
          <input value={state.preferredSchedule} onChange={(event) => set('preferredSchedule', event.target.value)} />
        </label>
        <StudentAvailabilityEditor
          values={state.availabilities}
          onChange={(availabilities) => set('availabilities', availabilities)}
        />
        <div className="student-form-actions">
          <button className="student-button danger" type="button" onClick={onCancel} disabled={loading}>
            Huỷ
          </button>
          <button className="student-button draft" name="action" value="draft" type="submit" disabled={loading}>
            {mode === 'create' ? 'Lưu bản nháp' : 'Lưu thay đổi'}
          </button>
          {mode === 'create' && (
            <button className="student-button payment" name="action" value="pay" type="submit" disabled={loading}>
              Lưu và thanh toán
            </button>
          )}
        </div>
      </form>
    </section>
  );
}

function fromRequest(request: StudyingRequest): FormState {
  return {
    subjectId: request.subjectId ?? 'OTHER',
    gradeId: request.gradeId,
    title: request.title,
    description: request.description ?? '',
    note: request.note ?? '',
    provinceId: '',
    districtId: request.districtId ?? '',
    detailAddress: request.detailAddress ?? '',
    minPrice: request.minPrice?.toString() ?? '',
    maxPrice: request.maxPrice?.toString() ?? '',
    learningGoals: request.learningGoals ?? '',
    learningMode: request.learningMode,
    preferredSchedule: request.preferredSchedule ?? '',
    availabilities: request.availabilities ?? []
  };
}
function optional(value: string) {
  return value.trim() || undefined;
}
function numberValue(value: string) {
  if (!value) return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}
function message(caught: unknown, fallback: string) {
  return caught instanceof ApiError || caught instanceof Error ? caught.message : fallback;
}
