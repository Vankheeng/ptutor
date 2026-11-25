import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { SavedSearchControls } from '../../components/recommendation/SavedSearches';
import { hasInvalidSlot } from '../../components/recommendation/availability';
import { TagChips } from '../../components/recommendation/TagChips';
import '../../components/recommendation/recommendation.css';
import { getGrades } from '../../services/gradeApi';
import { errorMessage } from '../../services/errorMessages';
import { ApiError } from '../../services/httpClient';
import {
  getRecommendationTags,
  getStudentPage,
  parseQuery,
  recordEvent,
  refineStudents,
  searchStudents,
  type RecommendationAction,
  type RecommendationTag,
  type Relaxation,
  type SavedSearch,
  type StudentRecommendationItem,
  type StudentRecommendationResponse,
  type StudentSearchFilters
} from '../../services/recommendationApi';
import { getSubjects } from '../../services/subjectApi';
import { getMyTeachingRequests } from '../../services/tutorApi';
import type { Grade, Subject, TeachingRequest } from '../../types/api';
import { formatCurrency } from '../../utils/formatters';
import { TutorStudentSearchPage } from './TutorStudentSearchPage';
import { TutorStudentSearchFilters, type TutorStudentFilters } from './components/TutorStudentSearchFilters';
import { initialTutorStudentFilters } from './tutorStudentFilterDefaults';

const PAGE_SIZE = 20;
const NEEDS_INPUT: Record<string, string> = { subjectId: 'môn dạy', gradeIds: 'lớp' };

/** Shared tutor search: ordinary request list and ranked student recommendations. */
export function TutorStudentRecommendationsPage() {
  const navigate = useNavigate();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [segmentTags, setSegmentTags] = useState<RecommendationTag[]>([]);
  const [myPostings, setMyPostings] = useState<TeachingRequest[]>([]);

  const [filters, setFilters] = useState<StudentSearchFilters>({});
  const [activeSmartFilters, setActiveSmartFilters] = useState<StudentSearchFilters>({});
  const [regularFilters, setRegularFilters] = useState<TutorStudentFilters>(initialTutorStudentFilters);
  const [appliedRegularFilters, setAppliedRegularFilters] = useState<TutorStudentFilters>(initialTutorStudentFilters);
  const [regularSearchVersion, setRegularSearchVersion] = useState(0);
  const [resultsMode, setResultsMode] = useState<'regular' | 'smart'>('regular');
  const [usePostingId, setUsePostingId] = useState('');
  const [sentence, setSentence] = useState('');
  const [fromSentence, setFromSentence] = useState<Set<string>>(new Set());
  const [notes, setNotes] = useState<string[]>([]);
  const [isParsing, setIsParsing] = useState(false);

  const [result, setResult] = useState<StudentRecommendationResponse | null>(null);
  const [items, setItems] = useState<StudentRecommendationItem[]>([]);
  const [pagesLoaded, setPagesLoaded] = useState(0);
  const [allLoaded, setAllLoaded] = useState(false);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [hasNewSignal, setHasNewSignal] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [notVerified, setNotVerified] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getSubjects('name', controller.signal)
      .then(setSubjects)
      .catch(() => undefined);
    getGrades(controller.signal)
      .then(setGrades)
      .catch(() => undefined);
    getRecommendationTags('SEGMENT')
      .then(setSegmentTags)
      .catch(() => undefined);
    getMyTeachingRequests('OPEN', controller.signal)
      .then(setMyPostings)
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  const change = useCallback((changes: Partial<StudentSearchFilters>) => {
    setFilters((previous) => ({ ...previous, ...changes }));
    setFromSentence((previous) => {
      const next = new Set(previous);
      Object.keys(changes).forEach((key) => next.delete(key));
      return next;
    });
  }, []);

  /** @param savedSearchId run a saved search: the server uses its saved criteria */
  const run = useCallback(
    async (searchFilters: StudentSearchFilters, savedSearchId?: string, postingId = usePostingId) => {
      if (hasInvalidSlot(searchFilters.availabilities ?? [])) {
        setError('Khung giờ rảnh chưa hợp lệ: giờ kết thúc phải sau giờ bắt đầu.');
        return;
      }
      setError('');
      setResultsMode('smart');
      setActiveSmartFilters(searchFilters);
      setResult(null);
      setIsSearching(true);
      try {
        const response = await searchStudents(
          { filters: searchFilters, useTeachingRequestId: postingId || undefined, savedSearchId },
          PAGE_SIZE
        );
        setNotVerified(false);
        setResult(response);
        setItems(response.items);
        setPagesLoaded(1);
        setAllLoaded(response.items.length >= response.totalElements);
        setDismissed(new Set());
        setFavorites(new Set());
        setHasNewSignal(false);
      } catch (caught) {
        if (caught instanceof ApiError && caught.code === 'TUTOR_PROFILE_NOT_VERIFIED') {
          setNotVerified(true);
          setResult(null);
        } else {
          setError(errorMessage(caught, 'Không tìm được gợi ý. Vui lòng thử lại.'));
        }
      } finally {
        setIsSearching(false);
      }
    },
    [usePostingId]
  );

  const runRegular = () => {
    setAppliedRegularFilters({ ...regularFilters });
    setRegularSearchVersion((version) => version + 1);
    setResultsMode('regular');
    setError('');
  };

  const runSmart = () => {
    const slots = regularFilters.scheduleSlots
      .filter((slot) => slot.dayOfWeek && slot.startTime && slot.endTime)
      .map((slot) => ({ dayOfWeek: Number(slot.dayOfWeek), startTime: slot.startTime, endTime: slot.endTime }));
    const minimum = Number(regularFilters.minBudget) || 0;
    const maximum = Number(regularFilters.maxBudget) || 0;
    const next: StudentSearchFilters = {
      ...filters,
      ...(regularFilters.subjectId ? { subjectId: regularFilters.subjectId } : {}),
      ...(regularFilters.gradeId
        ? { gradeIds: Array.from(new Set([regularFilters.gradeId, ...(filters.gradeIds ?? [])])) }
        : {}),
      ...(regularFilters.learningMode !== 'ALL' ? { learningMode: regularFilters.learningMode } : {}),
      ...(regularFilters.districtId
        ? {
            area: {
              scope: 'DISTRICTS' as const,
              provinceId: regularFilters.provinceId,
              districtIds: [regularFilters.districtId]
            }
          }
        : regularFilters.provinceId
          ? { area: { scope: 'PROVINCE' as const, provinceId: regularFilters.provinceId } }
          : {}),
      ...(regularFilters.maxBudget || regularFilters.minBudget
        ? { expectedPrice: minimum && maximum ? Math.round((minimum + maximum) / 2) : minimum || maximum }
        : {}),
      ...(slots.length ? { availabilities: slots } : {})
    };
    void run(next);
  };

  const clearAll = () => {
    setRegularFilters({ ...initialTutorStudentFilters });
    setAppliedRegularFilters({ ...initialTutorStudentFilters });
    setRegularSearchVersion((version) => version + 1);
    setFilters({});
    setActiveSmartFilters({});
    setFromSentence(new Set());
    setNotes([]);
    setSentence('');
    setUsePostingId('');
    setResult(null);
    setError('');
    setResultsMode('regular');
  };

  const fillFromSentence = async () => {
    const text = sentence.trim();
    if (!text) return;
    setIsParsing(true);
    setError('');
    try {
      const parsed = await parseQuery(text, 'TUTOR_TO_STUDENT');
      const parsedFilters = parsed.filters as unknown as StudentSearchFilters;
      setFilters((previous) => ({ ...previous, ...parsedFilters }));
      setRegularFilters((previous) => ({
        ...previous,
        subjectId: parsedFilters.subjectId ?? previous.subjectId,
        gradeId: parsedFilters.gradeIds?.[0] ?? previous.gradeId,
        learningMode:
          parsedFilters.learningMode === 'ONLINE' || parsedFilters.learningMode === 'OFFLINE'
            ? parsedFilters.learningMode
            : previous.learningMode
      }));
      setFromSentence(new Set(Object.keys(parsedFilters)));
      setNotes(parsed.notes);
    } catch (caught) {
      setError(errorMessage(caught, 'Chưa phân tích được câu. Bạn có thể chọn bộ lọc bằng tay.'));
    } finally {
      setIsParsing(false);
    }
  };

  const send = (item: StudentRecommendationItem, action: RecommendationAction) => {
    if (!result?.logId) return;
    // Feedback is best effort: a lost event must never get in the tutor's way.
    recordEvent(result.logId, item.studyingRequestId, action).catch(() => undefined);
    if (action !== 'DISMISSED') setHasNewSignal(true);
  };

  /** The posting page is the existing flow: full details and the proposal form. It reports the proposal (CONTACTED). */
  const open = (item: StudentRecommendationItem) => {
    send(item, 'VIEWED');
    navigate(`/tutor/studying-requests/${item.studyingRequestId}`, {
      state: result?.logId ? { recommendationLogId: result.logId } : undefined
    });
  };

  const favorite = (item: StudentRecommendationItem) => {
    if (favorites.has(item.studyingRequestId)) return;
    setFavorites((previous) => new Set(previous).add(item.studyingRequestId));
    send(item, 'FAVORITED');
  };

  const dismiss = (item: StudentRecommendationItem) => {
    setDismissed((previous) => new Set(previous).add(item.studyingRequestId));
    send(item, 'DISMISSED');
  };

  const loadMore = async () => {
    if (!result?.logId) return;
    setIsLoadingMore(true);
    try {
      if (hasNewSignal) {
        // What was liked so far reorders everything not shown yet; it all comes back at once.
        const refined = await refineStudents(result.logId, items.length);
        setItems((previous) => [...previous, ...refined.items]);
        setAllLoaded(true);
        setHasNewSignal(false);
      } else {
        const next = await getStudentPage(result.logId, pagesLoaded, PAGE_SIZE);
        setItems((previous) => [...previous, ...next.items]);
        setPagesLoaded((previous) => previous + 1);
        setAllLoaded(items.length + next.items.length >= next.totalElements || next.items.length === 0);
      }
    } catch (caught) {
      setError(errorMessage(caught, 'Không tải thêm được kết quả.'));
    } finally {
      setIsLoadingMore(false);
    }
  };

  const runSaved = (search: SavedSearch) => {
    setFilters(search.filters as StudentSearchFilters);
    setFromSentence(new Set());
    setNotes([]);
    setUsePostingId('');
    void run(search.filters as StudentSearchFilters, search.id, '');
  };

  const applyRelaxation = (relaxation: Relaxation) => {
    const next = { ...activeSmartFilters, ...relaxation.apply } as StudentSearchFilters;
    setFilters(next);
    void run(next);
  };

  const toggleGrade = (gradeId: string) => {
    const chosen = filters.gradeIds ?? [];
    const next = chosen.includes(gradeId) ? chosen.filter((id) => id !== gradeId) : [...chosen, gradeId];
    change({ gradeIds: next.length ? next : undefined });
  };

  const visible = useMemo(() => items.filter((item) => !dismissed.has(item.studyingRequestId)), [items, dismissed]);
  const mark = (key: string) => (fromSentence.has(key) ? <em className="rec-from-sentence">từ câu của bạn</em> : null);

  return (
    <section className="tutor-student-recommendations">
      <div className="tutor-page-heading">
        <div>
          <h1>Tìm kiếm học viên</h1>
        </div>
      </div>

      <TutorStudentSearchFilters
        filters={regularFilters}
        onChange={setRegularFilters}
        onSearch={runRegular}
        onSmartSearch={runSmart}
        onClear={clearAll}
        smartSearching={isSearching}
        advancedContent={
          <div className="tutor-smart-fields">
            <h3>Tiêu chí gợi ý thông minh</h3>
            <label className="rec-field tutor-smart-sentence">
              <span>Mô tả nhanh bằng một câu</span>
              <textarea
                rows={3}
                maxLength={500}
                value={sentence}
                onChange={(event) => setSentence(event.target.value)}
                placeholder="Ví dụ: Dạy Toán lớp 8, 9 online, tối thứ 2 và 4"
              />
            </label>
            <button
              className="tutor-button secondary"
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
            {myPostings.length > 0 && (
              <label className="rec-field">
                <span>Dùng tiêu chí từ tin dạy của tôi</span>
                <select value={usePostingId} onChange={(event) => setUsePostingId(event.target.value)}>
                  <option value="">Không dùng</option>
                  {myPostings.map((posting) => (
                    <option key={posting.id} value={posting.id}>
                      {posting.title || posting.subjectName || 'Tin dạy'}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="rec-field tutor-smart-grades">
              <span>Thêm lớp cho gợi ý {mark('gradeIds')}</span>
              <details className="rec-tag-dropdown tutor-grade-dropdown">
                <summary>
                  {filters.gradeIds?.length
                    ? grades
                        .filter((grade) => filters.gradeIds?.includes(grade.id))
                        .map((grade) => grade.name)
                        .join(', ')
                    : 'Chọn thêm lớp'}
                </summary>
                <div className="rec-tag-options">
                  {grades.map((grade) => (
                    <label key={grade.id}>
                      <input
                        type="checkbox"
                        checked={(filters.gradeIds ?? []).includes(grade.id)}
                        onChange={() => toggleGrade(grade.id)}
                      />
                      {grade.name}
                    </label>
                  ))}
                </div>
              </details>
            </div>
            <div className="rec-field">
              <span>Đối tượng muốn dạy {mark('targetSegmentCodes')}</span>
              <TagChips
                tags={segmentTags}
                value={filters.targetSegmentCodes ?? []}
                onChange={(codes) => change({ targetSegmentCodes: codes.length ? codes : undefined })}
              />
            </div>
          </div>
        }
      />
      {error && (
        <div className="tutor-error" role="alert">
          {error}
        </div>
      )}
      <div className="tutor-student-saved-searches">
        <SavedSearchControls
          direction="TUTOR_TO_STUDENT"
          filters={activeSmartFilters}
          suggestedName={[
            subjects.find((subject) => subject.id === activeSmartFilters.subjectId)?.name,
            grades
              .filter((grade) => (activeSmartFilters.gradeIds ?? []).includes(grade.id))
              .map((grade) => grade.name)
              .join(', ')
          ]
            .filter(Boolean)
            .join(' – ')}
          canSave={Boolean(resultsMode === 'smart' && result && result.needsInput.length === 0)}
          onRun={runSaved}
          variant="tutor"
        />
      </div>
      {resultsMode === 'regular' ? (
        <TutorStudentSearchPage
          key={regularSearchVersion}
          embedded
          appliedFilters={appliedRegularFilters}
          hasSearched={regularSearchVersion > 0}
        />
      ) : (
        <section className="rec-results" aria-live="polite">
          <div className="rec-toolbar">
            <strong>
              {result && result.needsInput.length === 0
                ? `Gợi ý thông minh · ${result.totalElements} học viên phù hợp`
                : 'Gợi ý thông minh'}
            </strong>
          </div>
          {notVerified && (
            <div className="tutor-empty">
              <h3>Hồ sơ của bạn chưa được duyệt</h3>
              <p>Gợi ý học viên mở khi hồ sơ gia sư đã được xác minh.</p>
              <Link className="tutor-button" to="/tutor/profile">
                Xem hồ sơ
              </Link>
            </div>
          )}

          {isSearching && <div className="tutor-empty">Đang tìm học viên phù hợp…</div>}

          {!result && !isSearching && !notVerified && !error && (
            <div className="tutor-empty">
              <h3>Chọn môn và lớp rồi bấm “Tìm học viên phù hợp”</h3>
            </div>
          )}

          {result && result.needsInput.length > 0 && (
            <div className="tutor-empty">
              <h3>Cần thêm {result.needsInput.map((field) => NEEDS_INPUT[field] ?? field).join(' và ')}</h3>
            </div>
          )}

          {result && result.needsInput.length === 0 && (
            <>
              <div className="rec-summary">
                {result.lowConfidence && (
                  <span className="rec-low-confidence">
                    Còn ít thông tin để so khớp. Thêm tiêu chí để gợi ý sát hơn.
                  </span>
                )}
              </div>

              {result.relaxations.length > 0 && (
                <div className="tutor-panel rec-relaxations">
                  <strong>Muốn có thêm lựa chọn?</strong>
                  {result.relaxations.map((relaxation) => (
                    <div className="rec-relaxation" key={relaxation.code}>
                      <span>{relaxation.message}</span>
                      <button
                        className="tutor-button secondary"
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

              <div className="rec-list">
                {visible.map((item) => (
                  <article
                    className={item.isExploration ? 'tutor-panel rec-card exploration' : 'tutor-panel rec-card'}
                    key={item.studyingRequestId}
                  >
                    <div className="rec-card-head">
                      <span className="rec-avatar">HS</span>
                      <div>
                        <h3>{item.student.displayName}</h3>
                        <small>{item.student.district ?? (item.learningMode === 'ONLINE' ? 'Học online' : '')}</small>
                      </div>
                      <span className="rec-score" title="Điểm phù hợp do hệ thống tính, từ 0 đến 100">
                        {Math.round(item.score * 100)}
                        <small>điểm phù hợp</small>
                      </span>
                    </div>
                    <button className="rec-title rec-title-button" type="button" onClick={() => open(item)}>
                      {item.title || 'Tin tìm gia sư'}
                    </button>
                    {item.learningGoals && <p className="rec-hint">{item.learningGoals}</p>}
                    <div className="rec-facts">
                      <span>{priceText(item.priceRange)}</span>
                      <span>{item.learningMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</span>
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
                      <button className="rec-link" type="button" onClick={() => open(item)}>
                        Xem chi tiết tin
                      </button>
                      <button className="rec-link" type="button" onClick={() => dismiss(item)}>
                        Không quan tâm
                      </button>
                      <button
                        className={favorites.has(item.studyingRequestId) ? 'rec-link active' : 'rec-link'}
                        type="button"
                        aria-pressed={favorites.has(item.studyingRequestId)}
                        onClick={() => favorite(item)}
                      >
                        {favorites.has(item.studyingRequestId) ? '♥ Đã lưu' : '♡ Lưu học viên'}
                      </button>
                      <button className="tutor-button" type="button" onClick={() => open(item)}>
                        Xem và gửi đề nghị
                      </button>
                    </div>
                  </article>
                ))}
              </div>

              {!visible.length && (
                <div className="tutor-empty">
                  <h3>Chưa có học viên nào khớp các tiêu chí này</h3>
                </div>
              )}

              {!allLoaded && (
                <div className="rec-more">
                  <button
                    className="tutor-button secondary"
                    type="button"
                    disabled={isLoadingMore}
                    onClick={() => void loadMore()}
                  >
                    {isLoadingMore ? 'Đang tải…' : 'Xem thêm học viên'}
                  </button>
                </div>
              )}
            </>
          )}
        </section>
      )}
    </section>
  );
}

function priceText(range: StudentRecommendationItem['priceRange']) {
  if (!range || (range.min === null && range.max === null)) return 'Học phí thoả thuận';
  if (range.min !== null && range.max !== null)
    return `${formatCurrency(range.min)} – ${formatCurrency(range.max)} / buổi`;
  return `${range.max !== null ? 'Đến' : 'Từ'} ${formatCurrency((range.max ?? range.min) as number)} / buổi`;
}
