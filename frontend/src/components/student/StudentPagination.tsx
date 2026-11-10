interface Props {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPrevious: () => void;
  onNext: () => void;
  previousLabel?: string;
  nextLabel?: string;
}

export function StudentPagination({
  page,
  totalPages,
  first,
  last,
  onPrevious,
  onNext,
  previousLabel = 'Previous',
  nextLabel = 'Next'
}: Props) {
  if (totalPages <= 1) return null;

  return (
    <div className="student-pagination">
      <button type="button" disabled={first} onClick={onPrevious}>
        {previousLabel}
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" disabled={last} onClick={onNext}>
        {nextLabel}
      </button>
    </div>
  );
}
