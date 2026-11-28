import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getTutorReviewOverview } from '../../services/reviewApi';
import { TutorRatingStars } from './TutorRatingStars';

const COMMENTS_PER_PAGE = 5;

function formatReviewDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('vi-VN');
}

export function TutorReviewsTab({ tutorId }: { tutorId: string }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const load = useCallback(
    (signal: AbortSignal) => getTutorReviewOverview(tutorId, page, COMMENTS_PER_PAGE, signal),
    [tutorId, page]
  );
  const query = useResource(load, revision);
  const data = query.data?.comments.page === page ? query.data : null;

  if (query.isLoading || (!data && !query.error)) {
    return <div className="profile-reviews-state">Đang tải đánh giá…</div>;
  }

  if (!data) {
    return (
      <div className="profile-reviews-state profile-reviews-error" role="alert">
        <p>{query.error || 'Không thể tải đánh giá của gia sư.'}</p>
        <button type="button" onClick={() => setRevision((value) => value + 1)}>
          Thử lại
        </button>
      </div>
    );
  }

  const average = Number(data.averageRating) || 0;
  const hasReviews = data.totalReviews > 0;

  return (
    <div className="profile-tutor-reviews">
      <section className="admin-card admin-card-pad profile-review-summary">
        <h2>Tổng hợp đánh giá</h2>
        <div className="profile-review-summary-content">
          <div className="profile-review-score">
            {hasReviews ? (
              <>
                <div>
                  <strong>
                    {average.toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
                  </strong>
                  <span>/ 5</span>
                </div>
                <TutorRatingStars rating={average} className="profile-review-summary-stars" />
                <p>{data.totalReviews.toLocaleString('vi-VN')} lượt đánh giá</p>
              </>
            ) : (
              <div className="profile-review-empty-score">
                <strong>—</strong>
                <p>Chưa có đánh giá</p>
              </div>
            )}
          </div>
          <div className="profile-review-distribution">
            {data.distribution.map((item) => {
              const percentage = Number(item.percentage) || 0;
              return (
                <div className="profile-review-distribution-row" key={item.rating}>
                  <span>{item.rating} sao</span>
                  <div className="profile-review-distribution-track" aria-hidden="true">
                    <span style={{ width: `${Math.min(100, Math.max(0, percentage))}%` }} />
                  </div>
                  <strong>{percentage.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%</strong>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="admin-card admin-card-pad profile-review-comments">
        <div className="profile-review-comments-heading">
          <h2>Nhận xét của học viên</h2>
          <span>{data.commentCount.toLocaleString('vi-VN')}</span>
        </div>
        {!hasReviews ? (
          <p className="profile-review-comments-empty">Gia sư chưa có đánh giá nào.</p>
        ) : data.commentCount === 0 ? (
          <p className="profile-review-comments-empty">Các lượt đánh giá hiện chỉ có điểm sao, chưa có nhận xét.</p>
        ) : (
          <>
            <div className="profile-review-comment-list">
              {data.comments.content.map((review, index) => {
                const displayName = review.displayName?.trim() || 'Học viên';
                return (
                  <article className="profile-review-comment" key={`${review.createdAt}-${index}`}>
                    <div className="profile-review-comment-avatar" aria-hidden="true">
                      {displayName.charAt(0).toLocaleUpperCase('vi-VN')}
                    </div>
                    <div className="profile-review-comment-content">
                      <div className="profile-review-comment-head">
                        <strong>{displayName}</strong>
                        <time dateTime={review.createdAt}>{formatReviewDate(review.createdAt)}</time>
                      </div>
                      <TutorRatingStars rating={review.rating ?? 0} className="profile-review-comment-stars" />
                      <p>{review.comment}</p>
                    </div>
                  </article>
                );
              })}
            </div>
            {data.comments.totalPages > 1 && (
              <nav className="profile-review-pagination" aria-label="Phân trang nhận xét">
                <button type="button" disabled={data.comments.first} onClick={() => setPage((value) => value - 1)}>
                  Trước
                </button>
                <span>
                  Trang {data.comments.page + 1}/{data.comments.totalPages}
                </span>
                <button type="button" disabled={data.comments.last} onClick={() => setPage((value) => value + 1)}>
                  Sau
                </button>
              </nav>
            )}
          </>
        )}
      </section>
    </div>
  );
}
