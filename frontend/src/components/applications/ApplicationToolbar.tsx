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
  filterLabel?: string;
  secondaryFilter?: {
    value: string;
    onChange: (value: string) => void;
    ariaLabel: string;
    label: string;
    options: readonly ApplicationToolbarFilter[];
  };
}

export function ApplicationToolbar({
  search,
  onSearchChange,
  placeholder,
  searchAriaLabel,
  filters,
  selectedFilter,
  onFilterChange,
  filterAriaLabel,
  filterLabel,
  secondaryFilter
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
      <label className="tutor-filter-group">
        {filterLabel && <span className="tutor-filter-group-label">{filterLabel}</span>}
        <select
          className="tutor-request-status-filter"
          value={selectedFilter}
          aria-label={filterAriaLabel}
          onChange={(event) => onFilterChange(event.target.value)}
        >
          {filters.map((filter) => (
            <option key={filter.value} value={filter.value}>
              {filter.label}
            </option>
          ))}
        </select>
      </label>
      {secondaryFilter && (
        <label className="tutor-filter-group tutor-filter-select-group">
          <span className="tutor-filter-group-label">{secondaryFilter.label}</span>
          <select
            className="tutor-request-status-filter"
            value={secondaryFilter.value}
            aria-label={secondaryFilter.ariaLabel}
            onChange={(event) => secondaryFilter.onChange(event.target.value)}
          >
            {secondaryFilter.options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}
