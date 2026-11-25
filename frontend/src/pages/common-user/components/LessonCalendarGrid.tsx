import type { ReactNode } from 'react';
import type { CalendarEvent } from '../../../hooks/useLessonCalendarData';

const pad = (value: number) => String(value).padStart(2, '0');
const dateKey = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const weekdayNames = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];

type Props = {
  cursor: Date;
  view: 'month' | 'week';
  events: CalendarEvent[];
  renderEvent: (event: CalendarEvent) => ReactNode;
  weekEventLayout?: 'single' | 'columns';
};

const WEEK_START_MINUTES = 7 * 60;
const WEEK_END_MINUTES = 24 * 60;
const WEEK_HOUR_HEIGHT = 52;

function minutesFromTime(value: string) {
  const [hours, minutes] = value.slice(0, 5).split(':').map(Number);
  return hours * 60 + minutes;
}

function arrangeWeekEvents(events: CalendarEvent[]) {
  const sorted = [...events].sort((left, right) => {
    const startDifference = minutesFromTime(left.startTime) - minutesFromTime(right.startTime);
    return startDifference || minutesFromTime(left.endTime) - minutesFromTime(right.endTime);
  });
  const groups: CalendarEvent[][] = [];
  let currentGroup: CalendarEvent[] = [];
  let currentGroupEnd = -Infinity;

  sorted.forEach((event) => {
    const start = minutesFromTime(event.startTime);
    const end = minutesFromTime(event.endTime);
    if (currentGroup.length > 0 && start >= currentGroupEnd) {
      groups.push(currentGroup);
      currentGroup = [];
      currentGroupEnd = -Infinity;
    }
    currentGroup.push(event);
    currentGroupEnd = Math.max(currentGroupEnd, end);
  });
  if (currentGroup.length > 0) groups.push(currentGroup);

  return groups.flatMap((group) => {
    const columns: CalendarEvent[][] = [];
    const columnById = new Map<string, number>();

    group.forEach((event) => {
      const start = minutesFromTime(event.startTime);
      const column = columns.findIndex((items) => {
        const previous = items[items.length - 1];
        return minutesFromTime(previous.endTime) <= start;
      });
      const columnIndex = column === -1 ? columns.length : column;
      if (!columns[columnIndex]) columns[columnIndex] = [];
      columns[columnIndex].push(event);
      columnById.set(event.id, columnIndex);
    });

    return group.map((event) => ({
      event,
      column: columnById.get(event.id) ?? 0,
      columnCount: columns.length
    }));
  });
}

function weekEventStyle(event: CalendarEvent, column = 0, columnCount = 1) {
  const start = minutesFromTime(event.startTime);
  const end = Math.max(start + 1, minutesFromTime(event.endTime));
  const visibleStart = Math.max(start, WEEK_START_MINUTES);
  const visibleEnd = Math.min(end, WEEK_END_MINUTES);
  const top = Math.max(0, ((visibleStart - WEEK_START_MINUTES) / 60) * WEEK_HOUR_HEIGHT);
  const height = Math.max(1, ((Math.max(visibleEnd, visibleStart + 1) - visibleStart) / 60) * WEEK_HOUR_HEIGHT);
  const columnWidth = 100 / columnCount;

  return {
    top,
    height,
    left: `calc(${column * columnWidth}% + 4px)`,
    width: `calc(${columnWidth}% - 8px)`,
    right: 'auto'
  };
}

export function LessonCalendarGrid({ cursor, view, events, renderEvent, weekEventLayout = 'single' }: Props) {
  const firstOfMonth = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
  const monthStart = new Date(firstOfMonth);
  monthStart.setDate(1 - ((firstOfMonth.getDay() + 6) % 7));
  const monthDays = Array.from({ length: 42 }, (_, index) => {
    const day = new Date(monthStart);
    day.setDate(monthStart.getDate() + index);
    return day;
  });
  const weekStart = new Date(cursor);
  weekStart.setDate(cursor.getDate() - ((cursor.getDay() + 6) % 7));
  const shownDays =
    view === 'month'
      ? monthDays
      : Array.from({ length: 7 }, (_, index) => {
          const day = new Date(weekStart);
          day.setDate(weekStart.getDate() + index);
          return day;
        });
  const hours = Array.from({ length: 17 }, (_, index) => index + 7);
  const todayKey = dateKey(new Date());

  if (view === 'week')
    return (
      <section className="lc-calendar lc-week-calendar">
        <div className="lc-week-header">
          <span />
          {shownDays.map((day) => (
            <div key={dateKey(day)}>
              <small>{weekdayNames[day.getDay()]}</small>
              <strong>{pad(day.getDate())}</strong>
            </div>
          ))}
        </div>
        <div className="lc-week-body">
          <div className="lc-time-axis">
            {hours.map((hour) => (
              <span key={hour}>{pad(hour)}:00</span>
            ))}
          </div>
          {shownDays.map((day) => {
            const key = dateKey(day);
            return (
              <div key={key} className={`lc-week-column${key === todayKey ? ' today' : ''}`}>
                {hours.map((hour) => (
                  <i key={hour} />
                ))}
                {(weekEventLayout === 'columns'
                  ? arrangeWeekEvents(events.filter((event) => event.date === key))
                  : events.filter((event) => event.date === key).map((event) => ({ event, column: 0, columnCount: 1 }))
                ).map(({ event, column, columnCount }) => (
                  <div
                    className="lc-positioned-event"
                    key={event.id}
                    style={weekEventStyle(event, column, columnCount)}
                  >
                    {renderEvent(event)}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </section>
    );

  return (
    <section className="lc-calendar month">
      <div className="lc-weekdays">
        {['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'].map((day) => (
          <span key={day}>{day}</span>
        ))}
      </div>
      <div className="lc-grid">
        {shownDays.map((day) => {
          const key = dateKey(day);
          const dayEvents = events
            .filter((event) => event.date === key)
            .sort((a, b) => a.startTime.localeCompare(b.startTime));
          return (
            <article
              key={key}
              className={`lc-day${day.getMonth() !== cursor.getMonth() ? ' muted' : ''}${key === todayKey ? ' today' : ''}`}
            >
              <span className="lc-day-number">{pad(day.getDate())}</span>
              {dayEvents.map(renderEvent)}
            </article>
          );
        })}
      </div>
    </section>
  );
}
