import { useCallback, useMemo, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getFavoriteTutorRequests, removeFavoriteTutor } from '../../services/recommendationApi';
import { getStudentApplications, type StudentApplication } from '../../services/studentApi';
import { errorMessage } from '../../services/errorMessages';
import { StudentTeachingRequestCard } from './StudentTeachingRequestCard';

interface Props {
  onFavoritesChanged: () => void;
}

export function FavoriteTutorResults({ onFavoritesChanged }: Props) {
  const [revision, setRevision] = useState(0);
  const [removingId, setRemovingId] = useState('');
  const [removedIds, setRemovedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState('');
  const load = useCallback(async (signal: AbortSignal) => {
    const [requests, applications] = await Promise.all([
      getFavoriteTutorRequests(signal),
      getStudentApplications(undefined, 0, 100, signal)
    ]);
    return { requests, applications: applications.content };
  }, []);
  const query = useResource(load, revision);
  const applicationsByRequest = useMemo(() => {
    const byRequest = new Map<string, StudentApplication>();
    query.data?.applications.forEach((application) => {
      if (!byRequest.has(application.teachingRequestId)) byRequest.set(application.teachingRequestId, application);
    });
    return byRequest;
  }, [query.data?.applications]);
  const visibleRequests = query.data?.requests.filter((request) => !removedIds.has(request.tutorId)) ?? [];

  const remove = async (tutorId: string) => {
    setRemovingId(tutorId);
    setError('');
    try {
      await removeFavoriteTutor(tutorId);
      setRemovedIds((previous) => new Set(previous).add(tutorId));
      setRevision((value) => value + 1);
      onFavoritesChanged();
    } catch (caught) {
      setError(errorMessage(caught, 'Chưa bỏ quan tâm được gia sư. Vui lòng thử lại.'));
    } finally {
      setRemovingId('');
    }
  };

  return (
    <section className="tutor-marketplace" aria-label="Gia sư đã quan tâm">
      <div className="tutor-section-heading">
        <h2>Gia sư đã quan tâm</h2>
        <span className="tutor-count">{visibleRequests.length} gia sư</span>
      </div>
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      {query.error ? (
        <div className="tutor-error" role="alert">
          {query.error}
        </div>
      ) : query.isLoading ? (
        <div className="tutor-request-grid">
          {[0, 1, 2].map((index) => (
            <div className="tutor-skeleton" key={index} />
          ))}
        </div>
      ) : visibleRequests.length ? (
        <div className="tutor-request-grid tutor-student-search-grid">
          {visibleRequests.map((request) => (
            <StudentTeachingRequestCard
              key={request.id}
              request={request}
              application={applicationsByRequest.get(request.id)}
              onChanged={() => setRevision((value) => value + 1)}
              favorite
              onToggleFavorite={() => void remove(request.tutorId)}
              favoritePending={removingId === request.tutorId}
            />
          ))}
        </div>
      ) : (
        <div className="tutor-empty">
          <h3>Chưa có gia sư đã quan tâm</h3>
          <p>Bấm biểu tượng trái tim trên thẻ gợi ý để lưu gia sư tại đây.</p>
        </div>
      )}
    </section>
  );
}
