import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { errorMessage } from '../../services/errorMessages';
import {
  createStudentApplication,
  getTeachingRequest,
  getTeachingRequests,
  type LearningMode
} from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { formatCurrency } from '../../utils/formatters';
import { StudentTutorRecommendations } from './StudentTutorRecommendations';

/**
 * Two ways to find a tutor: ranked for this student (the default), or every open posting, newest first.
 * {@code ?posting=<id>} (from a match notification) opens that posting first.
 */
export function StudentTeachingRequestsPage() {
  const [mode, setMode] = useState<'RECOMMENDED' | 'ALL'>('RECOMMENDED');
  const [searchParams, setSearchParams] = useSearchParams();
  const postingId = searchParams.get('posting');

  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">TÌM GIA SƯ</span>
          <h1>Tìm gia sư</h1>
          <p>Chọn tiêu chí để nhận gợi ý xếp theo mức phù hợp, hoặc xem toàn bộ tin dạy đang mở.</p>
        </div>
      </div>
      {postingId && (
        <NotifiedPosting
          postingId={postingId}
          onClose={() => {
            searchParams.delete('posting');
            setSearchParams(searchParams, { replace: true });
          }}
        />
      )}
      <div className="student-filter-tabs rec-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'RECOMMENDED'}
          className={mode === 'RECOMMENDED' ? 'active' : ''}
          onClick={() => setMode('RECOMMENDED')}
        >
          Gợi ý cho bạn
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'ALL'}
          className={mode === 'ALL' ? 'active' : ''}
          onClick={() => setMode('ALL')}
        >
          Tất cả
        </button>
      </div>
      {mode === 'RECOMMENDED' ? <StudentTutorRecommendations /> : <AllTeachingRequests />}
    </>
  );
}

/** The posting a match notification pointed to, with the form to apply. */
function NotifiedPosting({ postingId, onClose }: { postingId: string; onClose: () => void }) {
  const [posting, setPosting] = useState<TeachingRequest | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getTeachingRequest(postingId)
      .then(setPosting)
      .catch((caught) => setError(errorMessage(caught, 'Không mở được tin dạy này; có thể tin đã đóng.')));
  }, [postingId]);

  if (error) {
    return (
      <div className="student-error" role="alert">
        {error}
      </div>
    );
  }
  if (!posting) return null;
  return (
    <section className="student-panel rec-notified">
      <span className="student-eyebrow">TIN DẠY ĐƯỢC GỢI Ý CHO BẠN</span>
      <h2>{posting.title || posting.subjectName || 'Tin dạy'}</h2>
      <div className="rec-facts">
        <span>{posting.tutorName}</span>
        <span>{posting.subjectName || posting.customSubjectName}</span>
        <span>{posting.expectedPrice ? `${formatCurrency(posting.expectedPrice)} / buổi` : 'Học phí thoả thuận'}</span>
        <span>{posting.teachingMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</span>
        {posting.districts.length > 0 && <span>{posting.districts.map((district) => district.name).join(', ')}</span>}
      </div>
      {(posting.description || posting.note) && <p>{posting.description || posting.note}</p>}
      {notice ? (
        <div className="student-notice" role="status">
          {notice}
        </div>
      ) : (
        <ApplyForm
          posting={posting}
          onSent={() => setNotice(`Đã gửi đơn đăng ký tới ${posting.tutorName}.`)}
          onCancel={onClose}
        />
      )}
    </section>
  );
}

function AllTeachingRequests() {
  const [requests, setRequests] = useState<TeachingRequest[]>([]);
  const [active, setActive] = useState<TeachingRequest | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getTeachingRequests()
      .then(setRequests)
      .catch((caught) => setError(errorMessage(caught, 'Không tải được danh sách tin dạy.')));
  }, []);

  return (
    <>
      {notice && (
        <div className="student-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      {active && (
        <section className="student-panel" style={{ marginBottom: 16 }}>
          <h2>Đăng ký học: {active.title || active.subjectName || 'tin dạy'}</h2>
          <ApplyForm
            posting={active}
            onSent={() => {
              setNotice(`Đã gửi đơn đăng ký tới ${active.tutorName}.`);
              setActive(null);
            }}
            onCancel={() => setActive(null)}
          />
        </section>
      )}
      <div className="student-card-grid">
        {requests.map((request) => (
          <article className="student-panel student-request-card" key={request.id}>
            <div className="student-card-top">
              <span className="student-tag">{request.subjectName || request.customSubjectName || 'Môn học'}</span>
              <span>{request.teachingMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</span>
            </div>
            <h3>{request.title || 'Tin dạy'}</h3>
            <p>{request.description || request.note || 'Chưa có mô tả.'}</p>
            <div className="student-card-actions">
              <span>{request.tutorName}</span>
              <button className="student-button" type="button" onClick={() => setActive(request)}>
                Đăng ký học
              </button>
            </div>
          </article>
        ))}
      </div>
      {!requests.length && !error && (
        <div className="student-empty">
          <h3>Chưa có tin dạy nào đang mở</h3>
          <p>Tin mới sẽ xuất hiện ở đây.</p>
        </div>
      )}
    </>
  );
}

/** Applying to a teaching posting: the grade among the posting's, the price and the schedule wished for. */
function ApplyForm({
  posting,
  onSent,
  onCancel
}: {
  posting: TeachingRequest;
  onSent: () => void;
  onCancel: () => void;
}) {
  const [error, setError] = useState('');
  const [isSending, setIsSending] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const value = (key: string) => String(values.get(key) ?? '').trim();
    setError('');
    setIsSending(true);
    try {
      await createStudentApplication(posting.id, {
        gradeId: value('gradeId'),
        proposedPrice: Number(value('proposedPrice')),
        learningMode: value('learningMode') as LearningMode,
        preferredSchedule: value('preferredSchedule') || undefined,
        message: value('message') || undefined
      });
      onSent();
    } catch (caught) {
      setError(errorMessage(caught, 'Chưa gửi được đơn đăng ký.'));
    } finally {
      setIsSending(false);
    }
  };

  return (
    <form className="student-form-grid" onSubmit={(event) => void submit(event)}>
      {error && (
        <div className="student-error student-field-full" role="alert">
          {error}
        </div>
      )}
      <label>
        Lớp
        <select name="gradeId" required defaultValue="">
          <option value="">Chọn lớp</option>
          {posting.grades.map((grade) => (
            <option key={grade.id} value={grade.id}>
              {grade.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Học phí đề xuất mỗi buổi
        <input name="proposedPrice" type="number" min="0" step="10000" required defaultValue={posting.expectedPrice ?? ''} />
      </label>
      <label>
        Hình thức học
        <select name="learningMode" defaultValue={posting.teachingMode}>
          <option value="ONLINE">Online</option>
          <option value="OFFLINE">Trực tiếp</option>
        </select>
      </label>
      <label>
        Lịch học mong muốn
        <input name="preferredSchedule" placeholder="Ví dụ: tối thứ 3 và thứ 5" />
      </label>
      <label className="student-field-full">
        Lời nhắn
        <textarea name="message" rows={3} />
      </label>
      <div className="student-form-actions">
        <button className="student-button secondary" type="button" onClick={onCancel}>
          Đóng
        </button>
        <button className="student-button" type="submit" disabled={isSending}>
          {isSending ? 'Đang gửi…' : 'Gửi đơn đăng ký'}
        </button>
      </div>
    </form>
  );
}
