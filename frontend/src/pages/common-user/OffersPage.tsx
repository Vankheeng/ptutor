import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  acceptTutorOffer,
  getMyStudyingRequests,
  getTutorOffers,
  rejectTutorOffer,
  type ApplicationStatus,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import { RequestOfferActions } from '../../components/common-user/RequestOfferActions';
import '../tutor/tutor.css';

type OfferWithRequest = TutorOffer & { request: StudyingRequest };
const statuses: Array<'ALL' | ApplicationStatus> = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'];

export function OffersPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<'ALL' | ApplicationStatus>('ALL');
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(async (signal: AbortSignal) => {
    const requestPage = await getMyStudyingRequests(undefined, 0, 100, signal, 'DESC');
    const groups = await Promise.all(
      requestPage.content.map(async (request) => {
        const offers = await getTutorOffers(request.id, undefined, 0, 100, signal);
        return offers.content.map((offer) => ({ ...offer, request }));
      })
    );
    return groups.flat();
  }, []);
  const query = useResource(load, revision);
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return (query.data ?? []).filter((offer) => {
      const searchable = [
        tutorName(offer),
        offer.tutorEmail,
        offer.subjectName,
        offer.gradeName,
        offer.studyingRequestTitle,
        offer.message,
        offer.preferredSchedule
      ]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('vi-VN');
      const date = offer.createdAt.slice(0, 10);
      return (
        (status === 'ALL' || offer.status === status) &&
        (!term || searchable.includes(term)) &&
        (!from || date >= from) &&
        (!to || date <= to)
      );
    });
  }, [from, query.data, search, status, to]);
  const act = async (offer: OfferWithRequest, action: 'accept' | 'reject') => {
    setBusy(offer.id);
    try {
      await (action === 'accept'
        ? acceptTutorOffer(offer.request.id, offer.id)
        : rejectTutorOffer(offer.request.id, offer.id));
      setRevision((value) => value + 1);
    } finally {
      setBusy(null);
    }
  };
  return (
    <>
      <div className="tutor-page-heading tutor-proposals-heading">
        <div>
          <h1>Đề nghị từ gia sư</h1>
        </div>
        <div className="tutor-heading-actions">
          <span className="tutor-count">{filtered.length} đề nghị</span>
        </div>
      </div>
      <section className="tutor-panel tutor-proposals-filter-panel" aria-label="Bộ lọc đề nghị">
        <div className="tutor-proposals-filter-grid">
          <Filter label="Từ ngày">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Filter>
          <Filter label="Đến ngày">
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Filter>
          <Filter label="Trạng thái">
            <select value={status} onChange={(event) => setStatus(event.target.value as 'ALL' | ApplicationStatus)}>
              {statuses.map((item) => (
                <option key={item} value={item}>
                  {item === 'ALL' ? 'Tất cả trạng thái' : statusLabel(item)}
                </option>
              ))}
            </select>
          </Filter>
          <Filter label="Tìm kiếm">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm gia sư, môn học hoặc nội dung..."
            />
          </Filter>
        </div>
      </section>
      {query.error && (
        <div className="tutor-error" role="alert">
          {query.error}
        </div>
      )}
      {query.isLoading ? (
        <div className="tutor-empty">
          <h3>Đang tải đề nghị...</h3>
        </div>
      ) : filtered.length === 0 ? (
        <div className="tutor-empty">
          <h3>Không tìm thấy đề nghị phù hợp</h3>
          <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.</p>
        </div>
      ) : (
        <section className="tutor-proposals-table-wrap">
          <table className="tutor-proposals-table">
            <thead>
              <tr>
                <th>Gia sư</th>
                <th>Yêu cầu / Môn học</th>
                <th>Nội dung đề nghị</th>
                <th>Thời gian gửi</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((offer) => (
                <tr key={offer.id}>
                  <td>
                    <strong>{tutorName(offer)}</strong>
                    <small>{offer.tutorEmail}</small>
                  </td>
                  <td>
                    <strong>{offer.request.title || offer.studyingRequestTitle || 'Yêu cầu tìm gia sư'}</strong>
                    <small>
                      {offer.subjectName} · {offer.gradeName}
                    </small>
                  </td>
                  <td>
                    <span className="tutor-proposal-message">
                      {offer.message || offer.preferredSchedule || 'Không có lời nhắn'}
                    </span>
                  </td>
                  <td>{formatDateTime(offer.createdAt)}</td>
                  <td>
                    <span className={`tutor-proposal-status status-${offer.status.toLowerCase()}`}>
                      {statusLabel(offer.status)}
                    </span>
                  </td>
                  <td>
                    <div className="tutor-proposal-row-actions">
                      <button
                        className="tutor-button secondary small"
                        type="button"
                        onClick={() => navigate(`/student/studying-requests/${offer.request.id}`)}
                      >
                        Xem chi tiết
                      </button>
                      <RequestOfferActions
                        role="student"
                        status={offer.status}
                        busy={busy === offer.id}
                        canAct={offer.request.status === 'OPEN'}
                        onAccept={() => void act(offer, 'accept')}
                        onReject={() => void act(offer, 'reject')}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}

function Filter({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="tutor-proposals-filter">
      <span>{label}</span>
      {children}
    </label>
  );
}
function tutorName(offer: TutorOffer) {
  return [offer.tutorFirstName, offer.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
}
function statusLabel(status: ApplicationStatus | 'ALL') {
  return { ALL: 'Tất cả', PENDING: 'Chờ xử lý', ACCEPTED: 'Đã chấp nhận', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' }[
    status
  ];
}
function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}
