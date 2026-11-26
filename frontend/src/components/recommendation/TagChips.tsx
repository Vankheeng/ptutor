import type { RecommendationTag } from '../../services/recommendationApi';

interface TagChipsProps {
  tags: RecommendationTag[];
  /** Codes of the chosen tags. */
  value: string[];
  onChange: (value: string[]) => void;
  emptyText?: string;
}

/** Keep multi-selection while showing the choices only when the field is opened. */
export function TagChips({ tags, value, onChange, emptyText }: TagChipsProps) {
  if (!tags.length) return <small className="rec-hint">{emptyText ?? 'Chưa có lựa chọn'}</small>;
  const chosenNames = tags.filter((tag) => value.includes(tag.code)).map((tag) => tag.name);
  return (
    <details className="rec-tag-dropdown">
      <summary>{chosenNames.length ? chosenNames.join(', ') : 'Chọn lựa chọn'}</summary>
      <div className="rec-tag-options">
        {tags.map((tag) => (
          <label key={tag.code} title={tag.description ?? undefined}>
            <input
              type="checkbox"
              checked={value.includes(tag.code)}
              onChange={() =>
                onChange(value.includes(tag.code) ? value.filter((code) => code !== tag.code) : [...value, tag.code])
              }
            />
            {tag.name}
          </label>
        ))}
      </div>
    </details>
  );
}
