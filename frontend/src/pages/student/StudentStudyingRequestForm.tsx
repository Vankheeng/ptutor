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
        if (active) setCatalogError(message(caught, 'Could not load form options.'));
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
        if (active) setCatalogError(message(caught, 'Could not load districts.'));
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
      setError('Enter the custom subject in the Note field.');
      return;
    }
    const minPrice = numberValue(state.minPrice);
    const maxPrice = numberValue(state.maxPrice);
    if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
      setError('Minimum price must not exceed maximum price.');
      return;
    }
    const enteredSlots = state.availabilities.filter((slot) => slot.dayOfWeek || slot.startTime || slot.endTime);
    const invalidSlot = enteredSlots.find(
      (slot) => !slot.dayOfWeek || !slot.startTime || !slot.endTime || slot.endTime <= slot.startTime
    );
    if (invalidSlot) {
      setError('Each availability slot needs a weekday, start time, and an end time after the start time.');
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
      setError(message(caught, 'Could not save this studying request.'));
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
          Title
          <input value={state.title} onChange={(event) => set('title', event.target.value)} maxLength={255} required />
        </label>
        <label className="student-field-full">
          Description
          <textarea value={state.description} onChange={(event) => set('description', event.target.value)} rows={3} />
        </label>
        <label>
          Subject
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
                Select a subject
              </option>
            )}
            {mode === 'edit' && !initial?.subjectId && <option value="OTHER">Other / custom subject</option>}
            {activeSubjects.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
            {mode === 'create' && <option value="OTHER">Other</option>}
          </select>
        </label>
        {showCustomNote && (
          <label>
            {mode === 'create' ? 'Custom subject' : 'Custom subject / note'}
            <input value={state.note} onChange={(event) => set('note', event.target.value)} maxLength={255} required />
          </label>
        )}
        <label>
          Grade
          <select value={state.gradeId} onChange={(event) => set('gradeId', event.target.value)} required>
            <option value="" disabled>
              Select your grade
            </option>
            {activeGrades.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Learning mode
          <select
            value={state.learningMode}
            onChange={(event) => set('learningMode', event.target.value as LearningMode)}
          >
            <option value="ONLINE">Online</option>
            <option value="OFFLINE">In person</option>
          </select>
        </label>
        <label>
          Province
          <select
            value={state.provinceId}
            onChange={(event) =>
              setState((current) => ({ ...current, provinceId: event.target.value, districtId: '' }))
            }
          >
            <option value="">Select a province</option>
            {provinces.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          District
          <select
            value={state.districtId}
            onChange={(event) => set('districtId', event.target.value)}
            disabled={!state.provinceId}
          >
            <option value="">Select a district</option>
            {districts.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="student-field-full">
          Detail address
          <input
            value={state.detailAddress}
            onChange={(event) => set('detailAddress', event.target.value)}
            maxLength={255}
          />
        </label>
        <label>
          Minimum price (VND)
          <input
            value={state.minPrice}
            onChange={(event) => set('minPrice', event.target.value)}
            type="number"
            min="0"
            step="1000"
          />
        </label>
        <label>
          Maximum price (VND)
          <input
            value={state.maxPrice}
            onChange={(event) => set('maxPrice', event.target.value)}
            type="number"
            min="0"
            step="1000"
          />
        </label>
        <label className="student-field-full">
          Learning goals
          <textarea
            value={state.learningGoals}
            onChange={(event) => set('learningGoals', event.target.value)}
            rows={3}
          />
        </label>
        <label className="student-field-full">
          Preferred schedule
          <input value={state.preferredSchedule} onChange={(event) => set('preferredSchedule', event.target.value)} />
        </label>
        <AvailabilityEditor
          values={state.availabilities}
          onChange={(availabilities) => set('availabilities', availabilities)}
        />
        <div className="student-form-actions">
          <button className="student-button danger" type="button" onClick={onCancel} disabled={loading}>
            Cancel
          </button>
          <button className="student-button draft" name="action" value="draft" type="submit" disabled={loading}>
            {mode === 'create' ? 'Create draft' : 'Save changes'}
          </button>
          {mode === 'create' && (
            <button className="student-button payment" name="action" value="pay" type="submit" disabled={loading}>
              Create & pay
            </button>
          )}
        </div>
      </form>
    </section>
  );
}

function AvailabilityEditor({
  values,
  onChange
}: {
  values: Availability[];
  onChange: (values: Availability[]) => void;
}) {
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
