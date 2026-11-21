import type { RefundRequest } from '../../services/refundApi';
import { formatLocalDateTime } from './refundFormat';
import { RefundStatusBadge } from './RefundRequestOverview';

export function RefundRequestSidebar({ request }: { request: RefundRequest }) {
  const final = ['REFUNDED', 'REJECTED', 'CANCELLED'].includes(request.status);
  const reviewed = request.status !== 'PENDING' && request.status !== 'CANCELLED';
  const resultText =
    request.status === 'REFUNDED'
      ? 'Yêu cầu đã được hoàn tiền.'
      : request.status === 'REJECTED'
        ? 'Yêu cầu đã bị từ chối.'
        : request.status === 'CANCELLED'
          ? 'Yêu cầu đã bị hủy.'
          : 'Chưa có kết quả.';
  return (
    <aside className="rf-detail-sidebar">
      <section className="rf-card rf-timeline-card">
        <h2>Tiến trình xử lý</h2>
        <div className="rf-timeline">
          <div className="rf-timeline-step done">
            <b>✓</b>
            <span>
              <strong>Đã gửi yêu cầu</strong>
              <small>{formatLocalDateTime(request.createdAt)}</small>
            </span>
          </div>
          <div className={`rf-timeline-step${reviewed ? ' done' : ' active'}`}>
            <b>2</b>
            <span>
              <strong>Đang xem xét</strong>
              <small>{reviewed ? 'Yêu cầu đang được xử lý.' : 'Yêu cầu đang chờ tiếp nhận.'}</small>
            </span>
          </div>
          <div className={`rf-timeline-step${final ? ' done' : ''}`}>
            <b>3</b>
            <span>
              <strong>Kết quả xử lý</strong>
              <small>{resultText}</small>
            </span>
          </div>
        </div>
      </section>
      <section className="rf-card rf-result-card">
        <h2>Kết quả xử lý</h2>
        <div className="rf-result-highlight">
          <RefundStatusBadge status={request.status} />
        </div>
        <div className="rf-result-row">
          <span>Ngày tạo</span>
          <strong>{formatLocalDateTime(request.createdAt)}</strong>
        </div>
        <div className="rf-result-row">
          <span>Cập nhật lần cuối</span>
          <strong>{formatLocalDateTime(request.updatedAt)}</strong>
        </div>
      </section>
    </aside>
  );
}
