import { useEffect, useMemo, useState } from 'react';
import { useLessonCalendarActions } from '../../hooks/useLessonCalendarActions';
import { useLessonCalendarData, type CalendarEvent } from '../../hooks/useLessonCalendarData';
import { LessonCalendarGrid } from '../common-user/components/LessonCalendarGrid';
import { LessonCalendarToolbar } from '../common-user/components/LessonCalendarToolbar';
import { StudentLessonDetailPanel } from '../../components/student/lesson/StudentLessonDetailPanel';
import type { Contract } from '../../services/studentApi';
import { filterStudentCalendarEvents } from './studentCalendarStats';
import '../common-user/lesson-calendar.css';
import './student-lessons.css';
import './student-calendar.css';

const EMPTY_CONTRACTS: Contract[] = [];
const EMPTY_EVENTS: CalendarEvent[] = [];

const statusText: Record<string, string> = {
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

function lessonDateTime(event: CalendarEvent) {
  return new Date(`${event.date}T${event.startTime}`);
}

export function StudentCalendarPage() {
  const [cursor, setCursor] = useState(() => new Date());
  const [view, setView] = useState<'month' | 'week'>('month');
  const [contractFilter, setContractFilter] = useState('');
  const [personFilter, setPersonFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState('');
  const { data, error, isLoading } = useLessonCalendarData('STUDENT', revision);
  const actions = useLessonCalendarActions(
    'STUDENT',
    setNotice,
    () => setRevision((value) => value + 1),
    () => setSelectedLessonId(null)
  );

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(''), 7000);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  useEffect(() => {
    if (!selectedLessonId) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelectedLessonId(null);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedLessonId]);

  const contracts = data?.contracts ?? EMPTY_CONTRACTS;
  const allEvents = data?.events ?? EMPTY_EVENTS;
  const events = useMemo(
    () =>
      allEvents.filter((event) => {
        if (contractFilter && event.contractId !== contractFilter) return false;
        if (statusFilter && event.status !== statusFilter) return false;
        if (personFilter) {
          const contract = contracts.find((item) => item.id === event.contractId);
          const tutor = `${contract?.tutorFirstName ?? ''} ${contract?.tutorLastName ?? ''}`.trim();
          if (!tutor.toLocaleLowerCase('vi').includes(personFilter.toLocaleLowerCase('vi'))) return false;
        }
        return true;
      }),
    [allEvents, contractFilter, contracts, personFilter, statusFilter]
  );
  const periodEvents = useMemo(() => filterStudentCalendarEvents(events, cursor, view), [cursor, events, view]);

  const selectedLesson = selectedLessonId ? (events.find((event) => event.id === selectedLessonId) ?? null) : null;
  const selectedContract = selectedLesson
    ? (contracts.find((contract) => contract.id === selectedLesson.contractId) ?? null)
    : null;
  const people = [
    ...new Set(
      contracts
        .map((contract) => `${contract.tutorFirstName ?? ''} ${contract.tutorLastName ?? ''}`.trim())
        .filter(Boolean)
    )
  ];
  const now = new Date();
  const upcoming = periodEvents.filter(
    (event) => lessonDateTime(event) >= now && event.status !== 'CANCELLED' && event.status !== 'COMPLETED'
  ).length;
  const awaiting = periodEvents.filter((event) => event.status === 'PENDING_CONFIRMATION').length;
  const completed = periodEvents.filter((event) => event.status === 'CONFIRMED' || event.status === 'COMPLETED').length;

  const move = (amount: number) =>
    setCursor((current) => {
      const next = new Date(current);
      if (view === 'month') next.setMonth(next.getMonth() + amount);
      else next.setDate(next.getDate() + amount * 7);
      return next;
    });

  const renderEvent = (event: CalendarEvent) => {
    const contract = contracts.find((item) => item.id === event.contractId);
    const tutor = `${contract?.tutorFirstName ?? ''} ${contract?.tutorLastName ?? ''}`.trim();
    return (
      <button
        key={event.id}
        type="button"
        className={`lc-event ${event.status.toLowerCase()}${event.disputed ? ' disputed' : ''}`}
        onClick={() => setSelectedLessonId(event.id)}
      >
        <strong>
          {event.startTime.slice(0, 5)}–{event.endTime.slice(0, 5)}
        </strong>
        <span>{event.title || contract?.subjectName || 'Buổi học'}</span>
        <small>{tutor || 'Gia sư'}</small>
        <span className="lc-event-status">
          {event.disputed ? 'Đang khiếu nại / hoàn tiền' : (statusText[event.status] ?? event.status)}
        </span>
      </button>
    );
  };

  return (
    <div className="student-lesson-page">
      <header className="student-lesson-heading">
        <div>
          <span className="student-lesson-kicker">LỊCH HỌC</span>
          <h1>Lịch học của tôi</h1>
        </div>
      </header>

      <LessonCalendarToolbar
        role="STUDENT"
        variant="student"
        cursor={cursor}
        view={view}
        contracts={contracts}
        people={people}
        contractFilter={contractFilter}
        personFilter={personFilter}
        statusFilter={statusFilter}
        onToday={() => setCursor(new Date())}
        onMove={move}
        onMonthChange={(value) => {
          if (!value) return;
          const [year, month] = value.split('-').map(Number);
          setCursor(new Date(year, month - 1, 1));
        }}
        onViewChange={setView}
        onContractChange={(value) => {
          setSelectedLessonId(null);
          setContractFilter(value);
        }}
        onPersonChange={(value) => {
          setSelectedLessonId(null);
          setPersonFilter(value);
        }}
        onStatusChange={(value) => {
          setSelectedLessonId(null);
          setStatusFilter(value);
        }}
        onCreate={() => undefined}
      />

      <section className="student-lesson-stats" aria-label="Tổng quan lịch học theo bộ lọc">
        <Stat
          icon="▣"
          label="Tổng số buổi học"
          value={periodEvents.length}
          loading={isLoading && !data}
          tone="purple"
        />
        <Stat icon="▣" label="Buổi sắp tới" value={upcoming} loading={isLoading && !data} tone="green" />
        <Stat icon="⌛" label="Chờ xác nhận" value={awaiting} loading={isLoading && !data} tone="yellow" />
        <Stat icon="✓" label="Đã hoàn tất" value={completed} loading={isLoading && !data} tone="green" />
      </section>

      {notice && (
        <div className="lc-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="lc-error" role="alert">
          {error}
        </div>
      )}
      {isLoading && !data && <div className="student-lesson-empty">Đang tải lịch học…</div>}

      <div className="student-lesson-calendar-layout">
        <section className="student-lesson-calendar-card">
          <LessonCalendarGrid
            cursor={cursor}
            view={view}
            events={events}
            renderEvent={renderEvent}
            weekEventLayout="columns"
          />
          <footer className="lc-legend">
            <span>
              <i className="scheduled" />
              Đã lên lịch
            </span>
            <span>
              <i className="pending" />
              Chờ xác nhận
            </span>
            <span>
              <i className="confirmed" />
              Đã xác nhận
            </span>
            <span>
              <i className="cancelled" />
              Đã hủy
            </span>
          </footer>
        </section>
      </div>

      {selectedLesson && (
        <StudentLessonDetailPanel
          lesson={selectedLesson}
          contract={selectedContract}
          busy={actions.busy === selectedLesson.id}
          onConfirm={() => void actions.confirm(selectedLesson)}
          onClose={() => setSelectedLessonId(null)}
          dialog
        />
      )}
    </div>
  );
}

function Stat({
  icon,
  label,
  value,
  loading,
  tone
}: {
  icon: string;
  label: string;
  value: number;
  loading: boolean;
  tone: 'purple' | 'green' | 'yellow';
}) {
  return (
    <article className={`student-lesson-stat ${tone}`}>
      <span aria-hidden="true">{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{loading ? '—' : value}</strong>
      </div>
    </article>
  );
}
