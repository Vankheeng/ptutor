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
};

export function LessonCalendarGrid({ cursor, view, events, renderEvent }: Props) {
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
                {events
                  .filter((event) => event.date === key)
                  .map((event) => {
                    const startMinutes = Number(event.startTime.slice(0, 2)) * 60 + Number(event.startTime.slice(3, 5));
                    const endMinutes = Number(event.endTime.slice(0, 2)) * 60 + Number(event.endTime.slice(3, 5));
                    const top = Math.max(0, ((startMinutes - 7 * 60) / 60) * 52);
                    const height = Math.max(40, ((endMinutes - startMinutes) / 60) * 52);
                    return (
                      <div className="lc-positioned-event" key={event.id} style={{ top, height }}>
                        {renderEvent(event)}
                      </div>
                    );
                  })}
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
