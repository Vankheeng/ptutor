import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { AreaPicker } from '../../components/recommendation/AreaPicker';
import { AvailabilityEditor } from '../../components/recommendation/AvailabilityEditor';
import { hasInvalidSlot } from '../../components/recommendation/availability';
import { TagChips } from '../../components/recommendation/TagChips';
import '../../components/recommendation/recommendation.css';
import { getGrades } from '../../services/gradeApi';
import { ApiError } from '../../services/httpClient';
import {
  getRecommendationTags,
  getTutorPage,
  parseQuery,
  recordEvent,
  refineTutors,
  searchTutors,
  type ModePreference,
  type RecommendationAction,
  type RecommendationTag,
  type Relaxation,
  type TutorRecommendationItem,
  type TutorRecommendationResponse,
  type TutorSearchFilters
} from '../../services/recommendationApi';
import {
  createStudentApplication,
  getMyStudyingRequests,
  getStudentProfile,
  type StudyingRequest
} from '../../services/studentApi';
import { getSubjects } from '../../services/subjectApi';
import type { Grade, Subject } from '../../types/api';
import { formatCurrency, getInitials } from '../../utils/formatters';

const PAGE_SIZE = 20;
const MODES: { value: ModePreference; label: string }[] = [
  { value: 'ANY', label: 'Cả hai' },
  { value: 'ONLINE', label: 'Online' },
  { value: 'OFFLINE', label: 'Trực tiếp' }
];
const NEEDS_INPUT: Record<string, string> = { subjectId: 'môn học', gradeId: 'lớp' };

/** "Gợi ý cho bạn": tutors ranked for this student. The filters are the input; the sentence only fills them. */
export function StudentTutorRecommendations() {
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [styleTags, setStyleTags] = useState<RecommendationTag[]>([]);
  const [goalTags, setGoalTags] = useState<RecommendationTag[]>([]);
  const [topicTags, setTopicTags] = useState<RecommendationTag[]>([]);
  const [myRequests, setMyRequests] = useState<StudyingRequest[]>([]);
  const [homeProvinceId, setHomeProvinceId] = useState<string | null>(null);

  const [filters, setFilters] = useState<TutorSearchFilters>({});
  const [useRequestId, setUseRequestId] = useState('');
  const [sentence, setSentence] = useState('');
  /** The sentence the current filters came from; sent along so the search also uses its meaning. */
  const [parsedSentence, setParsedSentence] = useState<string | null>(null);
  const [fromSentence, setFromSentence] = useState<Set<string>>(new Set());
  const [notes, setNotes] = useState<string[]>([]);
  const [isParsing, setIsParsing] = useState(false);

  const [result, setResult] = useState<TutorRecommendationResponse | null>(null);
  const [items, setItems] = useState<TutorRecommendationItem[]>([]);
  const [pagesLoaded, setPagesLoaded] = useState(0);
  const [allLoaded, setAllLoaded] = useState(false);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [viewed, setViewed] = useState<Set<string>>(new Set());
  /** Something was opened or liked since the list was last ordered: the rest can be ordered better. */
  const [hasNewSignal, setHasNewSignal] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [active, setActive] = useState<TutorRecommendationItem | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getSubjects('name', controller.signal)
      .then(setSubjects)
      .catch(() => undefined);
    getGrades(controller.signal)
      .then(setGrades)
      .catch(() => undefined);
    getRecommendationTags('STYLE')
      .then(setStyleTags)
      .catch(() => undefined);
    getRecommendationTags('GOAL')
      .then(setGoalTags)
      .catch(() => undefined);
    getMyStudyingRequests('OPEN', 0, 50, controller.signal)
      .then((page) => setMyRequests(page.content))
      .catch(() => undefined);
    getStudentProfile(controller.signal)
      .then((profile) => setHomeProvinceId(profile.address?.provinceId ?? null))
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  // Topics belong to a subject: another subject means another list, and the old choices no longer apply.
  useEffect(() => {
    let current = true;
    if (!filters.subjectId) return;
    getRecommendationTags('TOPIC', filters.subjectId)
      .then((tags) => {
        if (current) setTopicTags(tags);
      })
      .catch(() => {
        if (current) setTopicTags([]);
      });
    return () => {
      current = false;
    };
  }, [filters.subjectId]);

  const change = useCallback((changes: Partial<TutorSearchFilters>) => {
    setFilters((previous) => {
      const next = { ...previous, ...changes };
      if ('subjectId' in changes && changes.subjectId !== previous.subjectId) delete next.topicTagCodes;
      return next;
    });
    setFromSentence((previous) => {
      const next = new Set(previous);
      Object.keys(changes).forEach((key) => next.delete(key));
      return next;
    });
  }, []);

  const run = useCallback(
    async (searchFilters: TutorSearchFilters) => {
      if (hasInvalidSlot(searchFilters.availabilities ?? [])) {
        setError('Khung giờ rảnh chưa hợp lệ: giờ kết thúc phải sau giờ bắt đầu.');
        return;
      }
      setError('');
      setNotice('');
      setIsSearching(true);
      try {
        const response = await searchTutors(
          {
            filters: searchFilters,
            semanticText: parsedSentence ?? undefined,
            queryText: parsedSentence ?? undefined,
            useStudyingRequestId: useRequestId || undefined
          },
          PAGE_SIZE
        );
        setResult(response);
        setItems(response.items);
        setPagesLoaded(1);
        setAllLoaded(response.items.length >= response.totalElements);
        setDismissed(new Set());
        setFavorites(new Set());
        setViewed(new Set());
        setHasNewSignal(false);
        setActive(null);
      } catch (caught) {
        setError(messageOf(caught, 'Không tìm được gợi ý. Vui lòng thử lại.'));
      } finally {
        setIsSearching(false);
      }
    },
    [parsedSentence, useRequestId]
  );

  const fillFromSentence = async () => {
    const text = sentence.trim();
    if (!text) return;
    setIsParsing(true);
    setError('');
    try {
      const parsed = await parseQuery(text, 'STUDENT_TO_TUTOR');
      setFilters((previous) => ({ ...previous, ...parsed.filters }));
      setFromSentence(new Set(Object.keys(parsed.filters)));
      setNotes(parsed.notes);
      setParsedSentence(parsed.semanticText ?? text);
    } catch (caught) {
      setError(messageOf(caught, 'Chưa phân tích được câu. Bạn có thể chọn bộ lọc bằng tay.'));
    } finally {
      setIsParsing(false);
    }
  };

  const send = (item: TutorRecommendationItem, action: RecommendationAction) => {
    if (!result?.logId) return;
    // Feedback is best effort: a lost event must never get in the user's way.
    recordEvent(result.logId, item.teachingRequestId, action).catch(() => undefined);
    if (action !== 'DISMISSED') setHasNewSignal(true);
  };

  const open = (item: TutorRecommendationItem) => {
    setActive(item);
    if (!viewed.has(item.teachingRequestId)) {
      setViewed((previous) => new Set(previous).add(item.teachingRequestId));
      send(item, 'VIEWED');
    }
  };

  const favorite = (item: TutorRecommendationItem) => {
    if (favorites.has(item.teachingRequestId)) return;
    setFavorites((previous) => new Set(previous).add(item.teachingRequestId));
    send(item, 'FAVORITED');
  };

  const dismiss = (item: TutorRecommendationItem) => {
    setDismissed((previous) => new Set(previous).add(item.teachingRequestId));
    if (active?.teachingRequestId === item.teachingRequestId) setActive(null);
    send(item, 'DISMISSED');
  };

  const loadMore = async () => {
    if (!result?.logId) return;
    setIsLoadingMore(true);
    try {
      if (hasNewSignal) {
        // What was opened or liked so far reorders everything not shown yet; it all comes back at once.
        const refined = await refineTutors(result.logId, items.length);
        setItems((previous) => [...previous, ...refined.items]);
        setAllLoaded(true);
        setHasNewSignal(false);
      } else {
        const next = await getTutorPage(result.logId, pagesLoaded, PAGE_SIZE);
        setItems((previous) => [...previous, ...next.items]);
        setPagesLoaded((previous) => previous + 1);
        setAllLoaded(items.length + next.items.length >= next.totalElements || next.items.length === 0);
      }
    } catch (caught) {
      setError(messageOf(caught, 'Không tải thêm được kết quả.'));
    } finally {
      setIsLoadingMore(false);
    }
  };

  const applyRelaxation = (relaxation: Relaxation) => {
    const next = { ...filters, ...relaxation.apply } as TutorSearchFilters;
    setFilters(next);
    void run(next);
  };

  const apply = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!active) return;
    const values = new FormData(event.currentTarget);
    const value = (key: string) => String(values.get(key) ?? '').trim();
    setError('');
    try {
      await createStudentApplication(active.teachingRequestId, {
        gradeId: value('gradeId'),
        proposedPrice: Number(value('proposedPrice')),
        learningMode: active.teachingMode,
        preferredSchedule: value('preferredSchedule') || undefined,
        message: value('message') || undefined
      });
      send(active, 'CONTACTED');
      setNotice(`Đã gửi đơn đăng ký tới ${active.tutor.displayName}.`);
      setActive(null);
    } catch (caught) {
      setError(messageOf(caught, 'Chưa gửi được đơn đăng ký.'));
    }
  };

  const visible = useMemo(() => items.filter((item) => !dismissed.has(item.teachingRequestId)), [items, dismissed]);
  const mark = (key: string) => (fromSentence.has(key) ? <em className="rec-from-sentence">từ câu của bạn</em> : null);
  const slots = filters.availabilities ?? [];

  return (
    <div className="rec-layout">
      <form
        className="student-panel rec-filters"
        onSubmit={(event) => {
          event.preventDefault();
          void run(filters);
        }}
      >
        <label className="rec-field">
          <span>Mô tả nhanh bằng một câu</span>
          <textarea
            rows={3}
            maxLength={500}
            value={sentence}
            onChange={(event) => setSentence(event.target.value)}
            placeholder="Ví dụ: Gia sư Toán lớp 9 kiên nhẫn, tối thứ 3 và 5, dưới 200k, học ở Cầu Giấy"
          />
        </label>
        <button
          className="student-button secondary"
          type="button"
          disabled={isParsing || !sentence.trim()}
          onClick={() => void fillFromSentence()}
        >
          {isParsing ? 'Đang phân tích…' : 'Điền bộ lọc từ câu này'}
        </button>
        {notes.length > 0 && (
          <ul className="rec-notes">
            {notes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        )}

        <div className="rec-divider" />

        {myRequests.length > 0 && (
          <label className="rec-field">
            <span>Dùng tiêu chí từ tin học của tôi</span>
            <select value={useRequestId} onChange={(event) => setUseRequestId(event.target.value)}>
              <option value="">Không dùng</option>
              {myRequests.map((request) => (
                <option key={request.id} value={request.id}>
                  {request.title || `${request.subjectName} – ${request.gradeName}`}
                </option>
              ))}
            </select>
            <small className="rec-hint">Ô nào bạn để trống sẽ được điền từ tin học này.</small>
          </label>
        )}

        <div className="rec-row">
          <label className="rec-field">
            <span>Môn học {mark('subjectId')}</span>
            <select
              value={filters.subjectId ?? ''}
              onChange={(event) => change({ subjectId: event.target.value || undefined })}
            >
              <option value="">Chọn môn</option>
              {subjects.map((subject) => (
                <option key={subject.id} value={subject.id}>
                  {subject.name}
                </option>
              ))}
            </select>
          </label>
          <label className="rec-field">
            <span>Lớp {mark('gradeId')}</span>
            <select
              value={filters.gradeId ?? ''}
              onChange={(event) => change({ gradeId: event.target.value || undefined })}
            >
              <option value="">Chọn lớp</option>
              {grades.map((grade) => (
                <option key={grade.id} value={grade.id}>
                  {grade.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="rec-field">
          <span>Hình thức học {mark('learningMode')}</span>
          <div className="rec-segmented" role="radiogroup" aria-label="Hình thức học">
            {MODES.map((mode) => (
              <button
                key={mode.value}
                type="button"
                role="radio"
                aria-checked={(filters.learningMode ?? 'ANY') === mode.value}
                className={(filters.learningMode ?? 'ANY') === mode.value ? 'active' : ''}
                onClick={() => change({ learningMode: mode.value === 'ANY' ? undefined : mode.value })}
              >
                {mode.label}
              </button>
            ))}
          </div>
        </div>

        {filters.learningMode !== 'ONLINE' && (
          <div className="rec-field">
            <span>Khu vực học trực tiếp {mark('area')}</span>
            <AreaPicker value={filters.area} onChange={(area) => change({ area })} homeProvinceId={homeProvinceId} />
          </div>
        )}

        <div className="rec-field">
          <span>Học phí mỗi buổi (đồng) {mark('price')}</span>
          <div className="rec-row">
            <input
              type="number"
              min={0}
              step={10000}
              placeholder="Từ"
              aria-label="Học phí tối thiểu"
              value={filters.price?.min ?? ''}
              onChange={(event) => change({ price: priceOf(event.target.value, filters.price?.max) })}
            />
            <input
              type="number"
              min={0}
              step={10000}
              placeholder="Đến"
              aria-label="Học phí tối đa"
              value={filters.price?.max ?? ''}
              onChange={(event) => change({ price: priceOf(filters.price?.min, event.target.value) })}
            />
          </div>
        </div>

        <div className="rec-field">
          <span>Lịch rảnh {mark('availabilities')}</span>
          {filters.availabilities && filters.availabilities.length === 0 && (
            <small className="rec-hint">Đang tìm không xét lịch.</small>
          )}
          <AvailabilityEditor
            value={slots}
            onChange={(next) => change({ availabilities: next.length ? next : undefined })}
          />
        </div>

        <div className="rec-field">
          <span>Phong cách dạy mong muốn {mark('styleTagCodes')}</span>
          <TagChips
            tags={styleTags}
            value={filters.styleTagCodes ?? []}
            onChange={(codes) => change({ styleTagCodes: codes.length ? codes : undefined })}
          />
        </div>
        <div className="rec-field">
          <span>Mục tiêu {mark('goalTagCodes')}</span>
          <TagChips
            tags={goalTags}
            value={filters.goalTagCodes ?? []}
            onChange={(codes) => change({ goalTagCodes: codes.length ? codes : undefined })}
          />
        </div>
        <div className="rec-field">
          <span>Phần cần cải thiện {mark('topicTagCodes')}</span>
          <TagChips
            tags={filters.subjectId ? topicTags : []}
            value={filters.topicTagCodes ?? []}
            onChange={(codes) => change({ topicTagCodes: codes.length ? codes : undefined })}
            emptyText={filters.subjectId ? 'Môn này chưa có danh sách chủ đề' : 'Chọn môn để xem các chủ đề'}
          />
        </div>

        <div className="rec-actions">
          <button
            className="student-button secondary"
            type="button"
            onClick={() => {
              setFilters({});
              setFromSentence(new Set());
              setNotes([]);
              setParsedSentence(null);
              setSentence('');
              setUseRequestId('');
            }}
          >
            Xoá bộ lọc
          </button>
          <button className="student-button" type="submit" disabled={isSearching}>
            {isSearching ? 'Đang tìm…' : 'Tìm gia sư phù hợp'}
          </button>
        </div>
      </form>

      <section className="rec-results" aria-live="polite">
        {notice && (
          <div className="student-notice" role="status">
            {notice}
          </div>
        )}
        {error && (
          <div className="student-error" role="alert">
            {error}
          </div>
        )}

        {!result && !isSearching && (
          <div className="student-empty">
            <h3>Chọn tiêu chí rồi bấm “Tìm gia sư phù hợp”</h3>
            <p>Chỉ cần môn và lớp là đủ để bắt đầu. Càng nhiều tiêu chí, gợi ý càng sát.</p>
          </div>
        )}

        {result && result.needsInput.length > 0 && (
          <div className="student-empty">
            <h3>Cần thêm {result.needsInput.map((field) => NEEDS_INPUT[field] ?? field).join(' và ')}</h3>
            <p>Chọn trong bộ lọc, hoặc dùng một tin học của bạn để điền sẵn.</p>
          </div>
        )}

        {result && result.needsInput.length === 0 && (
          <>
            <div className="rec-summary">
              <strong>{result.totalElements} gia sư phù hợp</strong>
              {result.lowConfidence && (
                <span className="rec-low-confidence">
                  Hệ thống còn ít thông tin để so khớp. Thêm tiêu chí để gợi ý chính xác hơn.
                </span>
              )}
            </div>

            {result.relaxations.length > 0 && (
              <div className="student-panel rec-relaxations">
                <strong>Muốn có thêm lựa chọn?</strong>
                {result.relaxations.map((relaxation) => (
                  <div className="rec-relaxation" key={relaxation.code}>
                    <span>{relaxation.message}</span>
                    <button
                      className="student-button secondary"
                      type="button"
                      disabled={isSearching}
                      onClick={() => applyRelaxation(relaxation)}
                    >
                      Áp dụng
                    </button>
                  </div>
                ))}
              </div>
            )}

            {active && (
              <section className="student-panel rec-apply">
                <h2>Đăng ký học với {active.tutor.displayName}</h2>
                <p className="rec-hint">{active.title}</p>
                <form className="student-form-grid" onSubmit={(event) => void apply(event)}>
                  <label>
                    Lớp
                    <select name="gradeId" required defaultValue={filters.gradeId ?? ''}>
                      <option value="">Chọn lớp</option>
                      {grades.map((grade) => (
                        <option key={grade.id} value={grade.id}>
                          {grade.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Học phí đề xuất mỗi buổi
                    <input
                      name="proposedPrice"
                      type="number"
                      min="0"
                      step="10000"
                      required
                      defaultValue={active.expectedPrice ?? ''}
                    />
                  </label>
                  <label className="student-field-full">
                    Lịch học mong muốn
                    <input name="preferredSchedule" placeholder="Ví dụ: tối thứ 3 và thứ 5" />
                  </label>
                  <label className="student-field-full">
                    Lời nhắn
                    <textarea name="message" rows={3} />
                  </label>
                  <div className="student-form-actions">
                    <button className="student-button secondary" type="button" onClick={() => setActive(null)}>
                      Đóng
                    </button>
                    <button className="student-button" type="submit">
                      Gửi đơn đăng ký
                    </button>
                  </div>
                </form>
              </section>
            )}

            <div className="rec-list">
              {visible.map((item) => (
                <article
                  className={item.isExploration ? 'student-panel rec-card exploration' : 'student-panel rec-card'}
                  key={item.teachingRequestId}
                >
                  <div className="rec-card-head">
                    <span className="rec-avatar">
                      {item.tutor.avatarUrl ? (
                        <img src={item.tutor.avatarUrl} alt="" />
                      ) : (
                        getInitials(item.tutor.displayName)
                      )}
                    </span>
                    <div>
                      <h3>{item.tutor.displayName}</h3>
                      <small>
                        {item.tutor.totalReviews
                          ? `★ ${Number(item.tutor.averageRating ?? 0).toFixed(1)} · ${item.tutor.totalReviews} đánh giá`
                          : 'Chưa có đánh giá'}
                      </small>
                    </div>
                    <span className="rec-score" title="Điểm phù hợp do hệ thống tính, từ 0 đến 100">
                      {Math.round(item.score * 100)}
                      <small>điểm phù hợp</small>
                    </span>
                  </div>
                  <p className="rec-title">{item.title || 'Tin dạy'}</p>
                  <div className="rec-facts">
                    <span>
                      {item.expectedPrice ? `${formatCurrency(item.expectedPrice)} / buổi` : 'Học phí thoả thuận'}
                    </span>
                    <span>{item.teachingMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</span>
                    {item.districts.length > 0 && <span>{item.districts.slice(0, 3).join(', ')}</span>}
                    {item.isExploration && <span className="rec-badge">Gợi ý mới</span>}
                  </div>
                  {item.reasons.length > 0 && (
                    <ul className="rec-reasons">
                      {item.reasons.map((reason) => (
                        <li key={reason}>{reason}</li>
                      ))}
                    </ul>
                  )}
                  {item.caution && <p className="rec-caution">{item.caution}</p>}
                  <div className="rec-card-actions">
                    <button className="rec-link" type="button" onClick={() => dismiss(item)}>
                      Không quan tâm
                    </button>
                    <button
                      className={favorites.has(item.teachingRequestId) ? 'rec-link active' : 'rec-link'}
                      type="button"
                      aria-pressed={favorites.has(item.teachingRequestId)}
                      onClick={() => favorite(item)}
                    >
                      {favorites.has(item.teachingRequestId) ? '♥ Đã lưu' : '♡ Lưu gia sư'}
                    </button>
                    <button className="student-button" type="button" onClick={() => open(item)}>
                      Đăng ký học
                    </button>
                  </div>
                </article>
              ))}
            </div>

            {!visible.length && (
              <div className="student-empty">
                <h3>Chưa có gia sư nào khớp các tiêu chí này</h3>
                <p>Thử nới khu vực, học phí hoặc lịch học.</p>
              </div>
            )}

            {!allLoaded && (
              <div className="rec-more">
                <button
                  className="student-button secondary"
                  type="button"
                  disabled={isLoadingMore}
                  onClick={() => void loadMore()}
                >
                  {isLoadingMore ? 'Đang tải…' : 'Xem thêm gia sư'}
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function priceOf(min: number | string | null | undefined, max: number | string | null | undefined) {
  const low = min === '' || min === null || min === undefined ? null : Number(min);
  const high = max === '' || max === null || max === undefined ? null : Number(max);
  return low === null && high === null ? undefined : { min: low, max: high };
}

function messageOf(caught: unknown, fallback: string): string {
  return caught instanceof ApiError || caught instanceof Error ? caught.message || fallback : fallback;
}
