interface Props {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPrevious: () => void;
  onNext: () => void;
  previousLabel?: string;
  nextLabel?: string;
  pageLabel?: string;
  ofLabel?: string;
}

export function StudentPagination({
  page,
  totalPages,
  first,
  last,
  onPrevious,
  onNext,
  previousLabel = 'Previous',
  nextLabel = 'Next',
  pageLabel = 'Page',
  ofLabel = 'of'
}: Props) {
  if (totalPages <= 1) return null;

  return (
    <div className="student-pagination">
      <button type="button" disabled={first} onClick={onPrevious}>
        {previousLabel}
      </button>
      <span>
        {pageLabel} {page + 1} {ofLabel} {totalPages}
      </span>
      <button type="button" disabled={last} onClick={onNext}>
        {nextLabel}
      </button>
    </div>
  );
}
