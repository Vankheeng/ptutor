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
        Trang trước
      </button>
      <span>
        Trang {page + 1} / {totalPages}
      </span>
      <button type="button" disabled={last} onClick={onNext}>
        Trang sau
      </button>
    </div>
  );
}
