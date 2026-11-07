import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useLessonCalendarData,
  type CalendarEvent as Event,
  type CalendarRole
} from '../../hooks/useLessonCalendarData';
import { useLessonCalendarActions } from '../../hooks/useLessonCalendarActions';
import { LessonCalendarToolbar } from './components/LessonCalendarToolbar';
import { LessonCalendarGrid } from './components/LessonCalendarGrid';
import './lesson-calendar.css';

const pad = (n: number) => String(n).padStart(2, '0');
const dateKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const statusText: Record<string, string> = {
  SCHEDULED: 'Đã lên lịch',
  PENDING_CONFIRMATION: 'Chờ học viên xác nhận',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy'
};

export function LessonCalendarPage({ role }: { role: CalendarRole }) {
  const [cursor, setCursor] = useState(() => new Date());
  const [view, setView] = useState<'month' | 'week'>('month');
  const [contractFilter, setContractFilter] = useState('');
  const [personFilter, setPersonFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState('');
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
  const [editingEvent, setEditingEvent] = useState(false);

  const { data, error, isLoading } = useLessonCalendarData(role, revision);
  const actions = useLessonCalendarActions(
    role,
    setNotice,
    () => setRevision((value) => value + 1),
    () => setSelectedEvent(null)
  );
  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(''), 7000);
    return () => window.clearTimeout(timeout);
  }, [notice]);
  const events = useMemo(
    () =>
      (data?.events ?? []).filter((event) => {
        if (contractFilter && event.contractId !== contractFilter) return false;
        if (statusFilter && event.status !== statusFilter) return false;
        if (personFilter) {
          const contract = data?.contracts.find((item) => item.id === event.contractId);
          const name =
            role === 'TUTOR'
              ? `${contract?.studentFirstName ?? ''} ${contract?.studentLastName ?? ''}`
              : `${contract?.tutorFirstName ?? ''} ${contract?.tutorLastName ?? ''}`;
          if (!name.toLocaleLowerCase('vi').includes(personFilter.toLocaleLowerCase('vi'))) return false;
        }
        return true;
      }),
    [data, contractFilter, statusFilter, personFilter, role]
  );

  const move = (amount: number) =>
    setCursor((current) => {
      const next = new Date(current);
      if (view === 'month') {
        next.setMonth(next.getMonth() + amount);
      } else {
        next.setDate(next.getDate() + 7 * amount);
      }
      return next;
    });
  const contracts = data?.contracts ?? [];
  const selectedContract = contracts.find((contract) => contract.id === contractFilter);
  const people =
    role === 'TUTOR'
      ? [
          ...new Set(
            contracts
              .map((contract) => `${contract.studentFirstName ?? ''} ${contract.studentLastName ?? ''}`.trim())
              .filter(Boolean)
          )
        ]
      : [
          ...new Set(
            contracts
              .map((contract) => `${contract.tutorFirstName ?? ''} ${contract.tutorLastName ?? ''}`.trim())
              .filter(Boolean)
          )
        ];
  const renderEvent = (event: Event) => {
    const contract = contracts.find((item) => item.id === event.contractId);
    const person =
      role === 'TUTOR'
        ? `${contract?.studentFirstName ?? ''} ${contract?.studentLastName ?? ''}`.trim()
        : `${contract?.tutorFirstName ?? ''} ${contract?.tutorLastName ?? ''}`.trim();
    return (
      <button
        key={event.id}
        type="button"
        className={`lc-event ${event.status.toLowerCase()}${event.disputed ? ' disputed' : ''}`}
        onClick={() => setSelectedEvent(event)}
      >
        <strong>
          {event.startTime.slice(0, 5)}–{event.endTime.slice(0, 5)}
        </strong>
        <span>{event.title}</span>
        <small>{person}</small>
        <span className="lc-event-status">
          {event.disputed ? 'Đang khiếu nại / hoàn tiền' : (statusText[event.status] ?? event.status)}
        </span>
      </button>
    );
  };

  return (
    <div className="lc-page">
      <LessonCalendarToolbar
        role={role}
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
        onContractChange={setContractFilter}
        onPersonChange={setPersonFilter}
        onStatusChange={setStatusFilter}
        onCreate={() => setShowCreate(true)}
      />
      {notice && (
        <p className="lc-notice" role="status">
          {notice}
          <button onClick={() => setNotice('')}>Đóng</button>
        </p>
      )}
      {error && (
        <p className="lc-error" role="alert">
          {error}
        </p>
      )}
      {isLoading && !data && <p>Đang tải lịch…</p>}
      {selectedContract && (
        <p className="lc-selection">
          Đang xem hợp đồng: <strong>{selectedContract.subjectName}</strong> · {selectedContract.startDate} –{' '}
          {selectedContract.endDate}
        </p>
      )}
      <LessonCalendarGrid cursor={cursor} view={view} events={events} renderEvent={renderEvent} />
      <footer className="lc-legend">
        <span>
          <i className="confirmed" />
          Đã xác nhận
        </span>
        <span>
          <i className="pending" />
          Chờ xác nhận
        </span>
        <span>
          <i className="scheduled" />
          Đã lên lịch
        </span>
        <span>
          <i className="cancelled" />
          Đã hủy
        </span>
      </footer>
      {selectedEvent &&
        (() => {
          const contract = contracts.find((item) => item.id === selectedEvent.contractId);
          const person =
            role === 'TUTOR'
              ? `${contract?.studentFirstName ?? ''} ${contract?.studentLastName ?? ''}`.trim()
              : `${contract?.tutorFirstName ?? ''} ${contract?.tutorLastName ?? ''}`.trim();
          return (
            <div
              className="lc-overlay"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) setSelectedEvent(null);
              }}
            >
              <section className="lc-lesson-modal" role="dialog" aria-modal="true" aria-labelledby="lc-lesson-title">
                <header>
                  <div>
                    <small>
                      {role === 'TUTOR' ? 'BUỔI DẠY' : 'BUỔI HỌC'} ·{' '}
                      {editingEvent
                        ? 'S\u1eeda L\u1ecbch h\u1ecdc'
                        : (statusText[selectedEvent.status] ?? selectedEvent.status)}
                    </small>
                    <h2 id="lc-lesson-title">{selectedEvent.title}</h2>
                  </div>
                  <button type="button" aria-label="Đóng" onClick={() => setSelectedEvent(null)}>
                    ×
                  </button>
                </header>
                {editingEvent ? (
                  <form
                    className="lc-edit-form"
                    onSubmit={(event) => {
                      event.preventDefault();
                      void actions.saveSchedule(new FormData(event.currentTarget), selectedEvent).then((saved) => {
                        if (saved) setEditingEvent(false);
                      });
                    }}
                  >
                    <label>
                      Tên buổi học
                      <input name="title" required defaultValue={selectedEvent.title} />
                    </label>
                    <label>
                      Ngày
                      <input name="date" type="date" required defaultValue={selectedEvent.date} />
                    </label>
                    <div className="lc-time-fields">
                      <label>
                        Bắt đầu
                        <input name="startTime" type="time" required defaultValue={selectedEvent.startTime} />
                      </label>
                      <label>
                        Kết thúc
                        <input name="endTime" type="time" required defaultValue={selectedEvent.endTime} />
                      </label>
                    </div>
                    <label>
                      Ghi chú
                      <input name="note" defaultValue={selectedEvent.note ?? ''} />
                    </label>
                    {actions.actionError && (
                      <p className="lc-error" role="alert">
                        {actions.actionError}
                      </p>
                    )}
                    <div className="lc-modal-actions">
                      <button type="button" onClick={() => setEditingEvent(false)}>
                        Quay lại
                      </button>
                      <button className="lc-primary" type="submit">
                        Lưu lịch
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="lc-lesson-facts">
                    <p>
                      <span>Hợp đồng</span>
                      <strong>{contract?.subjectName ?? 'Hợp đồng'}</strong>
                    </p>
                    <p>
                      <span>{role === 'TUTOR' ? 'Học viên' : 'Gia sư'}</span>
                      <strong>{person || 'Chưa có thông tin'}</strong>
                    </p>
                    <p>
                      <span>Ngày học</span>
                      <strong>
                        {new Intl.DateTimeFormat('vi-VN', {
                          weekday: 'long',
                          day: '2-digit',
                          month: '2-digit',
                          year: 'numeric'
                        }).format(new Date(`${selectedEvent.date}T00:00:00`))}
                      </strong>
                    </p>
                    <p>
                      <span>Thời gian</span>
                      <strong>
                        {selectedEvent.startTime.slice(0, 5)} – {selectedEvent.endTime.slice(0, 5)}
                      </strong>
                    </p>
                    <p>
                      <span>Trạng thái</span>
                      <strong>
                        {selectedEvent.disputed
                          ? 'Đang khiếu nại / yêu cầu hoàn tiền'
                          : (statusText[selectedEvent.status] ?? selectedEvent.status)}
                      </strong>
                    </p>
                    {selectedEvent.note && (
                      <p className="lc-note-row">
                        <span>Ghi chú</span>
                        <strong>{selectedEvent.note}</strong>
                      </p>
                    )}
                  </div>
                )}
                {actions.actionError && !editingEvent && (
                  <p className="lc-error" role="alert">
                    {actions.actionError}
                  </p>
                )}
                <div className="lc-modal-actions">
                  {role === 'TUTOR' && selectedEvent.status === 'SCHEDULED' && !editingEvent && (
                    <button
                      className="lc-primary"
                      disabled={actions.busy === selectedEvent.id}
                      onClick={() => void actions.markTaught(selectedEvent)}
                    >
                      Đánh dấu đã dạy
                    </button>
                  )}
                  {role === 'TUTOR' && selectedEvent.status === 'SCHEDULED' && !editingEvent && (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          actions.setActionError('');
                          setEditingEvent(true);
                        }}
                      >
                        Sửa lịch
                      </button>
                      <button
                        type="button"
                        className="lc-danger-button"
                        onClick={() => void actions.cancelSchedule(selectedEvent)}
                      >
                        Hủy buổi học
                      </button>
                    </>
                  )}
                  {role === 'STUDENT' && selectedEvent.status === 'PENDING_CONFIRMATION' && (
                    <button
                      className="lc-primary"
                      disabled={actions.busy === selectedEvent.id}
                      onClick={() => void actions.confirm(selectedEvent)}
                    >
                      Xác nhận đã học
                    </button>
                  )}
                  {role === 'STUDENT' && selectedEvent.status === 'PENDING_CONFIRMATION' && (
                    <Link
                      className="lc-complaint-link"
                      to={`/student/contracts/${selectedEvent.contractId}/refund-request`}
                    >
                      Khiếu nại / Hoàn tiền
                    </Link>
                  )}
                  {role === 'TUTOR' && selectedEvent.disputed && (
                    <Link className="lc-action-link" to={`/tutor/refund-requests/${selectedEvent.refundRequestId}`}>
                      Thêm bằng chứng
                    </Link>
                  )}
                  {role === 'TUTOR' && selectedEvent.disputed && (
                    <Link className="lc-action-link" to={`/tutor/refund-requests/${selectedEvent.refundRequestId}`}>
                      Phản hồi giải trình
                    </Link>
                  )}
                  {!editingEvent && (
                    <button type="button" onClick={() => setSelectedEvent(null)}>
                      Đóng
                    </button>
                  )}
                </div>
              </section>
            </div>
          );
        })()}
      {showCreate && (
        <div
          className="lc-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setShowCreate(false);
          }}
        >
          <form
            className="lc-modal"
            onSubmit={(event) => {
              event.preventDefault();
              void actions.create(new FormData(event.currentTarget), contracts.length > 0).then((saved) => {
                if (saved) setShowCreate(false);
              });
            }}
          >
            <h2>Thêm buổi học</h2>
            {actions.createError && (
              <p className="lc-error" role="alert">
                {actions.createError}
              </p>
            )}
            {contracts.length === 0 ? (
              <p className="lc-no-contracts" role="status">
                Lưu ý: Hiện không có hợp đồng nào để chọn. Bạn cần có hợp đồng trước khi thêm buổi học.
              </p>
            ) : (
              <>
                <label>
                  Hợp đồng
                  <select name="contractId" required>
                    {contracts.map((contract) => (
                      <option key={contract.id} value={contract.id}>
                        {contract.subjectName} · {contract.studentFirstName} {contract.studentLastName}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Tên buổi học
                  <input name="title" required maxLength={255} placeholder="Ví dụ: Đại số chương 2" />
                </label>
                <label>
                  Ngày
                  <input
                    name="date"
                    type="date"
                    required
                    defaultValue={
                      contracts[0] && dateKey(new Date()) < contracts[0].startDate
                        ? contracts[0].startDate
                        : dateKey(cursor)
                    }
                  />
                </label>
                <div className="lc-time-fields">
                  <label>
                    Bắt đầu
                    <input name="startTime" type="time" required />
                  </label>
                  <label>
                    Kết thúc
                    <input name="endTime" type="time" required />
                  </label>
                </div>
              </>
            )}
            <div className="lc-modal-actions">
              <button type="button" onClick={() => setShowCreate(false)}>
                Hủy
              </button>
              <button className="lc-primary" type="submit" disabled={contracts.length === 0}>
                Lưu buổi học
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
