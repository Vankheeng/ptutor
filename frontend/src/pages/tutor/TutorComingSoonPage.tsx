import { Link, useParams } from 'react-router-dom';

const copy: Record<string, { title: string; description: string }> = {
  'teaching-requests': {
    title: 'Tin dạy của tôi',
    description: 'Quản lý các tin dạy bạn đã đăng cho học viên.'
  },
  contracts: { title: 'Hợp đồng', description: 'Xem và quản lý hợp đồng với học viên.' },
  calendar: { title: 'Lịch dạy', description: 'Xem lịch dạy và các buổi học sắp tới.' },
  notifications: {
    title: 'Thông báo',
    description: 'Cập nhật về đề nghị, buổi học và tài khoản.'
  },
  wallet: { title: 'Ví', description: 'Xem số dư và giao dịch ví.' }
};

export function TutorComingSoonPage() {
  const { section = '' } = useParams();
  const page = copy[section] ?? {
    title: 'Khu gia sư',
    description: 'Mục này sẽ sớm có.'
  };
  return (
    <section className="tutor-panel tutor-coming-soon">
      <span className="tutor-eyebrow">KHU GIA SƯ</span>
      <h1>{page.title}</h1>
      <p>{page.description}</p>
      <span className="tutor-coming-badge">Sắp ra mắt</span>
      <Link className="tutor-detail-link" to="/tutor/dashboard">
        ← Về trang tổng quan
      </Link>
    </section>
  );
}
