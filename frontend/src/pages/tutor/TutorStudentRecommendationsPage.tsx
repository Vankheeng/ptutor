import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AreaPicker } from '../../components/recommendation/AreaPicker';
import { AvailabilityEditor } from '../../components/recommendation/AvailabilityEditor';
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
  type ModePreference,
  type RecommendationAction,
  type RecommendationTag,
  type Relaxation,
  type SavedSearch,
  type StudentRecommendationItem,
  type StudentRecommendationResponse,
  type StudentSearchFilters
} from '../../services/recommendationApi';
import { getSubjects } from '../../services/subjectApi';
import { getMyTeachingRequests, getTutorProfile } from '../../services/tutorApi';
import type { Grade, Subject, TeachingRequest } from '../../types/api';
import { formatCurrency } from '../../utils/formatters';

const PAGE_SIZE = 20;
const MODES: { value: ModePreference; label: string }[] = [
  { value: 'ANY', label: 'Cả hai' },
  { value: 'ONLINE', label: 'Online' },
  { value: 'OFFLINE', label: 'Trực tiếp' }
];
const NEEDS_INPUT: Record<string, string> = { subjectId: 'môn dạy', gradeIds: 'lớp' };

/** "Gợi ý học viên": studying postings ranked for this tutor (design 05, 5.3). Students are shown in short. */
export function TutorStudentRecommendationsPage() {
  const navigate = useNavigate();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [segmentTags, setSegmentTags] = useState<RecommendationTag[]>([]);
  const [myPostings, setMyPostings] = useState<TeachingRequest[]>([]);
  const [homeProvinceId, setHomeProvinceId] = useState<string | null>(null);

  const [filters, setFilters] = useState<StudentSearchFilters>({});
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
    getTutorProfile(controller.signal)
      .then((profile) => setHomeProvinceId(profile.address?.provinceId ?? null))
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
    async (searchFilters: StudentSearchFilters, savedSearchId?: string) => {
      if (hasInvalidSlot(searchFilters.availabilities ?? [])) {
        setError('Khung giờ rảnh chưa hợp lệ: giờ kết thúc phải sau giờ bắt đầu.');
        return;
      }
      setError('');
      setIsSearching(true);
      try {
        const response = await searchStudents(
          { filters: searchFilters, useTeachingRequestId: usePostingId || undefined, savedSearchId },
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

  const fillFromSentence = async () => {
    const text = sentence.trim();
    if (!text) return;
    setIsParsing(true);
    setError('');
    try {
      const parsed = await parseQuery(text, 'TUTOR_TO_STUDENT');
      const parsedFilters = parsed.filters as unknown as StudentSearchFilters;
      setFilters((previous) => ({ ...previous, ...parsedFilters }));
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

  /** The posting page is the existing flow: full details and the proposal form. */
  const open = (item: StudentRecommendationItem) => {
    send(item, 'VIEWED');
    navigate(`/tutor/studying-requests/${item.studyingRequestId}`);
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
    void run(search.filters as StudentSearchFilters, search.id);
  };

  const applyRelaxation = (relaxation: Relaxation) => {
    const next = { ...filters, ...relaxation.apply } as StudentSearchFilters;
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
          <span className="tutor-eyebrow">GỢI Ý HỌC VIÊN</span>
          <h1>Học viên phù hợp với bạn</h1>
        </div>
      </div>

      <div className="rec-layout">
        <form
          className="tutor-panel rec-filters"
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
              placeholder="Ví dụ: Dạy Toán lớp 8, 9 online, tối thứ 2 và 4, khoảng 200k một buổi"
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

          <div className="rec-divider" />

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

          <label className="rec-field">
            <span>Môn dạy {mark('subjectId')}</span>
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

          <div className="rec-field">
            <span>Lớp {mark('gradeIds')}</span>
            <div className="rec-chips">
              {grades.map((grade) => {
                const chosen = (filters.gradeIds ?? []).includes(grade.id);
                return (
                  <button
                    key={grade.id}
                    type="button"
                    className={chosen ? 'rec-chip active' : 'rec-chip'}
                    aria-pressed={chosen}
                    onClick={() => toggleGrade(grade.id)}
                  >
                    {grade.name}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="rec-field">
            <span>Hình thức dạy {mark('learningMode')}</span>
            <div className="rec-segmented" role="radiogroup" aria-label="Hình thức dạy">
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
              <span>Khu vực dạy trực tiếp được {mark('area')}</span>
              <AreaPicker value={filters.area} onChange={(area) => change({ area })} homeProvinceId={homeProvinceId} />
            </div>
          )}

          <label className="rec-field">
            <span>Học phí mong muốn mỗi buổi (đồng) {mark('expectedPrice')}</span>
            <input
              type="number"
              min={0}
              step={10000}
              placeholder="Để trống: lấy mức giữa các tin dạy của bạn"
              value={filters.expectedPrice ?? ''}
              onChange={(event) =>
                change({ expectedPrice: event.target.value === '' ? undefined : Number(event.target.value) })
              }
            />
          </label>

          <div className="rec-field">
            <span>Lịch rảnh {mark('availabilities')}</span>
            <AvailabilityEditor
              value={filters.availabilities ?? []}
              onChange={(next) => change({ availabilities: next.length ? next : undefined })}
            />
          </div>

          <div className="rec-field">
            <span>Đối tượng muốn dạy {mark('targetSegmentCodes')}</span>
            <TagChips
              tags={segmentTags}
              value={filters.targetSegmentCodes ?? []}
              onChange={(codes) => change({ targetSegmentCodes: codes.length ? codes : undefined })}
            />
          </div>

          <div className="rec-actions">
            <button
              className="tutor-button secondary"
              type="button"
              onClick={() => {
                setFilters({});
                setFromSentence(new Set());
                setNotes([]);
                setSentence('');
                setUsePostingId('');
              }}
            >
              Xoá bộ lọc
            </button>
            <button className="tutor-button" type="submit" disabled={isSearching}>
              {isSearching ? 'Đang tìm…' : 'Tìm học viên phù hợp'}
            </button>
          </div>
        </form>

        <section className="rec-results" aria-live="polite">
          <div className="rec-toolbar">
            <strong>
              {result && result.needsInput.length === 0 ? `${result.totalElements} học viên phù hợp` : ''}
            </strong>
            <SavedSearchControls
              direction="TUTOR_TO_STUDENT"
              filters={filters}
              suggestedName={[
                subjects.find((subject) => subject.id === filters.subjectId)?.name,
                grades
                  .filter((grade) => (filters.gradeIds ?? []).includes(grade.id))
                  .map((grade) => grade.name)
                  .join(', ')
              ]
                .filter(Boolean)
                .join(' – ')}
              canSave={Boolean(result && result.needsInput.length === 0)}
              onRun={runSaved}
              variant="tutor"
            />
          </div>
          {error && (
            <div className="tutor-error" role="alert">
              {error}
            </div>
          )}

          {notVerified && (
            <div className="tutor-empty">
              <h3>Hồ sơ của bạn chưa được duyệt</h3>
              <p>Gợi ý học viên mở khi hồ sơ gia sư đã được xác minh.</p>
              <Link className="tutor-button" to="/tutor/profile">
                Xem hồ sơ
              </Link>
            </div>
          )}

          {!result && !isSearching && !notVerified && (
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
                  <span className="rec-low-confidence">Còn ít thông tin để so khớp. Thêm tiêu chí để gợi ý sát hơn.</span>
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
                    <p className="rec-title">{item.title || 'Tin tìm gia sư'}</p>
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
      </div>
    </section>
  );
}

function priceText(range: StudentRecommendationItem['priceRange']) {
  if (!range || (range.min === null && range.max === null)) return 'Học phí thoả thuận';
  if (range.min !== null && range.max !== null) return `${formatCurrency(range.min)} – ${formatCurrency(range.max)} / buổi`;
  return `${range.max !== null ? 'Đến' : 'Từ'} ${formatCurrency((range.max ?? range.min) as number)} / buổi`;
}
