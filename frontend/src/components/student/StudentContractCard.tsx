import { Link } from 'react-router-dom';
import type { Contract } from '../../services/studentApi';
import { Icon } from '../ui/Icon';
import { StudentContractStatusBadge } from './StudentContractStatusBadge';

export function StudentContractCard({ contract }: { contract: Contract }) {
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const tutorInitials = tutorName
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
  const subjectName = contract.subjectName || 'Môn học';
  const gradeName = contract.gradeName || 'Chưa có lớp';
  const paymentPeriod = formatPaymentPeriod(contract.paymentPeriod);

  return (
    <article className="student-contract-list-card student-contract-card">
      <div className="student-contract-subject">
        <span className="student-contract-subject-icon" aria-hidden="true">
          <Icon name="contract" size={22} />
        </span>
        <div>
          <strong>{subjectName}</strong>
          <span>{gradeName}</span>
        </div>
      </div>

      <div className="student-contract-tutor">
        <span className="student-contract-tutor-avatar" aria-hidden="true">
          {tutorInitials}
        </span>
        <div>
          <strong>{tutorName}</strong>
          <span>Gia sư</span>
        </div>
      </div>

      <div className="student-contract-terms">
        <strong>
          {formatMoney(contract.price)} / {paymentPeriod.toLowerCase()}
        </strong>
        <span>{paymentPeriod}</span>
        <span className="student-contract-date">
          <span className="student-contract-calendar-icon" aria-hidden="true" />
          {formatDate(contract.startDate)} - {formatDate(contract.endDate)}
        </span>
      </div>

      <div className="student-contract-status">
        <StudentContractStatusBadge status={contract.status} />
      </div>

      <div className="student-contract-action">
        <Link className="student-button secondary student-contract-view" to={`/student/contracts/${contract.id}`}>
          <span>Xem hợp đồng</span>
          <span aria-hidden="true">&rarr;</span>
        </Link>
      </div>
    </article>
  );
}

function formatMoney(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VND`;
}

function formatPaymentPeriod(value: Contract['paymentPeriod']) {
  return {
    PER_LESSON: 'Mỗi buổi',
    WEEKLY: 'Hàng tuần',
    MONTHLY: 'Hàng tháng',
    PACKAGE: 'Theo gói'
  }[value];
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value));
}
