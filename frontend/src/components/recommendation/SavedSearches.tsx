import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '../../services/errorMessages';
import {
  createSavedSearch,
  deleteRecommendationHistory,
  deleteSavedSearch,
  getSavedSearches,
  updateSavedSearch,
  type RecommendationDirection,
  type SavedSearch,
  type StudentSearchFilters,
  type TutorSearchFilters
} from '../../services/recommendationApi';

const LIMIT = 5;

interface SavedSearchControlsProps {
  direction: RecommendationDirection;
  /** The criteria of the search on screen, saved as they are. */
  filters: TutorSearchFilters | StudentSearchFilters;
  semanticText?: string | null;
  /** The name a new saved search gets, for example "Toán – Lớp 9"; it can be renamed in the menu. */
  suggestedName?: string;
  /** Saving makes sense once the search on screen has the required criteria. */
  canSave: boolean;
  onRun: (search: SavedSearch) => void;
  /** Button style of the workspace: student or tutor. */
  variant: 'student' | 'tutor';
}

/**
 * Two small controls for the toolbar above the results (design 05, 5.6; 07, 7.3 and 7.4): an icon that saves the
 * search on screen, and a "Đã lưu" menu with the saved searches (at most five), their match notifications and the
 * switch that forgets the history the suggestions learn from.
 */
export function SavedSearchControls({
  direction,
  filters,
  semanticText,
  suggestedName,
  canSave,
  onRun,
  variant
}: SavedSearchControlsProps) {
  const button = variant === 'tutor' ? 'tutor-button' : 'student-button';
  const [searches, setSearches] = useState<SavedSearch[]>([]);
  /** Bumped after every change, to load the list again. */
  const [revision, setRevision] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  /** The criteria last saved from this screen, so the icon shows it is saved. */
  const [savedFilters, setSavedFilters] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [confirmForget, setConfirmForget] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    getSavedSearches(controller.signal)
      .then((all) => setSearches(all.filter((search) => search.direction === direction)))
      .catch((caught) => {
        if (!controller.signal.aborted) setError(errorMessage(caught, 'Không tải được các tìm kiếm đã lưu.'));
      });
    return () => controller.abort();
  }, [direction, revision]);

  // The menu closes on a click outside it or on Escape.
  useEffect(() => {
    if (!isOpen) return;
    const onClick = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setIsOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [isOpen]);

  const act = async (work: () => Promise<unknown>, done: string, failed: string) => {
    setError('');
    setMessage('');
    try {
      await work();
      setMessage(done);
      setRevision((value) => value + 1);
      return true;
    } catch (caught) {
      setError(errorMessage(caught, failed));
      return false;
    }
  };

  const currentFilters = JSON.stringify(filters);
  const isSaved = savedFilters === currentFilters;
  const isFull = searches.length >= LIMIT;
  const saveHint = isSaved
    ? 'Đã lưu tìm kiếm này'
    : isFull
      ? `Đã lưu đủ ${LIMIT} tìm kiếm — xoá bớt trong "Đã lưu"`
      : canSave
        ? 'Lưu tìm kiếm này'
        : 'Chọn môn và lớp rồi tìm, sau đó mới lưu được';

  const save = async () => {
    setIsSaving(true);
    const saved = await act(
      () =>
        createSavedSearch({
          direction,
          name: suggestedName || undefined,
          filters,
          semanticText: semanticText ?? undefined,
          notifyEnabled: true
        }),
      'Đã lưu tìm kiếm.',
      'Chưa lưu được tìm kiếm.'
    );
    if (saved) setSavedFilters(currentFilters);
    setIsSaving(false);
  };

  return (
    <div className="rec-saved-controls" ref={menuRef}>
      <button
        className={isSaved ? 'rec-icon-button active' : 'rec-icon-button'}
        type="button"
        aria-label={saveHint}
        data-tooltip={saveHint}
        disabled={!canSave || isSaved || isFull || isSaving}
        onClick={() => void save()}
      >
        <BookmarkIcon filled={isSaved} />
      </button>
      <button
        className={isOpen ? 'rec-menu-button open' : 'rec-menu-button'}
        type="button"
        aria-haspopup="true"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((open) => !open)}
      >
        Đã lưu ({searches.length}) <span aria-hidden="true">▾</span>
      </button>

      {(message || error) && !isOpen && (
        <span className={error ? 'rec-saved-error rec-saved-flash' : 'rec-saved-message rec-saved-flash'} role="status">
          {error || message}
        </span>
      )}

      {isOpen && (
        <div className="rec-saved-menu" role="menu">
          <div className="rec-saved-head">
            <strong>Tìm kiếm đã lưu</strong>
            <small>
              {searches.length}/{LIMIT}
            </small>
          </div>
          {searches.length === 0 && (
            <small className="rec-hint">Chưa có tìm kiếm nào. Bấm biểu tượng lưu sau khi tìm để lưu lại.</small>
          )}
          <ul className="rec-saved-list">
            {searches.map((search) => (
              <li key={search.id}>
                {editing === search.id ? (
                  <form
                    className="rec-saved-edit"
                    onSubmit={(event) => {
                      event.preventDefault();
                      void act(
                        () => updateSavedSearch(search.id, { name: editName.trim() }),
                        'Đã đổi tên.',
                        'Chưa đổi được tên.'
                      ).then(() => setEditing(null));
                    }}
                  >
                    <input
                      value={editName}
                      maxLength={120}
                      aria-label="Tên mới"
                      onChange={(event) => setEditName(event.target.value)}
                    />
                    <button className={`${button} secondary`} type="submit" disabled={!editName.trim()}>
                      Lưu
                    </button>
                    <button className="rec-link" type="button" onClick={() => setEditing(null)}>
                      Huỷ
                    </button>
                  </form>
                ) : (
                  <>
                    <button
                      className="rec-saved-name"
                      type="button"
                      title="Tìm lại với tiêu chí này"
                      onClick={() => {
                        setIsOpen(false);
                        onRun(search);
                      }}
                    >
                      {search.name || 'Tìm kiếm không tên'}
                    </button>
                    <label className="rec-saved-notify" title="Báo cho tôi khi có tin mới rất phù hợp">
                      <input
                        type="checkbox"
                        checked={search.notifyEnabled}
                        onChange={(event) =>
                          void act(
                            () => updateSavedSearch(search.id, { notifyEnabled: event.target.checked }),
                            event.target.checked ? 'Đã bật thông báo.' : 'Đã tắt thông báo.',
                            'Chưa đổi được thông báo.'
                          )
                        }
                      />
                      Báo tin mới
                    </label>
                    <span className="rec-saved-actions">
                      <button
                        className="rec-link"
                        type="button"
                        onClick={() => {
                          setEditing(search.id);
                          setEditName(search.name ?? '');
                        }}
                      >
                        Đổi tên
                      </button>
                      <button
                        className="rec-link"
                        type="button"
                        onClick={() => void act(() => deleteSavedSearch(search.id), 'Đã xoá.', 'Chưa xoá được.')}
                      >
                        Xoá
                      </button>
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>

          <div className="rec-divider" />
          {confirmForget ? (
            <div className="rec-saved-forget">
              <span>Xoá lịch sử xem, lưu và bỏ qua mà hệ thống dùng để cá nhân hoá gợi ý?</span>
              <button
                className={`${button} secondary`}
                type="button"
                onClick={() =>
                  void act(deleteRecommendationHistory, 'Đã xoá lịch sử gợi ý.', 'Chưa xoá được lịch sử.').then(() =>
                    setConfirmForget(false)
                  )
                }
              >
                Xoá
              </button>
              <button className="rec-link" type="button" onClick={() => setConfirmForget(false)}>
                Không
              </button>
            </div>
          ) : (
            <button className="rec-link" type="button" onClick={() => setConfirmForget(true)}>
              Xoá lịch sử gợi ý
            </button>
          )}
          {message && <small className="rec-saved-message">{message}</small>}
          {error && <small className="rec-saved-error">{error}</small>}
        </div>
      )}
    </div>
  );
}

function BookmarkIcon({ filled }: { filled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
      <path
        d="M6 3.5h12a1 1 0 0 1 1 1v16l-7-4.2-7 4.2v-16a1 1 0 0 1 1-1z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}
