import { useMemo, useState } from 'react';
import { useStudyingRequests } from '../../hooks/useStudyingRequests';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { TutorStudentSearchFilters, type TutorStudentFilters } from './components/TutorStudentSearchFilters';
import { TutorStudyingRequestCard } from './components/TutorStudyingRequestCard';

const PAGE_SIZE = 6;

export function TutorStudentSearchPage() {
  const [filters, setFilters] = useState<TutorStudentFilters>({
    search: '',
    learningMode: 'ALL',
    provinceId: '',
    provinceName: '',
    districtId: '',
    subjectId: '',
    gradeId: '',
    minBudget: '',
    maxBudget: '',
    scheduleSlots: [{ dayOfWeek: '', startTime: '', endTime: '' }]
  });
  const [activeFilters, setActiveFilters] = useState(filters);
  const [page, setPage] = useState(0);
  const requestsQuery = useStudyingRequests({
    page: 0,
    size: 50,
    status: 'OPEN',
    ...(activeFilters.learningMode === 'ALL' ? {} : { learningMode: activeFilters.learningMode })
  });
  const proposalsQuery = useTutorProposals();

  const requests = useMemo(() => {
    const term = activeFilters.search.trim().toLocaleLowerCase('vi-VN');
    const provinceName = activeFilters.provinceName.toLocaleLowerCase('vi-VN');
    const minimum = activeFilters.minBudget === '' ? null : Number(activeFilters.minBudget);
    const maximum = activeFilters.maxBudget === '' ? null : Number(activeFilters.maxBudget);
    const englishDays = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
    const vietnameseDays = ['thứ 2', 'thứ 3', 'thứ 4', 'thứ 5', 'thứ 6', 'thứ 7', 'chủ nhật'];

    return (requestsQuery.data?.content ?? []).filter((request) => {
      const matchesText = [
        request.studentFirstName,
        request.studentLastName,
        request.title,
        request.subjectName,
        request.gradeName,
        request.districtName,
        request.provinceName,
        request.detailAddress,
        request.description,
        request.learningGoals
      ].some((value) => value?.toLocaleLowerCase('vi-VN').includes(term));
      const matchesProvince = !provinceName || request.provinceName?.toLocaleLowerCase('vi-VN') === provinceName;
      const matchesDistrict = !activeFilters.districtId || request.districtId === activeFilters.districtId;
      const matchesSubject = !activeFilters.subjectId || request.subjectId === activeFilters.subjectId;
      const matchesGrade = !activeFilters.gradeId || request.gradeId === activeFilters.gradeId;
      const scheduleText = request.preferredSchedule?.toLocaleLowerCase('vi-VN') ?? '';
      const slots = request.availabilities ?? [];
      const requestedSlots = activeFilters.scheduleSlots.filter(
        (slot) => slot.dayOfWeek || slot.startTime || slot.endTime
      );
      const matchesLegacySchedule = requestedSlots.some((filterSlot) => {
        const dayIndex = filterSlot.dayOfWeek ? Number(filterSlot.dayOfWeek) - 1 : -1;
        const matchesDay =
          dayIndex < 0 ||
          scheduleText.includes(englishDays[dayIndex]) ||
          scheduleText.includes(vietnameseDays[dayIndex]);
        const matchesStart = !filterSlot.startTime || !scheduleText || scheduleText.includes(filterSlot.startTime);
        const matchesEnd = !filterSlot.endTime || !scheduleText || scheduleText.includes(filterSlot.endTime);
        return matchesDay && matchesStart && matchesEnd;
      });
      const matchesStructuredSchedule = slots.some((slot) =>
        requestedSlots.some((filterSlot) => {
          const matchesDay = !filterSlot.dayOfWeek || slot.dayOfWeek === Number(filterSlot.dayOfWeek);
          const matchesStart = !filterSlot.startTime || slot.endTime > filterSlot.startTime;
          const matchesEnd = !filterSlot.endTime || slot.startTime < filterSlot.endTime;
          return matchesDay && matchesStart && matchesEnd;
        })
      );
      const matchesSchedule = requestedSlots.length === 0 || (slots.length ? matchesStructuredSchedule : matchesLegacySchedule);
      const matchesBudget =
        (minimum === null || request.maxPrice === null || request.maxPrice >= minimum) &&
        (maximum === null || request.minPrice === null || request.minPrice <= maximum);

      return matchesText && matchesProvince && matchesDistrict && matchesSubject && matchesGrade && matchesSchedule && matchesBudget;
    });
  }, [activeFilters, requestsQuery.data]);

  const pageCount = Math.ceil(requests.length / PAGE_SIZE);
  const pageRequests = requests.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <>
      <header className="tutor-page-heading">
        <h1>Tìm kiếm học viên</h1>
        <span className="tutor-count">{requests.length} kết quả</span>
      </header>
      <section className="tutor-marketplace" aria-label="Tìm kiếm học viên">
        <TutorStudentSearchFilters
          filters={filters}
          onChange={setFilters}
          onSearch={() => {
            setActiveFilters(filters);
            setPage(0);
          }}
        />
        {requestsQuery.error ? (
          <div className="tutor-error" role="alert">{requestsQuery.error}</div>
        ) : requestsQuery.isLoading ? (
          <div className="tutor-request-grid">
            {[0, 1, 2].map((index) => <div className="tutor-skeleton" key={index} />)}
          </div>
        ) : requests.length ? (
          <>
            <div className="tutor-request-grid tutor-student-search-grid">
              {pageRequests.map((request) => (
                <TutorStudyingRequestCard
                  key={request.id}
                  request={request}
                  actionLabel="Ứng tuyển yêu cầu này"
                  proposal={proposalsQuery.data?.find((proposal) => proposal.studyingRequestId === request.id)}
                  applyInPlace
                />
              ))}
            </div>
            {pageCount > 1 && (
              <div className="tutor-marketplace-pagination" aria-label="Phân trang kết quả học viên">
                <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                  Trước
                </button>
                <span>Trang {page + 1}/{pageCount}</span>
                <button
                  type="button"
                  disabled={page + 1 >= pageCount}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Sau
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="tutor-empty">
            <h3>{activeFilters.search ? 'Không tìm thấy học viên phù hợp' : 'Chưa có yêu cầu học đang mở'}</h3>
            <p>Thử thay đổi bộ lọc hoặc quay lại sau để xem yêu cầu mới.</p>
          </div>
        )}
      </section>
    </>
  );
}
