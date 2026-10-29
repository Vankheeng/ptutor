import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getAdminTutorReviews } from '../../services/adminTutorApi';
import { formatDate, formatNumber } from './adminFormat';
import { useAdminTutor } from './adminTutorContext';
import { AdminPagination, AdminState, FilterChips } from './components/AdminUi';

export function AdminTutorRatingsPage() {
  const { tutor } = useAdminTutor();
  const [rating, setRating] = useState(0);
  const [page, setPage] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) => getAdminTutorReviews(tutor.userId, rating || undefined, page, signal),
    [page, rating, tutor.userId]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <div className="admin-columns">
      <aside className="admin-column-side admin-column-first">
        <section className="admin-card admin-card-pad">
          <h2>Tổng quan đánh giá</h2>
          <div className="admin-big-number">
            <strong>{tutor.totalReviews ? formatNumber(tutor.averageRating, 1) : '—'}</strong>
            <span>/ 5 · {tutor.totalReviews ?? 0} lượt</span>
          </div>
          <FilterChips<number>
            label="Lọc theo số sao"
            value={rating}
            options={[0, 5, 4, 3, 2, 1].map((value) => ({ value, label: value ? `${value} sao` : 'Tất cả' }))}
            onChange={(value) => {
              setRating(value);
              setPage(0);
            }}
          />
        </section>
      </aside>
      <div className="admin-column-main">
        {!data ? (
          <AdminState loading={isLoading} error={error} />
        ) : data.content.length === 0 ? (
          <div className="admin-card">
            <AdminState empty="Chưa có đánh giá nào." />
          </div>
        ) : (
          <>
            {data.content.map((review) => (
              <article key={review.reviewId} className="admin-card admin-card-pad admin-review">
                <header>
                  <div className="admin-stack">
                    <strong>{review.studentName || 'Học viên'}</strong>
                    <span>{review.studentEmail}</span>
                  </div>
                  <div className="admin-review-meta">
                    <span className="admin-stars" role="img" aria-label={`${review.rating} trên 5 sao`}>
                      {'★'.repeat(review.rating)}
                      {'☆'.repeat(5 - review.rating)}
                    </span>
                    <span>{formatDate(review.createdAt)}</span>
                  </div>
                </header>
                {review.comment && <p className="admin-prose">{review.comment}</p>}
              </article>
            ))}
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit="đánh giá"
              onChange={setPage}
            />
          </>
        )}
      </div>
    </div>
  );
}
