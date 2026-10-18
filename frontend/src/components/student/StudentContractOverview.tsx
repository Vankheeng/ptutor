import type { ReactNode } from 'react';
import type { Contract } from '../../services/studentApi';
import { Icon } from '../ui/Icon';

export function StudentContractOverview({ contract, createdByYou }: { contract: Contract; createdByYou: boolean }) {
  const leftRows = [
    ['Subject', contract.subjectName || 'Not specified'],
    ['Grade', contract.gradeName || 'Not specified'],
    ['Teaching mode', contract.teachingMode === 'ONLINE' ? 'Online' : 'In person'],
    ['Price', formatMoney(contract.price)],
    ['Payment period', formatPaymentPeriod(contract.paymentPeriod)],
    ['Total lessons', `${contract.totalLessons} lessons`]
  ];
  const rightRows: [string, ReactNode][] = [
    [
      'Preferred schedule',
      contract.schedules?.length ? formatSchedules(contract.schedules) : contract.preferredSchedule
    ],
    ['Start date', formatDateOnly(contract.startDate)],
    ['End date', formatDateOnly(contract.endDate)],
    ['Created by', createdByYou ? 'You' : 'Tutor'],
    ['Signed at', contract.signedAt ? formatDate(contract.signedAt) : '-']
  ];

  return (
    <section className="student-panel student-contract-overview">
      <h2 className="student-contract-panel-heading">
        <Icon name="contract" size={16} /> Contract Overview
      </h2>
      <div className="student-contract-overview-grid">
        <div>
          {leftRows.map(([label, value]) => (
            <OverviewRow key={label} label={label} value={value} />
          ))}
        </div>
        <div>
          {rightRows.map(([label, value]) => (
            <OverviewRow key={label} label={label} value={value} />
          ))}
        </div>
      </div>
    </section>
  );
}

function OverviewRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="student-detail-row">
      <span>{label}</span>
      <div className="student-detail-value">{value}</div>
    </div>
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value));
}

function formatPaymentPeriod(value: Contract['paymentPeriod']) {
  return {
    PER_LESSON: 'Per lesson',
    WEEKLY: 'Weekly',
    MONTHLY: 'Monthly',
    PACKAGE: 'Package'
  }[value];
}

function formatSchedules(schedules: Contract['schedules']) {
  const names = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  return (
    <span className="student-contract-schedule-summary">
      {schedules.map((schedule, index) => (
        <span key={`${schedule.dayOfWeek}-${schedule.startTime}-${index}`}>
          {names[schedule.dayOfWeek] ?? 'Day'} {schedule.startTime} - {schedule.endTime}
        </span>
      ))}
    </span>
  );
}
