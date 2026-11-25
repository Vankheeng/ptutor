import type { CalendarEvent } from '../../hooks/useLessonCalendarData';

export type StudentCalendarView = 'month' | 'week';

export type StudentCalendarPeriod = {
  startDate: string;
  endDate: string;
};

const pad = (value: number) => String(value).padStart(2, '0');

function dateKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function getStudentCalendarPeriod(cursor: Date, view: StudentCalendarView): StudentCalendarPeriod {
  if (view === 'month') {
    return {
      startDate: dateKey(new Date(cursor.getFullYear(), cursor.getMonth(), 1)),
      endDate: dateKey(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0))
    };
  }

  const start = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate());
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
  const end = new Date(start);
  end.setDate(start.getDate() + 6);

  return {
    startDate: dateKey(start),
    endDate: dateKey(end)
  };
}

export function filterStudentCalendarEvents(events: CalendarEvent[], cursor: Date, view: StudentCalendarView) {
  const { startDate, endDate } = getStudentCalendarPeriod(cursor, view);

  return events.filter((event) => {
    const eventDate = event.date.slice(0, 10);
    return eventDate >= startDate && eventDate <= endDate;
  });
}
