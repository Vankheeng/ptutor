import { useEffect, useState } from 'react';
import { ProfileNameLink } from '../common-user/ProfileNameLink';
import type { TutorOffer } from '../../services/studentApi';
import { formatStudyingRequestMoney } from './studyingRequestUtils';
import { RequestOfferActions } from '../common-user/RequestOfferActions';
import { tutorScheduleLabel } from '../../utils/tutorDisplay';

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
  const [selectedOffer, setSelectedOffer] = useState<TutorOffer | null>(null);

  useEffect(() => {
    if (!selectedOffer) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelectedOffer(null);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [selectedOffer]);

  const handleOfferAction = (offer: TutorOffer, action: 'accept' | 'reject') => {
    setSelectedOffer(null);
    onAction(offer, action);
  };

  return (
    <>
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
                  <span className={`student-status ${offer.status.toLowerCase()}`}>
                    {offerStatusLabel(offer.status)}
                  </span>
                </div>
                <button
                  className="student-button student-offer-detail-trigger"
                  type="button"
                  onClick={() => setSelectedOffer(offer)}
                >
                  Xem chi tiết
                </button>
              </article>
            ))}
          </div>
        ) : error ? null : (
          <div className="student-empty">
            <h3>{totalElements ? 'Không có đề nghị phù hợp' : 'Chưa có đề nghị nào'}</h3>
            <p>
              {totalElements
                ? 'Thử thay đổi từ khóa hoặc điều kiện lọc.'
                : 'Đề nghị từ gia sư sẽ được hiển thị tại đây.'}
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

      {selectedOffer && (
        <TutorOfferDetailModal
          offer={selectedOffer}
          requestStatus={requestStatus}
          busy={busyOfferId === selectedOffer.id}
          onClose={() => setSelectedOffer(null)}
          onAction={(action) => handleOfferAction(selectedOffer, action)}
          onCreateContract={
            onCreateContract
              ? () => {
                  setSelectedOffer(null);
                  onCreateContract(selectedOffer);
                }
              : undefined
          }
        />
      )}
    </>
  );
}

interface TutorOfferDetailModalProps {
  offer: TutorOffer;
  requestStatus: string;
  busy: boolean;
  onClose: () => void;
  onAction: (action: 'accept' | 'reject') => void;
  onCreateContract?: () => void;
}

function TutorOfferDetailModal({
  offer,
  requestStatus,
  busy,
  onClose,
  onAction,
  onCreateContract
}: TutorOfferDetailModalProps) {
  const tutorName = [offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const canAct =
    (offer.status === 'PENDING' && requestStatus === 'OPEN') ||
    (offer.status === 'ACCEPTED' && ['OPEN', 'MATCHED'].includes(requestStatus));

  return (
    <div className="student-offer-modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="student-panel student-offer-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="student-offer-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="student-offer-modal-header">
          <h2 id="student-offer-modal-title">Chi tiết đề nghị từ gia sư</h2>
          <button className="student-offer-modal-close" type="button" aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </header>

        <div className="student-tutor-offer-detail-grid">
          <div className="student-tutor-offer-detail-item">
            <span>Gia sư</span>
            <strong>
              <ProfileNameLink role="tutor" profileId={offer.tutorId}>
                {tutorName}
              </ProfileNameLink>
            </strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Email</span>
            <strong>{offer.tutorEmail}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Số điện thoại liên hệ</span>
            <strong>{offer.tutorPhone || 'Chưa cung cấp'}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Yêu cầu học</span>
            <strong>{offer.studyingRequestTitle || 'Yêu cầu học của bạn'}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Môn học</span>
            <strong>{offer.subjectName || 'Chưa cập nhật'}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Lớp</span>
            <strong>{offer.gradeName || 'Chưa cập nhật'}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Học phí đề xuất</span>
            <strong>{formatStudyingRequestMoney(offer.proposedPrice)}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Hình thức dạy</span>
            <strong>{offer.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Lịch dạy đề xuất</span>
            <strong>{tutorScheduleLabel(offer.preferredSchedule, 'Linh hoạt')}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Trạng thái</span>
            <strong>
              <span className={`student-status ${offer.status.toLowerCase()}`}>{offerStatusLabel(offer.status)}</span>
            </strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Thời gian gửi</span>
            <strong>{formatOfferDate(offer.createdAt)}</strong>
          </div>
          <div className="student-tutor-offer-detail-item">
            <span>Cập nhật lần cuối</span>
            <strong>{formatOfferDate(offer.updatedAt)}</strong>
          </div>
        </div>
        <div className="student-offer-modal-message">
          <span>Lời nhắn của gia sư</span>
          <p>{offer.message?.trim() || 'Gia sư không gửi kèm lời nhắn.'}</p>
        </div>

        <footer className="student-offer-modal-actions">
          {canAct && (
            <RequestOfferActions
              role="student"
              status={offer.status}
              busy={busy}
              canAct={canAct}
              onAccept={() => onAction('accept')}
              onReject={() => onAction('reject')}
            />
          )}
          {offer.status === 'ACCEPTED' && canAct && onCreateContract && (
            <button className="student-button draft" type="button" onClick={onCreateContract}>
              Tạo hợp đồng
            </button>
          )}
          <button className="student-button secondary" type="button" onClick={onClose}>
            Đóng
          </button>
        </footer>
      </section>
    </div>
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
    CONNECTED: 'Đã kết nối',
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
