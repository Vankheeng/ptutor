export interface ApplicationToolbarFilter {
  value: string;
  label: string;
}

interface ApplicationToolbarProps {
  search: string;
  onSearchChange: (value: string) => void;
  placeholder: string;
  searchAriaLabel: string;
  filters: readonly ApplicationToolbarFilter[];
  selectedFilter: string;
  onFilterChange: (value: string) => void;
  filterAriaLabel: string;
}

export function ApplicationToolbar({
  search,
  onSearchChange,
  placeholder,
  searchAriaLabel,
  filters,
  selectedFilter,
  onFilterChange,
  filterAriaLabel
}: ApplicationToolbarProps) {
  return (
    <div className="tutor-proposal-controls">
      <label className="tutor-proposal-search">
        <span aria-hidden="true">⌕</span>
        <input
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={placeholder}
          aria-label={searchAriaLabel}
        />
      </label>
      <div className="tutor-filter-tabs" role="tablist" aria-label={filterAriaLabel}>
        {filters.map((filter) => (
          <button
            key={filter.value}
            className={selectedFilter === filter.value ? 'active' : ''}
            type="button"
            role="tab"
            aria-selected={selectedFilter === filter.value}
            onClick={() => onFilterChange(filter.value)}
          >
            {filter.label}
          </button>
        ))}
      </div>
    </div>
  );
}
