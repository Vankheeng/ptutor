interface NotificationPaginationProps {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPageChange: (page: number) => void;
}

export function NotificationPagination({ page, totalPages, first, last, onPageChange }: NotificationPaginationProps) {
  if (totalPages <= 1) return null;

  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pages = Array.from({ length: Math.min(5, totalPages) }, (_, index) => start + index);

  return (
    <nav className="student-pagination" aria-label="Phân trang thông báo">
      <button type="button" disabled={first} onClick={() => onPageChange(page - 1)} aria-label="Trang trước">
        ‹ Trước
      </button>
      <div className="student-pagination-pages">
        {start > 0 && (
          <>
            <button type="button" onClick={() => onPageChange(0)} aria-label="Trang 1">
              1
            </button>
            {start > 1 && <span aria-hidden="true">…</span>}
          </>
        )}
        {pages.map((item) => (
          <button
            type="button"
            key={item}
            className={item === page ? 'active' : ''}
            aria-current={item === page ? 'page' : undefined}
            aria-label={`Trang ${item + 1}`}
            onClick={() => onPageChange(item)}
          >
            {item + 1}
          </button>
        ))}
        {start + pages.length < totalPages && (
          <>
            {start + pages.length < totalPages - 1 && <span aria-hidden="true">…</span>}
            <button type="button" onClick={() => onPageChange(totalPages - 1)} aria-label={`Trang ${totalPages}`}>
              {totalPages}
            </button>
          </>
        )}
      </div>
      <button type="button" disabled={last} onClick={() => onPageChange(page + 1)} aria-label="Trang sau">
        Sau ›
      </button>
    </nav>
  );
}
