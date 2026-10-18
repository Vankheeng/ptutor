import type { ReactNode } from 'react';
import type { Contract } from '../../services/studentApi';
import { Icon } from '../ui/Icon';

export function StudentContractOverview({ contract, createdByYou }: { contract: Contract; createdByYou: boolean }) {
  const leftRows = [
    ['Môn học', contract.subjectName || 'Chưa xác định'],
    ['Lớp', contract.gradeName || 'Chưa xác định'],
    ['Hình thức dạy', contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'],
    ['Giá', formatMoney(contract.price)],
    ['Chu kỳ thanh toán', formatPaymentPeriod(contract.paymentPeriod)],
    ['Tổng số buổi', `${contract.totalLessons} buổi`]
  ];
  const rightRows: [string, ReactNode][] = [
    ['Lịch học', contract.schedules?.length ? formatSchedules(contract.schedules) : contract.preferredSchedule],
    ['Ngày bắt đầu', formatDateOnly(contract.startDate)],
    ['Ngày kết thúc', formatDateOnly(contract.endDate)],
    ['Người tạo', createdByYou ? 'Bạn' : 'Gia sư'],
    ['Thời gian ký', contract.signedAt ? formatDate(contract.signedAt) : '-']
  ];

  return (
    <section className="student-panel student-contract-overview">
      <h2 className="student-contract-panel-heading">
        <Icon name="contract" size={16} /> Tổng quan hợp đồng
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
  return `${new Intl.NumberFormat('vi-VN').format(value)} VND`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value));
}

function formatPaymentPeriod(value: Contract['paymentPeriod']) {
  return {
    PER_LESSON: 'Mỗi buổi',
    WEEKLY: 'Hàng tuần',
    MONTHLY: 'Hàng tháng',
    PACKAGE: 'Theo gói'
  }[value];
}

function formatSchedules(schedules: Contract['schedules']) {
  const names = ['', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
  return (
    <span className="student-contract-schedule-summary">
      {schedules.map((schedule, index) => (
        <span key={`${schedule.dayOfWeek}-${schedule.startTime}-${index}`}>
          {names[schedule.dayOfWeek] ?? 'Ngày'} {schedule.startTime} - {schedule.endTime}
        </span>
      ))}
    </span>
  );
}
