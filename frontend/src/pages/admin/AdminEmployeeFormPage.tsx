import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  createEmployee,
  getEmployee,
  updateEmployee,
  type AdminEmployeeDetail,
  type EmployeeGender,
  type EmployeeJobFunction
} from '../../services/adminEmployeeApi';
import { ApiError } from '../../services/httpClient';
import { getDistricts, getProvinces } from '../../services/locationApi';
import type { District, Province } from '../../types/api';
import { AdminState } from './components/AdminUi';
import { EmployeeFunctionChangeDialog } from './components/EmployeeFunctionChangeDialog';
import { genderLabels } from './adminFormat';
import {
  PASSWORD_MAX,
  PASSWORD_MIN,
  employeeErrorCode,
  employeeErrorMessage,
  employeeJobFunctions,
  employeeName,
  jobFunctionMeta
} from './employeeFormat';

interface Draft {
  email: string;
  initialPassword: string;
  lastName: string;
  firstName: string;
  phone: string;
  dateOfBirth: string;
  gender: EmployeeGender | '';
  citizenId: string;
  provinceId: string;
  districtId: string;
  detailAddress: string;
  jobFunction: EmployeeJobFunction | '';
}

type Field = keyof Draft;
type Errors = Partial<Record<Field, string>>;

const genders: EmployeeGender[] = ['MALE', 'FEMALE', 'OTHER'];

const roleIcons: Record<EmployeeJobFunction, ReactNode> = {
  CUSTOMER_SERVICE: (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24">
      <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
      <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
    </svg>
  ),
  ACCOUNTANT: (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24">
      <rect x="4" y="2" width="16" height="20" rx="2" />
      <path d="M8 6h8M8 10h2M14 10h2M8 14h2M14 14h2M8 18h2M14 18h2" />
    </svg>
  )
};
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Backend error codes that belong to a single field. */
const fieldOfCode: Partial<Record<string, Field>> = {
  EMAIL_ALREADY_EXISTS: 'email',
  CITIZEN_ID_ALREADY_EXISTS: 'citizenId',
  INVALID_PROVINCE: 'provinceId',
  INVALID_DISTRICT: 'districtId',
  DISTRICT_PROVINCE_MISMATCH: 'districtId'
};

function todayValue() {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function toDraft(employee: AdminEmployeeDetail | null): Draft {
  return {
    email: employee?.email ?? '',
    initialPassword: '',
    lastName: employee?.lastName ?? '',
    firstName: employee?.firstName ?? '',
    phone: employee?.phone ?? '',
    dateOfBirth: employee?.dateOfBirth ?? '',
    gender: employee?.gender ?? '',
    citizenId: '',
    provinceId: employee?.address?.provinceId ?? '',
    districtId: employee?.address?.districtId ?? '',
    detailAddress: employee?.address?.detailAddress ?? '',
    jobFunction: employee?.jobFunction ?? ''
  };
}

function validate(draft: Draft, creating: boolean): Errors {
  const errors: Errors = {};
  const email = draft.email.trim();
  if (!email) errors.email = 'Vui lòng nhập email.';
  else if (!EMAIL_PATTERN.test(email)) errors.email = 'Email không hợp lệ.';
  if (creating && draft.initialPassword.length < PASSWORD_MIN)
    errors.initialPassword = `Mật khẩu cần ít nhất ${PASSWORD_MIN} ký tự.`;
  if (!draft.lastName.trim()) errors.lastName = 'Vui lòng nhập họ.';
  if (!draft.firstName.trim()) errors.firstName = 'Vui lòng nhập tên.';
  if (!draft.phone.trim()) errors.phone = 'Vui lòng nhập số điện thoại.';
  if (!draft.dateOfBirth) errors.dateOfBirth = 'Vui lòng chọn ngày sinh.';
  else if (draft.dateOfBirth > todayValue()) errors.dateOfBirth = 'Ngày sinh không hợp lệ.';
  if (!draft.gender) errors.gender = 'Vui lòng chọn giới tính.';
  const citizenId = draft.citizenId.trim();
  if (creating && !citizenId) errors.citizenId = 'Vui lòng nhập số CCCD.';
  else if (citizenId && !/^\d{12}$/.test(citizenId)) errors.citizenId = 'Số CCCD gồm đúng 12 chữ số.';
  if (!draft.provinceId) errors.provinceId = 'Vui lòng chọn tỉnh/thành phố.';
  if (!draft.districtId) errors.districtId = 'Vui lòng chọn quận/huyện.';
  if (!draft.jobFunction) errors.jobFunction = 'Vui lòng chọn chức năng.';
  return errors;
}

export function AdminEmployeeFormPage() {
  const { employeeId } = useParams();
  const load = useCallback(
    (signal: AbortSignal) => (employeeId ? getEmployee(employeeId, signal) : Promise.resolve(null)),
    [employeeId]
  );
  const { data, error, isLoading } = useResource(load);

  if (employeeId && !data) {
    return (
      <div className="admin-page">
        <Link className="admin-back" to="/admin/employees">
          ‹ Nhân viên
        </Link>
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }
  return <EmployeeForm key={data?.employeeId ?? 'new'} employee={data} />;
}

function EmployeeForm({ employee }: { employee: AdminEmployeeDetail | null }) {
  const navigate = useNavigate();
  const creating = !employee;
  const [draft, setDraft] = useState<Draft>(() => toDraft(employee));
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [confirmingFunction, setConfirmingFunction] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [loadedDistricts, setLoadedDistricts] = useState<{ provinceId: string; list: District[] }>({
    provinceId: '',
    list: []
  });
  const districts = loadedDistricts.provinceId === draft.provinceId ? loadedDistricts.list : [];
  const backTo = employee ? `/admin/employees/${employee.employeeId}` : '/admin/employees';

  useEffect(() => {
    getProvinces()
      .then(setProvinces)
      .catch(() => setFormError('Không tải được danh sách tỉnh/thành phố.'));
  }, []);

  useEffect(() => {
    const provinceId = draft.provinceId;
    if (!provinceId) return;
    let active = true;
    getDistricts(provinceId)
      .then((list) => active && setLoadedDistricts({ provinceId, list }))
      .catch(() => active && setFormError('Không tải được danh sách quận/huyện.'));
    return () => {
      active = false;
    };
  }, [draft.provinceId]);

  const change = <K extends Field>(key: K, value: Draft[K]) => {
    setDraft((current) => ({
      ...current,
      [key]: value,
      ...(key === 'provinceId' ? { districtId: '' } : {})
    }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const changingFunction = Boolean(employee && draft.jobFunction && draft.jobFunction !== employee.jobFunction);

  /** A change of job function is confirmed in a dialog that first hands over the employee's open work. */
  const save = async (functionChangeConfirmed = false) => {
    const found = validate(draft, creating);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    if (changingFunction && !functionChangeConfirmed) {
      setConfirmingFunction(true);
      return;
    }
    setBusy(true);
    setFormError(null);
    const citizenId = draft.citizenId.trim();
    const payload = {
      email: draft.email.trim(),
      lastName: draft.lastName.trim(),
      firstName: draft.firstName.trim(),
      phone: draft.phone.trim(),
      dateOfBirth: draft.dateOfBirth,
      gender: draft.gender as EmployeeGender,
      citizenId: citizenId || null,
      provinceId: draft.provinceId,
      districtId: draft.districtId,
      detailAddress: draft.detailAddress.trim() || null,
      jobFunction: draft.jobFunction as EmployeeJobFunction
    };
    try {
      const saved = employee
        ? await updateEmployee(employee.employeeId, payload)
        : await createEmployee({ ...payload, citizenId, initialPassword: draft.initialPassword });
      navigate(`/admin/employees/${saved.employeeId}`, {
        state: { message: employee ? 'Đã lưu thay đổi.' : 'Đã tạo tài khoản nhân viên.' }
      });
    } catch (saveError) {
      const code = employeeErrorCode(saveError);
      const field = code ? fieldOfCode[code] : undefined;
      if (field) {
        setErrors({ [field]: employeeErrorMessage(saveError) });
      } else if (saveError instanceof ApiError && Object.keys(saveError.fieldErrors).length > 0) {
        setErrors(saveError.fieldErrors as Errors);
        setFormError('Vui lòng kiểm tra lại thông tin.');
      } else {
        setFormError(employeeErrorMessage(saveError));
      }
      setConfirmingFunction(false);
      setBusy(false);
    }
  };

  const field = (key: Field, label: string, control: ReactNode, required = true) => (
    <div className="admin-field">
      <label htmlFor={`employee-${key}`}>
        {label} {required && <span className="admin-required">*</span>}
      </label>
      {control}
      {errors[key] && (
        <span id={`employee-${key}-error`} className="admin-field-error">
          {errors[key]}
        </span>
      )}
    </div>
  );

  const inputProps = (key: Field) => ({
    id: `employee-${key}`,
    'aria-invalid': Boolean(errors[key]),
    'aria-describedby': errors[key] ? `employee-${key}-error` : undefined
  });

  return (
    <div className="admin-page">
      <Link className="admin-back" to={backTo}>
        ‹ {employee ? employeeName(employee) : 'Nhân viên'}
      </Link>
      <h1 className="admin-title">{creating ? 'Thêm nhân viên' : 'Sửa thông tin nhân viên'}</h1>
      {formError && (
        <div className="admin-alert admin-alert-danger" role="alert">
          {formError}
        </div>
      )}
      <form
        className="admin-column-main"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <section className="admin-card admin-card-pad">
          <fieldset className="admin-choice-group">
            <legend>
              Chức năng <span className="admin-required">*</span>
            </legend>
            <div className="admin-choices">
              {employeeJobFunctions.map((value) => (
                <label key={value} className={`admin-employee-role${draft.jobFunction === value ? ' active' : ''}`}>
                  <input
                    type="radio"
                    name="employee-job-function"
                    checked={draft.jobFunction === value}
                    onChange={() => change('jobFunction', value)}
                  />
                  <span className={`admin-employee-role-icon admin-employee-role-${jobFunctionMeta[value].tone}`}>
                    {roleIcons[value]}
                  </span>
                  <strong>{jobFunctionMeta[value].label}</strong>
                </label>
              ))}
            </div>
            {errors.jobFunction && <span className="admin-field-error">{errors.jobFunction}</span>}
          </fieldset>
        </section>

        <section className="admin-card admin-card-pad">
          <h2>Tài khoản</h2>
          <div className="admin-form-grid">
            {field(
              'email',
              'Email',
              <input
                {...inputProps('email')}
                type="email"
                autoComplete="off"
                maxLength={255}
                value={draft.email}
                onChange={(event) => change('email', event.target.value)}
              />
            )}
            {field(
              'phone',
              'Số điện thoại',
              <input
                {...inputProps('phone')}
                type="tel"
                maxLength={20}
                value={draft.phone}
                onChange={(event) => change('phone', event.target.value)}
              />
            )}
            {creating &&
              field(
                'initialPassword',
                'Mật khẩu ban đầu',
                <div className="admin-password">
                  <input
                    {...inputProps('initialPassword')}
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    maxLength={PASSWORD_MAX}
                    value={draft.initialPassword}
                    onChange={(event) => change('initialPassword', event.target.value)}
                  />
                  <button
                    className="admin-icon-button"
                    type="button"
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((value) => !value)}
                  >
                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24">
                      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  </button>
                </div>
              )}
          </div>
        </section>

        <section className="admin-card admin-card-pad">
          <h2>Thông tin cá nhân</h2>
          <div className="admin-form-grid">
            {field(
              'lastName',
              'Họ',
              <input
                {...inputProps('lastName')}
                maxLength={100}
                value={draft.lastName}
                onChange={(event) => change('lastName', event.target.value)}
              />
            )}
            {field(
              'firstName',
              'Tên',
              <input
                {...inputProps('firstName')}
                maxLength={100}
                value={draft.firstName}
                onChange={(event) => change('firstName', event.target.value)}
              />
            )}
            {field(
              'dateOfBirth',
              'Ngày sinh',
              <input
                {...inputProps('dateOfBirth')}
                type="date"
                max={todayValue()}
                value={draft.dateOfBirth}
                onChange={(event) => change('dateOfBirth', event.target.value)}
              />
            )}
            <fieldset className="admin-choice-group admin-employee-gender">
              <legend>
                Giới tính <span className="admin-required">*</span>
              </legend>
              <div className="admin-choices admin-choices-compact">
                {genders.map((value) => (
                  <label key={value} className={draft.gender === value ? 'active' : undefined}>
                    <input
                      type="radio"
                      name="employee-gender"
                      checked={draft.gender === value}
                      onChange={() => change('gender', value)}
                    />
                    <strong>{genderLabels[value]}</strong>
                  </label>
                ))}
              </div>
              {errors.gender && <span className="admin-field-error">{errors.gender}</span>}
            </fieldset>
            {field(
              'citizenId',
              'Số CCCD',
              <input
                {...inputProps('citizenId')}
                inputMode="numeric"
                maxLength={12}
                placeholder={employee?.maskedCitizenId ?? undefined}
                value={draft.citizenId}
                onChange={(event) => change('citizenId', event.target.value.replace(/\D/g, ''))}
              />,
              creating
            )}
          </div>
        </section>

        <section className="admin-card admin-card-pad">
          <h2>Địa chỉ</h2>
          <div className="admin-form-grid">
            {field(
              'provinceId',
              'Tỉnh/thành phố',
              <select
                {...inputProps('provinceId')}
                value={draft.provinceId}
                onChange={(event) => change('provinceId', event.target.value)}
              >
                <option value="">Chọn</option>
                {provinces.map((province) => (
                  <option key={province.id} value={province.id}>
                    {province.name}
                  </option>
                ))}
              </select>
            )}
            {field(
              'districtId',
              'Quận/huyện',
              <select
                {...inputProps('districtId')}
                value={draft.districtId}
                disabled={!draft.provinceId}
                onChange={(event) => change('districtId', event.target.value)}
              >
                <option value="">Chọn</option>
                {districts.map((district) => (
                  <option key={district.id} value={district.id}>
                    {district.name}
                  </option>
                ))}
              </select>
            )}
            <div className="admin-form-grid-wide">
              {field(
                'detailAddress',
                'Địa chỉ chi tiết',
                <input
                  {...inputProps('detailAddress')}
                  maxLength={255}
                  value={draft.detailAddress}
                  onChange={(event) => change('detailAddress', event.target.value)}
                />,
                false
              )}
            </div>
          </div>
        </section>

        <div className="admin-action-bar">
          <Link className="admin-button secondary" to={backTo}>
            Hủy
          </Link>
          <button className="admin-button" type="submit" disabled={busy}>
            {creating ? 'Tạo nhân viên' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
      {confirmingFunction && employee && draft.jobFunction && (
        <EmployeeFunctionChangeDialog
          employee={employee}
          nextFunction={draft.jobFunction}
          onClose={() => setConfirmingFunction(false)}
          onConfirm={() => save(true)}
        />
      )}
    </div>
  );
}
