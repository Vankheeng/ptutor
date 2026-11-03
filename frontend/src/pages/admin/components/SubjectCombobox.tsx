import {
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent
} from 'react';
import type { Subject } from '../../../types/api';

/** Lower-cases and strips Vietnamese accents so "toan" matches "Toán". */
function fold(value: string) {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase().trim();
}

/** Lower is a closer match; null means the subject does not match at all. */
function matchScore(name: string, query: string) {
  const target = fold(name);
  if (!query) return 0;
  if (target === query) return 0;
  if (target.startsWith(query)) return 1;
  if (target.split(/\s+/).some((word) => word.startsWith(query))) return 2;
  if (target.includes(query)) return 3;
  let index = 0;
  for (const char of target) if (char === query[index]) index += 1;
  return index === query.length ? 4 : null;
}

/** Three 40px options plus the list padding and border, matching .subject-combobox-list in request-review.css. */
const LIST_MAX_HEIGHT = 130;
const LIST_GAP = 4;

interface Props {
  id: string;
  subjects: Subject[];
  value: string | null;
  invalid?: boolean;
  onChange: (subjectId: string) => void;
}

export function SubjectCombobox({ id, subjects, value, invalid, onChange }: Props) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const [listStyle, setListStyle] = useState<CSSProperties>({});
  const controlRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const selected = subjects.find((subject) => subject.id === value) ?? null;

  // The list is fixed to the viewport so it floats over the dialog instead of lengthening its scroll area.
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const rect = controlRef.current?.getBoundingClientRect();
      if (!rect) return;
      const below = window.innerHeight - rect.bottom - LIST_GAP;
      const openUp = below < LIST_MAX_HEIGHT && rect.top > below;
      setListStyle({
        left: rect.left,
        width: rect.width,
        ...(openUp ? { bottom: window.innerHeight - rect.top + LIST_GAP } : { top: rect.bottom + LIST_GAP })
      });
    };
    place();
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
    };
  }, [open]);

  useEffect(() => {
    if (open) listRef.current?.children[active]?.scrollIntoView({ block: 'nearest' });
  }, [active, open]);

  const options = useMemo(() => {
    const folded = fold(query ?? '');
    return subjects
      .map((subject) => ({ subject, score: matchScore(subject.name, folded) }))
      .filter((option): option is { subject: Subject; score: number } => option.score !== null)
      .sort((a, b) => a.score - b.score || a.subject.name.localeCompare(b.subject.name, 'vi'))
      .map((option) => option.subject);
  }, [query, subjects]);

  const close = () => {
    setOpen(false);
    setQuery(null);
  };

  const choose = (subject: Subject) => {
    onChange(subject.id);
    close();
  };

  const openList = () => {
    setOpen(true);
    setActive(
      Math.max(
        0,
        options.findIndex((subject) => subject.id === value)
      )
    );
  };

  const handleKey = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) return openList();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActive((current) => (current + step + options.length) % Math.max(options.length, 1));
    } else if (event.key === 'Enter' && open) {
      event.preventDefault();
      if (options[active]) choose(options[active]);
    } else if (event.key === 'Escape' && open) {
      // Keep the surrounding dialog open; Escape here only closes the list.
      event.stopPropagation();
      close();
    }
  };

  const activeOption = open ? options[active] : undefined;

  return (
    <div className="subject-combobox">
      <div ref={controlRef} className={`subject-combobox-control${invalid ? ' invalid' : ''}`}>
        <input
          id={id}
          type="text"
          role="combobox"
          autoComplete="off"
          placeholder="Gõ để tìm môn học"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeOption ? `${listId}-${activeOption.id}` : undefined}
          aria-invalid={invalid}
          value={query ?? selected?.name ?? ''}
          onChange={(event) => {
            setQuery(event.target.value);
            setActive(0);
            setOpen(true);
          }}
          onClick={() => !open && openList()}
          onKeyDown={handleKey}
          onBlur={close}
        />
        <button
          type="button"
          tabIndex={-1}
          aria-label={open ? 'Đóng danh sách môn' : 'Mở danh sách môn'}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => (open ? close() : openList())}
        >
          <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24">
            <path d={open ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'} />
          </svg>
        </button>
      </div>
      {open && (
        <ul
          ref={listRef}
          id={listId}
          className="subject-combobox-list"
          style={listStyle}
          role="listbox"
          aria-label="Danh mục môn học"
        >
          {options.length === 0 ? (
            <li className="subject-combobox-empty">Không tìm thấy môn phù hợp.</li>
          ) : (
            options.map((subject, index) => (
              <li
                key={subject.id}
                id={`${listId}-${subject.id}`}
                role="option"
                aria-selected={subject.id === value}
                className={index === active ? 'active' : undefined}
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(subject)}
              >
                {subject.name}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
