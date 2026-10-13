import type { RecommendationTag } from '../../services/recommendationApi';

interface TagChipsProps {
  tags: RecommendationTag[];
  /** Codes of the chosen tags. */
  value: string[];
  onChange: (value: string[]) => void;
  emptyText?: string;
}

/** Multi-select as chips: what the user picks here is matched exactly, unlike free text. */
export function TagChips({ tags, value, onChange, emptyText }: TagChipsProps) {
  if (!tags.length) return <small className="rec-hint">{emptyText ?? 'Chưa có lựa chọn'}</small>;
  return (
    <div className="rec-chips">
      {tags.map((tag) => {
        const chosen = value.includes(tag.code);
        return (
          <button
            key={tag.code}
            type="button"
            className={chosen ? 'rec-chip active' : 'rec-chip'}
            aria-pressed={chosen}
            title={tag.description ?? undefined}
            onClick={() => onChange(chosen ? value.filter((code) => code !== tag.code) : [...value, tag.code])}
          >
            {tag.name}
          </button>
        );
      })}
    </div>
  );
}
