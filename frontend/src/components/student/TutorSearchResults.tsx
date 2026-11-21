import { useCallback, useMemo, useState } from 'react';
import { useLocationOptions } from '../../hooks/useLocationOptions';
import { useResource } from '../../hooks/useResource';
import { getStudentApplications, type StudentApplication } from '../../services/studentApi';
import type { TutorSearchFilters } from '../../services/recommendationApi';
import { getTeachingRequests } from '../../services/teachingRequestApi';
import type { TeachingRequest } from '../../types/api';
import { StudentTeachingRequestCard } from './StudentTeachingRequestCard';
import '../../pages/tutor/tutor.css';

const PAGE_SIZE = 6;

interface Props {
  filters: TutorSearchFilters;
  searchTerm: string;
  isInitial?: boolean;
}

export function TutorSearchResults({ filters, searchTerm, isInitial = false }: Props) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const provinceId = filters.area?.scope === 'PROVINCE' ? (filters.area.provinceId ?? '') : '';
  const { districts } = useLocationOptions(provinceId);
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
  const requests = useMemo(
    () => filterTutorRequests(query.data?.requests ?? [], filters, searchTerm, districts),
    [query.data?.requests, filters, searchTerm, districts]
  );
  const pageCount = Math.ceil(requests.length / PAGE_SIZE);
  const currentPage = Math.min(page, Math.max(pageCount - 1, 0));
  const visibleRequests = requests.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  return (
    <section className="tutor-marketplace" aria-label="Kết quả tìm kiếm gia sư">
      <div className="tutor-section-heading">
        <div className="rec-section-title">
          <h2>Tất cả gia sư</h2>
          {!isInitial && <span className="rec-result-kind">Tìm theo bộ lọc</span>}
        </div>
        <span className="tutor-count">{requests.length} kết quả</span>
      </div>
      {!isInitial && <p className="rec-hint">Kết quả tìm kiếm theo từ khóa, môn, lớp, khu vực, giá và lịch học.</p>}
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
          <h3>Không tìm thấy gia sư phù hợp</h3>
          <p>Thử thay đổi bộ lọc để xem thêm gia sư.</p>
        </div>
      )}
    </section>
  );
}

function filterTutorRequests(
  requests: TeachingRequest[],
  filters: TutorSearchFilters,
  searchTerm: string,
  provinceDistricts: Array<{ id: string }>
) {
  const term = searchTerm.trim().toLocaleLowerCase('vi-VN');
  const requestedSlots = filters.availabilities ?? [];
  const provinceDistrictIds = new Set(provinceDistricts.map((district) => district.id));
  return requests.filter((request) => {
    const searchable = [
      request.tutorName,
      request.title,
      request.subjectName,
      request.customSubjectName,
      request.grades.map((grade) => grade.name).join(' '),
      request.districts.map((district) => district.name).join(' '),
      request.detailAddress,
      request.description,
      request.note,
      request.preferredSchedule
    ]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('vi-VN');
    const area = filters.area;
    const matchesArea =
      !area ||
      area.scope === 'ANY' ||
      (area.scope === 'DISTRICTS' && request.districts.some((district) => area.districtIds?.includes(district.id))) ||
      (area.scope === 'PROVINCE' &&
        (!provinceDistrictIds.size || request.districts.some((district) => provinceDistrictIds.has(district.id))));
    const matchesPrice =
      (filters.price?.min === undefined ||
        filters.price.min === null ||
        request.expectedPrice === null ||
        request.expectedPrice >= filters.price.min) &&
      (filters.price?.max === undefined ||
        filters.price.max === null ||
        request.expectedPrice === null ||
        request.expectedPrice <= filters.price.max);
    const matchesSchedule =
      !requestedSlots.length ||
      requestedSlots.some((wanted) =>
        request.availabilities.some(
          (slot) =>
            slot.dayOfWeek === wanted.dayOfWeek && slot.endTime > wanted.startTime && slot.startTime < wanted.endTime
        )
      );
    return (
      (!term || searchable.includes(term)) &&
      (!filters.subjectId || request.subjectId === filters.subjectId) &&
      (!filters.gradeId || request.grades.some((grade) => grade.id === filters.gradeId)) &&
      (!filters.learningMode || filters.learningMode === 'ANY' || request.teachingMode === filters.learningMode) &&
      matchesArea &&
      matchesPrice &&
      matchesSchedule
    );
  });
}
