import type { AuthUser, EmployeeJobFunction } from '../../types/auth';

export interface AdminModule {
  to: string;
  label: string;
  /** Employee functions that may open the module; administrators open every module. Empty: administrators only. */
  functions: readonly EmployeeJobFunction[];
}

const CUSTOMER_SERVICE = ['CUSTOMER_SERVICE'] as const;
const ACCOUNTANT = ['ACCOUNTANT'] as const;

/** Mirrors the backend's EmployeeFunctionAuthorizationFilter so the menu only offers modules the API allows. */
export const adminNavigation: { label: string; items: AdminModule[] }[] = [
  {
    label: 'Quản lý',
    items: [
      { to: '/admin/students', label: 'Học viên', functions: CUSTOMER_SERVICE },
      { to: '/admin/tutors', label: 'Gia sư', functions: CUSTOMER_SERVICE },
      { to: '/admin/notifications', label: 'Thông báo', functions: CUSTOMER_SERVICE },
      { to: '/admin/employees', label: 'Nhân viên', functions: [] }
    ]
  },
  {
    label: 'Kiểm duyệt',
    items: [
      { to: '/admin/tutor-profiles', label: 'Duyệt hồ sơ gia sư', functions: CUSTOMER_SERVICE },
      { to: '/admin/teaching-requests', label: 'Duyệt yêu cầu dạy', functions: CUSTOMER_SERVICE },
      { to: '/admin/studying-requests', label: 'Duyệt yêu cầu học', functions: CUSTOMER_SERVICE }
    ]
  },
  {
    label: 'Hỗ trợ',
    items: [
      { to: '/admin/refund-requests', label: 'Yêu cầu hoàn tiền', functions: ACCOUNTANT },
      { to: '/admin/complaints', label: 'Khiếu nại', functions: CUSTOMER_SERVICE },
      { to: '/admin/support-requests', label: 'Yêu cầu hỗ trợ', functions: CUSTOMER_SERVICE },
      { to: '/admin/accounts', label: 'Tài khoản người dùng', functions: CUSTOMER_SERVICE }
    ]
  },
  {
    // Trợ lý hỗ trợ: chỉ ADMIN quản lý kho tri thức (docs/support-chatbot/01 mục 1.3, 07 mục 7.1).
    label: 'Trợ lý',
    items: [
      { to: '/admin/assistant/documents', label: 'Kho tri thức', functions: [] },
      { to: '/admin/assistant/unanswered', label: 'Câu hỏi chưa trả lời', functions: [] },
      { to: '/admin/assistant/stats', label: 'Thống kê trợ lý', functions: [] }
    ]
  }
];

const modules = adminNavigation.flatMap((group) => group.items);

export function canOpenModule(user: AuthUser | null, module: AdminModule) {
  if (user?.role === 'ADMIN') return true;
  const jobFunction = user?.profile?.jobFunction;
  return user?.role === 'EMPLOYEE' && Boolean(jobFunction) && module.functions.includes(jobFunction!);
}

/** The module a path belongs to, e.g. /admin/tutor-profiles/certificates belongs to /admin/tutor-profiles. */
export function moduleForPath(pathname: string) {
  return modules.find((module) => pathname === module.to || pathname.startsWith(`${module.to}/`));
}

export function firstOpenModule(user: AuthUser | null) {
  return modules.find((module) => canOpenModule(user, module));
}

export function modulesForFunction(jobFunction: EmployeeJobFunction) {
  return modules.filter((module) => module.functions.includes(jobFunction)).map((module) => module.label);
}
