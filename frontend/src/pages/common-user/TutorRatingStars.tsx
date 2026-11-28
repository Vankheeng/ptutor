import type { CSSProperties } from 'react';

export function TutorRatingStars({ rating, className = '' }: { rating: number; className?: string }) {
  const normalizedRating = Math.min(5, Math.max(0, Number(rating) || 0));

  return (
    <span
      className={`profile-public-tutor-stars ${className}`.trim()}
      role="img"
      aria-label={`${normalizedRating.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} trên 5 sao`}
    >
      {Array.from({ length: 5 }, (_, index) => {
        const fill = Math.min(1, Math.max(0, normalizedRating - index)) * 100;
        return (
          <span
            className="profile-public-tutor-star"
            key={index}
            style={{ '--profile-star-fill': `${fill}%` } as CSSProperties}
            aria-hidden="true"
          >
            ★
          </span>
        );
      })}
    </span>
  );
}
