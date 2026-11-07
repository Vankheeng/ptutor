import type { Contract } from '../../../services/studentApi';
import type { CalendarRole } from '../../../hooks/useLessonCalendarData';

const pad = (value: number) => String(value).padStart(2, '0');
const statusLabels: Record<string, string> = {
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ học viên xác nhận',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

type Props = {
  role: CalendarRole;
  cursor: Date;
  view: 'month' | 'week';
  contracts: Contract[];
  people: string[];
  contractFilter: string;
  personFilter: string;
  statusFilter: string;
  onToday: () => void;
  onMove: (amount: number) => void;
  onMonthChange: (value: string) => void;
  onViewChange: (view: 'month' | 'week') => void;
  onContractChange: (value: string) => void;
  onPersonChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onCreate: () => void;
};

export function LessonCalendarToolbar(props: Props) {
  const { role, cursor, view, contracts, people, contractFilter, personFilter, statusFilter } = props;
  return (
    <section className="lc-toolbar">
      <h1>{role === 'TUTOR' ? 'Lịch dạy gia sư' : 'Lịch học của tôi'}</h1>
      <button onClick={props.onToday}>Hôm nay</button>
      <button aria-label="Tháng trước" onClick={() => props.onMove(-1)}>
        ‹
      </button>
      <button aria-label="Tháng sau" onClick={() => props.onMove(1)}>
        ›
      </button>
      <label className="lc-month-picker">
        <span>Tháng</span>
        <input
          aria-label="Chọn tháng và năm"
          type="month"
          value={`${cursor.getFullYear()}-${pad(cursor.getMonth() + 1)}`}
          onChange={(event) => props.onMonthChange(event.target.value)}
        />
      </label>
      <div className="lc-toggle">
        <button className={view === 'month' ? 'active' : ''} onClick={() => props.onViewChange('month')}>
          Tháng
        </button>
        <button className={view === 'week' ? 'active' : ''} onClick={() => props.onViewChange('week')}>
          Tuần
        </button>
      </div>
      <label className="lc-filter">
        <span>Hợp đồng</span>
        <select value={contractFilter} onChange={(event) => props.onContractChange(event.target.value)}>
          <option value="">Tất cả hợp đồng</option>
          {contracts.map((contract) => (
            <option key={contract.id} value={contract.id}>
              {contract.subjectName} ·{' '}
              {[contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') ||
                [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ')}
            </option>
          ))}
        </select>
      </label>
      <label className="lc-filter">
        <span>{role === 'TUTOR' ? 'Học viên' : 'Gia sư'}</span>
        <select value={personFilter} onChange={(event) => props.onPersonChange(event.target.value)}>
          <option value="">Tất cả</option>
          {people.map((person) => (
            <option key={person}>{person}</option>
          ))}
        </select>
      </label>
      <label className="lc-filter">
        <span>Trạng thái</span>
        <select value={statusFilter} onChange={(event) => props.onStatusChange(event.target.value)}>
          <option value="">Tất cả</option>
          {Object.entries(statusLabels).map(([status, label]) => (
            <option key={status} value={status}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {role === 'TUTOR' && (
        <button className="lc-primary lc-add" onClick={props.onCreate}>
          ＋ Thêm buổi học
        </button>
      )}
    </section>
  );
}
