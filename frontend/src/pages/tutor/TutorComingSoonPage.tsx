import { Link, useParams } from 'react-router-dom';

const copy: Record<string, { title: string; description: string }> = {
  'teaching-requests': {
    title: 'Yêu cầu dạy của tôi',
    description: 'Quản lý các yêu cầu dạy bạn đã đăng cho học viên.'
  },
  contracts: { title: 'Hợp đồng', description: 'Xem chi tiết hợp đồng và quản lý thỏa thuận với học viên.' },
  calendar: { title: 'Lịch dạy', description: 'Xem lịch dạy và lên kế hoạch cho các buổi học sắp tới.' },
  notifications: {
    title: 'Thông báo',
    description: 'Cập nhật về đề xuất, buổi học và hoạt động tài khoản.'
  },
  wallet: { title: 'Ví tiền', description: 'Xem số dư và các giao dịch trong ví gia sư.' }
};

export function TutorComingSoonPage() {
  const { section = '' } = useParams();
  const page = copy[section] ?? {
    title: 'Không gian gia sư',
    description: 'Mục này sẽ sớm được đưa vào hoạt động.'
  };
  return (
    <section className="tutor-panel tutor-coming-soon">
      <span className="tutor-eyebrow">KHÔNG GIAN GIA SƯ</span>
      <h1>{page.title}</h1>
      <p>{page.description}</p>
      <span className="tutor-coming-badge">Sắp ra mắt</span>
      <Link className="tutor-detail-link" to="/tutor/dashboard">
        ← Về trang tổng quan
      </Link>
    </section>
  );
}
