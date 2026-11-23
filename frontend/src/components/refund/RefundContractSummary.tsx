import { Link } from 'react-router-dom';
import type { Contract } from '../../services/studentApi';

type Props = { contract: Contract; role: 'STUDENT' | 'TUTOR' };

function date(value: string) {
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function money(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VND / buổi`;
}

export function RefundContractSummary({ contract, role }: Props) {
  const href = role === 'TUTOR' ? `/tutor/sections/contracts/${contract.id}` : `/student/contracts/${contract.id}`;
  const student = [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên';
  const tutor = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';

  return (
    <section className="rf-card rf-contract-summary" aria-labelledby="rf-contract-summary-title">
      <div className="rf-heading">
        <div>
          <span className="rf-kicker">HỢP ĐỒNG LIÊN QUAN</span>
          <h2 id="rf-contract-summary-title">{contract.subjectName || 'Hợp đồng học tập'}</h2>
        </div>
        <Link className="rf-button small secondary" to={href}>
          Xem hợp đồng
        </Link>
      </div>
      <dl className="rf-contract-summary-grid">
        <div>
          <dt>Học viên</dt>
          <dd>{student}</dd>
        </div>
        <div>
          <dt>Gia sư</dt>
          <dd>{tutor}</dd>
        </div>
        <div>
          <dt>Lớp</dt>
          <dd>{contract.gradeName || '—'}</dd>
        </div>
        <div>
          <dt>Giá mỗi buổi</dt>
          <dd>{money(contract.price)}</dd>
        </div>
        <div>
          <dt>Thời gian</dt>
          <dd>
            {date(contract.startDate)} – {date(contract.endDate)}
          </dd>
        </div>
        <div>
          <dt>Trạng thái</dt>
          <dd>{contract.status === 'ACTIVE' ? 'Đang hoạt động' : contract.status}</dd>
        </div>
      </dl>
    </section>
  );
}
