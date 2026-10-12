interface Props {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPrevious: () => void;
  onNext: () => void;
}

export function StudentPagination({ page, totalPages, first, last, onPrevious, onNext }: Props) {
  if (totalPages <= 1) return null;

  return (
    <div className="student-pagination">
      <button type="button" disabled={first} onClick={onPrevious}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" disabled={last} onClick={onNext}>
        Next
      </button>
    </div>
  );
}
