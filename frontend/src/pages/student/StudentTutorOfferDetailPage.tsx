import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getTutorOffer, type TutorOffer } from '../../services/studentApi';
import { formatStudyingRequestDate, formatStudyingRequestMoney } from '../../components/student/studyingRequestUtils';

export function StudentTutorOfferDetailPage() {
  const { studyingRequestId = '', tutorRequestId = '' } = useParams();
  const load = useCallback(() => getTutorOffer(studyingRequestId, tutorRequestId), [studyingRequestId, tutorRequestId]);
  const query = useResource(load);
  const offer: TutorOffer | null = query.data ?? null;

  if (query.error)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!offer)
    return (
      <div className="student-empty">
        <h3>Đang tải đề nghị của gia sư…</h3>
      </div>
    );

  const tutorName = [offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';

  return (
    <>
      <Link className="student-link" to={`/student/studying-requests/${studyingRequestId}#tutor-offers`}>
        ← Quay lại các đề nghị từ gia sư
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">ĐỀ NGHỊ DẠY HỌC</span>
          <h1>Chi tiết đề nghị từ gia sư</h1>
          <p>Xem đề nghị của gia sư cho yêu cầu tìm gia sư của bạn.</p>
        </div>
      </div>
      <section className="student-panel student-tutor-offer-detail">
        <div className="student-tutor-offer-detail-header">
          <div className="student-offer-tutor">
            <span className="student-offer-avatar" aria-hidden="true">
              {getInitials(offer)}
            </span>
            <div>
              <h2>{tutorName}</h2>
              <p>{offer.tutorEmail}</p>
            </div>
          </div>
          <span className={`student-status ${offer.status.toLowerCase()}`}>{offerStatusLabel(offer.status)}</span>
        </div>
        <div className="student-tutor-offer-detail-grid">
          <Detail label="Yêu cầu tìm gia sư" value={offer.studyingRequestTitle || 'Yêu cầu tìm gia sư'} />
          <Detail label="Môn học" value={offer.subjectName || 'Chưa xác định'} />
          <Detail label="Lớp" value={offer.gradeName || 'Chưa xác định'} />
          <Detail label="Giá đề nghị" value={formatStudyingRequestMoney(offer.proposedPrice)} />
          <Detail label="Hình thức dạy" value={offer.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
          <Detail label="Lịch học mong muốn" value={offer.preferredSchedule || 'Linh hoạt'} />
          <Detail label="Lời nhắn" value={offer.message || 'Không có lời nhắn'} />
          <Detail label="Gửi lúc" value={formatStudyingRequestDate(offer.createdAt)} />
          <Detail label="Cập nhật lúc" value={formatStudyingRequestDate(offer.updatedAt)} />
        </div>
      </section>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="student-tutor-offer-detail-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function getInitials(offer: TutorOffer) {
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
