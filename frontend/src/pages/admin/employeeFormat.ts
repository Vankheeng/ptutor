import type { EmployeeJobFunction, EmployeeStatus } from '../../services/adminEmployeeApi';
import { fullName, type StatusMeta } from './adminFormat';

export const REASON_MIN = 10;
export const REASON_MAX = 1000;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

export const employeeJobFunctions: readonly EmployeeJobFunction[] = ['CUSTOMER_SERVICE', 'ACCOUNTANT'];

export const jobFunctionMeta: Record<EmployeeJobFunction, StatusMeta> = {
  CUSTOMER_SERVICE: { label: 'Chăm sóc khách hàng', tone: 'brand' },
  ACCOUNTANT: { label: 'Kế toán', tone: 'info' }
};

/** The admin modules each function may open; mirrors the backend's EmployeeFunctionAuthorizationFilter. */
export const jobFunctionAccess: Record<EmployeeJobFunction, string[]> = {
  CUSTOMER_SERVICE: [
    'Khiếu nại',
    'Thông báo',
    'Duyệt hồ sơ gia sư',
    'Duyệt yêu cầu dạy',
    'Duyệt yêu cầu học',
    'Tài khoản người dùng'
  ],
  ACCOUNTANT: ['Yêu cầu hoàn tiền']
};

export const employeeStatusMeta: Record<EmployeeStatus, StatusMeta> = {
  ACTIVE: { label: 'Đang hoạt động', tone: 'success' },
  INACTIVE: { label: 'Đã vô hiệu hóa', tone: 'neutral' },
  BLOCKED: { label: 'Bị khóa', tone: 'danger' }
};

/** Vietnamese order: family name (lastName) first. */
export function employeeName(employee: { firstName: string | null; lastName: string | null; email: string }) {
  return fullName(employee.lastName, employee.firstName, employee.email);
}

const errorMessages: Record<string, string> = {
  EMPLOYEE_NOT_FOUND: 'Không tìm thấy nhân viên.',
  ADMIN_REQUIRED: 'Chỉ quản trị viên được quản lý nhân viên.',
  EMAIL_ALREADY_EXISTS: 'Email đã được sử dụng.',
  CITIZEN_ID_ALREADY_EXISTS: 'Số CCCD đã được sử dụng.',
  INVALID_PROVINCE: 'Tỉnh/thành phố không hợp lệ.',
  INVALID_DISTRICT: 'Quận/huyện không hợp lệ.',
  DISTRICT_PROVINCE_MISMATCH: 'Quận/huyện không thuộc tỉnh/thành phố đã chọn.',
  EMPLOYEE_HAS_OPEN_ASSIGNMENTS:
    'Nhân viên còn khiếu nại hoặc yêu cầu hoàn tiền đang xử lý. Hãy chuyển cho người khác trước.',
  EMPLOYEE_ALREADY_INACTIVE: 'Nhân viên đã bị vô hiệu hóa.',
  EMPLOYEE_NOT_ACTIVE: 'Chỉ vô hiệu hóa được nhân viên đang hoạt động.',
  EMPLOYEE_ALREADY_ACTIVE: 'Nhân viên đang hoạt động.',
  EMPLOYEE_REACTIVATION_NOT_ALLOWED: 'Chỉ kích hoạt lại được nhân viên đã bị vô hiệu hóa.',
  EMPLOYEE_FUNCTION_REQUIRED: 'Cần chọn chức năng trước khi kích hoạt lại.',
  INVALID_EMPLOYEE_ACCESS_REASON: `Lý do cần từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`
};

export function employeeErrorCode(error: unknown) {
  return error && typeof error === 'object' && 'code' in error ? String((error as { code: unknown }).code) : null;
}

export function employeeErrorMessage(error: unknown) {
  const code = employeeErrorCode(error);
  if (code && errorMessages[code]) return errorMessages[code];
  return error instanceof Error ? error.message : 'Không thể cập nhật nhân viên. Vui lòng thử lại.';
}
