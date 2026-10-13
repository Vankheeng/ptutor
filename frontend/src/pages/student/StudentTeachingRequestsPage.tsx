import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { createStudentApplication, getTeachingRequests, type LearningMode } from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { StudentTutorRecommendations } from './StudentTutorRecommendations';

/** Two ways to find a tutor: ranked for this student (the default), or every open posting, newest first. */
export function StudentTeachingRequestsPage() {
  const [mode, setMode] = useState<'RECOMMENDED' | 'ALL'>('RECOMMENDED');

  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">FIND YOUR TUTOR</span>
          <h1>Tìm gia sư</h1>
          <p>Chọn tiêu chí để nhận gợi ý xếp theo mức phù hợp, hoặc xem toàn bộ tin dạy đang mở.</p>
        </div>
      </div>
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

function AllTeachingRequests() {
  const [requests, setRequests] = useState<TeachingRequest[]>([]);
  const [active, setActive] = useState<TeachingRequest | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getTeachingRequests()
      .then(setRequests)
      .catch((caught) => setError(caught instanceof Error ? caught.message : 'Could not load teaching requests.'));
  }, []);

  const apply = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!active) return;
    const values = new FormData(event.currentTarget);
    const value = (key: string) => String(values.get(key) ?? '').trim();
    setError('');
    try {
      await createStudentApplication(active.id, {
        gradeId: value('gradeId'),
        proposedPrice: Number(value('proposedPrice')),
        learningMode: value('learningMode') as LearningMode,
        preferredSchedule: value('preferredSchedule') || undefined,
        message: value('message') || undefined
      });
      setNotice('Your application was sent.');
      setActive(null);
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not send your application.'
      );
    }
  };

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
          <h2>Apply to {active.title || active.subjectName || 'this request'}</h2>
          <form className="student-form-grid" onSubmit={(event) => void apply(event)}>
            <label>
              Grade ID
              <input name="gradeId" required placeholder="Grade UUID" />
            </label>
            <label>
              Proposed price
              <input name="proposedPrice" type="number" min="0" required />
            </label>
            <label>
              Learning mode
              <select name="learningMode" defaultValue="ONLINE">
                <option value="ONLINE">Online</option>
                <option value="OFFLINE">In person</option>
              </select>
            </label>
            <label>
              Preferred schedule
              <input name="preferredSchedule" />
            </label>
            <label className="student-field-full">
              Message
              <textarea name="message" rows={3} />
            </label>
            <div className="student-form-actions">
              <button className="student-button secondary" type="button" onClick={() => setActive(null)}>
                Cancel
              </button>
              <button className="student-button" type="submit">
                Send application
              </button>
            </div>
          </form>
        </section>
      )}
      <div className="student-card-grid">
        {requests.map((request) => (
          <article className="student-panel student-request-card" key={request.id}>
            <div className="student-card-top">
              <span className="student-tag">{request.subjectName || request.customSubjectName || 'Subject'}</span>
              <span>{request.teachingMode === 'ONLINE' ? 'Online' : 'In person'}</span>
            </div>
            <h3>{request.title || 'Teaching opportunity'}</h3>
            <p>{request.description || request.note || 'No description provided.'}</p>
            <div className="student-card-actions">
              <span>{request.tutorName}</span>
              <button className="student-button" type="button" onClick={() => setActive(request)}>
                Apply to study
              </button>
            </div>
          </article>
        ))}
      </div>
      {!requests.length && (
        <div className="student-empty">
          <h3>No teaching requests available</h3>
          <p>New opportunities will appear here.</p>
        </div>
      )}
    </>
  );
}
