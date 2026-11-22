import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { getGrades } from '../../services/gradeApi';
import { getAllDistricts, getDistricts, getProvinces } from '../../services/locationApi';
import {
  getStudentLessons,
  getStudyingRequestPaymentFee,
  type Availability,
  type LearningMode,
  type StudyingRequest,
  type StudyingRequestInput
} from '../../services/studentApi';
import { getSubjects } from '../../services/subjectApi';
import type { District, Grade, Province, Subject } from '../../types/api';
import { StudentAvailabilityEditor } from '../../components/student/StudentAvailabilityEditor';
import { RequestSubjectField } from '../common-user/components/RequestSubjectField';
import { RequestModeField } from '../common-user/components/RequestModeField';

type FormMode = 'create' | 'edit';
type PaymentMethod = 'wallet' | 'vnpay';

interface Props {
  mode: FormMode;
  initial?: StudyingRequest;
  locked?: boolean;
  onSubmit: (values: StudyingRequestInput, action: 'draft' | 'pay', paymentMethod: PaymentMethod) => Promise<void>;
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
  learningMode: LearningMode;
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
  learningMode: 'ONLINE',
  availabilities: [{ dayOfWeek: 0, startTime: '', endTime: '' }]
};

export function StudentStudyingRequestForm({ mode, initial, locked = false, onSubmit, onCancel }: Props) {
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
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('wallet');
  const [paymentFee, setPaymentFee] = useState<number>(100_000);
  const [scheduleWarnings, setScheduleWarnings] = useState<string[]>([]);

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
        if (active) setCatalogError(message(caught, 'Không thể tải danh mục môn học, khối lớp và địa chỉ.'));
      });
    return () => {
      active = false;
    };
  }, [mode, initial?.districtId]);

  useEffect(() => {
    if (mode !== 'create') return;
    let active = true;
    getStudyingRequestPaymentFee()
      .then((fee) => {
        if (active) setPaymentFee(fee);
      })
      .catch(() => {
        if (active) setPaymentFee(100_000);
      });
    return () => {
      active = false;
    };
  }, [mode]);

  useEffect(() => {
    if (!state.provinceId) return;
    let active = true;
    getDistricts(state.provinceId)
      .then((items) => {
        if (active) setDistrictCatalog({ provinceId: state.provinceId, items });
      })
      .catch((caught: unknown) => {
        if (active) setCatalogError(message(caught, 'Không thể tải danh sách quận/huyện.'));
      });
    return () => {
      active = false;
    };
  }, [state.provinceId]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setState((current) => ({ ...current, [key]: value }));

  useEffect(() => {
    const controller = new AbortController();
    void getStudentLessons(controller.signal)
      .then((result) => {
        const warnings = result.content.flatMap((lesson) => {
          if (lesson.status === 'CANCELLED') return [];
          const weekday = new Date(`${lesson.date}T00:00:00`).getDay() || 7;
          const conflict = state.availabilities.some(
            (slot) =>
              slot.dayOfWeek === weekday &&
              slot.startTime &&
              slot.endTime &&
              slot.startTime < lesson.endTime.slice(0, 5) &&
              slot.endTime > lesson.startTime.slice(0, 5)
          );
          return conflict
            ? [
                `Bạn đang có buổi học "${lesson.title || 'Buổi học'}" trùng khung giờ ${lesson.date} ${lesson.startTime.slice(0, 5)} - ${lesson.endTime.slice(0, 5)}.`
              ]
            : [];
        });
        setScheduleWarnings([...new Set(warnings)]);
      })
      .catch(() => setScheduleWarnings([]));
    return () => controller.abort();
  }, [state.availabilities]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    const action = submitter?.value === 'pay' ? 'pay' : 'draft';
    const selectedSubject = state.subjectId;
    if (selectedSubject === 'OTHER' && !state.note.trim()) {
      setError('Vui lòng nhập tên môn học tùy chỉnh trong phần Ghi chú.');
      return;
    }
    const minPrice = numberValue(state.minPrice);
    const maxPrice = numberValue(state.maxPrice);
    if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
      setError('Học phí tối thiểu không được lớn hơn học phí tối đa.');
      return;
    }
    const enteredSlots = state.availabilities.filter((slot) => slot.dayOfWeek || slot.startTime || slot.endTime);
    const invalidSlot = enteredSlots.find(
      (slot) => !slot.dayOfWeek || !slot.startTime || !slot.endTime || slot.endTime <= slot.startTime
    );
    if (invalidSlot) {
      setError('Mỗi khung giờ cần có đủ thứ, giờ bắt đầu và giờ kết thúc sau giờ bắt đầu.');
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
          learningMode: state.learningMode,
          availabilities: enteredSlots
        },
        action,
        paymentMethod
      );
    } catch (caught) {
      setError(message(caught, 'Không thể lưu yêu cầu tìm gia sư. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  };

  const activeSubjects = subjects.filter((item) => item.status === 'ACTIVE');
  const activeGrades = grades.filter((item) => item.status === 'ACTIVE');
  const districts = districtCatalog.provinceId === state.provinceId ? districtCatalog.items : [];
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
      <form className="student-form-grid student-studying-request-form" onSubmit={(event) => void submit(event)}>
        <fieldset className="student-form-fieldset" disabled={loading || locked}>
          <label className="student-field-full">
            Tiêu đề
            <input
              value={state.title}
              onChange={(event) => set('title', event.target.value)}
              maxLength={255}
              required
            />
          </label>
          <label className="student-field-full">
            Mô tả
            <textarea value={state.description} onChange={(event) => set('description', event.target.value)} rows={3} />
          </label>
          <RequestSubjectField
            subjects={activeSubjects}
            value={state.subjectId}
            customValue={state.note}
            required={mode === 'create'}
            customLabel={mode === 'create' ? 'Môn học tự nhập' : 'Môn học tự nhập / ghi chú'}
            onValueChange={(value) =>
              setState((current) => ({ ...current, subjectId: value, note: value === 'OTHER' ? current.note : '' }))
            }
            onCustomValueChange={(value) => set('note', value)}
          />
          <label>
            Khối lớp
            <select value={state.gradeId} onChange={(event) => set('gradeId', event.target.value)} required>
              <option value="" disabled>
                Chọn khối lớp
              </option>
              {activeGrades.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <RequestModeField value={state.learningMode} onChange={(value) => set('learningMode', value)} />
          <label>
            Tỉnh/thành phố
            <select
              value={state.provinceId}
              onChange={(event) =>
                setState((current) => ({ ...current, provinceId: event.target.value, districtId: '' }))
              }
            >
              <option value=""></option>
              {provinces.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Quận/huyện
            <select
              value={state.districtId}
              onChange={(event) => set('districtId', event.target.value)}
              disabled={!state.provinceId}
            >
              <option value=""></option>
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
            Học phí tối thiểu (VNĐ)
            <input
              value={state.minPrice}
              onChange={(event) => set('minPrice', event.target.value)}
              type="number"
              min="0"
              step="1000"
            />
          </label>
          <label>
            Học phí tối đa (VNĐ)
            <input
              value={state.maxPrice}
              onChange={(event) => set('maxPrice', event.target.value)}
              type="number"
              min="0"
              step="1000"
            />
          </label>
          <StudentAvailabilityEditor
            title="Khung giờ có thể học (không bắt buộc)"
            values={state.availabilities}
            onChange={(availabilities) => set('availabilities', availabilities)}
          />
          {scheduleWarnings.length > 0 && (
            <div className="tutor-schedule-warning student-field-full" role="status">
              <strong>Lịch có thể bị trùng</strong>
              {scheduleWarnings.map((warning) => (
                <div key={warning}>{warning}</div>
              ))}
              <div>Bạn vẫn có thể lưu yêu cầu vì chưa xác định ngày bắt đầu hợp đồng.</div>
            </div>
          )}
        </fieldset>
        {mode === 'create' && (
          <section className="student-field-full student-studying-request-payment">
            <div className="student-studying-request-payment-heading">
              <span className="student-studying-request-payment-icon" aria-hidden="true">
                $
              </span>
              <div>
                <h2>Thanh toán phí đăng bài</h2>
              </div>
              <strong className="student-studying-request-payment-price">
                {paymentFee.toLocaleString('vi-VN')} VNĐ
              </strong>
            </div>
            <p className="student-studying-request-payment-policy">
              Phí đăng bài không được hoàn lại sau khi thanh toán. Yêu cầu có môn học chưa có trong danh mục sẽ chờ
              duyệt; chỉ trường hợp này nếu bị quản trị viên từ chối do không hợp lệ mới được hoàn phí.
            </p>
            <label>
              Phương thức thanh toán
              <select
                value={paymentMethod}
                onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}
                disabled={loading}
              >
                <option value="wallet">Ví PTutor</option>
                <option value="vnpay">VNPay</option>
              </select>
            </label>
          </section>
        )}
        <div className="student-form-actions">
          <button className="student-button secondary" type="button" onClick={onCancel} disabled={loading}>
            Hủy
          </button>
          <button className="student-button draft" name="action" value="draft" type="submit" disabled={loading}>
            {mode === 'create' ? 'Tạo bản nháp' : 'Lưu thay đổi'}
          </button>
          {mode === 'create' && (
            <button className="student-button draft" name="action" value="pay" type="submit" disabled={loading}>
              Tạo và thanh toán
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
    learningMode: request.learningMode,
    availabilities: (request.availabilities ?? []).map((availability) => ({
      ...availability,
      startTime: timeSelectValue(availability.startTime),
      endTime: timeSelectValue(availability.endTime)
    }))
  };
}
function timeSelectValue(value: string | null | undefined) {
  if (!value) return '';
  const match = /^(\d{2}:\d{2})/.exec(value.trim());
  return match?.[1] ?? '';
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
