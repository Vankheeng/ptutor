interface NotificationPaginationProps {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPageChange: (page: number) => void;
}

export function NotificationPagination({ page, totalPages, first, last, onPageChange }: NotificationPaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="student-pagination">
      <button type="button" disabled={first} onClick={() => onPageChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" disabled={last} onClick={() => onPageChange(page + 1)}>
        Next
      </button>
    </div>
  );
}
