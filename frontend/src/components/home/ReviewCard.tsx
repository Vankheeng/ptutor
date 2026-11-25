import './ReviewCard.css';
import type { PublicReview } from '../../types/api';
import { getInitials } from '../../utils/formatters';
import { ProfileNameLink } from '../common-user/ProfileNameLink';

interface ReviewCardProps {
  review: PublicReview;
  index: number;
}

export function ReviewCard({ review, index }: ReviewCardProps) {
  return (
    <article className="testimonial-card">
      <div className="review-tutor">
        {review.tutorAvatarUrl ? (
          <img src={review.tutorAvatarUrl} alt={`Gia sư ${review.tutorName}`} />
        ) : (
          <span className={`review-tutor-fallback reviewer-color-${index}`}>{getInitials(review.tutorName)}</span>
        )}
        <div>
          <small>Gia sư được đánh giá</small>
          <strong>
            <ProfileNameLink role="tutor" profileId={review.tutorId}>
              {review.tutorName || 'Gia sư Ptutor'}
            </ProfileNameLink>
          </strong>
        </div>
      </div>
      <div className="quote-mark">“</div>
      <div className="stars">★★★★★</div>
      <blockquote>{review.comment || 'Học viên chưa để lại bình luận.'}</blockquote>
      <div className="reviewer">
        <span className={`reviewer-color reviewer-color-${index}`}>{getInitials(review.displayName)}</span>
        <div>
          <strong>{review.displayName}</strong>
          <small>Học viên Ptutor</small>
        </div>
      </div>
    </article>
  );
}
