import type { AuthUser, EmployeeJobFunction } from '../../types/auth';

export interface AdminModule {
  to: string;
  label: string;
  icon: string;
  /** Employee functions that may open the module; administrators open every module. Empty: administrators only. */
  functions: readonly EmployeeJobFunction[];
}

const CUSTOMER_SERVICE = ['CUSTOMER_SERVICE'] as const;
const ACCOUNTANT = ['ACCOUNTANT'] as const;

/** Mirrors the backend's EmployeeFunctionAuthorizationFilter so the menu only offers modules the API allows. */
export const adminNavigation: { label: string; items: AdminModule[] }[] = [
  {
    label: 'Tổng quan',
    items: [{ to: '/admin/statistics', label: 'Thống kê', icon: 'chart', functions: [] }]
  },
  {
    label: 'Quản lý',
    items: [
      { to: '/admin/students', label: 'Học viên', icon: 'student', functions: CUSTOMER_SERVICE },
      { to: '/admin/tutors', label: 'Gia sư', icon: 'tutor', functions: CUSTOMER_SERVICE },
      { to: '/admin/contracts', label: 'Hợp đồng', icon: 'contract', functions: CUSTOMER_SERVICE },
      { to: '/admin/notifications', label: 'Thông báo', icon: 'bell', functions: CUSTOMER_SERVICE },
      { to: '/admin/employees', label: 'Nhân viên', icon: 'team', functions: [] }
    ]
  },
  {
    label: 'Kiểm duyệt',
    items: [
      { to: '/admin/tutor-profiles', label: 'Duyệt hồ sơ gia sư', icon: 'verify', functions: CUSTOMER_SERVICE },
      { to: '/admin/teaching-requests', label: 'Duyệt yêu cầu dạy', icon: 'teach', functions: CUSTOMER_SERVICE },
      { to: '/admin/studying-requests', label: 'Duyệt yêu cầu học', icon: 'book', functions: CUSTOMER_SERVICE }
    ]
  },
  {
    label: 'Hỗ trợ',
    items: [
      { to: '/admin/refund-requests', label: 'Yêu cầu hoàn tiền', icon: 'refund', functions: ACCOUNTANT },
      { to: '/admin/complaints', label: 'Khiếu nại', icon: 'support', functions: CUSTOMER_SERVICE }
    ]
  }
];

const modules = [
  ...adminNavigation.flatMap((group) => group.items),
  // Keep authorization for bookmarked account details while the actions live in student/tutor management.
  { to: '/admin/accounts', label: 'Tài khoản người dùng', icon: 'student', functions: CUSTOMER_SERVICE }
];

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
