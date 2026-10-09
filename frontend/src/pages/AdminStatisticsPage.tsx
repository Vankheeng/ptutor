import { FormEvent, useEffect, useState } from 'react';
import logo from '../assets/ptutor-logo.svg';
import { clearAdminSession, getAdminAccessToken } from '../services/authApi';
import { ApiError } from '../services/httpClient';
import { getStatisticsReport } from '../services/statisticsApi';
import type {
  StatisticsFilters,
  StatisticsGranularity,
  StatisticsMetric,
  StatisticsReport,
  StatisticsResponse,
  StatisticsSeriesItem
} from '../types/statistics';
import './AdminStatisticsPage.css';

interface ReportItem {
  id: StatisticsReport;
  label: string;
  description: string;
}

interface ReportGroup {
  label: string;
  items: ReportItem[];
}

const REPORT_GROUPS: ReportGroup[] = [
  {
    label: 'Tổng quan',
    items: [{ id: 'overview', label: 'Tổng quan', description: 'Bức tranh toàn hệ thống' }]
  },
  {
    label: 'Người dùng',
    items: [
      { id: 'students', label: 'Học viên', description: 'Tăng trưởng và chuyển đổi' },
      { id: 'tutors', label: 'Gia sư', description: 'Hồ sơ và hiệu suất' },
      { id: 'employees', label: 'Nhân viên', description: 'Hoạt động kiểm duyệt' }
    ]
  },
  {
    label: 'Kết nối & học tập',
    items: [
      { id: 'studying-requests', label: 'Nhu cầu học', description: 'Yêu cầu từ học viên' },
      { id: 'teaching-requests', label: 'Nhu cầu dạy', description: 'Đề xuất từ gia sư' },
      { id: 'matching', label: 'Ghép lớp', description: 'Hiệu quả kết nối' },
      { id: 'contracts', label: 'Hợp đồng', description: 'Ký kết và thực hiện' },
      { id: 'lessons', label: 'Buổi học', description: 'Tiến độ giảng dạy' }
    ]
  },
  {
    label: 'Tài chính',
    items: [
      { id: 'finance', label: 'Doanh thu', description: 'Tổng hợp dòng tiền' },
      { id: 'payments', label: 'Thanh toán', description: 'Giao dịch học phí' },
      { id: 'installments', label: 'Kỳ thanh toán', description: 'Tiến độ thu học phí' },
      { id: 'wallets', label: 'Ví điện tử', description: 'Số dư và biến động' },
      { id: 'withdrawals', label: 'Rút tiền', description: 'Yêu cầu và xử lý' }
    ]
  }
];

const ALL_REPORTS = REPORT_GROUPS.flatMap((group) => group.items);

const PRIMARY_METRICS: Record<StatisticsReport, string[]> = {
  overview: ['platform_revenue_gross', 'new_students', 'new_tutors', 'signed_contracts'],
  students: ['total_students', 'new_students', 'paying_students', 'tuition_volume'],
  tutors: ['total_tutors', 'verified_tutors', 'average_rating', 'tuition_volume'],
  'studying-requests': ['created_requests', 'matched_requests', 'cancelled_requests', 'average_maximum_price'],
  'teaching-requests': ['created_requests', 'matched_requests', 'cancelled_requests', 'average_maximum_price'],
  matching: ['offers', 'accepted_offers', 'acceptance_rate', 'average_proposed_price'],
  contracts: ['created_contracts', 'signed_contracts', 'active_contracts', 'signing_rate'],
  lessons: ['scheduled_lessons', 'completed_lessons', 'completion_rate', 'cancellation_rate'],
  finance: ['platform_revenue_gross', 'platform_revenue_current_net', 'tuition_volume', 'successful_payments'],
  payments: ['initiated_payments', 'successful_payments', 'success_rate', 'initiated_value'],
  installments: ['installments', 'paid_installments', 'pending_installments', 'collection_rate'],
  wallets: ['wallets', 'available_balance', 'pending_balance', 'transactions'],
  withdrawals: ['withdrawal_requests', 'requested_value', 'completed_withdrawals', 'completion_rate'],
  employees: ['employees', 'active_employees', 'reviewed_teaching_requests', 'reviewed_tutor_profiles']
};

const METRIC_REPORTS: Record<string, StatisticsReport> = {
  platform_revenue_gross: 'finance',
  tuition_volume: 'finance',
  successful_payments: 'payments',
  new_students: 'students',
  new_tutors: 'tutors',
  verified_tutors: 'tutors',
  studying_requests: 'studying-requests',
  teaching_requests: 'teaching-requests',
  accepted_matches: 'matching',
  signed_contracts: 'contracts',
  completed_lessons: 'lessons',
  pending_withdrawals: 'withdrawals'
};

const METRIC_LABELS: Record<string, string> = {
  platform_revenue_gross: 'Doanh thu nền tảng',
  platform_fees_currently_refunded: 'Phí đang hoàn trả',
  platform_revenue_current_net: 'Doanh thu ròng hiện tại',
  tuition_volume: 'Tổng giá trị học phí',
  successful_payments: 'Thanh toán thành công',
  average_successful_payment: 'Giá trị thanh toán TB',
  new_students: 'Học viên mới',
  total_students: 'Tổng học viên',
  students_with_studying_request: 'Có nhu cầu học',
  students_sending_offer: 'Đã gửi đề nghị',
  students_with_signed_contract: 'Đã ký hợp đồng',
  students_with_completed_lesson: 'Đã hoàn thành buổi học',
  paying_students: 'Học viên phát sinh học phí',
  new_tutors: 'Gia sư mới',
  total_tutors: 'Tổng gia sư',
  verified_tutors: 'Gia sư đã xác minh',
  tutors_with_signed_contract: 'Có hợp đồng đã ký',
  tutors_with_active_contract: 'Có hợp đồng hoạt động',
  tutors_with_completed_lesson: 'Đã hoàn thành buổi học',
  average_rating: 'Điểm đánh giá trung bình',
  average_recommendation_score: 'Điểm đề xuất trung bình',
  studying_requests: 'Nhu cầu học',
  teaching_requests: 'Nhu cầu dạy',
  accepted_matches: 'Ghép lớp thành công',
  created_requests: 'Yêu cầu được tạo',
  matched_requests: 'Yêu cầu đã ghép',
  cancelled_requests: 'Yêu cầu đã hủy',
  average_minimum_price: 'Mức giá tối thiểu TB',
  average_maximum_price: 'Mức giá tối đa TB',
  offers: 'Đề nghị ghép lớp',
  accepted_offers: 'Đề nghị được chấp nhận',
  rejected_offers: 'Đề nghị bị từ chối',
  cancelled_offers: 'Đề nghị đã hủy',
  acceptance_rate: 'Tỷ lệ chấp nhận',
  average_proposed_price: 'Mức giá đề xuất TB',
  signed_contracts: 'Hợp đồng đã ký',
  created_contracts: 'Hợp đồng được tạo',
  pending_contracts: 'Hợp đồng chờ ký',
  active_contracts: 'Hợp đồng đang hoạt động',
  completed_contracts: 'Hợp đồng hoàn thành',
  cancelled_contracts: 'Hợp đồng đã hủy',
  average_price: 'Giá trị trung bình',
  average_lessons: 'Số buổi trung bình',
  renewed_contracts: 'Hợp đồng gia hạn',
  signing_rate: 'Tỷ lệ ký kết',
  completed_lessons: 'Buổi học hoàn thành',
  scheduled_lessons: 'Buổi học đã lên lịch',
  cancelled_lessons: 'Buổi học đã hủy',
  pending_confirmation_lessons: 'Chờ xác nhận hoàn thành',
  completion_rate: 'Tỷ lệ hoàn thành',
  cancellation_rate: 'Tỷ lệ hủy',
  scheduled_hours: 'Số giờ đã lên lịch',
  completed_hours: 'Số giờ hoàn thành',
  average_duration_minutes: 'Thời lượng trung bình',
  overdue_scheduled_lessons: 'Buổi học quá hạn',
  initiated_payments: 'Thanh toán được khởi tạo',
  failed_payments: 'Thanh toán thất bại',
  pending_payments: 'Thanh toán chờ xử lý',
  currently_refunded_payments: 'Thanh toán đang hoàn trả',
  success_rate: 'Tỷ lệ thành công',
  initiated_value: 'Giá trị khởi tạo',
  average_payment_value: 'Giá trị thanh toán TB',
  installments: 'Tổng kỳ thanh toán',
  paid_installments: 'Kỳ đã thanh toán',
  pending_installments: 'Kỳ chờ thanh toán',
  cancelled_installments: 'Kỳ đã hủy',
  overdue_installments: 'Kỳ quá hạn',
  installment_value: 'Tổng giá trị các kỳ',
  paid_value: 'Giá trị đã thu',
  pending_value: 'Giá trị chờ thu',
  overdue_value: 'Giá trị quá hạn',
  collection_rate: 'Tỷ lệ thu học phí',
  wallets: 'Tổng số ví',
  available_balance: 'Số dư khả dụng',
  pending_balance: 'Số dư chờ xử lý',
  transactions: 'Giao dịch ví',
  credit_value: 'Tổng tiền vào',
  debit_value: 'Tổng tiền ra',
  completed_transactions: 'Giao dịch hoàn thành',
  pending_transactions: 'Giao dịch chờ xử lý',
  withdrawal_requests: 'Yêu cầu rút tiền',
  requested_value: 'Giá trị yêu cầu',
  completed_withdrawals: 'Lệnh rút hoàn thành',
  completed_value: 'Giá trị đã rút',
  pending_withdrawals: 'Lệnh rút chờ xử lý',
  average_withdrawal: 'Giá trị rút trung bình',
  average_review_hours: 'Thời gian duyệt TB',
  average_completion_hours: 'Thời gian hoàn tất TB',
  employees: 'Tổng nhân viên',
  active_employees: 'Nhân viên hoạt động',
  reviewed_teaching_requests: 'Yêu cầu dạy đã duyệt',
  reviewed_tutor_profiles: 'Hồ sơ gia sư đã duyệt',
  reviewed_withdrawals: 'Lệnh rút tiền đã duyệt',
  requests: 'Yêu cầu rút tiền',
  platformRevenue: 'Doanh thu nền tảng',
  tuitionVolume: 'Tổng học phí',
  signedContracts: 'Hợp đồng đã ký',
  newStudents: 'Học viên mới',
  newTutors: 'Gia sư mới',
  completedLessons: 'Buổi học hoàn thành',
  registrations: 'Đăng ký mới',
  created: 'Được tạo',
  signed: 'Đã ký',
  initiated: 'Được khởi tạo',
  successful: 'Thành công',
  lessons: 'Buổi học'
};

const BREAKDOWN_LABELS: Record<string, string> = {
  accountStatus: 'Trạng thái tài khoản',
  gender: 'Giới tính',
  ageGroup: 'Nhóm tuổi',
  province: 'Tỉnh / Thành phố',
  profileStatus: 'Trạng thái hồ sơ',
  experience: 'Kinh nghiệm',
  status: 'Trạng thái',
  mode: 'Hình thức',
  subject: 'Môn học',
  grade: 'Khối lớp',
  direction: 'Chiều đề nghị',
  teachingMode: 'Hình thức dạy',
  paymentPeriod: 'Chu kỳ thanh toán',
  dayOfWeek: 'Ngày trong tuần',
  paymentType: 'Loại thanh toán',
  paymentMethod: 'Phương thức thanh toán',
  transactionType: 'Loại giao dịch',
  purpose: 'Mục đích giao dịch',
  bank: 'Ngân hàng',
  jobFunction: 'Vai trò nhân viên'
};

const VALUE_LABELS: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  LOCKED: 'Đã khóa',
  PENDING: 'Chờ xử lý',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
  ACCEPTED: 'Đã chấp nhận',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Thất bại',
  SUCCESS: 'Thành công',
  PAID: 'Đã thanh toán',
  REFUNDED: 'Đã hoàn tiền',
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
  ONLINE: 'Trực tuyến',
  OFFLINE: 'Trực tiếp',
  HYBRID: 'Kết hợp',
  STUDENT_TO_TUTOR: 'Học viên → Gia sư',
  TUTOR_TO_STUDENT: 'Gia sư → Học viên',
  MONDAY: 'Thứ Hai',
  TUESDAY: 'Thứ Ba',
  WEDNESDAY: 'Thứ Tư',
  THURSDAY: 'Thứ Năm',
  FRIDAY: 'Thứ Sáu',
  SATURDAY: 'Thứ Bảy',
  SUNDAY: 'Chủ Nhật'
};

const CURRENCY_KEYS = new Set([
  'platform_revenue_gross',
  'platform_fees_currently_refunded',
  'platform_revenue_current_net',
  'tuition_volume',
  'average_successful_payment',
  'average_minimum_price',
  'average_maximum_price',
  'average_proposed_price',
  'average_price',
  'initiated_value',
  'average_payment_value',
  'installment_value',
  'paid_value',
  'pending_value',
  'overdue_value',
  'available_balance',
  'pending_balance',
  'credit_value',
  'debit_value',
  'requested_value',
  'completed_value',
  'average_withdrawal',
  'platformRevenue',
  'tuitionVolume'
]);
const RATE_KEYS = /rate$/;
const HOUR_KEYS = /hours$/;
const MINUTE_KEYS = /minutes$/;

function toDateInput(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function createInitialFilters(): StatisticsFilters {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 29);
  return {
    fromDate: toDateInput(from),
    toDate: toDateInput(to),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Ho_Chi_Minh',
    granularity: 'AUTO'
  };
}

function getInitialReport(): StatisticsReport {
  const requestedReport = new URLSearchParams(window.location.search).get('report');
  return ALL_REPORTS.some((report) => report.id === requestedReport)
    ? (requestedReport as StatisticsReport)
    : 'overview';
}

function humanize(value: string): string {
  return (
    VALUE_LABELS[value] ??
    value
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replaceAll('_', ' ')
      .replace(/\b\w/g, (letter) => letter.toUpperCase())
  );
}

function formatMetricValue(key: string, value: number): string {
  if (CURRENCY_KEYS.has(key)) {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(
      value
    );
  }
  if (RATE_KEYS.test(key)) return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;
  if (HOUR_KEYS.test(key)) return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)} giờ`;
  if (MINUTE_KEYS.test(key))
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value)} phút`;
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value);
}

function formatPeriod(period: string): string {
  const date = new Date(`${period}T00:00:00`);
  if (Number.isNaN(date.getTime())) return period;
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(date);
}

function formatAxisValue(key: string, value: number): string {
  if (CURRENCY_KEYS.has(key)) {
    return new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value);
}

export default function AdminStatisticsPage() {
  const [accessToken] = useState(() => getAdminAccessToken());

  useEffect(() => {
    document.title = 'Thống kê quản trị | Ptutor';
    if (!accessToken) {
      window.location.replace('/?redirect=%2Fadmin%2Fstatistics');
    }
  }, [accessToken]);

  if (!accessToken) {
    return (
      <main className="statistics-guard" aria-live="polite">
        <span />
        <p>Đang kiểm tra quyền truy cập…</p>
      </main>
    );
  }

  return <StatisticsDashboard accessToken={accessToken} />;
}

function StatisticsDashboard({ accessToken }: { accessToken: string }) {
  const [activeReport, setActiveReport] = useState<StatisticsReport>(getInitialReport);
  const [filters, setFilters] = useState<StatisticsFilters>(createInitialFilters);
  const [draftFilters, setDraftFilters] = useState<StatisticsFilters>(filters);
  const [data, setData] = useState<StatisticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const report = ALL_REPORTS.find((item) => item.id === activeReport) ?? ALL_REPORTS[0];

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    getStatisticsReport(activeReport, filters, accessToken, controller.signal)
      .then((response) => {
        if (active) setData(response);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        if (reason instanceof ApiError && (reason.status === 401 || reason.status === 403)) {
          clearAdminSession();
          window.location.replace('/?redirect=%2Fadmin%2Fstatistics&reason=unauthorized');
          return;
        }
        setError(reason instanceof Error ? reason.message : 'Không thể tải báo cáo.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [accessToken, activeReport, filters, reloadKey]);

  function prepareReload() {
    setLoading(true);
    setError('');
  }

  function selectReport(nextReport: StatisticsReport) {
    prepareReload();
    setActiveReport(nextReport);
    setMenuOpen(false);
    window.history.replaceState(null, '', `/admin/statistics?report=${nextReport}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function applyPreset(days: number) {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - days + 1);
    const nextFilters = { ...draftFilters, fromDate: toDateInput(from), toDate: toDateInput(to) };
    prepareReload();
    setDraftFilters(nextFilters);
    setFilters(nextFilters);
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draftFilters.fromDate > draftFilters.toDate) {
      setError('Ngày bắt đầu không được sau ngày kết thúc.');
      return;
    }
    prepareReload();
    setFilters(draftFilters);
  }

  return (
    <div className="statistics-shell">
      <button
        type="button"
        className={menuOpen ? 'statistics-overlay is-open' : 'statistics-overlay'}
        onClick={() => setMenuOpen(false)}
        aria-label="Đóng menu"
      />
      <aside className={menuOpen ? 'statistics-sidebar is-open' : 'statistics-sidebar'}>
        <div className="statistics-brand">
          <img src={logo} alt="Ptutor" />
          <span>Analytics</span>
        </div>
        <nav aria-label="Danh mục thống kê">
          {REPORT_GROUPS.map((group) => (
            <div className="statistics-nav-group" key={group.label}>
              <span className="statistics-nav-label">{group.label}</span>
              {group.items.map((item) => (
                <button
                  type="button"
                  key={item.id}
                  className={activeReport === item.id ? 'is-active' : ''}
                  onClick={() => selectReport(item.id)}
                >
                  <span className="statistics-nav-marker" aria-hidden="true" />
                  <span>
                    <b>{item.label}</b>
                    <small>{item.description}</small>
                  </span>
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="statistics-account">
          <div className="statistics-avatar">A</div>
          <div>
            <b>Admin Dashboard</b>
            <span>Hệ thống báo cáo Ptutor</span>
          </div>
          <span className="statistics-account-status" title="Hệ thống đang hoạt động" />
        </div>
      </aside>

      <main className="statistics-main">
        <header className="statistics-topbar">
          <button
            type="button"
            className="statistics-menu-button"
            onClick={() => setMenuOpen(true)}
            aria-label="Mở menu"
          >
            ☰
          </button>
          <div>
            <span>
              Quản trị <i>/</i> Thống kê
            </span>
            <h1>{report.label}</h1>
            <p>{report.description} · So sánh với kỳ liền trước</p>
          </div>
          <div className="statistics-top-actions">
            <span className="statistics-live-dot" /> Dữ liệu hệ thống
          </div>
        </header>

        <section className="statistics-content">
          <form className="statistics-filter" onSubmit={applyFilters}>
            <div className="statistics-presets" aria-label="Khoảng thời gian nhanh">
              <button type="button" onClick={() => applyPreset(7)}>
                7 ngày
              </button>
              <button type="button" onClick={() => applyPreset(30)}>
                30 ngày
              </button>
              <button type="button" onClick={() => applyPreset(90)}>
                90 ngày
              </button>
            </div>
            <label>
              <span>Từ ngày</span>
              <input
                type="date"
                value={draftFilters.fromDate}
                max={draftFilters.toDate}
                onChange={(event) => setDraftFilters({ ...draftFilters, fromDate: event.target.value })}
                required
              />
            </label>
            <span className="statistics-date-arrow">→</span>
            <label>
              <span>Đến ngày</span>
              <input
                type="date"
                value={draftFilters.toDate}
                min={draftFilters.fromDate}
                onChange={(event) => setDraftFilters({ ...draftFilters, toDate: event.target.value })}
                required
              />
            </label>
            <label>
              <span>Nhóm dữ liệu</span>
              <select
                value={draftFilters.granularity}
                onChange={(event) =>
                  setDraftFilters({ ...draftFilters, granularity: event.target.value as StatisticsGranularity })
                }
              >
                <option value="AUTO">Tự động</option>
                <option value="DAY">Theo ngày</option>
                <option value="WEEK">Theo tuần</option>
                <option value="MONTH">Theo tháng</option>
              </select>
            </label>
            <button type="submit" className="statistics-apply">
              Áp dụng
            </button>
            <button
              type="button"
              className="statistics-refresh"
              onClick={() => {
                prepareReload();
                setReloadKey((value) => value + 1);
              }}
              disabled={loading}
              title="Tải lại dữ liệu"
            >
              ↻
            </button>
          </form>

          {error ? (
            <ErrorState
              message={error}
              onRetry={() => {
                prepareReload();
                setReloadKey((value) => value + 1);
              }}
            />
          ) : loading ? (
            <DashboardSkeleton />
          ) : data ? (
            <ReportContent data={data} activeReport={activeReport} onSelectReport={selectReport} />
          ) : null}
        </section>
      </main>
    </div>
  );
}

function ReportContent({
  data,
  activeReport,
  onSelectReport
}: {
  data: StatisticsResponse;
  activeReport: StatisticsReport;
  onSelectReport: (report: StatisticsReport) => void;
}) {
  const metrics = Object.entries(data.metrics);
  const series = Object.entries(data.series).filter(([, values]) => values.length > 0);
  const breakdowns = Object.entries(data.breakdowns).filter(([, values]) => values.length > 0);
  const primaryMetricKeys = new Set(PRIMARY_METRICS[activeReport]);
  const primaryMetrics = PRIMARY_METRICS[activeReport]
    .map((key) => metrics.find(([metricKey]) => metricKey === key))
    .filter((metric): metric is [string, StatisticsMetric] => Boolean(metric));
  const secondaryMetrics = metrics.filter(([key]) => !primaryMetricKeys.has(key));
  const generatedAt = new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(data.generatedAt));

  return (
    <div className="statistics-report">
      <section className="statistics-metrics" aria-label="Chỉ số chính">
        {primaryMetrics.map(([key, metric]) => (
          <MetricCard
            key={key}
            metricKey={key}
            metric={metric}
            onClick={
              activeReport === 'overview' && METRIC_REPORTS[key] ? () => onSelectReport(METRIC_REPORTS[key]) : undefined
            }
          />
        ))}
      </section>

      {(series.length > 0 || secondaryMetrics.length > 0) && (
        <section className={series.length > 0 ? 'statistics-analysis-grid' : 'statistics-analysis-grid no-chart'}>
          {series.length > 0 && <TrendPanel key={data.report} series={series} />}
          {secondaryMetrics.length > 0 && <MetricTable metrics={secondaryMetrics} />}
        </section>
      )}

      {breakdowns.length > 0 && (
        <section className="statistics-breakdown-section">
          <div className="statistics-section-heading">
            <div>
              <span>PHÂN TÍCH CHI TIẾT</span>
              <h3>Cơ cấu dữ liệu</h3>
            </div>
            <p>Phân bổ theo các nhóm quan trọng trong kỳ đã chọn.</p>
          </div>
          <div className="statistics-breakdown-grid">
            {breakdowns.map(([key, values]) => {
              const total = values.reduce((sum, item) => sum + item.value, 0);
              const max = Math.max(...values.map((item) => item.value), 1);
              return (
                <article className="statistics-card statistics-breakdown" key={key}>
                  <div className="statistics-card-heading compact">
                    <h3>{BREAKDOWN_LABELS[key] ?? humanize(key)}</h3>
                    <span>{new Intl.NumberFormat('vi-VN').format(total)} tổng</span>
                  </div>
                  <div className="statistics-bars">
                    {values.slice(0, 8).map((item) => (
                      <div className="statistics-bar-row" key={item.key}>
                        <div>
                          <span>{humanize(item.key)}</span>
                          <b>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(item.value)}</b>
                        </div>
                        <div className="statistics-bar-track">
                          <i style={{ width: `${(item.value / max) * 100}%` }} />
                        </div>
                      </div>
                    ))}
                  </div>
                </article>
              );
            })}
          </div>
        </section>
      )}

      {metrics.length === 0 && series.length === 0 && breakdowns.length === 0 && (
        <div className="statistics-empty">
          <span>⌁</span>
          <h3>Chưa có dữ liệu trong kỳ này</h3>
          <p>Hãy chọn một khoảng thời gian khác để xem báo cáo.</p>
        </div>
      )}
      <p className="statistics-generated">
        Cập nhật lúc {generatedAt} · Múi giờ {data.range.timezone} · Nhóm theo {humanize(data.range.granularity)}
      </p>
    </div>
  );
}

function MetricCard({
  metricKey,
  metric,
  onClick
}: {
  metricKey: string;
  metric: StatisticsMetric;
  onClick?: () => void;
}) {
  const change = metric.changePercent;
  const trend = change === null || change === 0 ? 'neutral' : change > 0 ? 'positive' : 'negative';
  const content = (
    <>
      <div className="statistics-metric-top">
        <span>{METRIC_LABELS[metricKey] ?? humanize(metricKey)}</span>
        {onClick && <b aria-hidden="true">→</b>}
      </div>
      <strong title={String(metric.value)}>{formatMetricValue(metricKey, metric.value)}</strong>
      <div className={`statistics-change ${trend}`}>
        <b>
          {trend === 'positive' ? '↗' : trend === 'negative' ? '↘' : '→'}{' '}
          {change === null ? 'Mới' : `${Math.abs(change).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`}
        </b>
        <span>so với kỳ trước · {formatMetricValue(metricKey, metric.previousValue)}</span>
      </div>
    </>
  );
  if (onClick)
    return (
      <button type="button" className="statistics-metric is-clickable" onClick={onClick}>
        {content}
      </button>
    );
  return <article className="statistics-metric">{content}</article>;
}

function MetricTable({ metrics }: { metrics: [string, StatisticsMetric][] }) {
  return (
    <article className="statistics-card statistics-detail-card">
      <div className="statistics-card-heading compact">
        <div>
          <span>CHỈ SỐ BỔ SUNG</span>
          <h3>Chi tiết trong kỳ</h3>
        </div>
        <b>{metrics.length} chỉ số</b>
      </div>
      <div className="statistics-detail-list">
        {metrics.map(([key, metric]) => {
          const change = metric.changePercent;
          const trend = change === null || change === 0 ? 'neutral' : change > 0 ? 'positive' : 'negative';
          return (
            <div className="statistics-detail-row" key={key}>
              <span>{METRIC_LABELS[key] ?? humanize(key)}</span>
              <strong>{formatMetricValue(key, metric.value)}</strong>
              <small className={trend}>
                {change === null
                  ? 'Mới'
                  : `${change > 0 ? '+' : ''}${change.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`}
              </small>
            </div>
          );
        })}
      </div>
    </article>
  );
}

function TrendPanel({ series }: { series: [string, StatisticsSeriesItem[]][] }) {
  const [activeKey, setActiveKey] = useState(series[0][0]);
  const activeSeries = series.find(([key]) => key === activeKey) ?? series[0];
  return (
    <section className="statistics-card statistics-chart-section">
      <div className="statistics-card-heading statistics-chart-heading">
        <div>
          <span>DIỄN BIẾN THEO THỜI GIAN</span>
          <h3>{METRIC_LABELS[activeSeries[0]] ?? humanize(activeSeries[0])}</h3>
        </div>
        {series.length > 1 && (
          <div className="statistics-series-tabs" aria-label="Chọn chỉ số biểu đồ">
            {series.map(([key]) => (
              <button
                type="button"
                className={key === activeSeries[0] ? 'is-active' : ''}
                onClick={() => setActiveKey(key)}
                key={key}
              >
                {METRIC_LABELS[key] ?? humanize(key)}
              </button>
            ))}
          </div>
        )}
      </div>
      <TrendChart series={[activeSeries]} />
    </section>
  );
}

function TrendChart({ series }: { series: [string, StatisticsSeriesItem[]][] }) {
  const width = 900;
  const height = 230;
  const padding = { top: 14, right: 18, bottom: 34, left: 54 };
  const allValues = series.flatMap(([, values]) => values.map((item) => item.value));
  const maximum = Math.max(...allValues, 1);
  const periods = series.reduce(
    (longest, [, values]) => (values.length > longest.length ? values : longest),
    [] as StatisticsSeriesItem[]
  );
  const x = (index: number, length: number) =>
    padding.left + (index * (width - padding.left - padding.right)) / Math.max(length - 1, 1);
  const y = (value: number) => height - padding.bottom - (value / maximum) * (height - padding.top - padding.bottom);
  const ticks = [0, 0.25, 0.5, 0.75, 1];

  return (
    <div className="statistics-chart-wrap">
      <svg className="statistics-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Biểu đồ xu hướng">
        {ticks.map((tick) => {
          const tickY = y(maximum * tick);
          return (
            <g key={tick}>
              <line x1={padding.left} y1={tickY} x2={width - padding.right} y2={tickY} />
              <text x={padding.left - 10} y={tickY + 4} textAnchor="end">
                {formatAxisValue(series[0][0], maximum * tick)}
              </text>
            </g>
          );
        })}
        {series.map(([key, values], seriesIndex) => {
          const points = values.map((item, index) => `${x(index, values.length)},${y(item.value)}`).join(' ');
          return (
            <g key={key} className={`chart-series color-${seriesIndex}`}>
              <polyline points={points} />
              {values.map((item, index) => (
                <circle key={`${item.period}-${index}`} cx={x(index, values.length)} cy={y(item.value)} r="4">
                  <title>{`${METRIC_LABELS[key] ?? humanize(key)} · ${formatPeriod(item.period)}: ${formatMetricValue(key, item.value)}`}</title>
                </circle>
              ))}
            </g>
          );
        })}
        {periods.map((item, index) => {
          if (periods.length > 7 && index % Math.ceil(periods.length / 7) !== 0 && index !== periods.length - 1)
            return null;
          return (
            <text key={item.period} x={x(index, periods.length)} y={height - 12} textAnchor="middle">
              {formatPeriod(item.period)}
            </text>
          );
        })}
      </svg>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="statistics-skeleton" aria-label="Đang tải báo cáo">
      <div className="statistics-metrics">
        {Array.from({ length: 6 }, (_, index) => (
          <div className="skeleton-block skeleton-metric" key={index} />
        ))}
      </div>
      <div className="skeleton-block skeleton-chart" />
      <div className="statistics-breakdown-grid">
        <div className="skeleton-block skeleton-breakdown" />
        <div className="skeleton-block skeleton-breakdown" />
      </div>
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="statistics-error" role="alert">
      <span>!</span>
      <div>
        <h3>Chưa thể tải báo cáo</h3>
        <p>{message}</p>
      </div>
      <button type="button" onClick={onRetry}>
        Thử lại
      </button>
    </div>
  );
}
