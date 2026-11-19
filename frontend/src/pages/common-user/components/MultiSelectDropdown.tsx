import { useEffect, useId, useRef } from 'react';

export interface MultiSelectOption {
  value: string;
  label: string;
}

interface Props {
  label: string;
  placeholder: string;
  options: MultiSelectOption[];
  values: string[];
  onChange: (values: string[]) => void;
  disabled?: boolean;
  className?: string;
}

export function MultiSelectDropdown({
  label,
  placeholder,
  options,
  values,
  onChange,
  disabled = false,
  className = ''
}: Props) {
  const labelId = useId();
  const dropdownRef = useRef<HTMLDetailsElement | null>(null);
  const selectedLabels = options.filter((option) => values.includes(option.value)).map((option) => option.label);
  const summary =
    selectedLabels.length === 0
      ? placeholder
      : selectedLabels.length <= 2
        ? selectedLabels.join(', ')
        : `${selectedLabels.slice(0, 2).join(', ')} và ${selectedLabels.length - 2} mục khác`;

  const toggle = (value: string) => {
    onChange(values.includes(value) ? values.filter((item) => item !== value) : [...values, value]);
  };

  useEffect(() => {
    const closeWhenClickedOutside = (event: PointerEvent) => {
      const dropdown = dropdownRef.current;
      if (dropdown?.open && !dropdown.contains(event.target as Node)) {
        dropdown.open = false;
      }
    };
    document.addEventListener('pointerdown', closeWhenClickedOutside);
    return () => document.removeEventListener('pointerdown', closeWhenClickedOutside);
  }, []);

  return (
    <div className={`multi-select-field ${className}`.trim()}>
      <span className="multi-select-label" id={labelId}>
        {label}
      </span>
      <details ref={dropdownRef} className="multi-select-dropdown">
        <summary
          aria-labelledby={labelId}
          aria-disabled={disabled}
          onClick={(event) => {
            if (disabled) event.preventDefault();
          }}
        >
          <span className="multi-select-summary-text">{summary}</span>
          {values.length > 0 && <span className="multi-select-count">{values.length} đã chọn</span>}
        </summary>
        <div className="multi-select-options" role="group" aria-label={label}>
          {options.length ? (
            options.map((option) => (
              <label className="multi-select-option" key={option.value}>
                <input
                  type="checkbox"
                  checked={values.includes(option.value)}
                  onChange={() => toggle(option.value)}
                  disabled={disabled}
                />
                <span>{option.label}</span>
              </label>
            ))
          ) : (
            <p className="multi-select-empty">{placeholder}</p>
          )}
        </div>
      </details>
    </div>
  );
}
