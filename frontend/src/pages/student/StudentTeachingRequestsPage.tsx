import { useCallback, useMemo, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { useLocationOptions } from '../../hooks/useLocationOptions';
import { getTeachingRequests } from '../../services/teachingRequestApi';
import { getStudentApplications, type StudentApplication } from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { TutorStudentSearchFilters, type TutorStudentFilters } from '../tutor/components/TutorStudentSearchFilters';
import { StudentTeachingRequestCard } from '../../components/student/StudentTeachingRequestCard';
import '../tutor/tutor.css';

const PAGE_SIZE = 6;

const initialFilters: TutorStudentFilters = {
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
};

export function StudentTeachingRequestsPage() {
  const [filters, setFilters] = useState<TutorStudentFilters>(initialFilters);
  const [activeFilters, setActiveFilters] = useState<TutorStudentFilters>(initialFilters);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const { districts } = useLocationOptions(activeFilters.provinceId);

  const load = useCallback(async (signal: AbortSignal) => {
    const [requests, applications] = await Promise.all([
      getTeachingRequests(100, {}, signal),
      getStudentApplications(undefined, 0, 100, signal)
    ]);
    return { requests, applications: applications.content };
  }, []);
  const query = useResource(load, revision);

  const applicationsByRequest = useMemo(() => {
    const byRequest = new Map<string, StudentApplication>();
    query.data?.applications.forEach((application) => {
      if (!byRequest.has(application.teachingRequestId)) byRequest.set(application.teachingRequestId, application);
    });
    return byRequest;
  }, [query.data?.applications]);

  const filteredRequests = useMemo(
    () => filterTeachingRequests(query.data?.requests ?? [], activeFilters, districts),
    [activeFilters, districts, query.data?.requests]
  );
  const pageCount = Math.ceil(filteredRequests.length / PAGE_SIZE);
  const currentPage = Math.min(page, Math.max(pageCount - 1, 0));
  const visibleRequests = filteredRequests.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  return (
    <>
      <header className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">TÌM KIẾM GIA SƯ</span>
          <h1>Tìm kiếm gia sư</h1>
          <p>Tìm những yêu cầu dạy phù hợp với nhu cầu học tập của bạn.</p>
        </div>
        <span className="tutor-count">{filteredRequests.length} kết quả</span>
      </header>

      <section className="tutor-marketplace" aria-label="Tìm kiếm gia sư">
        <TutorStudentSearchFilters
          filters={filters}
          onChange={setFilters}
          onSearch={() => {
            setActiveFilters(filters);
            setPage(0);
          }}
          searchPlaceholder="Tìm theo tên gia sư, môn học hoặc lớp"
          searchAriaLabel="Tìm theo tên gia sư, môn học hoặc lớp"
          scheduleLabel="Thời gian có thể học"
        />

        {query.error ? (
          <div className="tutor-error" role="alert">
            {query.error}
          </div>
        ) : query.isLoading ? (
          <div className="tutor-request-grid">
            {[0, 1, 2].map((index) => (
              <div className="tutor-skeleton" key={index} />
            ))}
          </div>
        ) : visibleRequests.length ? (
          <>
            <div className="tutor-request-grid tutor-student-search-grid">
              {visibleRequests.map((request) => (
                <StudentTeachingRequestCard
                  key={request.id}
                  request={request}
                  application={applicationsByRequest.get(request.id)}
                  onChanged={() => setRevision((value) => value + 1)}
                />
              ))}
            </div>
            {pageCount > 1 && (
              <div className="tutor-marketplace-pagination" aria-label="Phân trang tìm kiếm gia sư">
                <button type="button" disabled={currentPage === 0} onClick={() => setPage((value) => value - 1)}>
                  Trước
                </button>
                <span>
                  Trang {currentPage + 1}/{pageCount}
                </span>
                <button
                  type="button"
                  disabled={currentPage + 1 >= pageCount}
                  onClick={() => setPage((value) => value + 1)}
                >
                  Sau
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="tutor-empty">
            <h3>{activeFilters.search ? 'Không tìm thấy yêu cầu phù hợp' : 'Chưa có yêu cầu dạy đang mở'}</h3>
            <p>Thử thay đổi bộ lọc hoặc quay lại sau để xem yêu cầu mới.</p>
          </div>
        )}
      </section>
    </>
  );
}

function filterTeachingRequests(
  requests: TeachingRequest[],
  filters: TutorStudentFilters,
  provinceDistricts: Array<{ id: string }>
) {
  const term = filters.search.trim().toLocaleLowerCase('vi-VN');
  const minimum = filters.minBudget === '' ? null : Number(filters.minBudget);
  const maximum = filters.maxBudget === '' ? null : Number(filters.maxBudget);
  const requestedSlots = filters.scheduleSlots.filter((slot) => slot.dayOfWeek || slot.startTime || slot.endTime);
  const provinceDistrictIds = new Set(provinceDistricts.map((district) => district.id));

  return requests.filter((request) => {
    const grades = request.grades.map((grade) => grade.name).join(' ');
    const districts = request.districts.map((district) => district.name).join(' ');
    const searchable = [
      request.tutorName,
      request.title,
      request.subjectName,
      request.customSubjectName,
      grades,
      districts,
      request.detailAddress,
      request.description,
      request.note,
      request.preferredSchedule
    ]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('vi-VN');
    const matchesText = !term || searchable.includes(term);
    const matchesSubject = !filters.subjectId || request.subjectId === filters.subjectId;
    const matchesGrade = !filters.gradeId || request.grades.some((grade) => grade.id === filters.gradeId);
    const matchesMode = filters.learningMode === 'ALL' || request.teachingMode === filters.learningMode;
    const matchesProvince =
      !filters.provinceId ||
      !provinceDistrictIds.size ||
      request.districts.some((district) => provinceDistrictIds.has(district.id));
    const matchesDistrict =
      !filters.districtId || request.districts.some((district) => district.id === filters.districtId);
    const matchesBudget =
      (minimum === null || request.expectedPrice === null || request.expectedPrice >= minimum) &&
      (maximum === null || request.expectedPrice === null || request.expectedPrice <= maximum);
    const matchesSchedule =
      requestedSlots.length === 0 ||
      requestedSlots.some((filterSlot) =>
        request.availabilities.some((slot) => {
          const matchesDay = !filterSlot.dayOfWeek || slot.dayOfWeek === Number(filterSlot.dayOfWeek);
          const matchesStart = !filterSlot.startTime || slot.endTime > filterSlot.startTime;
          const matchesEnd = !filterSlot.endTime || slot.startTime < filterSlot.endTime;
          return matchesDay && matchesStart && matchesEnd;
        })
      );

    return (
      matchesText &&
      matchesSubject &&
      matchesGrade &&
      matchesMode &&
      matchesProvince &&
      matchesDistrict &&
      matchesBudget &&
      matchesSchedule
    );
  });
}
