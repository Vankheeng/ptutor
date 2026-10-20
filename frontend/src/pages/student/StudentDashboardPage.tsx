import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useStudentDashboard } from '../../hooks/useStudentDashboard';
import { statusLabel } from '../../utils/labels';

export function StudentDashboardPage() {
  const { user } = useAuth();
  const { data, isLoading } = useStudentDashboard();
  const firstName = user?.profile?.firstName || 'bạn';

  return (
    <>
      <section className="student-welcome">
        <div>
          <span className="student-eyebrow">GÓC HỌC TẬP CỦA BẠN</span>
          <h1>Chào mừng trở lại, {firstName}.</h1>
          <p>Mục tiêu học, trao đổi với gia sư, hợp đồng và thanh toán ở cùng một chỗ.</p>
        </div>
        <div className="student-welcome-art" aria-hidden="true" />
      </section>
      <section className="student-dashboard-stats" aria-label="Tổng quan học tập">
        <Stat
          label="Hợp đồng đang học"
          value={data?.contracts?.content.filter((item) => item.status === 'ACTIVE').length}
          loading={isLoading}
        />
        <Stat label="Tin tìm gia sư của tôi" value={data?.studyingRequests?.totalElements} loading={isLoading} />
        <Stat
          label="Đơn đang chờ trả lời"
          value={data?.applications?.content.filter((item) => item.status === 'PENDING').length}
          loading={isLoading}
        />
        <Stat label="Thông báo chưa đọc" value={data?.unreadCount} loading={isLoading} />
      </section>
      {data?.errors.length ? (
        <div className="student-error" role="alert">
          Một phần dữ liệu chưa tải được. Bạn vẫn dùng được các phần bên dưới.
        </div>
      ) : null}
      <div className="student-section-heading">
        <div>
          <span className="student-eyebrow">TRUY CẬP NHANH</span>
          <h2>Tiếp tục học</h2>
        </div>
        <Link className="student-link" to="/student/studying-requests">
          Xem tất cả tin →
        </Link>
      </div>
      <div className="student-card-grid">
        {(data?.studyingRequests?.content ?? []).slice(0, 3).map((request) => (
          <article className="student-panel student-request-card" key={request.id}>
            <div className="student-card-top">
              <span className="student-tag">{request.subjectName}</span>
              <span>{label(request.status)}</span>
            </div>
            <h3>{request.title}</h3>
            <p>{request.description || request.learningGoals || 'Chưa có mô tả.'}</p>
            <div className="student-card-actions">
              <span className={`student-status ${request.status.toLowerCase()}`}>{label(request.status)}</span>
              <Link className="student-link" to={`/student/studying-requests/${request.id}`}>
                Mở tin →
              </Link>
            </div>
          </article>
        ))}
        {!isLoading && !data?.studyingRequests?.content.length ? (
          <div className="student-empty">
            <h3>Bạn chưa có tin tìm gia sư nào</h3>
            <p>Đăng tin đầu tiên để tìm gia sư phù hợp.</p>
            <Link className="student-link" to="/student/studying-requests">
              Đăng tin →
            </Link>
          </div>
        ) : null}
      </div>
    </>
  );
}

function Stat({ label, value, loading }: { label: string; value?: number | null; loading: boolean }) {
  return (
    <article className="student-stat-card">
      <span className="student-stat-icon">✦</span>
      <div>
        <small>{label}</small>
        <strong>{loading ? '—' : (value ?? '—')}</strong>
      </div>
    </article>
  );
}

function label(value: string) {
  return statusLabel(value);
}
