import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getMyRefundRequests, type RefundRequest, type RefundRequestStatus } from '../../services/refundApi';
import { RefundStatusBadge } from '../../components/refund/RefundRequestOverview';
import {
  formatLessonDate,
  formatLocalDateTime,
  formatMoney,
  refundRequestTitle
} from '../../components/refund/refundFormat';
import { Icon } from '../../components/ui/Icon';
import '../../components/refund/refund.css';

const statusFilters: { value: 'ALL' | RefundRequestStatus; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ tiếp nhận' },
  { value: 'IN_REVIEW', label: 'Đang xử lý' },
  { value: 'REFUNDED', label: 'Đã hoàn tiền' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' }
];

const statuses = statusFilters
  .map((item) => item.value)
  .filter((value): value is RefundRequestStatus => value !== 'ALL');

function tutorInitials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0).toLocaleUpperCase('vi-VN'))
    .join('');
}

function visiblePages(currentPage: number, totalPages: number) {
  if (totalPages <= 5) return Array.from({ length: totalPages }, (_, index) => index);
  const start = Math.max(0, Math.min(currentPage - 2, totalPages - 5));
  return Array.from({ length: 5 }, (_, index) => start + index);
}

function RefundRequestCard({ request, onOpen }: { request: RefundRequest; onOpen: () => void }) {
  const lesson = request.disputedLessons[0];

  return (
    <article
      className="student-refund-list-card"
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpen();
        }
      }}
    >
      <section className="student-refund-list-card-section student-refund-list-card-main">
        <span className="student-refund-list-card-icon" aria-hidden="true">
          <Icon name="book" size={23} />
        </span>
        <div className="student-refund-list-card-copy">
          <span className="student-refund-list-subject">{refundRequestTitle(request)}</span>
          <h2>{lesson?.title || 'Buổi học yêu cầu hoàn tiền'}</h2>
          {lesson && (
            <span className="student-refund-list-meta">
              <Icon name="calendar" size={12} />
              Diễn ra: {formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}
            </span>
          )}
          <span className="student-refund-list-meta">
            <Icon name="history" size={12} />
            Tạo lúc: {formatLocalDateTime(request.createdAt)}
          </span>
        </div>
      </section>

      <section className="student-refund-list-card-section student-refund-list-card-center">
        <span className="student-refund-list-section-title">
          <Icon name="user" size={13} /> Gia sư
        </span>
        <span className="student-refund-list-tutor">
          <span className="student-refund-list-avatar" aria-hidden="true">
            {tutorInitials(request.tutorName) || '?'}
          </span>
          <span>
            <strong>{request.tutorName}</strong>
            <small>Gia sư</small>
          </span>
        </span>
      </section>

      <section className="student-refund-list-card-section student-refund-list-card-center">
        <span className="student-refund-list-section-title">
          <Icon name="wallet" size={13} /> Số tiền hoàn
        </span>
        <strong
          className={request.refundAmount === null ? 'student-refund-list-unknown' : 'student-refund-list-amount'}
        >
          {request.refundAmount === null ? 'Chưa xác định' : formatMoney(request.refundAmount)}
        </strong>
        {request.refundAmount === null && <small>Chờ kết quả xử lý</small>}
      </section>

      <section className="student-refund-list-card-section student-refund-list-card-status">
        <span className="student-refund-list-section-title">
          <Icon name="history" size={13} /> Trạng thái
        </span>
        <RefundStatusBadge status={request.status} />
      </section>
    </article>
  );
}

export function StudentRefundRequestsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<'ALL' | RefundRequestStatus>('ALL');
  const load = useCallback(
    async (signal: AbortSignal) => {
      const selectedStatus = status === 'ALL' ? undefined : status;
      const [list, ...statusPages] = await Promise.all([
        getMyRefundRequests(page, signal, selectedStatus),
        ...statuses.map((value) => getMyRefundRequests(0, signal, value, 1))
      ]);
      const counts = Object.fromEntries(
        statuses.map((value, index) => [value, statusPages[index].totalElements])
      ) as Record<RefundRequestStatus, number>;
      return {
        list,
        counts: {
          ALL: Object.values(counts).reduce((total, count) => total + count, 0),
          ...counts
        } as Record<'ALL' | RefundRequestStatus, number>
      };
    },
    [page, status]
  );
  const { data, error, isLoading } = useResource(load);
  const list = data?.list;

  return (
    <div className="rf-page student-refund-list-page">
      <header className="student-refund-list-header">
        <div className="student-refund-list-header-copy">
          <h1>Yêu cầu hoàn tiền</h1>
        </div>
        <Link className="rf-button student-refund-list-create" to="/student/refund-requests/new">
          <span aria-hidden="true">＋</span> Tạo yêu cầu hoàn tiền
        </Link>
      </header>

      <nav className="student-refund-list-tabs" aria-label="Lọc yêu cầu hoàn tiền theo trạng thái">
        {statusFilters.map((item) => (
          <button
            key={item.value}
            type="button"
            className={status === item.value ? 'active' : ''}
            aria-pressed={status === item.value}
            onClick={() => {
              setStatus(item.value);
              setPage(0);
            }}
          >
            {item.label} <span>({data?.counts[item.value] ?? 0})</span>
          </button>
        ))}
      </nav>

      {isLoading && !data && <div className="rf-card rf-state">Đang tải danh sách yêu cầu hoàn tiền…</div>}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}
      {list && list.content.length === 0 && (
        <section className="rf-card student-refund-list-empty">
          <span aria-hidden="true">
            <Icon name="refund" size={32} />
          </span>
          <h2>Không có yêu cầu hoàn tiền</h2>
          <p>Chưa có yêu cầu nào phù hợp với trạng thái đã chọn.</p>
        </section>
      )}
      {list && list.content.length > 0 && (
        <section className="student-refund-list-cards" aria-label="Danh sách yêu cầu hoàn tiền">
          {list.content.map((request) => (
            <RefundRequestCard
              key={request.id}
              request={request}
              onOpen={() => navigate(`/student/refund-requests/${request.id}`)}
            />
          ))}
        </section>
      )}

      {list && list.totalPages > 1 && (
        <nav className="student-refund-list-pagination" aria-label="Phân trang yêu cầu hoàn tiền">
          <button
            type="button"
            aria-label="Trang trước"
            disabled={list.first}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
          >
            ‹
          </button>
          {visiblePages(list.page, list.totalPages).map((index) => (
            <button
              key={index}
              type="button"
              className={index === list.page ? 'active' : ''}
              aria-current={index === list.page ? 'page' : undefined}
              onClick={() => setPage(index)}
            >
              {index + 1}
            </button>
          ))}
          <button
            type="button"
            aria-label="Trang sau"
            disabled={list.last}
            onClick={() => setPage((value) => Math.min(list.totalPages - 1, value + 1))}
          >
            ›
          </button>
        </nav>
      )}
    </div>
  );
}
