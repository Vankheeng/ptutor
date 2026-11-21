import { Link } from 'react-router-dom';
import { ProfileNameLink } from '../common-user/ProfileNameLink';
import type { TutorOffer } from '../../services/studentApi';
import { formatStudyingRequestMoney } from './studyingRequestUtils';
import { RequestOfferActions } from '../common-user/RequestOfferActions';

interface Props {
  requestStatus: string;
  offers: TutorOffer[];
  statusFilter: '' | TutorOffer['status'];
  sortOrder: 'DESC' | 'ASC';
  page: number;
  totalPages: number;
  totalElements: number;
  loading: boolean;
  error: string | null;
  busyOfferId: string | null;
  onStatusChange: (value: '' | TutorOffer['status']) => void;
  onSortChange: (value: 'DESC' | 'ASC') => void;
  onPageChange: (value: number) => void;
  onAction: (offer: TutorOffer, action: 'accept' | 'reject') => void;
  onCreateContract?: (offer: TutorOffer) => void;
}

export function Offers({
  requestStatus,
  offers,
  statusFilter,
  sortOrder,
  page,
  totalPages,
  totalElements,
  loading,
  error,
  busyOfferId,
  onStatusChange,
  onSortChange,
  onPageChange,
  onAction,
  onCreateContract
}: Props) {
  return (
    <section id="tutor-offers" className="student-panel student-tutor-offers">
      <div className="student-tutor-offers-heading">
        <h2>Đề nghị từ gia sư</h2>
      </div>
      <div className="student-offer-filters">
        <label>
          <span>Trạng thái</span>
          <select
            value={statusFilter}
            onChange={(event) => onStatusChange(event.target.value as '' | TutorOffer['status'])}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Đang chờ</option>
            <option value="ACCEPTED">Đã chấp nhận</option>
            <option value="REJECTED">Đã từ chối</option>
            <option value="CANCELLED">Đã hủy</option>
          </select>
        </label>
        <label>
          <span>Sắp xếp</span>
          <select value={sortOrder} onChange={(event) => onSortChange(event.target.value as 'DESC' | 'ASC')}>
            <option value="DESC">Mới nhất</option>
            <option value="ASC">Cũ nhất</option>
          </select>
        </label>
      </div>
      {error && (
        <div className="student-error student-offer-query-error" role="alert">
          {error}
        </div>
      )}
      {loading ? (
        <div className="student-offer-query-state" role="status">
          Đang tải đề nghị…
        </div>
      ) : offers.length ? (
        <div className="student-offer-list">
          {offers.map((offer) => (
            <article className="student-offer-row" key={offer.id}>
              <div className="student-offer-tutor">
                <span className="student-offer-avatar" aria-hidden="true">
                  {getTutorInitials(offer)}
                </span>
                <div>
                  <h3>
                    <ProfileNameLink role="tutor" profileId={offer.tutorId}>
                      {[offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Gia sư'}
                    </ProfileNameLink>
                  </h3>
                  <p>{formatStudyingRequestMoney(offer.proposedPrice)}</p>
                  <time dateTime={offer.createdAt}>Gửi lúc {formatOfferDate(offer.createdAt)}</time>
                </div>
              </div>

              <div className="student-offer-status">
                <span className={`student-status ${offer.status.toLowerCase()}`}>{offerStatusLabel(offer.status)}</span>
              </div>

              <div className="student-offer-actions">
                {offer.status === 'PENDING' && requestStatus === 'OPEN' && (
                  <div className="student-form-actions">
                    <RequestOfferActions
                      role="student"
                      status={offer.status}
                      busy={busyOfferId === offer.id}
                      onAccept={() => onAction(offer, 'accept')}
                      onReject={() => onAction(offer, 'reject')}
                    />
                  </div>
                )}
                {offer.status === 'ACCEPTED' && ['OPEN', 'MATCHED'].includes(requestStatus) && onCreateContract && (
                  <button
                    className="student-button student-offer-create"
                    type="button"
                    onClick={() => onCreateContract(offer)}
                  >
                    Tạo hợp đồng
                  </button>
                )}
                {!['PENDING', 'ACCEPTED'].includes(offer.status) && (
                  <Link
                    className="student-button student-offer-view"
                    to={`/student/studying-requests/${offer.studyingRequestId}/tutor-requests/${offer.id}`}
                  >
                    Xem
                  </Link>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : error ? null : (
        <div className="student-empty">
          <h3>{totalElements ? 'Không có đề nghị phù hợp' : 'Chưa có đề nghị nào'}</h3>
          <p>
            {totalElements ? 'Thử thay đổi từ khóa hoặc điều kiện lọc.' : 'Đề nghị từ gia sư sẽ được hiển thị tại đây.'}
          </p>
        </div>
      )}
      {!loading && totalPages > 1 && (
        <div className="student-offer-pagination">
          <span>
            Trang {page + 1}/{totalPages} · {totalElements} đề nghị
          </span>
          <div>
            <button
              type="button"
              className="student-button secondary"
              disabled={page === 0}
              onClick={() => onPageChange(page - 1)}
            >
              Trước
            </button>
            <button
              type="button"
              className="student-button secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
            >
              Sau
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function getTutorInitials(offer: TutorOffer) {
  const initials = [offer.tutorFirstName, offer.tutorLastName]
    .filter(Boolean)
    .map((part) => part!.trim().charAt(0).toUpperCase())
    .join('');
  return initials || 'T';
}

function offerStatusLabel(status: TutorOffer['status']) {
  return {
    PENDING: 'Đang chờ',
    ACCEPTED: 'Đã chấp nhận',
    REJECTED: 'Đã từ chối',
    CANCELLED: 'Đã hủy'
  }[status];
}

function formatOfferDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}
